package io.github.javcinema.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MagnetStreamPolicyTest {

    private val pieceBytes = 256L * 1024L

    /** 故意取不整除的尾数：最后一片不满，才测得出「按文件尾钳制」。 */
    private val fileEnd = 39L * pieceBytes + 1000L

    // ---------------- pieceContribution ----------------

    @Test
    fun readsFromMidPiece_contributesUpToPieceEnd() {
        val readFrom = 3L * pieceBytes + 1000L
        assertEquals(pieceBytes - 1000L, pieceContribution(readFrom, 3, pieceBytes, fileEnd))
    }

    @Test
    fun readsFromPieceStart_contributesWholePiece() {
        assertEquals(pieceBytes, pieceContribution(5L * pieceBytes, 5, pieceBytes, fileEnd))
    }

    @Test
    fun lastPieceIsClampedToFileEnd() {
        val lastPiece = (fileEnd / pieceBytes).toInt()
        assertEquals(
            1000L,
            pieceContribution(lastPiece.toLong() * pieceBytes, lastPiece, pieceBytes, fileEnd)
        )
    }

    @Test
    fun readFromBeyondThisPieceEnd_contributesNothing() {
        // 多片累加时「已读到的位置」已经越过这一片，不能再算一遍
        val readFrom = 7L * pieceBytes + 500L
        assertEquals(0L, pieceContribution(readFrom, 6, pieceBytes, fileEnd))
    }

    @Test
    fun consecutivePiecesSumWithoutGapOrOverlap() {
        val start = 2L * pieceBytes + 300L
        val first = pieceContribution(start, 2, pieceBytes, fileEnd)
        val second = pieceContribution(start + first, 3, pieceBytes, fileEnd)
        assertEquals(2L * pieceBytes - 300L, first + second)
    }

    @Test
    fun readFromPastFileEnd_contributesNothing() {
        assertEquals(0L, pieceContribution(fileEnd + 10L, 0, pieceBytes, fileEnd))
    }

    @Test
    fun nonPositivePieceBytes_neverThrows() {
        assertEquals(0L, pieceContribution(0L, 0, 0L, fileEnd))
        assertEquals(0L, pieceContribution(123L, 5, -1L, fileEnd))
    }

    // ---------------- shouldClearFailureOnProgress ----------------

    @Test
    fun stalledIsClearedOnceThereIsProgress() {
        // 停滞是带宽抖动，读出数据即自愈 —— 留着会让「播成功」也带失败原因
        assertTrue(shouldClearFailureOnProgress(MagnetUnavailable.Stalled("停滞")))
    }

    @Test
    fun terminalReasonsAreKept() {
        assertFalse(shouldClearFailureOnProgress(MagnetUnavailable.BadMagnet("磁力链接格式不对")))
        assertFalse(shouldClearFailureOnProgress(MagnetUnavailable.NoMetadata("拿不到种子信息")))
        assertFalse(shouldClearFailureOnProgress(MagnetUnavailable.NoVideoFile("没有视频文件")))
    }

    @Test
    fun nothingRecorded_meansNothingToClear() {
        assertFalse(shouldClearFailureOnProgress(null))
    }
}
