package com.liftley.sync360

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.liftley.sync360.core.designsystem.Sync360Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            Sync360Theme(dynamicColor = true) {
                Sync360Root()
            }
        }
    }
}
