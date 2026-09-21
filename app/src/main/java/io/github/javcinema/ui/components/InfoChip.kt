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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 胶囊内容的左右内边距。
 *
 * 与胶囊并排摆放的**纯文本行**要按这个值缩进，文字才和胶囊里的文字左侧对齐
 * （女优详情页表头的名称行就是这么对上的）。
 */
internal val INFO_CHIP_HORIZONTAL_PADDING = 8.dp

/**
 * 「标签 + 值」的**胶囊**写法 —— [InfoRow] 的紧凑版。
 *
 * 一行一个字段的 [InfoRow] 在女优资料页会占掉 8 行高度，把下面的作品列表挤下去；
 * 胶囊版把同样的字段压成几行可换行的圆角标签（配 `FlowRow` 使用），
 * 高度大约只要一半，且字段多少都不怕。
 *
 * 长按复制值，与 [InfoRow] 保持一致。
 */
@Composable
fun InfoChip(label: String, value: String, modifier: Modifier = Modifier) {
    InfoChip(label, AnnotatedString(value), modifier)
}

/**
 * [value] 为 [AnnotatedString] 时，可以对值里的某一段单独着墨 ——
 * 例如「4617 部 · 可下载 2433」里的「可下载」按标签的字重渲染，
 * 免得它和两侧数字一样粗、被读成第三个数字。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InfoChip(label: String, value: AnnotatedString, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText(label, value.text))
                Toast.makeText(context, "已复制: ${value.text}", Toast.LENGTH_SHORT).show()
            }
        ),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = INFO_CHIP_HORIZONTAL_PADDING,
                vertical = 2.dp
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                // 标签与值**同一个字号**：早先标签用 labelSmall（本项目主题里是 10sp）、
                // 值用 labelMedium（12sp），而标签必定是中文、值多半是数字，
                // 于是每行都出现「中文比数字高一点」的错位。
                // 区分两者交给字重和颜色，不靠字号。
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
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
