package com.padraik99.grindhaus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.padraik99.grindhaus.ui.GrindhausApp
import com.padraik99.grindhaus.ui.theme.GrindhausTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrindhausTheme {
                GrindhausApp()
            }
        }
    }
}
