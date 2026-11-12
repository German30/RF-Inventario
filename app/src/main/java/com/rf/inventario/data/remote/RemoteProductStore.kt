package com.rf.inventario.data.remote

import com.google.gson.Gson
import com.rf.inventario.data.ConflictException
import com.rf.inventario.data.NetworkException
import com.rf.inventario.data.Product
import com.rf.inventario.data.ProductStore
import com.rf.inventario.data.remote.dto.ConflictoEdicionDto
import com.rf.inventario.data.remote.dto.toBody
import com.rf.inventario.data.remote.dto.toDomain
import retrofit2.HttpException
import java.io.IOException

/** [ProductStore] respaldado por el backend Spring. */
class RemoteProductStore(private val api: PanelApi) : ProductStore {

    override suspend fun getAll(): List<Product> =
        apiCall { api.productos(null).map { it.toDomain() } }

    override suspend fun search(query: String): List<Product> =
        apiCall { api.productos(query).map { it.toDomain() } }

    override suspend fun getById(id: Long): Product? = orNullOn404 { api.producto(id).toDomain() }

    /** Por SKU o por cualquier código alternativo (el backend resuelve ambos). */
    override suspend fun getBySku(sku: String): Product? = orNullOn404 { api.productoPorSku(sku).toDomain() }

    override suspend fun insert(product: Product): Product =
        apiCall { api.crear(product.toBody()).toDomain() }

    override suspend fun update(product: Product): Product =
        try {
            api.actualizar(product.id, product.toBody()).toDomain()
        } catch (e: HttpException) {
            if (e.code() == 409) {
                // 409 puede ser conflicto de versión (trae `actual`) o una regla
                // de negocio (SKU repetido): solo el primero es un ConflictException.
                val cuerpo = cuerpoDe(e)
                throw conflictoDe(cuerpo) ?: traducir(e, cuerpo)
            }
            throw traducir(e)
        } catch (e: IOException) {
            throw NetworkException(cause = e)
        }

    override suspend fun delete(product: Product) {
        apiCall {
            val response = api.eliminar(product.id)
            // Response<Unit> no lanza en 4xx/5xx por sí solo.
            if (!response.isSuccessful) throw HttpException(response)
        }
    }

    private suspend fun orNullOn404(block: suspend () -> Product): Product? =
        try {
            block()
        } catch (e: HttpException) {
            if (e.code() == 404) null else throw traducir(e)
        } catch (e: IOException) {
            throw NetworkException(cause = e)
        }

    private fun conflictoDe(cuerpo: String?): ConflictException? {
        if (cuerpo == null) return null
        val dto = runCatching { Gson().fromJson(cuerpo, ConflictoEdicionDto::class.java) }.getOrNull()
        if (dto?.actual == null) return null
        return ConflictException(
            message = dto.message ?: "Otro cambio se guardó mientras editabas.",
            serverProduct = dto.actual.toDomain(),
        )
    }
}
