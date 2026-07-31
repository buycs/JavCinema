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
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.widget.Toast
import androidx.navigation.NavController
import io.github.javcinema.JavCinema
import io.github.javcinema.data.model.Configurations
import io.github.javcinema.ui.navigation.NavRoutes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController? = null) {
    var showDataSourceDialog by remember { mutableStateOf(false) }
    var showDataUrlDialog by remember { mutableStateOf(false) }
    var showMagnetUrlDialog by remember { mutableStateOf(false) }
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
                    navController?.navigate(NavRoutes.HOME) {
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "留空则恢复默认地址",
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
            Button(onClick = {
                items.forEach { item ->
                    item.onSave(urls[item.label]?.value?.ifBlank { null })
                }
                Configurations.savePrefs(context)

                val config = JavCinema.CONFIGURATIONS
                if (config != null) {
                    config.applyCustomUrls()
                    config.save()
                }
                try {
                    if (reloadOnSave) {
                        JavCinema.recreateService()
                        onDismiss()
                        navController?.navigate(NavRoutes.HOME) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    } else {
                        onDismiss()
                    }
                    saved = true
                    Toast.makeText(context, "保存成功", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    saved = false
                    errorMsg = "保存失败: ${e.localizedMessage}"
                    Toast.makeText(context, "保存失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}


