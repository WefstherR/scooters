package cl.recibos.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import cl.recibos.app.ui.AppNavHost
import cl.recibos.app.ui.theme.RecibosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as RecibosApp).container
        setContent {
            RecibosTheme {
                AppNavHost(container)
            }
        }
    }
}
