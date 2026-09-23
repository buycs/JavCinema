package io.github.javcinema.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AvmooApiPolicyTest {

    /** 站点实测的成功码，别顺手改。 */
    @Test
    fun successCodeIs200() {
        assertEquals(200, AVMOO_SUCCESS_CODE)
    }

    /** 成功码放行 —— 包括「code 200 + 空列表」这种**合法的没有结果**。 */
    @Test
    fun successCodePasses() {
        requireAvmooSuccess(AVMOO_SUCCESS_CODE)
    }

    /** 资源不存在（实测 `code:404` + `data:null`）必须抛异常，不能当成「没有结果」。 */
    @Test
    fun notFoundCodeThrows() {
        val e = assertThrows(IllegalStateException::class.java) { requireAvmooSuccess(404) }
        assertEquals("站点接口返回错误（code=404）", e.message)
    }

    /** 任何非 200 都不能放行 —— 接口永远回 HTTP 200，所以 code 是唯一的失败信号。 */
    @Test
    fun everyNonSuccessCodeThrows() {
        listOf(-1, 0, 1, 201, 400, 500).forEach { code ->
            assertThrows(IllegalStateException::class.java) { requireAvmooSuccess(code) }
        }
    }
}
