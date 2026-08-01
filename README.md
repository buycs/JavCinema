# JavCinema

## 项目背景

本项目基于上游开源项目 **JAViewer**（原仓库 `https://github.com/SplashCodes/JAViewer`）二次开发而来。

- 本目录 `origin` 指向的是**上游仓库**，仅供跟踪/参考，**禁止直接推送**到上游。
- 本次二次开发对原项目做了全面重塑与重构：
  - 品牌重命名：`JAViewer` → `JavCinema`（包名、类名、资源、文档、启动 logo、应用名全部迁移）
  - 代码迁移：从 Java/XML 迁移为 **100% Kotlin + Jetpack Compose + Material 3**，采用单 Activity + Compose Navigation 架构
  - 图片加载改用 Coil，播放器改用 Media3 ExoPlayer
  - 新增 HTTP 重试拦截器（IOException + 5xx/429 自动重试）
  - 兼容性适配：ABI 拆分包（arm64-v8a / armeabi-v7a / x86_64）、API≤28 存储权限运行时申请、命名 APK 产物
  - 版本号从上游的 `2.2.1 (19)` 重置为 `0.0.1-alpha (1)`

## 官方Telegram交流群
点击加入 [JavCinema 官方步行街](https://t.me/joinchat/Bp7eL0ehwp4WI-GxWxitQg) 【禁止车辆通行】
