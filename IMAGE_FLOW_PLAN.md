# JavCinema 图片资源全链路方案（完整版）

> 状态：方案评审稿 + Phase 0 实测已完成，未开始改代码。
> 日期：2026-08-01

## 0. Phase 0 实测结论（真实 API 验证）

实测三个数据源（骑兵 avmoo.shop / 欧美 avheat.shop / 步兵 avsox.click，步兵经代理 127.0.0.1:7890）：

| 验证项 | 骑兵 avmoo | 欧美 avheat | 步兵 avsox |
|---|---|---|---|
| 列表返回 posterSmall / posterLarge | ✅ | ✅ | ✅（10/10） |
| 列表返回 sampleSmall / sampleLarge | ✅ 10 张 | ✅ 6 张 | **仅 FC2 类 ~20%**，其余为空 |
| 关联列表有 poster、无 sample | ✅ | ✅ | ✅ |
| 详情返回 star[].avatarUrl | ✅ | ✅（含 nowprinting.gif 占位） | ✅（webp 格式） |
| posterSmall 比例 | 147×200 **竖 0.735** | 640×360 = 16:9 | **150×150 正方形 1.0** |
| posterLarge 比例 | 800×539 **横 1.484** | 1920×1080 = 16:9 | 960×540 = 16:9 |
| sampleSmall 比例 | **120×90 = 4:3**（非 16:9） | 640×360 = 16:9 | FC2 有、比例同 JAV |
| sampleLarge 比例 | 800×450 = 16:9 | 1920×1080 = 16:9 | FC2 有、比例同 JAV |
| URL 规律 | `xxxps.jpg` / `xxxpl.jpg` | `s_00.jpg` / `b_00.jpg` | FC2=`ps/pl`；业余厂牌=`l_thum.jpg` / `l_hd.jpg` |

**五个关键发现：**
1. **欧美源 `ps→pl` 推断失效（现状缺陷）**：`MovieCard.kt:57` / `MovieDetailScreen.kt:134` 的 `endsWith("ps.jpg")` 对欧美源恒 false → 大封面预取完全跳过。注册表直接取 `posterLarge` 可顺带修复。
2. **sampleSmall 并非 16:9**：骑兵为 120×90（4:3），`ScreenshotRow.kt:49` 用 16:9 容器会裁边。
3. **posterLarge 实际比例 1.484**，代码占位 `800/565=1.416` 略不一致。
4. **步兵列表 sample 覆盖极低**（~20%，仅 FC2）：多数步兵影片无截图（详情也为空），截图预取优化对这部分不生效，但详情页现状同样走"无截图 → 封面预览兜底"，方案无需特殊处理。
5. **三个源 posterLarge 在列表/关联/详情接口 URL 完全一致** → 注册表直取 `posterLarge` 在所有源成立。

**待真机验证（CLI 无法测）**：① 写主 loader 内存缓存→"立即回显"；② "成功一个回显一个"对已组合 AsyncImage 的重渲染。

## 1. 目标与适用范围

- 覆盖：主列表（热门/全部/发行）、筛选列表（genre/star/studio/director/series/label）、搜索列表、关联影片列表、详情页。
- 三个 API 源（骑兵/步兵/欧美）字段一致、**普遍适用**（注册表不依赖 ps/pl 后缀）；HTML 源与 favorites 无以下字段，走回退逻辑。

## 2. 数据源字段确认

| 字段 | 列表接口 | 关联列表 | 详情接口 | 比例（实测，按源而异） |
|---|---|---|---|---|
| `posterSmall` | ✅ | ✅ | ✅ | 骑兵 竖 0.735；步兵 正方形 1.0；欧美 16:9 |
| `posterLarge` | ✅ | ✅ | ✅ | 骑兵 横 1.484；步兵/欧美 16:9 |
| `sampleSmall[]` | ✅ | ❌ | ✅ | 骑兵 4:3；欧美 16:9 |
| `sampleLarge[]` | ✅ | ❌ | ✅ | 16:9 |
| `avatarUrl[]` | ❌ | ❌ | ✅ | 圆形（jpg/gif/webp，个别占位图） |
| `avatarUrl[]` | ❌ | ❌ | ✅ | 圆形（个别为占位 gif） |

## 3. 内存注册表（核心新增）

`JavCinema` companion 增加：

```
movieId/link -> ImageUrls(
    posterSmall: String?,
    posterLarge: String?,
    sampleSmall: List<String>?,
    sampleLarge: List<String>?
)
```

- `ConcurrentHashMap`，按 movieId 索引，全 app 可见。
- 填充点：`AVMOProvider.fromApiList()`（主列表/筛选/搜索统一走这里）；关联影片返回后**只写 poster 两项**。
- 清理点：详情页销毁时移除该条；容量可设上限（LRU 简单裁剪）防泄漏。
- 用途：跳转详情时不用 nav args 传多参数，注册表一次取全；`posterSmall` 用于模糊底兜底 + `ps→pl` 回退。

## 4. 阶段 ① 列表页流程

