package com.rf.inventario

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rf.inventario.scanner.KeyboardWedgeScanner
import com.rf.inventario.ui.AppNavHost
import com.rf.inventario.data.remote.ServerStatus
import com.rf.inventario.ui.login.LoginScreen
import com.rf.inventario.ui.startup.ServerCheckingScreen
import com.rf.inventario.ui.startup.ServerErrorScreen
import com.rf.inventario.ui.theme.RFInventarioTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private val app: InventarioApplication
        get() = application as InventarioApplication

    private val wedgeScanner: KeyboardWedgeScanner
        get() = app.keyboardWedgeScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RFInventarioTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    // Hasta que el backend esté verificado no se muestra ni el login:
                    // si no responde, la ejecución se detiene en la pantalla de error.
                    val server by app.serverGate.status.collectAsStateWithLifecycle()
                    val serverUrl = app.managedConfig.apiBaseUrl
                    when (val status = server) {
                        ServerStatus.Checking -> ServerCheckingScreen(serverUrl)
                        is ServerStatus.Failed -> ServerErrorScreen(
                            error = status.error,
                            serverUrl = serverUrl,
                            onRetry = app.serverGate::verify,
                        )
                        ServerStatus.Ready -> MainContent()
                    }
                }
            }
        }
    }

    @Composable
    private fun MainContent() {
        val user by app.sessionManager.current.collectAsStateWithLifecycle()
        val settings by app.settings.settings.collectAsStateWithLifecycle()

        // Bloqueo por inactividad: en una terminal compartida, el que
        // agarra el equipo no hereda la sesión del turno anterior.
        LaunchedEffect(user, settings.idleMinutes) {
            if (user == null || settings.idleMinutes <= 0) return@LaunchedEffect
            while (true) {
                delay(15_000)
                if (app.sessionManager.logoutIfIdle(settings.idleMinutes * 60_000L)) break
            }
        }

        val loggedIn = user
        if (loggedIn == null) {
            LoginScreen()
        } else {
            AppNavHost(user = loggedIn, onLogout = { app.authRepository.logout() })
        }
    }

    override fun onResume() {
        super.onResume()
        // Volver a la app después de mucho tiempo también cuenta como inactividad.
        val minutes = app.settings.current.idleMinutes
        if (minutes > 0) app.sessionManager.logoutIfIdle(minutes * 60_000L)
    }

    /**
     * Route every key event through the wedge scanner before normal dispatch.
     * RestrictedApi es un falso positivo: se sobrescribe Activity.dispatchKeyEvent
     * (API pública), no la variante interna de androidx.core.
     */
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        app.sessionManager.touch()
        if (wedgeScanner.onKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        app.sessionManager.touch()
        return super.dispatchTouchEvent(ev)
    }
}
