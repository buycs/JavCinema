package io.github.javcinema.torrent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 引擎级诊断页（只在 debug 包里，`src/debug`）。
 * 用 `adb shell am start -n io.github.javcinema/.torrent.MagnetProbeActivity` 直接拉起。
 *
 * 与 [MagnetRunActivity] 的分工：这里只碰 `SessionManager`、**不经过 ExoPlayer**，
 * 用来批量跑样本量「引擎能不能连上 swarm、速率够不够」；那边跑的是完整生产链路。
 * 排查「播不了」时先跑这边，能把「引擎问题」和「播放器问题」切开。
 */
class MagnetProbeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preset = intent?.getStringExtra("magnet").orEmpty()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MagnetProbeScreen(presetMagnet = preset)
                }
            }
        }
    }
}

@Composable
private fun MagnetProbeScreen(presetMagnet: String) {
    val context = LocalContext.current
    var lines by remember { mutableStateOf(listOf<String>()) }
    var custom by remember { mutableStateOf(presetMagnet) }
    val engine = remember {
        MagnetProbeEngine(context.applicationContext) { message ->
            android.util.Log.i("MagnetProbe", message)
            (context as? android.app.Activity)?.runOnUiThread { lines = lines + message }
                ?: run { lines = lines + message }
        }
    }
    DisposableEffect(Unit) {
        onDispose { engine.destroy() }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { engine.runSamples(M0_SAMPLES) }) { Text("跑全部样本") }
            Button(onClick = { engine.stop() }) { Text("停止") }
        }
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it },
            label = { Text("或粘贴单条磁力") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                val magnet = custom.trim()
                if (magnet.isNotEmpty()) engine.runSamples(listOf(ProbeSample("自定义", magnet)))
            }
        ) { Text("只跑这一条") }
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            lines.forEach { line ->
                Text(text = line, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
