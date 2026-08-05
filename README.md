# JavCinema

基于上游开源项目 **JAViewer** 的二次开发的 JAV 影片浏览应用。
https://github.com/SplashCodes/JAViewer

## 特性

- 多数据源切换，数据源地址可动态配置
- 磁力搜索多源并发，可自定义磁力源地址
- 在线播放（Media3 ExoPlayer）、收藏、女优浏览、类型分类
- 100% Kotlin + Jetpack Compose + Material 3，单 Activity + Compose Navigation
- Coil 图片加载、封面预取、共享磁盘缓存、HTTP 重试拦截器
- 图库查看、保存到相册
- ABI 拆分包（arm64-v8a / armeabi-v7a / x86_64），命名 APK 产物

## 版本

当前版本：**0.0.1-alpha (1)**

## 构建

要求：JDK 17、Android SDK（compileSdk 35）

```bash
# 调试构建
./gradlew assembleDebug

# 安装到已连接设备
./gradlew installDebug

# Release 构建（R8 混淆 + debug 签名）
./gradlew assembleRelease

# 单元测试
./gradlew test
```

> 注意：本机有全局 gradle init 脚本注入阿里云 Maven 镜像，可能与项目冲突，请使用 `./gradlew.bat`。

## 致谢

本项目基于 [SplashCodes/JAViewer](https://github.com/SplashCodes/JAViewer) 二次开发，遵循上游项目的开源方式。上游项目未提供 LICENSE 文件，使用前请自行评估。

## 免责声明

本项目仅供技术学习与交流，请勿用于任何违法用途。所有内容均来自第三方数据源，与本项目无关。
