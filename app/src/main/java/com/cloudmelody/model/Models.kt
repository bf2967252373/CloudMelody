package com.cloudmelody.model

/**
 * Core data models – kept minimal to reduce serialization overhead.
 * NOTE: Playlist is defined separately in Playlist.kt
 */

data class Song(
    val id: Long,
    val name: String,
    val artist: String,
    val album: String,
    val coverUrl: String?,
    val duration: Long          // milliseconds
)

data class LyricResult(
    val original: String,       // raw LRC text
    val translated: String      // translation LRC (may be empty)
)

/** Parsed single line from LRC. */
data class LyricLine(
    val timeMs: Long,           // start time in ms
    val text: String,           // display text
    val translation: String = ""// optional translated line
)

/** Playback repeat mode. */
enum class RepeatMode { NONE, ALL, SINGLE }
