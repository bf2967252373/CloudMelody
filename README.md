# 🎵 CloudMelody

> 轻量级第三方网易云音乐安卓客户端

[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## ✨ 特性

- 🪶 **极小体积** — 无广告、无推送 SDK，APK < 5MB
- 📱 **广泛兼容** — 支持 Android 5.0（API 21）~ Android 14（API 34）
- 🎤 **Apple 风格歌词** — 逐词高亮 + 毛玻璃背景，媲美 iOS Apple Music
- 🎶 **完整播放功能** — 播放/暂停/上一首/下一首/随机/循环
- 🔍 **搜索歌曲** — 关键词搜索网易云音乐库
- 🌙 **深色模式** — 跟随系统自动切换
- 📶 **无需登录** — 免登录收听热门歌曲

## 📸 界面预览

| 主页 | 播放器 | 歌词页 |
|------|--------|--------|
| 歌单列表 | 专辑封面旋转 | 逐词点亮歌词 |

## 🏗️ 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Kotlin 1.9 |
| UI | ViewBinding + Material Design 3 |
| 网络 | OkHttp（无 Retrofit，减小体积）|
| 歌词解析 | 自研 LRC/YLYRIC 解析器 |
| 音频播放 | Android MediaPlayer / ExoPlayer-core |
| 图片加载 | Coil（轻量 Kotlin-first）|
| 最低版本 | Android 5.0（API 21）|

## 🚀 快速开始

```bash
git clone https://github.com/bf2967252373/CloudMelody.git
cd CloudMelody
# 用 Android Studio 打开，等待 Gradle 同步后直接运行
```

> ⚠️ 本项目仅供学习交流，请支持正版音乐。

## 📂 项目结构

```
app/src/main/
├── java/com/cloudmelody/
│   ├── api/          # 网易云非官方 API 封装
│   ├── model/        # 数据模型
│   ├── ui/
│   │   ├── home/     # 主页（推荐 + 搜索）
│   │   ├── player/   # 播放器页面
│   │   └── lyrics/   # Apple 风格歌词视图
│   ├── service/      # 后台播放 Service
│   └── util/         # 工具类
└── res/
    ├── layout/       # XML 布局
    └── values/       # 主题/颜色/字符串
```

## 📜 License

MIT © 2024 CloudMelody
