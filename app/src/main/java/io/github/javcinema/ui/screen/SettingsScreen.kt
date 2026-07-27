package io.github.javcinema.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.javcinema.JAViewer
import io.github.javcinema.data.model.Configurations

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    var showDataSourceDialog by remember { mutableStateOf(false) }
    var showDataUrlDialog by remember { mutableStateOf(false) }
    var showMagnetUrlDialog by remember { mutableStateOf(false) }
    var showActiveAddressesDialog by remember { mutableStateOf(false) }

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
            icon = Icons.Filled.Star,
            title = "当前生效地址",
            summary = "查看当前使用的地址",
            onClick = { showActiveAddressesDialog = true }
        )
    }

    if (showDataSourceDialog) {
        DataSourceDialog(onDismiss = { showDataSourceDialog = false })
    }
    if (showDataUrlDialog) {
        DataUrlDialog(onDismiss = { showDataUrlDialog = false })
    }
    if (showMagnetUrlDialog) {
        MagnetUrlDialog(onDismiss = { showMagnetUrlDialog = false })
    }
    if (showActiveAddressesDialog) {
        ActiveAddressesDialog(onDismiss = { showActiveAddressesDialog = false })
    }
}

private fun getCurrentSourceSummary(): String {
    val ds = JAViewer.getDataSource()
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
private fun DataSourceDialog(onDismiss: () -> Unit) {
    val dataSources = JAViewer.DATA_SOURCES
    val currentSource = JAViewer.getDataSource()
    var selectedSource by remember { mutableStateOf(currentSource) }
    var saved by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val iconMap = mapOf(
        "AVMOO 日本" to Icons.Filled.Star,
        "AVSOX 日本无码" to Icons.Filled.Visibility,
        "AVMEMO 欧美" to Icons.Filled.Language
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
                            .padding(vertical = 8.dp)
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
                val config = JAViewer.CONFIGURATIONS
                if (config != null) {
                    config.dataSource = selectedSource
                    config.save()
                }
                try {
                    JAViewer.recreateService()
                    saved = true
                    onDismiss()
                } catch (e: Exception) {
                    errorMsg = "保存失败: ${e.localizedMessage}"
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
private fun DataUrlDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var avmooUrl by remember { mutableStateOf(Configurations.customAvmooUrl ?: "") }
    var avsoUrl by remember { mutableStateOf(Configurations.customAvsoUrl ?: "") }
    var avxoUrl by remember { mutableStateOf(Configurations.customAvxoUrl ?: "") }
    var saved by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("数据源配置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "留空则使用 properties.json 中的默认地址",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                val avmooDefault = JAViewer.DATA_SOURCES.find { it.name == "AVMOO 日本" }?.link
                TextField(
                    value = avmooUrl,
                    onValueChange = { avmooUrl = it; saved = false; errorMsg = null },
                    label = { Text("骑兵") },
                    placeholder = { Text("骑兵") },
                    supportingText = { Text(avmooDefault ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                val avsoDefault = JAViewer.DATA_SOURCES.find { it.name == "AVSOX 日本无码" }?.link
                TextField(
                    value = avsoUrl,
                    onValueChange = { avsoUrl = it; saved = false; errorMsg = null },
                    label = { Text("步兵") },
                    placeholder = { Text("步兵") },
                    supportingText = { Text(avsoDefault ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                val avxoDefault = JAViewer.DATA_SOURCES.find { it.name == "AVMEMO 欧美" }?.link
                TextField(
                    value = avxoUrl,
                    onValueChange = { avxoUrl = it; saved = false; errorMsg = null },
                    label = { Text("欧美") },
                    placeholder = { Text("欧美") },
                    supportingText = { Text(avxoDefault ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (saved) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "已保存",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                Configurations.customAvmooUrl = avmooUrl.ifBlank { null }
                Configurations.customAvsoUrl = avsoUrl.ifBlank { null }
                Configurations.customAvxoUrl = avxoUrl.ifBlank { null }
                Configurations.savePrefs(context)

                val config = JAViewer.CONFIGURATIONS
                if (config != null) {
                    config.applyCustomUrls()
                    config.save()
                }
                try {
                    JAViewer.recreateService()
                    saved = true
                } catch (e: Exception) {
                    saved = false
                    errorMsg = "保存失败: ${e.localizedMessage}"
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

@Composable
private fun MagnetUrlDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var btSearchUrl by remember { mutableStateOf(Configurations.customBtSearchUrl ?: "") }
    var ciliUrl by remember { mutableStateOf(Configurations.customCiliUrl ?: "") }
    var btsowUrl by remember { mutableStateOf(Configurations.customBtsowUrl ?: "") }
    var saved by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("磁力源配置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                TextField(
                    value = btSearchUrl,
                    onValueChange = { btSearchUrl = it; saved = false; errorMsg = null },
                    label = { Text("BtSearch") },
                    placeholder = { Text("BtSearch") },
                    supportingText = { Text(io.github.javcinema.network.BtSearch.BASE_URL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = ciliUrl,
                    onValueChange = { ciliUrl = it; saved = false; errorMsg = null },
                    label = { Text("Cili") },
                    placeholder = { Text("Cili") },
                    supportingText = { Text(io.github.javcinema.network.CiliInfo.BASE_URL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                TextField(
                    value = btsowUrl,
                    onValueChange = { btsowUrl = it; saved = false; errorMsg = null },
                    label = { Text("BTSOW") },
                    placeholder = { Text("BTSOW") },
                    supportingText = { Text(io.github.javcinema.network.BTSO.BASE_URL, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (saved) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "已保存",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                Configurations.customBtSearchUrl = btSearchUrl.ifBlank { null }
                Configurations.customCiliUrl = ciliUrl.ifBlank { null }
                Configurations.customBtsowUrl = btsowUrl.ifBlank { null }
                Configurations.savePrefs(context)

                val config = JAViewer.CONFIGURATIONS
                if (config != null) {
                    config.applyCustomUrls()
                    config.save()
                }
                try {
                    JAViewer.recreateService()
                    saved = true
                } catch (e: Exception) {
                    saved = false
                    errorMsg = "保存失败: ${e.localizedMessage}"
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

@Composable
private fun ActiveAddressesDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("当前生效地址") },
        text = {
            Column {
                Text("数据源", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                JAViewer.DATA_SOURCES.forEach { ds ->
                    Text(
                        text = "${ds.name}: ${ds.link ?: "未设置"}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("磁力源", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "BtSearch: ${io.github.javcinema.network.BtSearch.BASE_URL}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Cili: ${io.github.javcinema.network.CiliInfo.BASE_URL}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "BTSOW: ${io.github.javcinema.network.BTSO.BASE_URL}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
