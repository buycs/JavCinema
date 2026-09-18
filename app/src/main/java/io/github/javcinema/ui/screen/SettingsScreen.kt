package io.github.javcinema.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ImportExport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.widget.Toast
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.navigation.NavController
import coil.imageLoader
import io.github.javcinema.BuildConfig
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Configurations
import io.github.javcinema.ui.navigation.NavRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, coil.annotation.ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(navController: NavController? = null) {
    var showDataSourceDialog by remember { mutableStateOf(false) }
    var showDataUrlDialog by remember { mutableStateOf(false) }
    var showMagnetUrlDialog by remember { mutableStateOf(false) }
    var showHomePageDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showGridDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showFavoriteBackupChooser by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val exportFavoritesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { exportFavoritesToUri(context, uri) }
            Toast.makeText(context, if (ok) "已导出收藏" else "导出失败", Toast.LENGTH_SHORT).show()
        }
    }
    val importFavoritesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    val added = importFavoritesFromUri(context, uri)
                    "已导入 $added 条"
                }.getOrElse { "导入失败: ${it.message ?: "文件无效"}" }
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SettingsItem(
            icon = Icons.Filled.Star,
            title = "数据源选择",
            summary = getCurrentSourceSummary(),
            onClick = { showDataSourceDialog = true }
        )
        SettingsItem(
            icon = Icons.Filled.Language,
            title = "数据源配置",
            summary = "自定义数据源地址",
            onClick = { showDataUrlDialog = true }
        )
        SettingsItem(
            icon = Icons.Filled.Visibility,
            title = "磁力源配置",
            summary = "自定义磁力源地址",
            onClick = { showMagnetUrlDialog = true }
        )
        SettingsItem(
            icon = Icons.Filled.Home,
            title = "首页设置",
            summary = getHomePageSummary(),
            onClick = { showHomePageDialog = true }
        )
        SettingsItem(
            icon = Icons.Filled.Palette,
            title = "主题",
            summary = getThemeSummary(),
            onClick = { showThemeDialog = true }
        )
        SettingsItem(
            icon = Icons.Filled.GridView,
            title = "网格列数",
            summary = "${Configurations.gridColumns.coerceIn(2, 4)} 列",
            onClick = { showGridDialog = true }
        )
        SettingsItem(
            icon = Icons.Filled.ImportExport,
            title = "导入/导出收藏",
            summary = "通过 JSON 文件备份影片和女优",
            onClick = { showFavoriteBackupChooser = true }
        )
        SettingsItem(
            icon = Icons.Filled.Delete,
            title = "清理图片缓存",
            summary = "清除封面磁盘缓存",
            onClick = {
                scope.launch {
                    val cleared = withContext(Dispatchers.IO) {
                        runCatching { context.imageLoader.diskCache?.clear() }.isSuccess
                    }
                    Toast.makeText(context, if (cleared) "缓存已清理" else "清理失败", Toast.LENGTH_SHORT).show()
                }
            }
        )
        SettingsItem(
            icon = Icons.Filled.Info,
            title = "关于",
            summary = "JavCinema ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            onClick = { showAboutDialog = true }
        )
    }

    if (showDataSourceDialog) {
        DataSourceDialog(
            onDismiss = { showDataSourceDialog = false },
            navController = navController
        )
    }
    if (showDataUrlDialog) {
        DataUrlDialog(
            onDismiss = { showDataUrlDialog = false },
            navController = navController
        )
    }
    if (showMagnetUrlDialog) {
        MagnetUrlDialog(
            onDismiss = { showMagnetUrlDialog = false },
            navController = navController
        )
    }
    if (showHomePageDialog) {
        HomePageDialog(onDismiss = { showHomePageDialog = false })
    }
    if (showThemeDialog) {
        ThemeDialog(onDismiss = { showThemeDialog = false })
    }
    if (showGridDialog) {
        GridColumnsDialog(onDismiss = { showGridDialog = false })
    }
    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
    if (showFavoriteBackupChooser) {
        AlertDialog(
            onDismissRequest = { showFavoriteBackupChooser = false },
            title = { Text("导入/导出收藏") },
            text = {
                Text("只操作已收藏的影片和女优，使用 JSON 文件导入或导出。")
            },
            confirmButton = {
                TextButton(onClick = {
                    showFavoriteBackupChooser = false
                    if (!hasFavoritesToExport()) {
                        Toast.makeText(context, "没有可导出的收藏", Toast.LENGTH_SHORT).show()
                    } else {
                        exportFavoritesLauncher.launch(FAVORITE_BACKUP_FILE_NAME)
                    }
                }) { Text("导出") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showFavoriteBackupChooser = false
                    importFavoritesLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                }) { Text("导入") }
            }
        )
    }
}

