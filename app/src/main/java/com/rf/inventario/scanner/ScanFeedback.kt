package com.rf.inventario.scanner

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.rf.inventario.config.AppSettings

/**
 * Beep + vibración al procesar un escaneo. En depósito el operario no mira
 * la pantalla a cada lectura: el sonido le dice si salió bien (un pip corto)
 * o mal (tono grave largo y doble vibración).
 */
class ScanFeedback(context: Context, private val settings: AppSettings) {

    private val tone: ToneGenerator? = runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull()

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun success() {
        if (settings.current.beep) tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
        vibrate(longArrayOf(0, 40))
    }

    /** Encolado offline: aviso distinto, no es un error. */
    fun queued() {
        if (settings.current.beep) tone?.startTone(ToneGenerator.TONE_PROP_ACK, 150)
        vibrate(longArrayOf(0, 40, 80, 40))
    }

    fun error() {
        if (settings.current.beep) tone?.startTone(ToneGenerator.TONE_SUP_ERROR, 400)
        vibrate(longArrayOf(0, 150, 100, 150))
    }

    private fun vibrate(pattern: LongArray) {
        if (!settings.current.vibrate) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(pattern, -1)
        }
    }
}