```
请求列表接口(getMovies / getFilterMovies)
   │
   ├─ 响应 JSON
   │     │
   │     └─ AVMOProvider.fromApiList(apiMovies)
   │           ├─ 生成 Movie 列表（coverUrl = posterSmall）
   │           └─ 填充注册表: movieId -> {posterSmall, posterLarge, sampleSmall[], sampleLarge[]}
   │
   ├─ LazyGrid 组合可见卡片（3 列，可见约 9~12 张）
   │     │
   │     ├─ 每张卡片: 占位组件（surfaceVariant 底 + 番号文字）→ AsyncImage(posterSmall)
   │     └─ 并发控制: OkHttp Dispatcher maxRequests=3（仅当前可见）
   │
   └─ 预取策略
         ├─ 小封面: 仅"当前可见"由卡片 AsyncImage 触发（替换原整页 60 张 eager 预取）
         └─ posterLarge/sampleSmall/sampleLarge: 只缓存 URL，不下载（避免 60×10 无效流量）
```

**改动**：`preloadCovers`（`HomeViewModel.kt:47` / `MovieListViewModel.kt:42` / `SearchViewModel.kt:45`）改为可见性预取；`fromApiList` 填充注册表；`MovieCard` 加 placeholder。

## 5. 阶段 ② 详情页流程

```
点击卡片
  │
  ├─ 命中注册表(movieId)?
  │     ├─ 是 → 拿 posterLarge + sampleSmall[]
  │     └─ 否 → 回退 ps→pl 推断（favorites/HTML/关联无样本）
  │
  ├─ [高优·单路] 大封面预取启动
  │     ├─ loader: 主 ImageLoader（写主内存缓存 → 回显"立即"）
  │     ├─ 超时 3s × 重试 3 次（RetryInterceptor）
  │     └─ 成功 → 立即回显（模糊→清晰动画）
  │
  ├─ Compose 导航（enterTransition 淡入模糊过渡）
  │
  └─ 详情页挂载（Loading 态：全屏模糊底 = posterSmall / 已回显大封面）
        │
        ├─ [并发] getMovie 详情接口 ──────────────┐
        ├─ [并发] getRelatedMovies 关联列表  ←── 并发而非串行（现为详情成功后请求）
        ├─ [并发] 截图缩略图预取（5 线程）
        │        └─ sampleSmall[]（注册表命中）成功一个回显一个
        │
        ├─ getMovie 返回
        │     ├─ 渲染详情内容（标题/元信息/封面→posterLarge）
        │     ├─ [回退路径] 注册表未命中 → 此时补预取截图缩略图
        │     └─ [串行·单路] 女优头像预取
        │              └─ avatarUrl[] 顺序 enqueue（数量少、图小，无需并发）
        │
        ├─ getRelatedMovies 返回
        │     ├─ 底部关联影片渲染（MovieCard，coverUrl=posterSmall）
        │     └─ poster 字段回写注册表（供下次点入）
        │
        └─ 点截图 → GalleryOverlay
              └─ [窗口化·5 线程] sampleLarge 预取（见 §5.1）
```

### 5.1 图库 GalleryOverlay — sampleLarge 窗口化预取

```
点击截图缩略图打开图库
   │
   ├─ URL 已齐备（detail.screenshots.mapNotNull{ getImageUrl() } = sampleLarge[]）
   │     无需额外请求
   │
   ├─ [5 线程] 窗口化预取 sampleLarge
   │     ├─ 只预取 当前页 ±1（如 currentIndex-1..currentIndex+1）
   │     ├─ loader: 主 ImageLoader（写内存缓存 → 滑动即时显示）
   │     ├─ 随 Pager 滑动滚动补取下一窗口
   │     └─ 超时 3s × 重试 3 次（RetryInterceptor，与详情统一）
   │
   ├─ 关闭图库
   │     └─ DisposableEffect/onClose 取消未完成预取
   │
   └─ 提示: 不预取全部（十几张整图会挤占内存缓存 0.25 + 浪费带宽）
```

**并发模型汇总（4 条独立通道）**：

| 通道 | 对象 | 并发 | 依赖 |
|---|---|---|---|
| 高优单路 | 大封面 `posterLarge` | 1 | 注册表/回退 |
| 5 线程 | 截图缩略图 `sampleSmall[]` | ≤5 | 注册表（详情返回仅作回退） |
| 串行单路 | 女优头像 `avatarUrl[]` | 1 | **必须等 getMovie 返回** |
| 5 线程·窗口化 | 截图大图 `sampleLarge[]`（图库） | ≤5 | 点击截图时，URL 已齐备 |
| — | `getMovie` / `getRelatedMovies` | 2 并发 | 无 |

## 6. 统一超时/重试

- 为详情/预取客户端统一：`connectTimeout=3s`、`readTimeout=3s`、`RetryInterceptor(maxRetries=3)`。
- 删掉 `enqueueCoverInternal`（`JavCinemaApp.kt:163`）的手写重试；`PREFETCH_HTTP_CLIENT` 补挂 `RetryInterceptor`（目前缺）。
- 并发上限用 OkHttp `Dispatcher(maxRequests/maxRequestsPerHost)` 表达，不用原生线程。

