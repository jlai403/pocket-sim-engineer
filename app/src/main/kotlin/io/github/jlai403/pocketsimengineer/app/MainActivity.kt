package io.github.jlai403.pocketsimengineer.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.github.jlai403.pocketsimengineer.app.ui.PocketSimEngineerTheme
import io.github.jlai403.pocketsimengineer.app.ui.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PocketSimEngineerTheme {
                App()
            }
        }
    }
}
