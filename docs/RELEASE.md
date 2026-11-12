# Release y distribución por MDM

RF Inventario es de uso interno: no se publica en Play Store. Se distribuye
como APK firmado, pusheado por el MDM de la organización (Intune, Workspace
ONE, SOTI MobiControl, consola de Android Enterprise, o el que corresponda).

## 1. La clave de firma

**`release-keystore.jks` + `keystore.properties`** (ambos en la raíz del repo,
ambos gitignored — nunca se commitean) son la ÚNICA clave que va a poder
firmar actualizaciones de la app en todos los equipos donde ya esté instalada.
Si se pierde, la única salida es desinstalar y reinstalar en cada dispositivo
(con el MDM no es grave, pero es evitable).

**Guardalos ya mismo en un lugar seguro fuera del repo** (gestor de
contraseñas del equipo, vault de la organización, secreto de la consola del
MDM). `git` nunca los va a respaldar.

Datos de la clave actual:

| | |
|---|---|
| Archivo | `release-keystore.jks` (PKCS12) |
| Alias | `rf-inventario` |
| Algoritmo | RSA 2048, SHA384withRSA |
| Validez | 10.000 días (~27 años) desde que se generó |

### Si hay que regenerarla (rotar la clave)

Solo si se perdió o se filtró la actual — implica reinstalar en todos los
equipos, porque Android no deja actualizar una app con una firma distinta.

```powershell
keytool -genkeypair -v -keystore release-keystore.jks -storetype PKCS12 `
  -alias rf-inventario -keyalg RSA -keysize 2048 -validity 10000
```
(con PKCS12, `storePassword` y `keyPassword` terminan siendo el mismo valor;
`keytool` lo avisa y lo hace solo). Después completá `keystore.properties`
(copiá `keystore.properties.example`) con esos datos.

## 2. Versionado

En `app/build.gradle.kts`:

```kotlin
versionCode = 1       // entero, SIEMPRE mayor que el anterior en cada build que se distribuye
versionName = "1.0.0" // solo texto, para mostrar
```

El MDM decide si hay actualización comparando `versionCode`. **Convención:**
bumpear `versionCode` en +1 en cada build que se sube al MDM, y `versionName`
en semver (`1.0.0` → `1.1.0` funcionalidad nueva, `1.0.1` fix). Se hace a mano;
si el equipo crece conviene automatizarlo desde CI.

## 3. Compilar el release firmado

```powershell
.\scripts\build-release.ps1
```

Corre `assembleRelease` y muestra el APK final, su tamaño y la firma
(`apksigner verify --print-certs`). Si falta `keystore.properties` avisa y el
APK sale como `app-release-unsigned.apk` (no instalable en ningún equipo).

El APK queda en `app\build\outputs\apk\release\app-release.apk` — es lo que
se sube a la consola del MDM.

## 4. URL del backend: fija vs. administrada por el MDM

La app soporta las dos formas, en este orden de prioridad:

1. **Configuración administrada del MDM** ("App Restrictions" / Android
   Enterprise) — la clave `apiBaseUrl`, declarada en
   [`app/src/main/res/xml/app_restrictions.xml`](../app/src/main/res/xml/app_restrictions.xml).
   La mayoría de los MDM la detectan solos al asignar el APK y muestran un
   campo de texto "URL del backend" en el perfil de la app. Se puede setear
   distinto por dispositivo o por grupo (por ejemplo, un backend por sede),
   **sin recompilar**. Se lee una sola vez al arrancar; un cambio que empuje
   el MDM en caliente se aplica en el próximo inicio de la app.

2. **Si el MDM no manda nada** (o no soporta App Restrictions), la app usa la
   URL con la que se compiló (`BuildConfig.API_BASE_URL`):

   ```powershell
   .\scripts\build-release.ps1 -ApiBaseUrl http://192.168.10.5:8082/
   ```

   Sin `-ApiBaseUrl`, por defecto compila con `http://10.0.2.2:8082/` (útil
   solo para probar en emulador — casi seguro hay que pasar la IP real antes
   de distribuir).

Ver [`ManagedConfig.kt`](../app/src/main/java/com/rf/inventario/config/ManagedConfig.kt)
para la implementación.

### Otras claves administradas

| Clave | Tipo | Efecto |
|---|---|---|
| `idleTimeoutMinutes` | entero | Si es > 0, fija el cierre de sesión por inactividad y el operario no puede cambiarlo (terminales compartidas entre turnos). |
| `printerHost` | texto | IP de la impresora Zebra (ZPL, puerto 9100) de la zona del equipo. |

## 4 bis. HTTPS y tráfico sin cifrar

El APK de **debug** permite HTTP (backend en la LAN o en el emulador). El de
**release** lo bloquea (`usesCleartextTraffic=false`): el PIN y el token viajan
cifrados o no viajan.

Si el backend de un cliente está en una LAN cerrada sin certificado, hay que
pedir la excepción al compilar:

```powershell
./gradlew assembleRelease -PallowCleartext=true
```

`scripts/build-release.ps1` lo agrega solo cuando la URL empieza con `http://`
(y lo avisa en amarillo). OJO: la URL que pushee el MDM no cambia esto, se
decide al compilar; si el MDM va a mandar una URL `http://`, el APK tiene que
haberse compilado con la excepción.

El token de sesión se guarda cifrado con una clave del **Android Keystore**
(no exportable), y los backups de la app están desactivados (`allowBackup=false`).

## 5. Qué NO hace falta (porque no va a Play Store)

Cuenta de Play Console, política de privacidad publicada, capturas de
pantalla, ficha de tienda, revisión de contenido. Nada de eso aplica a una
distribución 100% por MDM.