## 7. 占位与过渡

- **占位策略（固定容器 + ContentScale.Crop，不逐源追比例）**：
  - 实测各源比例差异大（骑兵小封面 0.735 / 步兵正方形 1.0 / 欧美大封面 16:9），逐个对齐收益低；**保持现状固定容器 + Crop 裁剪**即可，视觉差异可接受。
  - 关键点是"占位框比例 == 最终容器比例"（同一屏内不跳变），而非"容器 == 图片真实比例"。现状两处均满足，保留：
    - 列表卡片 `aspectRatio(0.7)`（`MovieCard.kt:67`）。
    - 详情大封面 `aspectRatio(800/565≈1.42)`（`MovieDetailScreen.kt:537`）。
  - 图库截图容器 16:9（现状，骑兵 4:3 缩略图会被裁边，可后续按源优化，非本方案重点）。
- **Loading 态**：`blur(20.dp)` 静态 → `animateFloatAsState(20→0, tween(600))`（全屏模糊底不设比例，内容区按横版占位）。
- **导航过渡**：`composable(enterTransition = fadeIn)` 配合模糊底，实现"跳转途中全屏模糊"。

## 8. 退出回收

| 任务 | 处理 |
|---|---|
| 截图缩略图预取 | 随 VM `onCleared` / `DisposableEffect` 取消 |
| 女优头像预取 | 同上取消 |
| 图库 `sampleLarge` 窗口预取 | 随 GalleryOverlay `DisposableEffect` / `onClose` 取消 |
| `getMovie`/`getRelatedMovies` | `viewModelScope` 天然取消 |
| 大封面预取 | **放行写盘**（磁盘缓存下次秒开），不强制中断 |
| 注册表条目 | 详情销毁时移除该 movieId，防泄漏 |

## 9. 涉及文件清单

| 文件 | 改动 |
|---|---|
| `JavCinemaApp.kt` | 注册表 + `ImageUrls` 模型；prefetch 客户端加 RetryInterceptor + 3s 超时；可选详情专用 loader |
| `network/provider/AVMOProvider.kt` | `fromApiList` 填充注册表 |
| `ui/screen/HomeViewModel.kt` / `MovieListViewModel.kt` / `SearchViewModel.kt` | `preloadCovers` 改可见性预取 + 并发 3 |
| `ui/components/MovieCard.kt` | 加占位；去掉卡片内 `ps→pl` 大封面预取（改由详情入口统一做，或保留为低优先预取） |
| `ui/navigation/JavCinemaNavHost.kt` | `enterTransition` 模糊过渡 |
| `ui/screen/MovieDetailViewModel.kt` | `getMovie`/`getRelatedMovies` 并发；详情返回后女优串行预取；注册表读写 |
| `ui/screen/MovieDetailScreen.kt` | 占位比例统一；blur 动画；截图/女优预取触发与取消句柄；GalleryOverlay 窗口化 `sampleLarge` 预取 |
| `ui/screen/MovieListScreen.kt` / `HomeScreen.kt` / `FavouritesScreen.kt` | 跳转携带不变（注册表取数） |

## 10. 实施阶段

1. **Phase 0 ✅ 已完成**：三源接口字段与图片比例已实测确认（见 §0），步兵经代理 127.0.0.1:7890 验证。
2. **Phase 1**：注册表 + `fromApiList` 填充（含欧美 `posterLarge` 直取，顺带修复 ps→pl 失效）+ `MovieCard` 占位 + 可见性预取（替换全量 eager）。
3. **Phase 2**：详情并发改造（大封面直取 `posterLarge` 写主内存缓存；截图缩略图注册表驱动；女优串行；关联列表并发；回写注册表）。
4. **Phase 3**：占位比例对齐实测（小竖 0.735 / 大横 1.484，截图容器按需适配 4:3）、blur 动画、导航过渡。
5. **Phase 4**：退出回收 + 注册表清理。
6. **Phase 5**：`./gradlew assembleDebug` + `./gradlew test` + 弱网/快速进出/切数据源回归；真机验证"立即回显"与"成功一个回显一个"。

## 11. 关键风险

- 并发上限若共用 OkHttp 连接池，列表预取与详情预取会互相挤占 → 详情入口临时提权或独立 dispatcher。
- "立即回显"依赖**主 loader 内存缓存**，专用 loader 预取写不进去（现状 `prefetchImageLoader` 独立 5% 缓存）。
- 关联影片/ favorites/HTML 无样本字段，务必保留详情接口回退路径。
- 欧美源无 ps/pl 后缀，任何 `ps→pl` 推断对欧美失效 → 一律用注册表 `posterLarge`，推断仅作 favorites/HTML 兜底。
- 女优头像存在 `nowprinting.gif` 占位图，预取/展示需容忍，勿当失败处理。
