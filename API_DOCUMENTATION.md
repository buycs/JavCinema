# JavCinema API 接口文档

> 基于当前 Kotlin/Compose 源码整理（100% Kotlin，0 Java）

---

## 目录

1. [数据源配置](#1-数据源配置)
2. [AvmooApiService — 主数据源 JSON API](#2-avmooapiservice--主数据源-json-api)
3. [BasicService — HTML 抓取 API（后备）](#3-basicservice--html-抓取-apibackup)
4. [Avgle — 视频搜索 API（已废弃）](#4-avgle--视频搜索-apideprecated)
5. [BTSO — BT 搜索 JSON API](#5-btso--bt-搜索-json-api)
6. [BtSearch — 带签名验证的 BT 搜索 API](#6-btsearch--带签名验证的-bt-搜索-api)
7. [CiliInfo — 无极磁链 HTML 解析 API](#7-ciliinfo--无极磁链-html-解析-api)
8. [TorrentKitty — 磁力搜索 API](#8-torrentkitty--磁力搜索-api)
9. [PSVS — 在线视频播放 API](#9-psvs--在线视频播放-api)
10. [Provider 层](#10-provider-层)
11. [数据模型](#11-数据模型)
12. [接口调用流程图](#12-接口调用流程图)

---

## 1. 数据源配置

**文件:** `app/src/main/assets/properties.json`

应用启动时从 `assets/properties.json` 加载数据源列表，运行时可在 UI 中切换。数据源也支持从远程 `properties.json` 拉取更新。

```json
{
  "latest_version": "0.0.1-alpha",
  "latest_version_code": "1",
  "changelog": "",
  "data_sources": [
    {
      "name": "骑兵",
      "link": "https://avmoo.shop",
      "apiPath": "/jav/data/api/",
      "legacies": ["javzoo.com", "avmoo.xyz", "avmoo.net", "avmoo.pw", ...]
    },
    {
      "name": "步兵",
      "link": "https://avsox.click",
      "apiPath": "/javu/data/api/",
      "legacies": ["avme.pw", "avsox.net", ...]
    },
    {
      "name": "欧美",
      "link": "https://avheat.shop",
      "apiPath": "/wav/data/api/",
      "legacies": []
    }
  ],
  "magnet_sources": [
    { "name": "BtSearch", "link": "https://www.btsearch.love" },
    { "name": "Cili", "link": "https://cili.info" },
    { "name": "BTSOW", "link": "https://btsow.live" }
  ]
}
```

> 注意：字段名为 `link`（不是 `domain`），对应 `DataSource.link`。

**Base URL 构建规则:** `link + apiPath`，例: `https://avmoo.shop/jav/data/api/`

**域名替换:** `JavCinema.hostReplacements` Map 可将旧域名（legacies）映射为当前活跃域名，所有 OkHttp 请求通过 `replaceUrl()` 拦截器自动替换。

**magnet_sources:** 磁力搜索源列表（BtSearch / Cili / BTSOW），在下载页（DownloadScreen）三 Tab 并发搜索。

---

## 2. AvmooApiService — 主数据源 JSON API

**文件:** `network/AvmooApiService.kt`

主数据源的现代 JSON API。所有请求为 **POST**，`Content-Type: application/json`，请求体均为 `List<Object>` (JSON Array)，通过 `JavCinema.AVMOO_API_SERVICE`（懒初始化，数据源切换时 recreate）调用。

```kotlin
interface AvmooApiService {
    @POST("getMovies")      suspend fun getMovies(@Body params: List<Any>): AvmooMovieListResponse
    @POST("getMovie")       suspend fun getMovie(@Body params: List<Any>): AvmooMovieDetailResponse
    @POST("getRelatedMovies") suspend fun getRelatedMovies(@Body params: List<Any>): AvmooMovieListResponse
    @POST("getFilterMovies") suspend fun getFilterMovies(@Body params: List<Any>): AvmooMovieListResponse
    @POST("getStars")       suspend fun getStars(@Body params: List<Any>): AvmooStarListResponse
    @POST("getStar")        suspend fun getStar(@Body params: List<Any>): AvmooStarResponse
    @POST("getGenres")      suspend fun getGenres(@Body params: List<Any>): AvmooGenreListResponse
}
```

### 2.1 获取首页影片

```
POST {baseUrl}getMovies
Body: ["home", 60, page]
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `[0]` | string | 固定 `"home"` |
| `[1]` | int | 每页数量（固定 60） |
| `[2]` | int | 页码（从 1 开始） |

### 2.2 获取影片详情

```
POST {baseUrl}getMovie
Body: [movieId, "cn"]
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `[0]` | string | `Movie.id` / `Movie.link`（movieId） |
| `[1]` | string | 语言，固定 `"cn"` |

### 2.3 获取女优列表

```
POST {baseUrl}getStars
Body: ["stars", 60, page]
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `[0]` | string | 固定 `"stars"` |
| `[1]` | int | 每页数量（固定 60） |
| `[2]` | int | 页码（从 1 开始） |

### 2.4 获取分类列表

```
POST {baseUrl}getGenres
Body: ["types", 60]
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `[0]` | string | 固定 `"types"` |
| `[1]` | int | 数量（固定 60） |

### 2.5 获取筛选影片列表

```
POST {baseUrl}getFilterMovies
Body: [action, keyword, "cn", 60, page]
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `[0]` | string | 筛选类型: `"popular"` / `"released"` / `"genre"` / `"star"` / `"studio"` / `"director"` / `"label"` / `"series"` |
| `[1]` | string | 筛选关键字（ID） |
| `[2]` | string | 语言，固定 `"cn"` |
| `[3]` | int | 每页数量（固定 60） |
| `[4]` | int | 页码（从 1 开始） |

**调用方:**
- PopularScreen / ReleasedScreen / MovieListScreen / HomeScreen(热门 tab)

### 2.6 搜索影片

```
POST {baseUrl}getFilterMovies
Body: ["search", keyword, "cn", 60, page]
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `[0]` | string | 固定 `"search"` |
| `[1]` | string | 搜索关键词 |
| `[2]` | string | 语言，固定 `"cn"` |
| `[3]` | int | 每页数量（固定 60） |
| `[4]` | int | 页码（从 1 开始） |

> 搜索走 `getFilterMovies` 的 `"search"` action（旧版 `search` 端点不再使用）。

**调用方:** `SearchViewModel` → `SearchScreen`

### 2.7 获取相关影片

```
POST {baseUrl}getRelatedMovies
Body: [movieId, "cn", 12]
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `[0]` | string | 影片 ID（`Movie.link`） |
| `[1]` | string | 语言，固定 `"cn"` |
| `[2]` | int | 返回数量（固定 12） |

### 2.8 获取单女优详情

```
POST {baseUrl}getStar
Body: [starId]
```

**响应:** `AvmooStarResponse`（含女优头像、作品数、生日等）。

---

## 3. BasicService — HTML 抓取 API（backup）

**文件:** `network/BasicService.kt`

旧式 HTML 抓取接口，作为 JSON API 不可用时的后备。全部为 **GET**，返回 HTML，由 `AVMOProvider` 用 Jsoup 解析。经 `JavCinema.SERVICE` 调用（懒初始化，数据源切换时 recreate）。

```kotlin
interface BasicService {
    @GET("$LANGUAGE_NODE/page/{page}")        suspend fun getHomePage(@Path("page") page: Int): ResponseBody
    @GET("$LANGUAGE_NODE/released/page/{page}") suspend fun getReleased(@Path("page") page: Int): ResponseBody
    @GET("$LANGUAGE_NODE/popular/page/{page}")  suspend fun getPopular(@Path("page") page: Int): ResponseBody
    @GET("$LANGUAGE_NODE/actresses/page/{page}") suspend fun getActresses(@Path("page") page: Int): ResponseBody
    @GET("$LANGUAGE_NODE/genre")               suspend fun getGenre(): ResponseBody
    @GET                                     suspend fun get(@Url url: String): ResponseBody
}
```

| 方法 | 说明 |
|------|------|
| `getHomePage(page)` | `/cn/page/{page}` 首页影片列表 |
| `getReleased(page)` | `/cn/released/page/{page}` 最新发行 |
| `getPopular(page)`  | `/cn/popular/page/{page}` 热门 |
| `getActresses(page)` | `/cn/actresses/page/{page}` 女优列表 |
| `getGenre()`        | `/cn/genre` 分类页 |
| `get(url)`          | 任意详情页 URL |

---

## 4. Avgle — 视频搜索 API（deprecated）

**文件:** `network/Avgle.kt`

**Base URL:** `https://api.avgle.com`

```kotlin
interface Avgle {
    @GET("v1/search/{keyword}/0?limit=1")
    suspend fun search(@Path("keyword") keyword: String): AvgleSearchResult
    @GET
    suspend fun get(@Url url: String): AvgleSearchResult
}
```

**调用方:** 已废弃，被 PSVS 替代。

---

## 5. BTSO — BT 搜索 JSON API

**文件:** `network/BTSO.kt`

**Base URL:** `https://btsow.live`

```kotlin
interface BTSO {
    @POST("/bts/data/api/search")
    suspend fun search(@Body body: RequestBody): BTSOSearchResponse
    @POST("/bts/data/api/magnet")
    suspend fun getMagnet(@Body body: RequestBody): BTSOMagnetResponse
}
```

使用 `JavCinema.HTTP_CLIENT` 共享客户端 + Gson 转换器。请求体为原始 JSON 字符串（`RequestBody`）。

### 5.1 搜索

```
POST /bts/data/api/search
Content-Type: application/json

[{"search": "MADB-004"}, 30, 1]
```

| Body 元素 | 类型 | 说明 |
|-----------|------|------|
| `[0].search` | string | 搜索关键词 |
| `[1]` | int | 每页数量（固定 30） |
| `[2]` | int | 页码 |

**响应:**

```json
{
  "code": 0,
  "data": [
    {
      "hash": "6D8CD7F3E8821906D4EB3E0759A59F581FB2A8F5",
      "name": "MADB-004-C ...",
      "size": 8484889014,
      "lastUpdateTime": 1783840027
    }
  ]
}
```

### 5.2 获取文件列表

```
POST /bts/data/api/magnet
Content-Type: application/json

["6D8CD7F3E8821906D4EB3E0759A59F581FB2A8F5"]
```

**响应:**

```json
{
  "code": 0,
  "data": {
    "files": [
      {"filename": "file1.mp4", "size": 2001226},
      {"filename": "file2.mp4", "size": 6081313040}
    ]
  }
}
```

---

## 6. BtSearch — 带签名验证的 BT 搜索 API

**文件:** `network/BtSearch.kt`

**Base URL:** `https://www.btsearch.love`

使用**独立 OkHttpClient**（`OkHttpClient.Builder().addInterceptor(signingInterceptor)`），通过拦截器自动注入签名头。每次请求都动态计算签名。

### 6.0 鉴权机制

#### 6.0.1 签名流程

```
客户端请求
    │
    ├─ 收集所有 Query 参数（来自 Retrofit @Query 注解）
    │
    ├─ 生成 timestamp = currentTimeMillis() / 1000（秒级 Unix 时间戳）
    │
    ├─ 生成 nonce    = UUID.randomUUID() → 去连字符 → 取前 8 位
    │
    ├─ 合并参数 Map: query参数 + timestamp + nonce
    │
    ├─ 按字典序排序所有 key=value
    │
    ├─ 用 & 拼接 → 末尾追加 "&key=long2ice"
    │
    ├─ MD5 加密（小写 hex）→ 转大写
    │
    └─ 设置请求头:
        ├─ x-timestamp
        ├─ x-nonce
        ├─ x-sign
        ├─ Accept: "application/json"
        ├─ User-Agent: Chrome 91 Windows UA
        └─ Referer: "https://www.btsearch.love/search"
```

#### 6.0.2 关键参数

| 参数 | 生成方式 | 代码位置 |
|------|----------|----------|
| `timestamp` | `System.currentTimeMillis() / 1000`（秒级） | `BtSearch.kt` signingInterceptor |
| `nonce` | `UUID.randomUUID().toString().replace("-","").substring(0,8)` | `BtSearch.kt` signingInterceptor |
| `sign` | MD5(排序后 query string + `&key=long2ice`).toUpperCase() | `BtSearch.kt` signingInterceptor |
| `Secret Key` | 固定值 `long2ice` | `BtSearch.kt` companion |

#### 6.0.3 Kotlin 实现（签名拦截器）

```kotlin
private val signingInterceptor = Interceptor { chain ->
    val original = chain.request()
    val url = original.url
    val timestamp = System.currentTimeMillis() / 1000
    val nonce = UUID.randomUUID().toString().replace("-", "").substring(0, 8)

    val params = mutableMapOf<String, String>()
    url.queryParameterNames.forEach { name ->
        url.queryParameter(name)?.let { params[name] = it }
    }
    params["timestamp"] = timestamp.toString()
    params["nonce"] = nonce

    val sorted = params.entries.sortedBy { it.key }
    val raw = sorted.joinToString("&") { "${it.key}=${it.value}" } + "&key=$SECRET_KEY"
    val sign = md5(raw).uppercase()

    val request = original.newBuilder()
        .header("x-timestamp", timestamp.toString())
        .header("x-nonce", nonce)
        .header("x-sign", sign)
        .header("Accept", "application/json")
        .header("User-Agent", JavCinema.USER_AGENT)
        .header("Referer", "https://www.btsearch.love/search")
        .build()
    chain.proceed(request)
}
```

#### 6.0.4 请求头汇总

| Header | 值 | 说明 |
|--------|-----|------|
| `x-timestamp` | Unix 秒级时间戳 | 防重放，服务端校验时间窗口 |
| `x-nonce` | 8 位随机 hex 字符串 | 防重放，与 timestamp 组合唯一 |
| `x-sign` | MD5 签名（大写 32 位） | 请求完整性校验 |
| `Accept` | `application/json` | 强制 JSON 响应 |
| `User-Agent` | Chrome 91 on Windows 10 | 反爬虫绕过 |
| `Referer` | `https://www.btsearch.love/search` | 模拟浏览器来源 |

### 6.1 搜索

```
GET /api/search?keyword={keyword}&limit={limit}&offset={offset}&mode={mode}&time={time}&sort={sort}&sort_type={sort_type}&size={size}
```

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `keyword` | string | - | 搜索关键词 |
| `limit` | int | 10 | 每页数量 |
| `offset` | int | 0 | 偏移量 |
| `mode` | string | "" | 模式 |
| `time` | string | "" | 时间范围 |
| `sort` | string | "" | 排序字段 |
| `sort_type` | string | "asc" | 排序方向 |
| `size` | string | "" | 大小过滤 |

**响应 (Gson 反序列化为 `BtSearchResponse`):**

```json
{
  "total": 100,
  "data": [
    {
      "id": 2419147587,
      "name": "MADB-004",
      "size": "6083314266",
      "created_at": "2026-07-25T21:05:20.240Z",
      "hash": "969af3a5efd45076cb3aadd1a83fb54fd7d3b195",
      "count": 2,
      "hot": 0
    }
  ],
  "time_ms": 123
}
```

### 6.2 获取详情（文件列表）

```
GET /api/torrent/{id}?keyword={keyword}
```

| 参数 | 位置 | 类型 | 说明 |
|------|------|------|------|
| `id` | Path | long | 种子 ID |
| `keyword` | Query | string | 搜索关键词 |

**响应:**

```json
{
  "torrentfile": [
    {"name": "file1.mp4", "size": "2001226"},
    {"name": "file2.mp4", "size": "6081313040"}
  ]
}
```

**调用方:** `BtSearchLinkProvider.searchApi()` / `BtSearchLinkProvider.getDetail()` — 签名由拦截器自动注入，上层无感知。

---

## 7. CiliInfo — 无极磁链 HTML 解析 API

**文件:** `network/CiliInfo.kt`

**Base URL:** `https://cili.info`

```kotlin
interface CiliInfo {
    @GET("/search")
    @Headers("Accept-Language: zh-CN,zh;q=0.8,en;q=0.6")
    suspend fun search(@Query("q") keyword: String): ResponseBody

    @GET("/{path}")
    @Headers("Accept-Language: zh-CN,zh;q=0.8,en;q=0.6")
    suspend fun get(@Path("path", encoded = true) path: String): ResponseBody
}
```

使用 `JavCinema.HTTP_CLIENT` 共享客户端。**无 Gson 转换器**，返回 `ResponseBody`，由 `CiliInfoLinkProvider` 用 Jsoup 解析。

### 7.1 搜索

```
GET /search?q={keyword}
```

**响应:** HTML，由 `CiliInfoLinkProvider.parseDownloadLinks()` 解析。

**HTML 结构:**

```html
<table class="table table-hover file-list">
  <tbody>
    <tr>
      <td><a href="/!lBfm">APKH-197</a></td>
      <td class="td-size">7.02GB</td>
    </tr>
  </tbody>
</table>
```

### 7.2 获取详情

```
GET {url}  (e.g., /!lBfm 或 https://cili.info/!lBfm)
```

**HTML 结构:**

```html
<input id="input-magnet" value="magnet:?xt=urn:btih:..." />
<table class="table table-hover file-list">
  <tbody>
    <tr>
      <td>filename.mp4</td>
      <td class="td-size">5.66 GB</td>
    </tr>
  </tbody>
</table>
<dt>发布日期 :</dt><dd>2026-07-26 02:01:02</dd>
```

---

## 8. TorrentKitty — 磁力搜索 API

**文件:** `network/TorrentKitty.kt`

**Base URL:** `https://www.torrentkitty.tv`

```kotlin
interface TorrentKitty {
    @GET("/search/{keyword}")         suspend fun search(@Path("keyword") keyword: String): ResponseBody
    @GET("/search/{keyword}/{page}")  suspend fun searchPage(@Path("keyword") keyword: String, @Path("page") page: Int): ResponseBody
    @GET                              suspend fun get(@Url url: String): ResponseBody
}
```

返回 HTML（带 Referer/Accept/Accept-Language 请求头），由 `TorrentKittyLinkProvider` 用 Jsoup 解析 `#archiveResult` 表格。**不是 magnet_sources 默认配置**，但 `DownloadLinkProvider.getProvider("torrentkitty")` 支持切换。

**HTML 结构:**

```html
<table id="archiveResult">
  <tbody>
    <tr>
      <td><a class="name" href="/detail/...">MADB-004</a></td>
      <td>2.01GB</td>
      <td>2026-07-25</td>
    </tr>
  </tbody>
</table>
```

---

## 9. PSVS — 在线视频播放 API

**文件:** `network/PSVS.kt`

**Base URL:** `http://api.rekonquer.com`

```kotlin
interface PSVS {
    @GET("/psvs/search.php")
    suspend fun search(@Query("kw") keyword: String): AvgleSearchResult
}
```

使用 `JavCinema.HTTP_CLIENT` + Gson 转换器。

### 9.1 搜索视频源

```
GET /psvs/search.php?kw={keyword}
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `kw` | string | 影片番号 |

**响应:** `AvgleSearchResult`（同 Avgle API 响应结构）

### 9.2 播放地址生成

获取到 `vid` 后，构造播放 URL:

```
http://api.rekonquer.com/psvs/mp4.php?vid={vid}&ts={timestamp}&sign={sign}
```

- `timestamp` = `System.currentTimeMillis() / 1000`（秒级 Unix 时间戳）
- `sign` = MD5(`vid` + `timestamp` + "Brynhildr") 的 hex 字符串

---

## 10. Provider 层

### 10.1 AVMOProvider

**文件:** `network/provider/AVMOProvider.kt`（object）

主数据源解析器，**同时支持 JSON API 与 HTML 抓取**两套数据路径，并维护图片注册表。

| 方法 | 输入 | 输出 | 说明 |
|------|------|------|------|
| `parseMovies(html)` | HTML | `List<Movie>` | Jsoup 解析影片列表（`a.movie-box`） |
| `parseActresses(html)` | HTML | `List<Actress>` | Jsoup 解析女优列表（`a.avatar-box`） |
| `parseMoviesDetail(html)` | HTML | `MovieDetail` | Jsoup 解析影片详情 |
| `parseGenres(html)` | HTML | `Map<String, List<Genre>>` | Jsoup 解析分类页 |
| `fromApiList(apiMovies)` | `List<AvmooMovie>` | `List<Movie>` | JSON API 列表 → Movie + 写入 `imageUrlsRegistry` |
| `fromApiDetail(api)` | `AvmooMovieDetail` | `MovieDetail` | JSON API 详情 → MovieDetail |

**JSON API 字段映射（fromApi*）:**

| 模型字段 | JSON 路径 | 说明 |
|----------|-----------|------|
| `Movie.id` / `Movie.link` | `movieId` | 详情链接 ID |
| `Movie.code` | `movieFanHao` | 影片番号 |
| `Movie.title` | `title` / `title_ja` / `title_cn` | 标题（多语言回退） |
| `Movie.coverUrl` | `posterSmall` | 封面小图 |
| `Movie.date` | `releaseDate` | 发行日期 |
| `MovieDetail.coverUrl` | `posterLarge ?: posterSmall` | 封面大图 |
| `MovieDetail.btsSearchUrl` | `btsSearchUrl` | BT 搜索 URL |
| `Screenshot.thumbnailUrl` | `sampleSmall[]` | 截图缩略图 |
| `Screenshot.link` | `sampleLarge[]` | 截图大图 |
| `Actress.name` | `star[].starName_ja/_en/_cn` | 女优名 |
| `Actress.imageUrl` | `star[].avatarUrl ?: avatar` | 头像 |
| `Genre.name` | `genre[].genreName/_ja/_cn` | 分类名称 |
| `MovieDetail.headers` | `releaseDate/length/director/studio/label/series` | 元数据（发行日期/时长/导演/制作商/发行商/系列） |

**图片注册表:** `fromApiList` 将每个 `movieId → ImageUrls(posterSmall, posterLarge, sampleSmall, sampleLarge)` 写入 `JavCinema.imageUrlsRegistry`，供列表封面预取与详情大图使用。

### 10.2 DownloadLinkProvider 体系

**文件:** `network/provider/DownloadLinkProvider.kt`（抽象类）

```kotlin
abstract class DownloadLinkProvider {
    abstract suspend fun search(keyword: String, page: Int): ResponseBody?
    abstract suspend fun parseDownloadLinks(htmlContent: String): List<DownloadLink>
    abstract suspend fun get(url: String): ResponseBody?
    abstract suspend fun parseMagnetLink(htmlContent: String): MagnetLink?
    companion object {
        fun getProvider(name: String): DownloadLinkProvider?  // 按名称分发
    }
}
```

| 名称 | 实现类 | 网络接口 |
|------|--------|----------|
| `"btso"` | `BTSOLinkProvider` | `BTSO`（JSON） |
| `"torrentkitty"` | `TorrentKittyLinkProvider` | `TorrentKitty`（HTML） |
| `"btsearch"` | `BtSearchLinkProvider` | `BtSearch`（JSON+签名） |
| `"ciliinfo"` / `"cili"` | `CiliInfoLinkProvider` | `CiliInfo`（HTML） |

### 10.3 BTSOLinkProvider

**文件:** `network/provider/BTSOLinkProvider.kt`

走 **JSON API**（BTSO），不实现基类的 HTML 抽象方法（均返回空）。额外提供:
- `searchApi(keyword, page): List<DownloadLink>` — 构造 `[{"search": kw}, 30, page]` JSON body → 搜索
- `getMagnetDetail(hash): List<MagnetFile>` — 构造 `[hash]` JSON body → 文件列表
- 磁力链接: `magnet:?xt=urn:btih:{hash}`
- 大小/日期格式化（`formatSize` / `formatTimestamp`）

### 10.4 TorrentKittyLinkProvider

**文件:** `network/provider/TorrentKittyLinkProvider.kt`

- 搜索: 解析 `#archiveResult` 表格中 `class="name"` / 大小 / 日期
- 详情: 解析磁力链接

### 10.5 BtSearchLinkProvider

**文件:** `network/provider/BtSearchLinkProvider.kt`

- `searchApi()`: 调 `BtSearch.INSTANCE.search(keyword, 10, (page-1)*10, ...)` → `List<DownloadLink>`
- `getDetail()`: 调 `BtSearch.INSTANCE.getDetail(id, keyword)` → 文件列表

### 10.6 CiliInfoLinkProvider

**文件:** `network/provider/CiliInfoLinkProvider.kt`

- 搜索: 按 `table.table-hover.file-list tbody tr` 提取（含 `date` 提取）
- 详情: 按 `#input-magnet` 提取磁力链接值
- 文件列表: 同上表格

---

## 11. 数据模型

**包:** `data/model/`

### 通用模型（来自 JSON API / HTML）

| 类 | 字段 | 说明 |
|----|------|------|
| `Linkable` | `link: String` | 可链接基类（序列化） |
| `Movie` | `id, title, code, coverUrl, date, hot, link` | 影片列表项 |
| `MovieDetail` | `title, code, coverUrl, btsSearchUrl, id, link, screenshots, headers, genres, actresses` | 影片详情 |
| `MovieDetail.Header` | `name, value, link` | 元数据行（发行日期/时长/导演/制作商/发行商/系列） |
| `Actress` | `name, imageUrl, link` | 女优 |
| `Genre` | `name, link` | 分类 |
| `Screenshot` | `thumbnailUrl, link(imageUrl)` | 截图 |
| `DownloadLink` | `title, size, date, link, magnetLink, files, filesExpanded` | 下载链接项 |
| `MagnetLink` | `magnetLink: String` | 磁力链接 |
| `MagnetFile` | `hash, torrentName, filename, size` | 磁力文件 |
| `TorrentGroup` | `hash, torrentName, totalSize, date, expanded, files` | 磁力组 |
| `DataSource` | `name, link, apiPath, legacies` | 数据源配置 |
| `Properties` | `latest_version, latest_version_code, changelog, data_sources` | properties.json 映射 |
| `Configurations` | 数据源/图片相关配置（Gson 文件缓存） | 外部存储 configurations.json |

### Avmoo JSON API 模型（`AvmooModels.kt`）

| 类 | 字段 | 说明 |
|----|------|------|
| `AvmooMovieListResponse` | `code, data: List<AvmooMovie>?` | 列表响应 |
| `AvmooMovieDetailResponse` | `code, data: AvmooMovieDetail?` | 详情响应 |
| `AvmooStarListResponse` | `code, data: List<AvmooStar>?` | 女优列表响应 |
| `AvmooStarResponse` | `code, data: AvmooStar?` | 单女优响应 |
| `AvmooGenreListResponse` | `code, data: JsonElement?` | 分类响应（结构动态，用 JsonElement） |
| `AvmooMovie` | `movieId, movieFanHao, title_ja/_cn/_en/_tw, releaseDate, posterSmall/Large, sampleSmall/Large, length, movieDmmUrl, starId, genreId, studioId, directorId, labelId, seriesId` | 影片 |
| `AvmooMovieDetail` | `movieId, movieFanHao, 多语言标题, releaseDate, length, posterSmall/Large, sampleSmall/Large, studio, director, label, series, genre[], star[], btsSearchUrl` | 影片详情 |
| `AvmooStar` | `starId, starDmmId, starName_ja/_en/_cn/_tw, avatar, avatarUrl, movieCount, weight, birthday, size` | 女优 |
| `AvmooGenre` | `genreId, genreDmmId, genreName_ja/_cn/_en/_tw, type` | 分类 |
| `AvmooDirector` | `directorId, directorName_ja/_en/_cn/_tw` | 导演 |
| `AvmooStudio` | `studioId, studioName_ja/_en/_cn/_tw` | 制作商 |
| `AvmooLabel` | `labelId, labelName_ja/_en/_cn/_tw` | 发行商 |
| `AvmooSeries` | `seriesId, seriesName_ja/_en/_cn/_tw` | 系列 |
| `FilterMoviesRequest` | `filter, filterType, filterId, lang="cn", page` | getFilterMovies 请求体构造 |

### 其他

| 类 | 说明 |
|----|------|
| `AvgleSearchResult` | Avgle/PSVS 搜索结果 |
| `BtSearchResponse` / `BtSearchItem` / `BtSearchDetailResponse` / `BtSearchTorrentFile` | BtSearch 响应（定义在 `BtSearch.kt`） |
| `BTSOSearchResponse` / `BTSOSearchItem` / `BTSOMagnetResponse` / `BTSOMagnetData` / `BTSOFile` | BTSO 响应（定义在 `BTSO.kt`） |

---

## 12. 接口调用流程图

```
应用启动
  │
  └─ StartActivity
      ├─ 读取 assets/properties.json → JavCinema.DATA_SOURCES
      ├─ 从 /sdcard/JavCinema/configurations.json 加载配置（Gson）
      ├─ 尝试拉取远程 properties.json（失败用本地）
      └─ 启动 MainActivity

MainActivity (Compose Navigation)
  ├─ recreateService() / getService() → 创建 Retrofit(BasicService) HTML 接口
  ├─ recreateApiService() / getApiService() → 创建 Retrofit(AvmooApiService) JSON 接口
  │
  ├─ 首页: HomeViewModel → getMovies ["home", 60, page]   (热门 tab)
  ├─ 最新: ReleasedViewModel → getFilterMovies ["released", "", "cn", 60, page]
  ├─ 女优: ActressesViewModel → getStars ["stars", 60, page]
  ├─ 分类: GenreViewModel → getGenres ["types", 60]
  ├─ 搜索: SearchViewModel → getFilterMovies ["search", keyword, "cn", 60, page]
  │
  └─ 影片详情: MovieDetailViewModel
      ├─ getMovie [movieId, "cn"] → 解析 fromApiDetail()
      ├─ getRelatedMovies [movieId, "cn", 12] → 相关影片
      └─ 收藏/下载入口

DownloadScreen (Compose，三 Tab 并发搜索)
  │
  ├─ Tab 1: BTSOW (btso)
  │   ├─ POST /bts/data/api/search  [{"search": kw}, 30, page]
  │   ├─ POST /bts/data/api/magnet  [hash]
  │   └─ Provider: BTSOLinkProvider
  │
  ├─ Tab 2: BtSearch
  │   ├─ GET /api/search (MD5 签名头)
  │   ├─ GET /api/torrent/{id} (文件列表)
  │   └─ Provider: BtSearchLinkProvider
  │
  └─ Tab 3: 无极磁链 (cili.info)
      ├─ GET /search?q=keyword (HTML)
      ├─ GET /!xxxxx (详情 HTML)
      └─ Provider: CiliInfoLinkProvider

通用 OkHttp 拦截器 (JavCinema.HTTP_CLIENT):
  └─ User-Agent: Chrome 91
  └─ X-Requested-With: XMLHttpRequest
  └─ 域名替换: hostReplacements Map → 自动替换 host
  └─ RetryInterceptor: 失败重试
  └─ Cookie 持久化: CookieJar
```

---

*文档生成时间: 2026-08-02*
