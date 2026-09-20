# JavCinema

基于 [SplashCodes/JAViewer](https://github.com/SplashCodes/JAViewer) 二次开发的 JAV 影片浏览应用。

## 特性

- 多数据源切换，数据源与磁力源地址均可自定义
- 磁力搜索多源并发
- 收藏、女优浏览、类型分类
- 图库查看，支持保存到相册
- 封面预取 + 共享磁盘缓存 + HTTP 重试
- 按 ABI 拆包：arm64-v8a / armeabi-v7a / x86_64

## 构建

需要 JDK 17 与 Android SDK（compileSdk 35）。Windows 下请用 `gradlew.bat`。

```bash
./gradlew assembleDebug           # 调试包
./gradlew installDebug            # 安装到已连接设备
./gradlew assembleRelease         # Release（R8 混淆，沿用 debug 签名）
./gradlew :app:testDebugUnitTest  # 单元测试
```

## 致谢与免责

基于 [SplashCodes/JAViewer](https://github.com/SplashCodes/JAViewer) 二次开发，遵循上游开源方式；上游未提供 LICENSE，使用前请自行评估。

本项目仅供技术学习与交流，请勿用于违法用途。所有内容均来自第三方数据源，与本项目无关。
