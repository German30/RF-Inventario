# RF-Inventario 2.0: de CRUD de productos a app RF de depósito

**Fecha:** 11/11/2026

La versión 2.0 convierte la app de un CRUD de productos con escáner en una app RF de depósito completa. Abarca los tres proyectos:

| Proyecto | Carpeta |
|---|---|
| App Android (Kotlin / Compose) | `RF-Inventario` |
| Backend (Spring Boot 3, Java 21) | `RF-Inventario-Web-Panel-Back` |
| Panel web (Angular 20) | `RF-Inventario-Web-Panel-Front` |

> **Estado:** todos los cambios están sin commitear en `main`.

## Verificación

- **Backend:** 36 tests en verde. La migración V6 se aplicó sobre una **copia** de la base real y funcionó bien.
- **App:** 26 tests y lint en verde.
- **Panel:** `ng build` compila.
- **Emulador (contra la copia de la base):**
  - Una entrada con conexión.
  - Una salida sin red, que se envió sola al volver la conexión y no se duplicó.
  - La sesión cifrada sobrevivió al reinicio de la app.
- La base de desarrollo no se tocó. Las bases temporales de prueba se borraron.

## Funcionalidades nuevas

### Operación en el depósito (app)

- Entrada, salida, transferencia y ajuste guiados por escaneo, con motivos. Volver a escanear el mismo producto suma 1.
- La app distingue sola si se leyó un estante o un producto, incluso por el EAN del proveedor.
- **Recepción** contra pedidos de compra.
- **Picking** contra pedidos de venta, que sugiere sacar primero lo que vence antes (FEFO).
- **Conteo cíclico**, ciego por defecto. El supervisor revisa las diferencias y las aplica.
- Stock **por ubicación y por lote**, con vencimiento, stock mínimo, costo y alertas.
- Beep y vibración distintos para OK, guardado sin red y error.

### Sin conexión

- La lista de productos se lee del teléfono (Room) y se sincroniza sola cuando hay red.
- Cada operación hecha sin red se guarda en una cola y se envía sola al volver la conexión.
- El servidor descarta los reintentos (`clientOpId`), así que ninguna operación se duplica.

### Resto

- Pantalla de inicio con indicadores: productos, unidades, valor, bajo mínimo y movimientos del día.
- Historial global de movimientos.
- Etiquetas para impresoras Zebra (ZPL).
- Exportación a CSV.
- Español e inglés.
- **Seguridad:**
  - Rol supervisor.
  - Sesión guardada cifrada (Android Keystore).
  - Cierre por inactividad, configurable por MDM.
  - Bloqueo tras 5 PIN fallidos.
  - HTTPS obligatorio en el APK de producción.
- **Panel web:**
  - Páginas nuevas de Resumen, Pedidos, Conteos y Ubicaciones.
  - Ficha de producto completa.

## Bugs encontrados y corregidos

1. **Nadie podía entrar al panel web.** El panel usaba el login de la app y exigía el rol `SUPERVISOR`. Desde la migración V5, los administradores entran por otro endpoint (`/api/panel/auth/login`) con el rol `PANEL`.
2. **Un escaneo podía cerrar la sesión.** Con un lector que escribe como teclado, el Enter final de cada lectura llegaba al botón que tuviera foco. Se reprodujo en el emulador. Ahora el lector consume ese Enter, y cerrar sesión pide confirmación.
3. **Borrar un producto sin permiso se tomaba como éxito.** Un 403 o un 404 no se detectaba.
4. **Crash en Android 7** (terminales Zebra viejas). La app usaba fechas de `java.time`, que no existen en esa versión de Android. Lo detectó lint.

## Antes de usarlo

- **Hacer un backup de `rf_inventario` antes de levantar el backend.** Al arrancar, el backend aplica la migración V6, que convierte la ubicación de texto en ubicaciones reales. Sobre la copia funcionó bien, pero el cambio no se deshace.
- Las funciones de supervisor necesitan un operario con rol `SUPERVISOR`. Se crea desde el panel.
- Para compilar el APK de producción contra un backend `http://`, hay que habilitarlo explícitamente (`-PallowCleartext=true`). El script `scripts/build-release.ps1` ya lo hace solo.
- Se sacaron del control de versiones los 1061 archivos de `app/build/`. Siguen en disco.

## Pendiente para estar a la par de los líderes

- RFID y números de serie (los lotes ya están).
- Varios depósitos como entidad propia (hoy hay zonas).
- Fotos de producto.
- Integración con ERPs.
- Iniciar sesión sin red.
- Escaneo continuo con la cámara.
- Paginación real en la app para catálogos de más de 500 productos visibles a la vez (hoy la búsqueda acota los resultados).
