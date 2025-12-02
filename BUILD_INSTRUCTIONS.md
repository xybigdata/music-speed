# 构建 APK 的替代方案

## 方案1：使用 GitHub Actions（推荐）

1. 将项目上传到 GitHub
2. 在项目根目录创建 `.github/workflows/build.yml`
3. GitHub 会自动构建 APK
4. 从 Actions 页面下载构建好的 APK

## 方案2：使用 Appetize.io

1. 访问 https://appetize.io
2. 上传项目文件
3. 在线测试应用

## 方案3：使用 Termux（手机上直接构建）

在手机上安装 Termux，然后：
```bash
pkg install openjdk-17 gradle
cd /sdcard/music-speed
gradle assembleDebug
```

## 方案4：简化版 HTML5 应用

如果只是测试功能，可以用浏览器版本（见下方）
