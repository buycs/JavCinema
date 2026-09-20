package io.github.javcinema.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * 「标签 + 值」的**胶囊**写法 —— [InfoRow] 的紧凑版。
 *
 * 一行一个字段的 [InfoRow] 在女优资料页会占掉 8 行高度，把下面的作品列表挤下去；
 * 胶囊版把同样的字段压成几行可换行的圆角标签（配 `FlowRow` 使用），
 * 高度大约只要一半，且字段多少都不怕。
 *
 * 长按复制值，与 [InfoRow] 保持一致。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoChip(label: String, value: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                Toast.makeText(context, "已复制: $value", Toast.LENGTH_SHORT).show()
            }
        ),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
