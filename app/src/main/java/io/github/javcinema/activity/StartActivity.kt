package io.github.javcinema.activity

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.javcinema.JAViewer
import io.github.javcinema.data.model.Configurations
import io.github.javcinema.data.model.Properties
import io.github.javcinema.ui.theme.JAViewerTheme
import io.github.javcinema.util.UTF_8
import io.github.javcinema.util.readText
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException

class StartActivity : ComponentActivity() {

    private var updateDialog by mutableStateOf<UpdateInfo?>(null)

    data class UpdateInfo(val message: String, val url: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)

        setContent {
            JAViewerTheme {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }

                updateDialog?.let { info ->
                    AlertDialog(
                        onDismissRequest = { },
                        title = { Text("发现更新") },
                        text = { Text(info.message) },
                        confirmButton = {
                            TextButton(onClick = {
                                start()
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.url)))
                            }) {
                                Text("更新")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { start() }) {
                                Text("忽略更新")
                            }
                        }
                    )
                }
            }
        }

        checkPermissions()
    }

    private fun readProperties() {
        try {
            assets.open("properties.json").use { `is` ->
                val properties = JAViewer.parseJson(Properties::class.java, readText(`is`, UTF_8))
                if (properties != null) {
                    handleProperties(properties)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            start()
        }
    }

    private fun handleProperties(properties: Properties) {
        JAViewer.DATA_SOURCES.clear()
        properties.dataSources?.let { JAViewer.DATA_SOURCES.addAll(it) }
        JAViewer.MAGNET_SOURCES.clear()
        properties.magnetSources?.let { JAViewer.MAGNET_SOURCES.addAll(it) }

        JAViewer.CONFIGURATIONS?.applyCustomUrls()

        JAViewer.hostReplacements.clear()
        val currentDs = JAViewer.getDataSource()
        val currentHost = try { URI(currentDs.link).host } catch (_: Exception) { null }
        if (currentHost != null) {
            currentDs.legacies?.forEach { h ->
                JAViewer.hostReplacements[h] = currentHost
            }
        }

        val currentVersion: Int = try {
            packageManager.getPackageInfo(packageName, 0).versionCode
        } catch (e: PackageManager.NameNotFoundException) {
            throw RuntimeException("Hacked???")
        }

        if (properties.latestVersionCode > 0 && currentVersion < properties.latestVersionCode) {
            var message = "新版本：" + properties.latestVersion
            if (properties.changelog != null) {
                message += "\n\n更新日志：\n\n" + properties.changelog + "\n"
            }
            updateDialog = UpdateInfo(message, "https://github.com/SplashCodes/JAViewer/releases")
        } else {
            start()
        }
    }

    private fun start() {
        startActivity(Intent(this@StartActivity, MainActivity::class.java))
        finish()
    }

    private fun checkPermissions() {
        val config = File(JAViewer.getStorageDir(), "configurations.json")

        val noMedia = File(JAViewer.getStorageDir(), ".nomedia")
        try {
            noMedia.createNewFile()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        JAViewer.CONFIGURATIONS = Configurations().load(config)
        Configurations.loadPrefs(this)

        readProperties()
    }
}
