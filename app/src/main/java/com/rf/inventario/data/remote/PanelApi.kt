package com.rf.inventario.data.remote

import com.rf.inventario.data.remote.dto.AjusteStockDto
import com.rf.inventario.data.remote.dto.ConteoAbrirDto
import com.rf.inventario.data.remote.dto.ConteoDto
import com.rf.inventario.data.remote.dto.ConteoLineaBodyDto
import com.rf.inventario.data.remote.dto.DashboardDto
import com.rf.inventario.data.remote.dto.ErrorClienteDto
import com.rf.inventario.data.remote.dto.ExistenciaDto
import com.rf.inventario.data.remote.dto.LoginRequestDto
import com.rf.inventario.data.remote.dto.MovimientoDto
import com.rf.inventario.data.remote.dto.OperacionBodyDto
import com.rf.inventario.data.remote.dto.OperacionResultadoDto
import com.rf.inventario.data.remote.dto.PaginaDto
import com.rf.inventario.data.remote.dto.PedidoDto
import com.rf.inventario.data.remote.dto.PedidoEscaneoDto
import com.rf.inventario.data.remote.dto.PingDto
import com.rf.inventario.data.remote.dto.ProductoBodyDto
import com.rf.inventario.data.remote.dto.ProductoDto
import com.rf.inventario.data.remote.dto.SesionDto
import com.rf.inventario.data.remote.dto.SyncDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** Endpoints del backend Spring rf-inventario-panel-api. */
interface PanelApi {

    /** Público: la app lo llama al arrancar para verificar el servidor. */
    @GET("api/ping")
    suspend fun ping(): PingDto

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginRequestDto): SesionDto

    // ---- Productos
    @GET("api/productos")
    suspend fun productos(@Query("q") q: String?): List<ProductoDto>

    @GET("api/productos/{id}")
    suspend fun producto(@Path("id") id: Long): ProductoDto

    @GET("api/productos/sku/{sku}")
    suspend fun productoPorSku(@Path("sku") sku: String): ProductoDto

    @POST("api/productos")
    suspend fun crear(@Body body: ProductoBodyDto): ProductoDto

    @PUT("api/productos/{id}")
    suspend fun actualizar(@Path("id") id: Long, @Body body: ProductoBodyDto): ProductoDto

    @PATCH("api/productos/{id}/stock")
    suspend fun ajustarStock(@Path("id") id: Long, @Body body: AjusteStockDto): ProductoDto

    @DELETE("api/productos/{id}")
    suspend fun eliminar(@Path("id") id: Long): Response<Unit>

    @GET("api/productos/{id}/existencias")
    suspend fun existencias(@Path("id") id: Long): List<ExistenciaDto>

    @GET("api/productos/{id}/movimientos")
    suspend fun movimientosDe(@Path("id") id: Long): List<MovimientoDto>

    @GET("api/productos/categorias")
    suspend fun categorias(): List<String>

    // ---- Operaciones e historial
    @POST("api/operaciones")
    suspend fun operar(@Body body: OperacionBodyDto): OperacionResultadoDto

    @GET("api/movimientos")
    suspend fun movimientos(
        @Query("sku") sku: String?,
        @Query("accion") accion: String?,
        @Query("pagina") pagina: Int,
        @Query("tamanio") tamanio: Int = 50,
    ): PaginaDto<MovimientoDto>

    // ---- Ubicaciones
    @GET("api/ubicaciones/{codigo}/contenido")
    suspend fun contenidoUbicacion(@Path("codigo") codigo: String): List<ExistenciaDto>

    // ---- Sync, dashboard, diagnóstico
    @GET("api/sync")
    suspend fun sync(@Query("desde") desde: Long): SyncDto

    @GET("api/dashboard")
    suspend fun dashboard(): DashboardDto

    @POST("api/diagnostico/errores")
    suspend fun reportarError(@Body body: ErrorClienteDto): Response<Unit>

    // ---- Conteos
    @GET("api/conteos")
    suspend fun conteos(@Query("abiertos") abiertos: Boolean = true): List<ConteoDto>

    @GET("api/conteos/{id}")
    suspend fun conteo(@Path("id") id: Long): ConteoDto

    @POST("api/conteos")
    suspend fun abrirConteo(@Body body: ConteoAbrirDto): ConteoDto

    @POST("api/conteos/{id}/lineas")
    suspend fun contar(@Path("id") id: Long, @Body body: ConteoLineaBodyDto): ConteoDto

    @POST("api/conteos/{id}/{accion}")
    suspend fun accionConteo(@Path("id") id: Long, @Path("accion") accion: String): ConteoDto

    // ---- Pedidos
    @GET("api/pedidos")
    suspend fun pedidos(@Query("tipo") tipo: String?, @Query("abiertos") abiertos: Boolean = true): List<PedidoDto>

    @GET("api/pedidos/{id}")
    suspend fun pedido(@Path("id") id: Long): PedidoDto

    @POST("api/pedidos/{id}/escaneos")
    suspend fun escanearPedido(@Path("id") id: Long, @Body body: PedidoEscaneoDto): PedidoDto

    @POST("api/pedidos/{id}/completar")
    suspend fun completarPedido(@Path("id") id: Long): PedidoDto
}
