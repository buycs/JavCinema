package io.github.javcinema.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 「标签 + 值」信息行 —— 影片详情页与女优详情页共用，保证两处的资料排版一致。
 *
 * 长按复制值：详情页的番号、女优的身高三围都常有人要往外贴。
 * 标签固定 [LABEL_WIDTH]，多行之间能对齐。
 */
private val LABEL_WIDTH = 80.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Row(
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                Toast.makeText(context, "已复制: $value", Toast.LENGTH_SHORT).show()
            }
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(LABEL_WIDTH).alignByBaseline()
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.alignByBaseline()
        )
    }
}
