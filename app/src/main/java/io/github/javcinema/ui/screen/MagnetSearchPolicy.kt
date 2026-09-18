package io.github.javcinema.ui.screen

import io.github.javcinema.data.model.DownloadLink
import io.github.javcinema.data.model.MagnetFile

sealed class MagnetSourceUi {
    data object Idle : MagnetSourceUi()
    data object Loading : MagnetSourceUi()
    class Success(val items: List<DownloadLink>) : MagnetSourceUi()
    data class Error(val message: String) : MagnetSourceUi()
}

internal fun toMagnetSourceUi(
    success: Boolean,
    items: List<DownloadLink>,
    errorMessage: String?
): MagnetSourceUi {
    return if (success) {
        MagnetSourceUi.Success(items)
    } else {
        MagnetSourceUi.Error(errorMessage?.takeIf { it.isNotBlank() } ?: "搜索失败")
    }
}

internal fun shouldLoadFiles(
    files: List<MagnetFile>?,
    alreadyLoading: Boolean
): Boolean = files == null && !alreadyLoading
