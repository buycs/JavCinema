@echo off
rtk gradlew installDebug && adb shell am start -n io.github.javcinema/io.github.javcinema.activity.StartActivity