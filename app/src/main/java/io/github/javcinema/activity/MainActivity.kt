package io.github.javcinema.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.javcinema.JavCinema
import io.github.javcinema.ui.screen.MainScreen
import io.github.javcinema.ui.theme.JavCinemaTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (JavCinema.CONFIGURATIONS == null) {
            startActivity(Intent(this, StartActivity::class.java))
            finish()
            return
        }

        JavCinema.CONFIGURATIONS?.applyCustomUrls()
        JavCinema.recreateService()

        setContent {
            JavCinemaTheme {
                MainScreen()
            }
        }
    }
}
