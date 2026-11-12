package com.rf.inventario.data.remote

import com.rf.inventario.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Construye el cliente Retrofit contra el backend Spring.
 *
 * - Añade `Authorization: Bearer <token>` a cada petición cuando hay sesión.
 * - Si el backend responde 401 (token vencido) fuera del login, dispara
 *   [onUnauthorized] para que la app cierre sesión y vuelva al login.
 */
object ApiClient {

    fun create(
        baseUrl: String,
        token: () -> String?,
        onUnauthorized: () -> Unit,
    ): PanelApi {
        val auth = Interceptor { chain ->
            val original = chain.request()
            val sent = token()
            val request = sent
                ?.let { original.newBuilder().header("Authorization", "Bearer $it").build() }
                ?: original

            val response = chain.proceed(request)
            // Solo cuenta como sesión vencida si el 401 rechazó el token vigente. Una
            // petición sin token o lanzada con el token anterior (p.ej. sync que salió
            // antes del login y vuelve después) no debe tirar la sesión recién abierta.
            if (response.code == 401 &&
                sent != null && sent == token() &&
                !request.url.encodedPath.endsWith("/api/auth/login")
            ) {
                onUnauthorized()
            }
            response
        }

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PanelApi::class.java)
    }
}