private fun getThemeSummary(): String {
    return when (Configurations.themeMode) {
        "light" -> "浅色"
        "dark" -> "深色"
        else -> "跟随系统"
    }
}

@Composable
private fun ThemeDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(Configurations.themeMode ?: "system") }
    val options = listOf(
        "system" to "跟随系统",
        "light" to "浅色",
        "dark" to "深色"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("主题") },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = value }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selected == value,
                            onClick = { selected = value }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(text = label)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                Configurations.themeMode = selected
                Configurations.savePrefs(context)
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun GridColumnsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(Configurations.gridColumns.coerceIn(2, 4)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("网格列数") },
        text = {
            Column {
                listOf(2, 3, 4).forEach { columns ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = columns }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selected == columns,
                            onClick = { selected = columns }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(text = "${columns} 列")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                Configurations.gridColumns = selected
                Configurations.savePrefs(context)
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun openUrlWithChooser(context: Context, url: String) {
    // createChooser 保证每次都弹应用选择框，避免被默认应用直接接管。
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching {
        context.startActivity(Intent.createChooser(intent, "选择应用打开"))
    }.onFailure {
        Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repos = listOf(
        "原项目源码" to "https://github.com/SplashCodes/JAViewer",
        "本项目源码" to "https://github.com/buycs/JavCinema"
    )
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var latest by remember { mutableStateOf<ReleaseInfo?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关于") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "JavCinema ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n\n" +
                        "基于 JAViewer 的二次开发。内容来自第三方数据源，仅供学习交流。"
                )
                Spacer(Modifier.height(12.dp))
                Text("项目源码", style = MaterialTheme.typography.titleSmall)
                repos.forEach { (label, url) ->
                    TextButton(
                        onClick = { openUrlWithChooser(context, url) }
                    ) { Text(label) }
                }
                Spacer(Modifier.height(8.dp))
                Text("版本更新", style = MaterialTheme.typography.titleSmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        enabled = !checking,
                        onClick = {
                            checking = true
                            status = null
                            latest = null
                            scope.launch {
                                val result = fetchLatestRelease()
                                checking = false
                                result.onSuccess { info ->
                                    latest = info
                                    status = if (isNewerVersion(info.tag, BuildConfig.VERSION_NAME)) {
                                        "发现新版 ${info.tag.ifBlank { info.name }}（当前 ${BuildConfig.VERSION_NAME}）"
                                    } else {
                                        "已是最新版本（${BuildConfig.VERSION_NAME}）"
                                    }
                                }.onFailure { e ->
                                    status = "检查失败：${e.message ?: "网络异常"}"
                                }
                            }
                        }
                    ) { Text(if (checking) "检查中…" else "检查更新") }
                }
                if (status != null) {
                    Text(
                        text = status!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val info = latest
                if (info != null && isNewerVersion(info.tag, BuildConfig.VERSION_NAME)) {
                    if (info.body.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = info.body.take(200),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            openUrlWithChooser(context, info.htmlUrl.ifBlank { UPDATE_RELEASES_PAGE })
                        }
                    ) { Text("去下载新版") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

private fun getHomePageSummary(): String {
    return when (Configurations.homePage) {
        NavRoutes.SEARCH -> "搜索为首页"
        else -> "影片为首页"
    }
}

@Composable
private fun HomePageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val current = Configurations.homePage
    var selected by remember { mutableStateOf(current ?: NavRoutes.HOME) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val options = listOf(
        NavRoutes.HOME to "影片为首页",
        NavRoutes.SEARCH to "搜索为首页"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("首页设置") },
        text = {
            Column {
                Text(
                    text = "app 启动时默认显示的功能页面",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                options.forEach { (route, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = route }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selected == route,
                            onClick = { selected = route }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(text = label)
                    }
                }
                if (errorMsg != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    Configurations.homePage = selected
                    Configurations.savePrefs(context)
                    Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
                    onDismiss()
                } catch (e: Exception) {
                    errorMsg = "保存失败: ${e.localizedMessage}"
                    Toast.makeText(context, "保存失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun getCurrentSourceSummary(): String {
    val ds = JavCinema.getDataSource()
    return ds?.toString() ?: "未选择"
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DataSourceDialog(onDismiss: () -> Unit, navController: NavController? = null) {
    val context = LocalContext.current
    val dataSources = JavCinema.DATA_SOURCES
    val currentSource = JavCinema.getDataSource()
    var selectedSource by remember { mutableStateOf(currentSource) }
    var saved by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val iconMap = mapOf(
        "骑兵" to Icons.Filled.Star,
        "步兵" to Icons.Filled.Visibility,
        "欧美" to Icons.Filled.Language
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("数据源选择") },
        text = {
            Column {
                dataSources.forEach { ds ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedSource = ds }
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = selectedSource == ds,
                            onClick = { selectedSource = ds }
                        )
                        Spacer(Modifier.width(4.dp))
                        val icon = iconMap[ds.name]
                        if (icon != null) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(text = ds.toString())
                    }
                }
                if (errorMsg != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val config = JavCinema.CONFIGURATIONS
                if (config != null) {
                    config.dataSource = selectedSource
                    config.save()
                }
                try {
                    JavCinema.recreateService()
                    saved = true
                    Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
                    onDismiss()
                    navController?.navigate(homeDestination()) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                } catch (e: Exception) {
                    errorMsg = "保存失败: ${e.localizedMessage}"
                    Toast.makeText(context, "保存失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun DataUrlDialog(onDismiss: () -> Unit, navController: NavController? = null) {
    val context = LocalContext.current
    val items = JavCinema.DATA_SOURCES.map { ds ->
        val defaultLink = ds.link ?: ""
        val custom = when (ds.name) {
            "骑兵" -> Configurations.customAvmooUrl
            "步兵" -> Configurations.customAvsoUrl
            "欧美" -> Configurations.customAvxoUrl
            else -> null
        }
        SourceItem(ds.name ?: "", custom ?: defaultLink, defaultLink,
            onSave = { v -> when (ds.name) {
                "骑兵" -> Configurations.customAvmooUrl = v
                "步兵" -> Configurations.customAvsoUrl = v
                "欧美" -> Configurations.customAvxoUrl = v
            }}
        )
    }
    SourceConfigDialog("数据源配置", items, onDismiss, context, navController)
}

@Composable
private fun MagnetUrlDialog(onDismiss: () -> Unit, navController: NavController? = null) {
    val context = LocalContext.current
    val items = JavCinema.MAGNET_SOURCES.map { ds ->
        val defaultLink = ds.link ?: ""
        val custom = when (ds.name) {
            "BtSearch" -> Configurations.customBtSearchUrl
            "Cili" -> Configurations.customCiliUrl
            "BTSOW" -> Configurations.customBtsowUrl
            else -> null
        }
        SourceItem(ds.name ?: "", custom ?: defaultLink, defaultLink,
            onSave = { v -> when (ds.name) {
                "BtSearch" -> Configurations.customBtSearchUrl = v
                "Cili" -> Configurations.customCiliUrl = v
                "BTSOW" -> Configurations.customBtsowUrl = v
            }}
        )
    }
    SourceConfigDialog("磁力源配置", items, onDismiss, context, navController, reloadOnSave = false)
}

private data class SourceItem(
    val label: String,
    val initial: String,
    val defaultLink: String,
    val onSave: (String?) -> Unit
)

@Composable
private fun SourceConfigDialog(title: String, items: List<SourceItem>, onDismiss: () -> Unit, context: android.content.Context, navController: NavController? = null, reloadOnSave: Boolean = true) {
    val urls = remember { items.associate { it.label to mutableStateOf(it.initial) } }
    var saved by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var pendingHttp by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "留空则恢复默认地址。默认只接受 https，http 需确认。保存前会探测地址。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                val labelWidth = 72.dp
                items.forEach { item ->
                    val state = urls[item.label]!!
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("${item.label}：", textAlign = TextAlign.End, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(labelWidth))
                        TextField(
                            value = state.value,
                            onValueChange = { state.value = it; saved = false; errorMsg = null },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall,
                            placeholder = { Text("默认: ${item.defaultLink}", style = MaterialTheme.typography.bodySmall) },
                            colors = TextFieldDefaults.colors(
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            modifier = Modifier.height(56.dp).weight(1f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (errorMsg != null) {
                    Text(text = errorMsg!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else if (saved) {
                    Text(text = "已保存", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !checking,
                onClick = {
                    scope.launch {
                        persistSourceUrls(
                            items = items,
                            urls = urls.mapValues { it.value.value },
                            allowHttp = false,
                            context = context,
                            navController = navController,
                            reloadOnSave = reloadOnSave,
                            onDismiss = onDismiss,
                            onSaved = { saved = true },
                            onError = { errorMsg = it },
                            onChecking = { checking = it },
                            onNeedHttpConfirm = { pendingHttp = it }
                        )
                    }
                }
            ) {
                Text(if (checking) "探测中…" else "保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
    if (pendingHttp.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { pendingHttp = emptyList() },
            title = { Text("允许 HTTP？") },
            text = { Text("以下地址不是 https，确认后才会保存：\n${pendingHttp.joinToString("\n")}") },
            confirmButton = {
                TextButton(onClick = {
                    pendingHttp = emptyList()
                    scope.launch {
                        persistSourceUrls(
                            items = items,
                            urls = urls.mapValues { it.value.value },
                            allowHttp = true,
                            context = context,
                            navController = navController,
                            reloadOnSave = reloadOnSave,
                            onDismiss = onDismiss,
                            onSaved = { saved = true },
                            onError = { errorMsg = it },
                            onChecking = { checking = it },
                            onNeedHttpConfirm = {}
                        )
                    }
                }) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { pendingHttp = emptyList() }) { Text("取消") }
            }
        )
    }
}

private suspend fun persistSourceUrls(
    items: List<SourceItem>,
    urls: Map<String, String>,
    allowHttp: Boolean,
    context: android.content.Context,
    navController: NavController?,
    reloadOnSave: Boolean,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    onError: (String) -> Unit,
    onChecking: (Boolean) -> Unit,
    onNeedHttpConfirm: (List<String>) -> Unit
) {
    val httpPending = mutableListOf<String>()
    val normalized = mutableMapOf<String, String?>()
    items.forEach { item ->
        val check = validateSourceUrl(urls[item.label], allowHttp)
        if (check.needsHttpConfirm) {
            httpPending += check.normalized ?: urls[item.label].orEmpty()
        } else if (check.error != null) {
            onError("${item.label}: ${check.error}")
            return
        } else {
            normalized[item.label] = check.normalized
        }
    }
    if (httpPending.isNotEmpty()) {
        onNeedHttpConfirm(httpPending)
        return
    }
    onChecking(true)
    try {
        normalized.forEach { (label, url) ->
            if (!url.isNullOrBlank()) {
                val probeError = withContext(Dispatchers.IO) { probeSourceUrl(url) }
                if (probeError != null) {
                    onError("$label: $probeError")
                    return
                }
            }
        }
        items.forEach { item ->
            item.onSave(normalized[item.label])
        }
        Configurations.savePrefs(context)
        val config = JavCinema.CONFIGURATIONS
        if (config != null) {
            config.applyCustomUrls()
            config.save()
        }
        if (reloadOnSave) {
            JavCinema.recreateService()
            onDismiss()
            navController?.navigate(homeDestination()) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        } else {
            onDismiss()
        }
        onSaved()
        Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        onError("保存失败: ${e.localizedMessage}")
        Toast.makeText(context, "保存失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    } finally {
        onChecking(false)
    }
}

private fun homeDestination(): String =
    if (Configurations.homePage == NavRoutes.SEARCH) NavRoutes.SEARCH else NavRoutes.HOME


