package io.github.javcinema.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Configurations
import io.github.javcinema.data.model.Properties
import io.github.javcinema.ui.theme.JavCinemaTheme
import io.github.javcinema.util.UTF_8
import io.github.javcinema.util.readText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.URI

class StartActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            JavCinemaTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        bootstrapApp()
    }

    private fun readProperties() {
        assets.open("properties.json").use { `is` ->
            handleProperties(JavCinema.parseJson(Properties::class.java, readText(`is`, UTF_8)))
        }
    }

    private fun handleProperties(properties: Properties) {
        JavCinema.DATA_SOURCES.clear()
        properties.dataSources?.let { JavCinema.DATA_SOURCES.addAll(it) }
        JavCinema.MAGNET_SOURCES.clear()
        properties.magnetSources?.let { JavCinema.MAGNET_SOURCES.addAll(it) }

        JavCinema.CONFIGURATIONS?.applyCustomUrls()

        JavCinema.hostReplacements.clear()
        val currentDs = JavCinema.getDataSource()
        val currentHost = try { URI(currentDs.link).host } catch (_: Exception) { null }
        if (currentHost != null) {
            currentDs.legacies?.forEach { h ->
                JavCinema.hostReplacements[h] = currentHost
            }
        }
    }

    private fun start() {
        startActivity(Intent(this@StartActivity, MainActivity::class.java))
        finish()
    }

    private fun bootstrapApp() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val storageDir = JavCinema.getStorageDir()
                    val noMedia = File(storageDir, ".nomedia")
                    try {
                        noMedia.createNewFile()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                    JavCinema.CONFIGURATIONS = Configurations().load(File(storageDir, "configurations.json"))
                    Configurations.loadPrefs(this@StartActivity)
                    readProperties()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                e.printStackTrace()
            }
            start()
        }
    }
}
