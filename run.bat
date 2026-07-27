@echo off
rtk gradlew installDebug && adb shell am start -n io.github.javcinema.opt/io.github.javcinema.activity.StartActivity