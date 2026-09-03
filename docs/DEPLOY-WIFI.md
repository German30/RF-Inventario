# Despliegue inalámbrico (ADB Wi-Fi)

Instalar y correr la app en un dispositivo físico por red, sin cable USB.
Requiere **Android 11 o superior** (para el emparejamiento con código). Para
Android 10 o menor, ver el apéndice.

## 1. Preparar el teléfono (una sola vez)

1. **Ajustes → Información del teléfono →** pulsar **Número de compilación** 7 veces
   → se activan las *Opciones de desarrollo*.
2. **Opciones de desarrollo → Depuración inalámbrica → ON**.
3. El teléfono y la PC deben estar en la **misma red Wi-Fi**.
   Esta PC está en `192.168.1.231`, así que el teléfono debe estar en `192.168.1.x`
   (mismo router, no una red de invitados que aísle clientes).

## 2. Emparejar (una vez por red)

En el teléfono: **Depuración inalámbrica → Vincular dispositivo con código de
vinculación**. Muestra algo como:

```
Dirección IP y puerto: 192.168.1.50:37123
Código de vinculación: 482913
```

En la PC (PowerShell, desde la raíz del repo):

```powershell
.\scripts\wifi-deploy.ps1 -Pair 192.168.1.50:37123 -Code 482913
```

> El puerto de vinculación es efímero y distinto del puerto de conexión.

## 3. Conectar, compilar, instalar y abrir

En la **pantalla principal** de *Depuración inalámbrica* hay otra `IP:PUERTO`
(el puerto es fijo mientras la depuración siga activa):

```powershell
.\scripts\wifi-deploy.ps1 -Connect 192.168.1.50:41285 -Install
```

Esto hace: `adb connect` → `gradlew installDebug` → `am start` de
`com.rf.inventario/.MainActivity`. La dirección queda guardada en
`scripts/.wifi-target`.

## 4. Despliegues siguientes

```powershell
.\scripts\wifi-deploy.ps1 -Install            # reusa la última dirección
.\scripts\wifi-deploy.ps1 -Install -Logcat    # además engancha logcat de la app
.\scripts\wifi-deploy.ps1 -Disconnect         # soltar el dispositivo
```

Si tras un rato sale `device offline` o `failed to authenticate`, reabrí la
pantalla de Depuración inalámbrica en el teléfono y volvé a `-Connect`
(el puerto pudo haber cambiado al apagarse/encenderse el Wi-Fi).

## Alternativa: Android Studio

`Run > Pair Devices Using Wi-Fi` hace el mismo emparejamiento con un QR y deja el
dispositivo disponible en el selector de *Run*.

## Apéndice: Android 10 o inferior (necesita un cable la primera vez)

```powershell
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
# con el teléfono conectado por USB y depuración USB activada:
& $adb tcpip 5555
# desconectar el cable, luego:
& $adb connect 192.168.1.50:5555
.\scripts\wifi-deploy.ps1 -Connect 192.168.1.50:5555 -Install
```

## Notas

- El firewall de Windows puede pedir permiso para `adb.exe` la primera vez: **permitir** en redes privadas.
- La cámara del escáner **no** funciona en el emulador con webcam virtual de forma fiable; probar el escaneo por cámara en dispositivo real.
- Wi-Fi ADB va más lento que USB: el primer `installDebug` sube ~40 MB (APK debug con el modelo de ML Kit).
