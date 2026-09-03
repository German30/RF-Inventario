# RF Inventario

Aplicación Android nativa (Kotlin + Jetpack Compose) para gestión de inventario:
alta, edición, búsqueda y borrado de productos con persistencia local.

## Stack

| Área | Tecnología |
|------|-----------|
| Lenguaje | Kotlin 2.0 |
| UI | Jetpack Compose + Material 3 |
| Arquitectura | MVVM (ViewModel + StateFlow) |
| Persistencia | Room |
| Async | Coroutines / Flow |
| Build | Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`) |
| minSdk / targetSdk | 24 / 35 |

## Estructura

```
app/src/main/java/com/rf/inventario/
├── InventarioApplication.kt      # contenedor DI manual + arranque de scanners
├── MainActivity.kt               # host de Compose + captura de teclas (wedge)
├── data/
│   ├── Product.kt                # entidad Room
│   ├── ProductDao.kt             # consultas (incluye getBySku)
│   ├── InventarioDatabase.kt     # base de datos
│   └── ProductRepository.kt      # capa de acceso a datos
├── scanner/
│   ├── ScanResult.kt             # value + symbology + source
│   ├── ScannerCapabilities.kt    # ¿el equipo tiene lector físico?
│   ├── KeyboardWedgeScanner.kt   # lector en modo teclado
│   ├── DataWedgeScanner.kt       # Zebra DataWedge (broadcast)
│   ├── DataWedgeConfig.kt        # auto-perfil de DataWedge
│   └── BarcodeAnalyzer.kt        # ML Kit sobre frames de CameraX
└── ui/
    ├── theme/                    # Color / Type / Theme
    ├── scanner/
    │   ├── ScanAction.kt         # botón de la barra; elige método
    │   ├── HardwareScanSheet.kt  # "esperando lectura…" + fallback
    │   └── CameraScannerScreen.kt
    └── products/
        ├── ProductsViewModel.kt
        ├── ProductsScreen.kt     # lista + búsqueda + escaneo
        └── ProductEditorDialog.kt
```

## Escaneo (lector físico o cámara)

El botón de escaneo de la barra superior elige el método automáticamente: lector
RF por hardware si el equipo lo tiene (Zebra DataWedge o *keyboard wedge*), y
cámara con ML Kit (offline, sin Google Play Services) en cualquier otro caso.
Siempre hay un botón *"Usar cámara"* como plan B. Detalle en
[docs/SCANNING.md](docs/SCANNING.md).

## Puesta en marcha

1. Abrir la carpeta en **Android Studio** (Ladybug o superior). El IDE sincroniza
   Gradle y descarga el SDK necesario.
2. Si compilas por línea de comandos, crea `local.properties` en la raíz con la
   ruta de tu SDK:
   ```
   sdk.dir=C\:\\Users\\TU_USUARIO\\AppData\\Local\\Android\\Sdk
   ```
3. Compilar / probar / instalar:
   ```
   ./gradlew assembleDebug      # APK de debug
   ./gradlew test               # tests unitarios (JVM)
   ./gradlew connectedCheck     # tests instrumentados (requiere emulador/dispositivo)
   ./gradlew installDebug       # instala en el dispositivo conectado
   ```

> El binario `gradle/wrapper/gradle-wrapper.jar` está incluido (Gradle 8.11.1).

## Roadmap sugerido

- Ajuste manual de modo de escaneo (Auto / Hardware / Cámara) persistido.
- Modo de conteo de inventario: escaneo en serie con acumulado y ajuste de stock.
- RFID UHF vía SDK del fabricante (Zebra RFID3 API).
- Sincronización con backend (Retrofit + WorkManager).
- Exportar inventario a CSV.
- Inyección de dependencias con Hilt.
