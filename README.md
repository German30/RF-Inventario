# RF Inventario

App Android nativa (Kotlin + Jetpack Compose) para operar un depósito con
terminales RF (Zebra, Honeywell…) o con la cámara del teléfono: **recepción,
picking, entradas, salidas, transferencias, ajustes y conteo cíclico**, con
stock por ubicación y lote, y **funcionamiento completo sin conexión**.

Es el cliente de piso de un ecosistema de tres piezas:

| Pieza | Repo | Rol |
|---|---|---|
| App Android | este | Operarios en el depósito (escaneo) |
| API Spring Boot | `RF-Inventario-Web-Panel-Back` | Única dueña de MySQL, reglas de stock, auditoría |
| Panel web Angular | `RF-Inventario-Web-Panel-Front` | Administración: pedidos, conteos, ubicaciones, reportes |

## Qué hace

**Operaciones guiadas por escaneo** — el operario no toca la pantalla entre lecturas:
- *Entrada / Salida / Ajuste*: escanear estante → producto (re-escanear suma 1) → confirmar. Motivos predefinidos (rotura, vencido, consumo interno…).
- *Transferencia*: estante origen → producto → estante destino.
- *Recepción* contra pedidos de compra y *picking* contra pedidos de venta, con avance por línea y **sugerencia de ubicación FEFO** (primero lo que vence antes).
- *Conteo cíclico* por ubicación o libre, **ciego** por defecto; el supervisor revisa y aplica las diferencias.
- *Consulta de ubicación*: escanear la etiqueta de un estante muestra qué contiene.

**La app distingue sola qué se escaneó** (`ScanRouter`): si el código es una
ubicación conocida va al campo de ubicación; si es un producto (SKU o cualquier
**código alternativo**, p. ej. el EAN del proveedor), lo selecciona. Funciona offline contra el caché.

**Offline-first**
- El catálogo vive en Room y se sincroniza incrementalmente (`GET /api/sync?desde=`) con WorkManager.
- Toda operación lleva un `clientOpId` (UUID). Si no hay red, **se encola**, el stock se refleja localmente y se envía sola al volver la conexión, en orden. El backend descarta duplicados: un timeout que en realidad llegó no mueve stock dos veces.
- Las rechazadas por el servidor (p. ej. stock insuficiente) quedan en *Pendientes* para que el operario decida.

**Producto completo**: categoría, unidad, stock mínimo con alertas, costo (valorización), control por lote/vencimiento, códigos alternativos, historial por producto.

**Inicio con KPIs**: productos, unidades, valor del inventario, bajo mínimo, sin stock, movimientos del día, pedidos y conteos pendientes, lotes por vencer.

**Etiquetas ZPL** de producto y de ubicación, enviadas por TCP 9100 a una Zebra (o compartidas como texto si no hay impresora). **Export CSV** del inventario desde el caché (funciona offline).

**Seguridad y operación**
- Roles: `OPERARIO` y `SUPERVISOR` (aplicar conteos, borrar productos). El backend lo valida; la app solo adapta la UI.
- Token JWT cifrado con AES-GCM en el **Android Keystore**.
- **Cierre de sesión por inactividad** (terminales compartidas), configurable o fijado por MDM.
- Bloqueo del login tras 5 PIN fallidos (backend, HTTP 429).
- Release sin tráfico HTTP en claro salvo excepción explícita (`-PallowCleartext=true`).
- Crash reporting autohospedado: el crash se guarda y se envía al backend en el próximo arranque (sin SDKs de terceros).

**Feedback de depósito**: beep + vibración distintos para OK, encolado y error. Idiomas: español e inglés.

## Stack

| Área | Tecnología |
|------|-----------|
| Lenguaje / UI | Kotlin 2.0, Jetpack Compose + Material 3, Navigation Compose |
| Arquitectura | MVVM (ViewModel + StateFlow), DI manual (`InventarioApplication`) |
| Red | Retrofit + OkHttp (JWT por interceptor) |
| Local | Room (catálogo, ubicaciones, cola de operaciones), WorkManager |
| Escaneo | Zebra DataWedge (intents), keyboard wedge, CameraX + ML Kit (offline) |
| minSdk / targetSdk | 24 / 35 |
| Distribución | APK firmado vía MDM (App Restrictions) — ver [docs/RELEASE.md](docs/RELEASE.md) |

## Estructura

```
app/src/main/java/com/rf/inventario/
├── InventarioApplication.kt   # contenedor de dependencias, arranque de sync y crash reporter
├── MainActivity.kt            # host Compose, teclas del lector, bloqueo por inactividad
├── auth/                      # sesión (token cifrado en Keystore), login, motivos de cierre
├── config/                    # ManagedConfig (MDM) + AppSettings (preferencias del operario)
├── data/
│   ├── Product.kt, Inventory.kt          # modelo de dominio
│   ├── ops/OperationsRepository.kt       # cola offline idempotente  ← núcleo offline
│   ├── sync/SyncRepository.kt, SyncWorker.kt
│   ├── local/                            # Room: productos, ubicaciones, pending_ops
│   └── remote/                           # Retrofit: API, DTOs, traducción de errores
├── scanner/                   # DataWedge, wedge, ScanRouter, ScanFeedback
├── labels/                    # ZPL + impresión TCP 9100
├── export/                    # CSV
├── diagnostics/               # CrashReporter
└── ui/                        # una carpeta por módulo (home, movement, counts, orders, …)
```

## Configuración por MDM (App Restrictions)

| Clave | Tipo | Efecto |
|---|---|---|
| `apiBaseUrl` | string | URL del backend para ese dispositivo/grupo |
| `idleTimeoutMinutes` | int | Fija el cierre por inactividad (el operario no lo puede cambiar) |
| `printerHost` | string | Impresora Zebra de la zona |

## Puesta en marcha

1. Levantar el backend (`RF-Inventario-Web-Panel-Back`, puerto 8082). Aplica las migraciones Flyway solo.
2. Abrir esta carpeta en Android Studio, o por línea de comandos (con `local.properties` apuntando al SDK):
   ```
   ./gradlew assembleDebug        # APK de debug (backend en http://10.0.2.2:8082/ = localhost del emulador)
   ./gradlew testDebugUnitTest    # tests unitarios (JVM)
   ./gradlew installDebug
   ```
   Dispositivo físico: `-PapiBaseUrl=http://<IP-LAN>:8082/` o ver [docs/DEPLOY-WIFI.md](docs/DEPLOY-WIFI.md).
3. Usuarios de demo: operario `1001` / `1234`. Para probar funciones de supervisor, crear desde el panel un operario con rol `SUPERVISOR`.

Más detalle: [docs/SCANNING.md](docs/SCANNING.md) (lectores y escaneo), [docs/RELEASE.md](docs/RELEASE.md) (firma, MDM, HTTPS).

## Tests

- `OperationsRepositoryTest`: envío inmediato, rechazo, encolado offline con ajuste optimista, deduplicación por `clientOpId`, orden de la cola.
- `KeyboardWedgeScannerTest`: el Enter de una lectura (DOWN **y UP**) no llega a la UI.
- `ScanAndLabelsTest`: clasificación ubicación/producto, prefijos AIM, ZPL, CSV injection, bloqueo por inactividad.
- `ProductRepositoryTest`, `AuthRepositoryTest`.

CI: `.github/workflows/android-ci.yml` (tests + lint + APK en cada push/PR).
