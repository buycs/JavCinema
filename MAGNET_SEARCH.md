# 磁力搜索功能汇总

> Kotlin/Compose 版本 (JavCinema)

## 一、入口

| 触发方式 | 目标 | 关键参数 |
|----------|------|----------|
| 影片详情页点击"影片番号" Header 行 | `DownloadScreen` | `keyword = detail.code` |

> 入口仅"影片番号" Header 行点击（`MovieDetailScreen.kt:576`，`InfoRowClickableMagnet`），无 FAB 按钮。

---

## 二、DownloadScreen（三 Tab）

初始加载时**并发同时搜索三个 Tab**（`DownloadScreen.kt:63-72`）：

```kotlin
LaunchedEffect(keyword) {
    if (keyword.isBlank()) { viewModel.resetSearch(); return@LaunchedEffect }
    viewModel.startSearch()
    viewModel.search(keyword, "btsearch")
    viewModel.search(keyword, "cili")
    viewModel.search(keyword, "btso")
}
```

三个搜索各自独立 `viewModelScope.launch` 并发执行，各自维护独立结果 StateFlow（`btSearchResults` / `ciliResults` / `btsoResults`），互不阻塞。

### Tab 1: BtSearch

| 步骤 | 接口 | 方法 | 说明 |
|------|------|------|------|
| 1 | `GET /api/search` | `BtSearch.search()` | JSON API，带签名验证 |
| 2 | `GET /api/torrent/{id}` | `BtSearch.getDetail()` | 点击三角时请求，返回 `torrentfile` |
| 3 | 解析 JSON 中的 `torrentfile` 数组 | `BtSearchLinkProvider.parseFilesFromTorrentFiles()` | 获取文件列表 |

**请求参数:**
- search: `keyword`, `limit`, `offset`, `mode`, `time`, `sort`, `sort_type`, `size`
- detail: `id` (path), `keyword` (query)

**请求头（拦截器自动注入，上层无感知）:**
- `x-timestamp`: Unix 秒级时间戳
- `x-nonce`: 8 位随机字符串
- `x-sign`: MD5(排序 query + `&key=long2ice`).toUpperCase()

- **Provider**: `BtSearchLinkProvider` (`network/provider/BtSearchLinkProvider.kt`) — `searchApi()` / `getDetail()`
- **网络接口**: `BtSearch.kt`（Retrofit + 独立 OkHttpClient + MD5 签名拦截器）
- **搜索结果**: 标题 + 大小 + 日期 + 磁力链接（直接可用，由 hash 构建 `magnet:?xt=urn:btih:${hash}`）
- **文件列表**: 搜索完成后自动为每条结果请求详情获取文件（`DownloadViewModel.kt:52-64`）

### Tab 2: 无极磁链 (CiliInfo)

| 步骤 | 接口 | 方法 | 说明 |
|------|------|------|------|
| 1 | `GET /search?q=X` | `CiliInfo.search()` | 返回 HTML |
| 2 | 解析 `table.table-hover.file-list tbody tr` | `CiliInfoLinkProvider.parseDownloadLinks()` | 提取标题、大小、详情链接；日期可选（2列或3列表） |
| 3 | `GET /!lBfm` | `CiliInfo.get()` | 搜索完成后自动为每条结果请求详情页 |
| 4 | 解析 `#input-magnet` + 文件列表 | `CiliInfoLinkProvider.parseMagnetLink()` / `parseFiles()` | 提取磁力链接和文件；日期回写 |

**搜索页 HTML 结构:**
```html
<table class="table table-hover file-list">
  <tbody>
    <tr>
      <td><a href="/!lBfm">APKH-197</a></td>
      <td>7.02GB</td>
    </tr>
  </tbody>
</table>
```
> 搜索结果只有 2 列（标题 + 大小），无日期列。

**详情页 HTML 结构:**
```html
<input id="input-magnet" value="magnet:?xt=urn:btih:..." />
<table class="table table-hover file-list">
  <tbody>
    <tr>
      <td>filename.mp4</td>
      <td>5.66 GB</td>
    </tr>
  </tbody>
</table>
<dt>发布日期 :</dt><dd>2026-07-26 02:01:02</dd>
```

