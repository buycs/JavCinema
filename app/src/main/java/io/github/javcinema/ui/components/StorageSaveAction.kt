package io.github.javcinema.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.github.javcinema.util.saveImageToGallery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun rememberSaveImageAction(): (url: String, subDir: String) -> Unit {
    val context = LocalContext.current
    var pendingSave by remember { mutableStateOf<Pair<String, String>?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingSave
        pendingSave = null
        if (granted) {
            pending?.let { (url, subDir) -> doSave(context, url, subDir) }
        } else {
            Toast.makeText(context, "需要存储权限才能保存图片", Toast.LENGTH_SHORT).show()
        }
    }

    return { url, subDir ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            doSave(context, url, subDir)
        } else if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            doSave(context, url, subDir)
        } else {
            pendingSave = url to subDir
            launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }
}

private fun doSave(context: Context, url: String, subDir: String) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            saveImageToGallery(context, url, subDir)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "保存失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
