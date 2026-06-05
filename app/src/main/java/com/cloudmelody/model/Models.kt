package com.cloudmelody.model

/**
 * 核心数据模型 —— 所有模型集中于此，避免重复定义。
 * 原仓库中 Playlist.kt 与 Models.kt 同时定义了 Playlist（字段还不一致），
 * 已删除 Playlist.kt，只保留此处定义。
 */

data class Song(
    val id: Long,
    val name: String,
    val artist: String,
    val album: String = "",
    val coverUrl: String? = null,
    val duration: Long = 0L,   // 毫秒
    val url: String? = null    // 播放地址，懒加载
)

data class Playlist(
    val id: Long,
    val name: String,
    val coverUrl: String? = null,
    val trackCount: Int = 0,
    val playCount: Long = 0L,
    val description: String? = null
)

data class LyricResult(
    val original: String,
    val translated: String = ""
)

/** LRC 单行 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val translation: String = ""
)

/** 播放循环模式 */
enum class RepeatMode { NONE, ALL, SINGLE }
