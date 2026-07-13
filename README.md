# 🎵 CloudMelody

> 轻量级第三方网易云音乐安卓客户端 — 极简体积，Apple Music 风格歌词体验

[![API](https://img.shields.io/badge/API-21%2B-brightgreen.svg)](https://android-arsenal.com/api?level=21)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue.svg)](https://kotlinlang.org)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## ✨ 特性

- 🪶 **极小体积** — 无广告、无推送 SDK，APK < 5MB
- 📱 **广泛兼容** — 支持 Android 5.0（API 21）~ Android 15+（API 35）
- 🎤 **Apple 风格歌词** — 逐词高亮 + 渐变金色高亮 + 毛玻璃滚动效果
- 🎶 **完整播放功能** — 播放/暂停/上一首/下一首/随机/列表循环/单曲循环
- 🔍 **搜索歌曲** — 关键词搜索网易云音乐库，防抖搜索
- 🖼️ **封面旋转动画** — 播放时专辑封面平滑旋转
- 🔔 **后台播放通知** — 前台 Service + MediaSession，锁屏控制
- 🌙 **BlueArchive 深色主题** — 深蓝 + 金色点缀

## 📸 界面预览

| 主页 | 播放器 | 歌词页 |
|------|--------|--------|
| 歌单列表 + 搜索 | 专辑封面旋转 | 逐词金色高亮 |

## 🏗️ 技术栈

| 层级 | 技术 |
|------|------|
| 语言 | Kotlin 1.9 |
| UI | ViewBinding + Material Design 3 |
| 网络 | OkHttp（无 Retrofit，减小体积）|
| 歌词解析 | 自研 LRC/YLYRIC 解析器 |
| 音频播放 | Android MediaPlayer |
| 图片加载 | Coil（轻量 Kotlin-first）|
| 架构 | MVVM，StateFlow + ViewModel |
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
│   ├── api/              # 网易云非官方 API 封装（推荐、搜索、歌词、播放URL）
│   ├── model/            # 数据模型（Song, Playlist, LyricLine, RepeatMode）
│   ├── service/          # 后台播放 Service（前台通知 + MediaSession）
│   ├── ui/
│   │   ├── home/         # 主页（推荐歌单 + 搜索）
│   │   ├── player/       # 播放器页面（封面旋转、歌词切换）
│   │   └── lyrics/       # Apple 风格歌词自定义 View
│   ├── util/             # 工具类（时间格式化、LRC 解析）
│   └── CloudMelodyApp.kt # Application（Coil 全局配置）
└── res/
    ├── layout/           # XML 布局
    ├── drawable/         # 矢量图标 & 形状
    ├── mipmap-anydpi-v26/# 自适应启动图标
    └── values/           # BlueArchive 主题色 / 字符串
```

## 🐛 已修复问题 (v1.0.15)

### API 层 (NeteaseApi.kt)
- **新增** `searchSongs()` — 搜索歌曲 API
- **新增** `getLyrics()` — 歌词获取 API
- **新增** `songDetail()` — 歌曲详情 API
- **修复** `songUrl()` — 正确跟随 302 重定向获取真实 MP3 URL
- **修复** 速率限制与 UA 轮换重试机制
- **修复** 跨 v3/v6 API 版本兼容（artist/album/时长字段）

### 播放服务 (MusicService.kt)
- **完整实现** MediaPlayer 生命周期：prepareAsync → onPrepared → onError → onCompletion
- **新增** 前台通知 + 通知渠道，支持后台播放
- **新增** MediaSession 集成，锁屏控制
- **新增** 专辑封面旋转动画
- **新增** 播放失败自动跳过 + 下一个备选
- **修复** 重复模式：NONE / ALL / SINGLE 完整支持
- **修复** skipPrev 逻辑（3秒内返回，否则重新开始当前歌曲）

### 歌词系统
- **新增** `LyricParser` — 完整 LRC 解析器（多标签、偏移标签、翻译合并）
- **升级** `AppleLyricsView` — 金色渐变高亮、上下淡出效果
- **集成** 歌词获取 → 解析 → 显示完整流程

### UI
- **修复** HomeFragment 搜索功能 — 防抖输入 + 内联歌曲列表
- **修复** fragment_home.xml — 添加搜索栏和歌曲 RecyclerView
- **修复** PlaylistAdapter / SongAdapter — 泛型参数、字段引用
- **修复** PlayerActivity — 进度条循环、封面旋转、shuffle/repeat 点击
- **新增** 自适应图标 (adaptive icon) 支持 API 26+

### 构建配置
- **修复** 签名配置 — debug/release 分离，避免签名错误
- **更新** ProGuard 规则 — 扩展至 OkHttp/Coil/JSON 保护
- **更新** 依赖 — 添加 androidx.media2 支持 MediaSession

## 📜 License

MIT © 2024 CloudMelody
