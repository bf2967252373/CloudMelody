package com.cloudmelody.service

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import com.cloudmelody.model.Song

/**
 * Background music playback service.
 * Bound by MainActivity; controls are exposed via MusicBinder.
 */
class MusicService : Service() {

    private val binder = MusicBinder()

    // ─── Playback state ───────────────────────────────────────────────────────
    private var playlist: List<Song> = emptyList()
    private var currentIndex: Int = -1
    var isPlaying: Boolean = false
        private set

    val currentSong: Song? get() = playlist.getOrNull(currentIndex)

    // ─── Binder ───────────────────────────────────────────────────────────────
    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    // ─── Public controls ──────────────────────────────────────────────────────

    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        playlist = songs
        currentIndex = startIndex.coerceIn(0, songs.lastIndex.coerceAtLeast(0))
    }

    fun play() {
        isPlaying = true
        // TODO: integrate MediaPlayer / ExoPlayer
    }

    fun pause() {
        isPlaying = false
        // TODO: pause MediaPlayer / ExoPlayer
    }

    fun togglePlayPause() {
        if (isPlaying) pause() else play()
    }

    fun skipNext() {
        if (playlist.isEmpty()) return
        currentIndex = (currentIndex + 1) % playlist.size
        play()
    }

    fun skipPrev() {
        if (playlist.isEmpty()) return
        currentIndex = if (currentIndex <= 0) playlist.lastIndex else currentIndex - 1
        play()
    }

    override fun onDestroy() {
        isPlaying = false
        super.onDestroy()
    }
}
