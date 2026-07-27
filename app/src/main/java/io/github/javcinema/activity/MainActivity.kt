package io.github.javcinema.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.javcinema.JAViewer
import io.github.javcinema.ui.screen.MainScreen
import io.github.javcinema.ui.theme.JAViewerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)

        if (JAViewer.CONFIGURATIONS == null) {
            startActivity(Intent(this, StartActivity::class.java))
            finish()
            return
        }

        JAViewer.CONFIGURATIONS?.applyCustomUrls()
        JAViewer.recreateService()

        setContent {
            JAViewerTheme {
                MainScreen()
            }
        }
    }
}
