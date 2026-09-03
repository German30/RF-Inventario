package com.rf.inventario

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rf.inventario.scanner.KeyboardWedgeScanner
import com.rf.inventario.ui.products.ProductsScreen
import com.rf.inventario.ui.theme.RFInventarioTheme

class MainActivity : ComponentActivity() {

    private val wedgeScanner: KeyboardWedgeScanner
        get() = (application as InventarioApplication).keyboardWedgeScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RFInventarioTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ProductsScreen()
                }
            }
        }
    }

    /** Route every key event through the wedge scanner before normal dispatch. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (wedgeScanner.onKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }
}
