package com.cloudmelody.service

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import android.util.Log
import com.cloudmelody.api.NeteaseApi
import com.cloudmelody.model.RepeatMode
import com.cloudmelody.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Bug 修复：
 * 原代码 play/pause 全部是空 TODO，没有任何实际 MediaPlayer 实现。
 * 完整实现：
 * - prepareAsync 异步准备播放
 * - onCompletion 自动切歌（支持 RepeatMode）
 * - onError 错误回调
 * - currentPosition / duration 属性供 PlayerActivity 轮询
 * - skipNext / skipPrev
 * - seekTo
 * - 生命周期结束时正确 release MediaPlayer 和取消协程
 */
class MusicService : Service() {

    companion object {
        private const val TAG = "MusicService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var player: MediaPlayer? = null
    private var prepareJob: Job? = null

    private var playlist: List<Song> = emptyList()
    private var currentIndex: Int = -1

    var isPlaying: Boolean = false
        private set

    var repeatMode: RepeatMode = RepeatMode.ALL

    val currentSong: Song? get() = playlist.getOrNull(currentIndex)
    val currentPosition: Int get() = runCatching { player?.currentPosition ?: 0 }.getOrDefault(0)
    val duration: Int get() = runCatching { player?.duration ?: 0 }.getOrDefault(0)

    // ─── Binder ─────────────────────────────────────────────────────────────────

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    private val binder = MusicBinder()

    override fun onBind(intent: Intent): IBinder = binder
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    // ─── 公开控制接口 ────────────────────────────────────────────────────────────

    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        playlist = songs
        if (songs.isEmpty()) { currentIndex = -1; return }
        currentIndex = startIndex.coerceIn(0, songs.lastIndex)
        prepareAndPlayCurrent()
    }

    fun togglePlayPause() {
        val mp = player ?: return
        if (mp.isPlaying) {
            mp.pause(); isPlaying = false
        } else {
            mp.start(); isPlaying = true
        }
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        isPlaying = false
    }

    fun play() {
        player?.let {
            if (!it.isPlaying) { it.start(); isPlaying = true }
        } ?: prepareAndPlayCurrent()
    }

    fun seekTo(ms: Int) {
        player?.seekTo(ms)
    }

    fun skipNext() {
        if (playlist.isEmpty()) return
        currentIndex = (currentIndex + 1) % playlist.size
        prepareAndPlayCurrent()
    }

    fun skipPrev() {
        if (playlist.isEmpty()) return
        currentIndex = if (currentIndex <= 0) playlist.lastIndex else currentIndex - 1
        prepareAndPlayCurrent()
    }

    // ─── 内部实现 ────────────────────────────────────────────────────────────────

    private fun prepareAndPlayCurrent() {
        val song = currentSong ?: return
        prepareJob?.cancel()
        releasePlayer()
        prepareJob = scope.launch {
            // 优先使用 Song.url（直链），否则通过 API 解析
            val url = song.url ?: NeteaseApi.songUrl(song.id).getOrNull()
            if (url.isNullOrBlank()) {
                Log.w(TAG, "Cannot resolve url for song id=${song.id}")
                return@launch
            }
            try {
                player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setOnCompletionListener { onTrackComplete() }
                    setOnErrorListener { _, what, extra ->
                        Log.e(TAG, "MediaPlayer error what=$what extra=$extra")
                        releasePlayer()
                        true
                    }
                    setOnPreparedListener { mp ->
                        mp.start()
                        isPlaying = true
                    }
                    setDataSource(url)
                    prepareAsync()   // 非阻塞，准备完成后回调 onPrepared
                }
            } catch (e: Exception) {
                Log.e(TAG, "prepareAsync failed for song id=${song.id}", e)
                releasePlayer()
            }
        }
    }

    private fun onTrackComplete() {
        when (repeatMode) {
            RepeatMode.SINGLE -> { player?.seekTo(0); player?.start() }
            RepeatMode.ALL    -> skipNext()
            RepeatMode.NONE   -> {
                if (currentIndex < playlist.lastIndex) skipNext()
                else isPlaying = false
            }
        }
    }

    private fun releasePlayer() {
        player?.runCatching { reset(); release() }
        player = null
        isPlaying = false
    }

    override fun onDestroy() {
        prepareJob?.cancel()
        releasePlayer()
        scope.cancel()
        super.onDestroy()
    }
}
