package io.github.javcinema.ui.screen

import org.junit.Assert.assertTrue
import org.junit.Test

class DataSourcePolicyTest {

    /** `properties.json` 里三个源的真实 `apiPath`，一个都不能漏。 */
    @Test
    fun realApiPathsUseApi() {
        assertTrue(isAvmooApiSource("/jav/data/api/"))
        assertTrue(isAvmooApiSource("/javu/data/api/"))
        assertTrue(isAvmooApiSource("/wav/data/api/"))
    }

    /** 站点标识比较忽略大小写，前后斜杠与空白也不影响判断。 */
    @Test
    fun apiPathIsNormalized() {
        assertTrue(isAvmooApiSource("/JAV/data/api/"))
        assertTrue(isAvmooApiSource("jav/data/api/"))
        assertTrue(isAvmooApiSource("/jav/"))
        assertTrue(isAvmooApiSource("  /javu/data/api/  "))
    }

    /**
     * `apiPath` 为空时按骑兵兜底 —— 与 `JavCinema.recreateService` 的
     * `?: "/jav/data/api/"` 保持同一套语义。
     */
    @Test
    fun blankApiPathFallsBackToApi() {
        assertTrue(isAvmooApiSource(null))
        assertTrue(isAvmooApiSource(""))
        assertTrue(isAvmooApiSource("   "))
        assertTrue(isAvmooApiSource("/"))
    }

    /**
     * ⚠️ 没见过的站点标识**也走接口** —— 这是刻意的，不是漏写，别顺手改成 false。
     *
     * 判据：接口失败会抛异常 → 界面显示错误文案；HTML 抓取器失败会静默返回空列表 →
     * 界面显示「暂无数据」。宁可报错，也不要把失败说成「站点没有」。
     */
    @Test
    fun unknownSiteAlsoUsesApi() {
        assertTrue(isAvmooApiSource("/xyz/data/api/"))
        assertTrue(isAvmooApiSource("avmoo.shop/jav/data/api/"))
        assertTrue(isAvmooApiSource("/jav/data/api/"))
    }
}
