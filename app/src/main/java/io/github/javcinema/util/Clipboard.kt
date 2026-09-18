package io.github.javcinema.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

fun copyText(context: Context, text: String, toast: String = "已复制番号") {
    if (text.isBlank()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("javcinema", text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}
