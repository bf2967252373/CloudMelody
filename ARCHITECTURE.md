# 🛠️ 深度技术说明

## 架构设计原则

### 1. 极小 APK 体积策略

| 策略 | 说明 |
|------|------|
| 无 Retrofit | 直接用 OkHttp 手写 HTTP 封装，减小 ~500KB |
| 无 Gson/Moshi | 直接用 `org.json`（已内置于 Android SDK）|
| 无 ExoPlayer（完整版）| 使用 Android `MediaPlayer`，需要更大功能时再切换 |
| Coil 代替 Glide/Picasso | Kotlin-first，更轻量 |
| ProGuard + R8 | 开启代码压缩 + 资源去除 |
| ABI Splits | 按架构拆分 APK，单个 < 5MB |

### 2. Apple 风格歌词实现详解

`AppleLyricsView` 是完全自研的自定义 `View`，核心实现：

```
远处行（微小、半透明）
中间行（稍大、较透明）
→ 当前行 ←  最大字号、纯白、加粗
中间行（稍大、较透明）
远处行（微小、半透明）
```

- 上下渐变 `LinearGradient` 道具实现“边缘透明消失”效果
- `ValueAnimator` + `DecelerateInterpolator` 实现 450ms 平滑滚动
- 下方显示翻译歌词（斜体小字）
- 点击任意行跳转播放进度
- 手动滑动可昈2

### 3. API 代理说明

本项目默认使用 [NeteaseCloudMusicApi](https://github.com/Binaryify/NeteaseCloudMusicApi) 的 Vercel 部署实例。
建议自行部署以获得更稳定的服务：

```bash
# 克隆 NeteaseCloudMusicApi
git clone https://github.com/Binaryify/NeteaseCloudMusicApi.git
cd NeteaseCloudMusicApi
npm install
node app.js
# 默认运行在 http://localhost:3000
```

然后将 `NeteaseApi.kt` 中的 `PROXY_BASE` 改为你的服务器地址。

### 4. 支持 Android 版本详情

| API 级别 | Android 版本 | 兼容说明 |
|---------|------------|--------|
| 21 | Android 5.0 | `MediaPlayer` 基础功能 |
| 23 | Android 6.0 | 运行时权限 |
| 26 | Android 8.0 | `MediaPlayer.SEEK_CLOSEST` 精准 seek |
| 28 | Android 9.0 | 网络安全配置 |
| 31 | Android 12 | `PendingIntent.FLAG_IMMUTABLE` |
| 33 | Android 13 | `FOREGROUND_SERVICE_MEDIA_PLAYBACK` |
| 34 | Android 14 | 最新目标 SDK |

### 5. 进一步优化建议

- [ ] 加入 `Room` 持久化收藏列表
- [ ] 实现 `MediaSession`，支持蕊耳机按键和车载媒体
- [ ] 加入在线/离线诊断逐步降级
- [ ] 实现登录功能（收藏/日推歌曲）
- [ ] 完整词内逐词高亮（需要 YLYRIC 格式支持）
