# Escaneo de códigos

La app resuelve la captura de códigos en este orden, sin que el usuario configure nada:

| Situación del equipo | Qué hace la app |
|---|---|
| Terminal RF (Zebra, Honeywell, Datalogic…) con DataWedge | Escucha el gatillo físico vía **DataWedge** (broadcast) |
| Terminal RF con lector en modo *keyboard wedge* | Captura el barrido de teclas terminado en Enter/Tab |
| Teléfono / tablet sin lector | Abre el **escáner por cámara** (CameraX + ML Kit, offline) |
| Cualquiera | Botón *"Usar cámara"* siempre disponible como plan B |

## Arquitectura

```
scanner/
├── ScanResult.kt            # value + symbology + source (HARDWARE_WEDGE | DATAWEDGE | CAMERA)
├── ScannerCapabilities.kt   # heurística: ¿este equipo tiene lector físico?
├── KeyboardWedgeScanner.kt  # sniff de KeyEvent con guarda de tiempo (120 ms)
├── DataWedgeScanner.kt      # BroadcastReceiver dinámico + auto-perfil
├── DataWedgeConfig.kt       # crea el perfil DataWedge por intents
└── BarcodeAnalyzer.kt       # ImageAnalysis.Analyzer con ML Kit (modelo embebido)

ui/scanner/
├── ScanAction.kt            # icono en la barra; elige método y entrega ScanResult
├── HardwareScanSheet.kt     # hoja "esperando lectura…" con fallback a cámara
└── CameraScannerScreen.kt   # preview a pantalla completa + visor + linterna
```

- `MainActivity.dispatchKeyEvent` alimenta `KeyboardWedgeScanner`.
- `InventarioApplication.onCreate` arranca `DataWedgeScanner` (inerte fuera de Zebra).
- Flujo: escaneo → `ProductsViewModel.findBySku` → abre el editor (producto existente o nuevo con el SKU precargado) + snackbar.

## Zebra DataWedge

Al iniciar, `DataWedgeConfig` intenta crear el perfil **RFInventario** automáticamente:

- plugin BARCODE activado (selección de escáner `auto`),
- salida por **Intent**, acción `com.rf.inventario.ACTION_DATAWEDGE_SCAN`, entrega *broadcast*,
- salida por teclado (Keystroke) **desactivada** para no duplicar el dato.

### Configuración manual (si el auto-perfil falla)

1. Abrir **DataWedge** → crear perfil `RFInventario`.
2. *Associated apps* → añadir `com.rf.inventario`, actividad `*`.
3. *Barcode input* → ON.
4. *Keystroke output* → OFF.
5. *Intent output* → ON
   - Intent action: `com.rf.inventario.ACTION_DATAWEDGE_SCAN`
   - Intent delivery: **Broadcast intent**

## Formatos soportados (cámara)

EAN-13/8, UPC-A/E, Code 128/39/93, ITF, Codabar, QR, DataMatrix, PDF417, Aztec
(`Barcode.FORMAT_ALL_FORMATS`). Para acelerar el enfoque en almacén conviene
restringirlo a los que realmente se usan.

## Límites conocidos / siguientes pasos

- La detección de "tiene lector físico" es heurística (marca + features + DataWedge).
  Falso negativo → el usuario toca *"Usar cámara"*. Falso positivo (raro) → pendiente
  añadir un ajuste manual *Auto / Hardware / Cámara* persistido.
- El sniff de wedge no distingue el barrido si alguien teclea muy rápido en un
  teclado físico; la guarda de 120 ms cubre el caso normal.
- Sin de-duplicado entre lecturas repetidas de cámara más allá del "primer hit y
  cierra"; para un modo de conteo continuo habrá que añadir *debounce* por valor.
