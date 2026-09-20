package io.github.javcinema.network.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CfEmailPolicyTest {

    /**
     * 真实回归用例：cili.info 详情页里 6.13 GB 那一行的 `data-cfemail`。
     * 原始文件名是 `4k2.me@roe-556.mp4`，被 Cloudflare 邮件保护整段替换成
     * `[email protected]`，扩展名一起丢失 → 正片被当成非媒体文件隐藏。
     */
    @Test
    fun decodesRealWorldCiliFilename() {
        assertEquals(
            "4k2.me@roe-556.mp4",
            decodeCfEmail("c5f1aef7eba8a085b7aaa0e8f0f0f3eba8b5f1")
        )
    }

    @Test
    fun decodesOtherEmailLikeFilenames() {
        assertEquals("a1b2.cc@SSIS-001.mkv", decodeCfEmail("c5a4f4a7f7eba6a68596968c96e8f5f5f4eba8aeb3"))
        assertEquals("ROE-556.mp4", decodeCfEmail("c5978a80e8f0f0f3eba8b5f1"))
    }

    @Test
    fun decodesUtf8Content() {
        assertEquals("测试@abc.mp4", decodeCfEmail("c523704e2d6a5085a4a7a6eba8b5f1"))
    }

    @Test
    fun acceptsUppercaseHexAndSurroundingWhitespace() {
        assertEquals("ROE-556.mp4", decodeCfEmail("  C5978A80E8F0F0F3EBA8B5F1  "))
    }

    @Test
    fun returnsNullForInvalidInput() {
        assertNull(decodeCfEmail(null))
        assertNull(decodeCfEmail(""))
        assertNull(decodeCfEmail("abc"))            // 奇数长度
        assertNull(decodeCfEmail("zz"))             // 非十六进制
        assertNull(decodeCfEmail("c5"))             // 只有密钥、没有内容
    }

    /** 解码结果必须能通过媒体扩展名判定 —— 否则修了等于没修。 */
    @Test
    fun decodedNameIsRecognizedAsMediaFile() {
        val name = decodeCfEmail("c5f1aef7eba8a085b7aaa0e8f0f0f3eba8b5f1")
        assertEquals("4k2.me@roe-556.mp4", name)
        assertEquals(true, name?.lowercase()?.endsWith(".mp4"))
    }
}
