package com.pouch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pouch.ui.navigation.PouchNavHost
import com.pouch.ui.theme.PouchTheme

/** Single-activity app: every screen is a Compose destination in [PouchNavHost]. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PouchTheme {
                PouchNavHost()
            }
        }
    }
}
