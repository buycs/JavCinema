package io.github.javcinema.activity

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Configurations
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

        // 用户开启「最近任务隐藏」后，让本应用的任务不出现在系统最近任务列表。
        // API 30+ 立即生效；更低版本需重启应用（详见 applyRecentsExclusion 注释）。
        if (Configurations.hideFromRecents) {
            applyRecentsExclusion(true)
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
