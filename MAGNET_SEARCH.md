# 磁力搜索功能汇总

> Kotlin/Compose 版本 (JAViewerApp)

## 一、入口

| 触发方式 | 目标 | 关键参数 |
|----------|------|----------|
| 影片详情页点击"影片番号" Header 行 | `DownloadScreen` | `keyword = detail.code` |

> 无 FAB 按钮，只有番号 Header 点击。

---

## 二、DownloadScreen（三 Tab）

初始加载时**同时搜索三个 Tab**（btsearch / cili / btso）。

### Tab 1: BtSearch

| 步骤 | 接口 | 方法 | 说明 |
|------|------|------|------|
| 1 | `GET /api/search` | `BtSearch.search()` | JSON API，带签名验证 |
| 2 | `GET /api/torrent/{id}` | `BtSearch.getDetail()` | 点击三角时请求，返回torrentfile |
| 3 | 解析 JSON 中的 `torrentfile` 数组 | `BtSearchLinkProvider` | 获取文件列表 |

**请求参数:**
- search: `keyword`, `limit`, `offset`, `mode`, `time`, `sort`, `sort_type`, `size`
- detail: `id` (path), `keyword` (query)

**请求头:**
- `x-timestamp`: Unix 时间戳
- `x-nonce`: 随机字符串
- `x-sign`: MD5 签名

- **Provider**: `BtSearchLinkProvider` (`network/provider/BtSearchLinkProvider.kt`)
- **网络接口**: `BtSearch.kt`（Retrofit + 独立 OkHttpClient + MD5 签名）
- **搜索结果**: 标题 + 大小 + 日期 + 磁力链接（直接可用，由 hash 构建 `magnet:?xt=urn:btih:${hash}`）
- **文件列表**: 需点击三角请求详情 API 获取

### Tab 2: 无极磁链 (CiliInfo)

| 步骤 | 接口 | 方法 | 说明 |
|------|------|------|------|
| 1 | `GET /search?q=X` | `CiliInfo.search()` | 返回 HTML |
| 2 | 解析 `table.table-hover.file-list tbody tr` | Jsoup | 提取标题、大小、详情链接；日期可选（2列或3列表） |
| 3 | `GET /!lBfm` | `CiliInfo.get()` | 点击三角时请求 |
| 4 | 解析 `#input-magnet` + 文件列表 | Jsoup | 提取磁力链接和文件 |

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
- **时间回写**: 点击三角请求详情页后，从详情页 `发布日期` 提取并回写
- **文件列表**: 需点击三角请求详情页获取

### Tab 3: btsow (BTSO)

| 步骤 | 接口 | 方法 | 说明 |
|------|------|------|------|
| 1 | `GET /btso.php?kw=X&page=N` | `BTSO.search()` | 返回 HTML |
| 2 | 解析 `.row` 元素 | Jsoup | 提取标题、大小、日期、详情磁力链接 |
| 3 | `GET {url}` | `BTSO.get()` | 点击三角时请求详情页 |
| 4 | 解析 `.magnet-link` | Jsoup | 提取磁力链接 |

**搜索页 HTML 结构:**
```html
<div class="row">
  <a href="...">影片标题</a>
  <span class="file">文件名</span>
  <span class="size">文件大小</span>
  <span class="date">日期</span>
</div>
```

- **Provider**: `BTSOLinkProvider` (`network/provider/BTSOLinkProvider.kt`)
- **网络接口**: `BTSO.kt`（Retrofit + `JAViewer.HTTP_CLIENT`）
- **Base URL**: `https://api.rekonquer.com`（通过 `BTSO.kt` 配置）
- **搜索结果**: 标题 + 大小 + 日期 + 详情链接
- **文件列表**: 需点击三角请求详情页获取 `.magnet-link`
- **磁力链接**: 整行点击弹出磁力链接对话框（复制/关闭）
- **长按**: 无

---

## 三、UI 交互

### DownloadScreen 三 Tab

| 操作 | BtSearch Tab | 无极磁链 Tab | btsow Tab |
|------|-------------|-------------|-----------|
| 点击整行 | 弹出磁力链接对话框 | 弹出磁力链接对话框 | 弹出磁力链接对话框 |
| 点击三角 ▶ | 请求详情 API + 展开文件列表 | 请求详情页 + 展开文件列表 | 请求详情页 + 展开文件列表 |

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
- 三角符号在右侧
- 文件列表在 AnimatedVisibility 中展开

---

## 四、涉及文件清单

### 网络层
| 文件 | 职责 |
|------|------|
| `network/BtSearch.kt` | BtSearch API + MD5 签名 + 详情接口 |
| `network/provider/BtSearchLinkProvider.kt` | BtSearch 搜索、详情解析 |
| `network/CiliInfo.kt` | CiliInfo API（search + get） |
| `network/provider/CiliInfoLinkProvider.kt` | CiliInfo HTML 解析 |
| `network/BTSO.kt` | BTSO API（search + get，HTML 响应） |
| `network/provider/BTSOLinkProvider.kt` | BTSO HTML 解析 |
| `network/provider/DownloadLinkProvider.kt` | 抽象基类 + provider 工厂 |

### Screen / ViewModel
| 文件 | 职责 |
|------|------|
| `ui/screen/DownloadScreen.kt` | Compose UI：3 Tab + 列表 + 磁力链接对话框 |
| `ui/screen/DownloadViewModel.kt` | 搜索逻辑 + 详情获取 + 状态管理 |

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
| `ui/navigation/JAViewerNavHost.kt` | NavHost 注册 `download/{keyword}` 路由 |

---

## 五、流程图

```
MovieDetailScreen
    │
    └── 点击"影片番号" Header 行
        └── → DownloadScreen (keyword = detail.code)

DownloadScreen (三 Tab，同时搜索)
    │
    ├── Tab 1: BtSearch
    │   ├── 搜索: GET /api/search (JSON, 签名验证)
    │   │   └── 返回: id, name, size, hash, created_at
    │   │   └── 磁力: 由 hash 构建 magnet:?xt=urn:btih:${hash}
    │   ├── 文件列表: GET /api/torrent/{id} (JSON, 点击三角)
    │   │   └── 返回: torrentfile[] (name, size)
    │   └── 点击整行: 弹出磁力链接对话框
    │
    ├── Tab 2: 无极磁链
    │   ├── 搜索: GET /search?q= (HTML)
    │   │   └── 解析: table.file-list tr → title, size
    │   ├── 文件列表: GET /!id (HTML, 点击三角)
    │   │   └── 解析: #input-magnet + table.file-list + 发布日期
    │   └── 点击整行: 弹出磁力链接对话框
    │
    └── Tab 3: BTSO
        ├── 搜索: GET /btso.php?kw=X&page=N (HTML)
        │   └── 解析: .row → title, size, date, detail link
        ├── 文件列表: GET detail_url (HTML, 点击三角)
        │   └── 解析: .magnet-link
        └── 点击整行: 弹出磁力链接对话框
```

---

## 六、版本历史

| 版本 | 变更 |
|------|------|
| v3.0+ | Kotlin/Compose 重写：Java/XML 全部移除，DownloadScreen 替代 DownloadActivity |
| v2.2.1 | 修复 MovieActivity 空指针 |
| v2.2.0 | DownloadActivity 新增 btsow Tab |
| v2.1.0 | 新增 cili.info 源 |
| v2.0.3 | BtSearch 磁力搜索 |
