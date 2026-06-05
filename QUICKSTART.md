# 快速开始指南

## 前置条件

- Android Studio Hedgehog (2023.1.1) 或更新
- JDK 17+
- Android SDK 21–34

## 步骤 1: 克隆

```bash
git clone https://github.com/bf2967252373/CloudMelody.git
cd CloudMelody
```

## 步骤 2: 配置 API 代理

打开 `app/src/main/java/com/cloudmelody/api/NeteaseApi.kt`，
将 `PROXY_BASE` 改为你的 NeteaseCloudMusicApi 实例地址：

```kotlin
private const val PROXY_BASE = "http://你的服务器 IP:3000"
```

## 步骤 3: 用 Android Studio 打开

1. 打开 Android Studio
2. `File → Open` 选择 `CloudMelody` 文件夹
3. 等待 Gradle Sync 完成
4. 连接设备或启动模拟器
5. 点击运行 ▶️

## 常见问题

### Gradle Sync 失败

确保网络可以访问 `dl.google.com` 和 `repo1.maven.org`。

### 播放时没有声音

检查 `PROXY_BASE` 是否可以访问，确认 API 返回了有效的 song URL。

### 歌词不显示

试试在播放器页面点击封面图切换至歌词视图。
歌词不可用时显示“暂无歌词”占位符。