- **Provider**: `CiliInfoLinkProvider` (`network/provider/CiliInfoLinkProvider.kt`)
- **网络接口**: `CiliInfo.kt`（Retrofit + Jsoup）
- **搜索结果**: 标题 + 大小 + 日期（如无则为空）
- **时间回写**: 详情页 `发布日期` 提取并回写（`DownloadViewModel.kt:93-96`）
- **文件列表**: 搜索完成后自动为每条结果请求详情页获取（`DownloadViewModel.kt:85-105`）

### Tab 3: BTSOW (BTSO)

> **注意：BTSO 已改为 JSON API，不再是 HTML 解析。**

| 步骤 | 接口 | 方法 | 说明 |
|------|------|------|------|
| 1 | `POST /bts/data/api/search` | `BTSO.search()` | JSON API；body `[{"search": kw}, 30, page]` |
| 2 | 解析 `data[]` (hash/name/size/lastUpdateTime) | `BTSOLinkProvider.searchApi()` | 提取标题、大小、日期 |
| 3 | `POST /bts/data/api/magnet` | `BTSO.getMagnet()` | 搜索完成后自动为每条结果请求；body `[hash]` |
| 4 | 解析 `data.files[]` | `BTSOLinkProvider.getMagnetDetail()` | 提取文件列表 |

**请求体示例:**
```json
// 搜索
[{"search": "MADB-004"}, 30, 1]
// 文件列表
["6D8CD7F3E8821906D4EB3E0759A59F581FB2A8F5"]
```

**磁力链接构造:**
```
magnet:?xt=urn:btih:{hash}
```

- **Provider**: `BTSOLinkProvider` (`network/provider/BTSOLinkProvider.kt`) — `searchApi()` / `getMagnetDetail()`（基类 HTML 抽象方法均返回空）
- **网络接口**: `BTSO.kt`（Retrofit + `JavCinema.HTTP_CLIENT` + Gson）
- **Base URL**: `https://btsow.live`
- **搜索结果**: 标题 + 大小 + 日期 + 磁力链接（由 hash 直接构建，无需再请求详情页）
- **文件列表**: 搜索完成后自动获取（`DownloadViewModel.kt:67-78`）

---

## 三、UI 交互

### DownloadScreen 三 Tab（TabRow + HorizontalPager）

| 操作 | BtSearch Tab | 无极磁链 Tab | BTSOW Tab |
|------|-------------|-------------|-----------|
| 点击整行 | 弹出磁力链接对话框 | 弹出磁力链接对话框 | 弹出磁力链接对话框 |
| 点击三角 ▶ | 展开文件列表 | 展开文件列表 | 展开文件列表 |

**磁力链接对话框按钮:** 复制链接 / 关闭 / 打开（ACTION_VIEW 外部应用）

**列表项布局:**
```
┌─────────────────────────────────────┐
│ 影片编号(primary色)         ▶/▼    │
│ 时间  大小                          │
├─────────────────────────────────────┤
│ 文件1                               │
│ 文件2                               │
└─────────────────────────────────────┘
```

- 标题 primary 色显示
- 第一行: 标题
- 第二行: 大小 + 时间
- 三角符号在右侧（IconButton 展开/收起）
- 文件列表在 AnimatedVisibility 中展开（加载中显示"加载中..."）

---

## 四、涉及文件清单

