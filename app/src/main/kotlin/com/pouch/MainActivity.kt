package com.pouch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pouch.ui.common.SuccessHost
import com.pouch.ui.navigation.PouchNavHost
import com.pouch.ui.theme.PouchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PouchTheme {
                // Success overlay sits above every screen (and above navigation).
                SuccessHost((application as PouchApp).container.success) {
                    PouchNavHost()
                }
            }
        }
    }
}
