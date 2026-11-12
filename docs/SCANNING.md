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

├── ScanRouter.kt            # ¿lo escaneado es una ubicación o un producto? (contra el caché, offline)
└── ScanFeedback.kt          # beep + vibración: OK / encolado / error

ui/common/Common.kt
├── ScanListener             # escucha el lector mientras la pantalla está visible (RESUMED)
├── CameraScanButton         # plan B: cámara (CameraScannerScreen) en un diálogo
└── CodeEntryField           # tipeo manual (etiqueta dañada) con botón de cámara
```

- `MainActivity.dispatchKeyEvent` alimenta `KeyboardWedgeScanner`.
- `InventarioApplication.onCreate` arranca `DataWedgeScanner` (inerte fuera de Zebra);
  `hardwareScans` une ambas fuentes.
- **Escaneo continuo**: cada pantalla de trabajo (movimientos, conteo, recepción,
  picking, ubicación, inicio) se suscribe con `ScanListener`; no hace falta tocar
  un botón antes de cada lectura.
- **Ruteo automático** (`ScanRouter`): si el código es una ubicación conocida se
  usa como ubicación (o destino, en una transferencia); si es un producto (SKU o
  código alternativo) se selecciona; si es desconocido se consulta al backend y,
  si tampoco existe, se ofrece darlo de alta. Se limpian espacios y prefijos AIM (`]E0`, `]C1`…).

### El Enter del lector no debe llegar a la UI

Un lector en modo teclado termina cada lectura con Enter (o Tab, o CR+LF).
Compose dispara el click del botón que tenga foco al **soltar** Enter, así que
si solo se consumiera el `ACTION_DOWN`, cada escaneo "tocaría" el botón
enfocado (en la práctica: cerraba la sesión). `KeyboardWedgeScanner` consume
el DOWN **y el UP** del terminador de una lectura, y también un segundo
terminador pegado. Además, cerrar sesión pide confirmación.

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
- La cámara lee de a un código ("primer hit y cierra"); para trabajo intensivo
  conviene un lector físico. Un modo de cámara continuo con *debounce* por valor
  queda como mejora.
- RFID UHF (Zebra RFID3 API) no está implementado.