### 网络层
| 文件 | 职责 |
|------|------|
| `network/BtSearch.kt` | BtSearch API + MD5 签名拦截器 + 详情接口 |
| `network/provider/BtSearchLinkProvider.kt` | BtSearch 搜索、详情解析 |
| `network/CiliInfo.kt` | CiliInfo API（search + get，HTML 响应） |
| `network/provider/CiliInfoLinkProvider.kt` | CiliInfo HTML 解析 |
| `network/BTSO.kt` | BTSO JSON API（search + getMagnet，Gson 转换） |
| `network/provider/BTSOLinkProvider.kt` | BTSO JSON 解析 + 磁力构造 |
| `network/TorrentKitty.kt` | TorrentKitty API（search/searchPage/get，HTML） |
| `network/provider/TorrentKittyLinkProvider.kt` | TorrentKitty HTML 解析 |
| `network/provider/DownloadLinkProvider.kt` | 抽象基类 + provider 工厂（btso/torrentkitty/ciliinfo/cili/btsearch） |

### Screen / ViewModel
| 文件 | 职责 |
|------|------|
| `ui/screen/DownloadScreen.kt` | Compose UI：3 Tab + 列表 + 磁力链接对话框 |
| `ui/screen/DownloadViewModel.kt` | 三源并发搜索 + 详情获取 + 状态管理（StateFlow） |

### 数据模型
| 文件 | 职责 |
|------|------|
| `data/model/DownloadLink.kt` | 下载链接数据类（title, size, date, link, magnetLink, files, filesExpanded） |
| `data/model/MagnetLink.kt` | 磁力链接数据类 |
| `data/model/MagnetFile.kt` | 磁力文件数据类（filename, size） |

### 导航
| 文件 | 职责 |
|------|------|
| `ui/navigation/NavRoutes.kt` | 路由常量 + `download(keyword)` 辅助函数 |
| `ui/navigation/JavCinemaNavHost.kt` | NavHost 注册 `download/{keyword}` 路由 |

---

## 五、流程图

```
MovieDetailScreen
    │
    └── 点击"影片番号" Header 行 (InfoRowClickableMagnet)
        └── → DownloadScreen (keyword = detail.code)

DownloadScreen (三 Tab，并发同时搜索)
    │
    ├── Tab 1: BtSearch (viewModel.search(keyword, "btsearch"))
    │   ├── 搜索: GET /api/search (JSON, 签名验证)
    │   │   └── 返回: id, name, size, hash, created_at
    │   │   └── 磁力: 由 hash 构建 magnet:?xt=urn:btih:${hash}
    │   ├── 文件列表: GET /api/torrent/{id} (JSON, 自动获取)
    │   │   └── 返回: torrentfile[] (name, size)
    │   └── 点击整行: 弹出磁力链接对话框
    │
    ├── Tab 2: 无极磁链 (viewModel.search(keyword, "cili"))
    │   ├── 搜索: GET /search?q= (HTML)
    │   │   └── 解析: table.file-list tr → title, size
    │   ├── 文件列表: GET /!id (HTML, 自动获取)
    │   │   └── 解析: #input-magnet + table.file-list + 发布日期回写
    │   └── 点击整行: 弹出磁力链接对话框
    │
    └── Tab 3: BTSOW (viewModel.search(keyword, "btso"))
        ├── 搜索: POST /bts/data/api/search (JSON, [{"search":kw},30,page])
        │   └── 解析: data[] → title, size, date, hash
        │   └── 磁力: 由 hash 构建 magnet:?xt=urn:btih:${hash}
        ├── 文件列表: POST /bts/data/api/magnet (JSON, [hash], 自动获取)
        │   └── 解析: data.files[]
        └── 点击整行: 弹出磁力链接对话框
```

---

## 六、版本历史

> 版本记录已合并至 **README.md「更新日志」**，此处仅保留磁力搜索相关演进。

| 版本 | 磁力搜索变更 |
|------|--------------|
| **0.0.1-alpha (当前)** | 三源并发搜索（btsearch/cili/btso），BTSO 改 JSON API，搜索完成自动预取文件列表，磁力链接由 hash 直接构造 |
| v3.0+ | Kotlin/Compose 重写：DownloadScreen 替代 DownloadActivity，三 Tab（btsearch/cili/btso） |
| v2.2.0 | DownloadActivity 新增 btsow Tab |
| v2.1.0 | 新增 cili.info 源 |
| v2.0.3 | BtSearch 磁力搜索 |
