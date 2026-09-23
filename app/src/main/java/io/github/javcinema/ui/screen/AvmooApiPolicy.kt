package io.github.javcinema.ui.screen

/**
 * AVMOO 系接口（骑兵 / 步兵 / 欧美）的**成功状态码**。
 *
 * ⚠️ 这套接口**永远返回 HTTP 200**，真正的状态在 JSON 的 `code` 字段里。实测（2026-09-24）：
 *
 * | 场景 | HTTP | `code` | `data` |
 * |---|---|---|---|
 * | 正常列表 / 详情 / 类别 / 搜索 | 200 | **200** | 非空 |
 * | 页码超范围、空关键词（**真的没有结果**） | 200 | **200** | **空列表 `[]`** |
 * | 资源不存在 / 接口不存在 | 200 | **404** | **`null`** |
 *
 * 所以 **`data == null` 不等于「没有结果」，它等于「接口报错」**。
 * 全项目此前只看 `data`、从不看 `code`，于是把接口错误渲染成「暂无数据」/「未找到结果」——
 * 与「有资源却报没有资源」是同一类**错误结论**（失败被说成「站点没有」）。
 */
internal const val AVMOO_SUCCESS_CODE = 200

/**
 * 校验接口状态码；非成功就抛异常，交给各 ViewModel 已有的 `catch` → `Error` 态。
 *
 * 判据**只看 `code`、不看 `data`**：`code == 200` + 空列表是合法的「没有结果」，
 * 必须原样放行（搜索无结果、翻到最后一页之后的空页都走这条），不能误报成错误。
 */
internal fun requireAvmooSuccess(code: Int) {
    if (code != AVMOO_SUCCESS_CODE) {
        throw IllegalStateException("站点接口返回错误（code=$code）")
    }
}
