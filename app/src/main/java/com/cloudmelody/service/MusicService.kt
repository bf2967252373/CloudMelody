package com.cloudmelody.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cloudmelody.R
import com.cloudmelody.api.NeteaseApi
import com.cloudmelody.model.RepeatMode
import com.cloudmelody.model.Song
import com.cloudmelody.ui.player.PlayerActivity
import kotlinx.coroutines.*

/**
 * 后台音乐播放 Service
 *
 * Fixed:
 * - Full MediaPlayer lifecycle: prepareAsync, onPrepared, onCompletion, onError
 * - Foreground service notification for background playback
 * - MediaSession integration for lock screen controls
 * - Proper error handling and fallback for URL resolution
 * - Repeat mode: NONE, ALL, SINGLE
 */
class MusicService : Service() {

    companion object {
        private const val TAG = "MusicService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "cloudmelody_playback"
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

    // Called when song changes (for PlayerActivity UI updates)
    var onSongChanged: ((Song) -> Unit)? = null
    var onPlayStateChanged: ((Boolean) -> Unit)? = null

    private lateinit var mediaSession: MediaSessionCompat

    // ─── Binder ─────────────────────────────────────────────────

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    private val binder = MusicBinder()

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        setupMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    // ─── 公开控制接口 ───────────────────────────────────────────

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
        onPlayStateChanged?.invoke(isPlaying)
        updateMediaSessionState()
    }

    fun pause() {
        player?.takeIf { it.isPlaying }?.pause()
        isPlaying = false
        onPlayStateChanged?.invoke(false)
        updateMediaSessionState()
    }

    fun play() {
        player?.let {
            if (!it.isPlaying) { it.start(); isPlaying = true }
        } ?: prepareAndPlayCurrent()
        onPlayStateChanged?.invoke(isPlaying)
        updateMediaSessionState()
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
        // If more than 3 seconds in, restart current song
        if (currentPosition > 3000) {
            player?.seekTo(0)
            return
        }
        currentIndex = if (currentIndex <= 0) playlist.lastIndex else currentIndex - 1
        prepareAndPlayCurrent()
    }

    fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.SINGLE
            RepeatMode.SINGLE -> RepeatMode.NONE
        }
    }

    // ─── 内部实现 ───────────────────────────────────────────────

    private fun prepareAndPlayCurrent() {
        val song = currentSong ?: return
        prepareJob?.cancel()
        releasePlayer()
        onSongChanged?.invoke(song)

        prepareJob = scope.launch {
            // Use direct URL if available, otherwise resolve via API
            val url = song.url ?: NeteaseApi.songUrl(song.id).getOrNull()
            if (url.isNullOrBlank()) {
                Log.w(TAG, "Cannot resolve url for song id=${song.id}, trying next track")
                // Auto skip to next on failure
                skipNext()
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
                        // Auto skip on error
                        scope.launch { skipNext() }
                        true
                    }
                    setOnPreparedListener { mp ->
                        mp.start()
                        isPlaying = true
                        onPlayStateChanged?.invoke(true)
                        startForeground(NOTIFICATION_ID, buildNotification())
                        updateMediaSessionState()
                    }
                    setDataSource(url)
                    prepareAsync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "prepareAsync failed for song id=${song.id}", e)
                releasePlayer()
                scope.launch { skipNext() }
            }
        }
    }

    private fun onTrackComplete() {
        when (repeatMode) {
            RepeatMode.SINGLE -> {
                player?.seekTo(0)
                player?.start()
            }
            RepeatMode.ALL -> skipNext()
            RepeatMode.NONE -> {
                if (currentIndex < playlist.lastIndex) skipNext()
                else {
                    isPlaying = false
                    onPlayStateChanged?.invoke(false)
                    updateMediaSessionState()
                }
            }
        }
    }

    private fun releasePlayer() {
        player?.runCatching { reset(); release() }
        player = null
        isPlaying = false
    }

    // ─── Notification ───────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "CloudMelody playback controls"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val song = currentSong ?: return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(getString(R.string.app_name))
            .build()

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, PlayerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song.name)
            .setContentText("${song.artist} · ${song.album}")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(contentIntent)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isPlaying) "Pause" else "Play",
                null // playback controls handled by MediaSession
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0)
            )
            .build()
    }

    // ─── MediaSession ───────────────────────────────────────────

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "CloudMelody").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { play() }
                override fun onPause() { pause() }
                override fun onSkipToNext() { skipNext() }
                override fun onSkipToPrevious() { skipPrev() }
            })
            isActive = true
        }
    }

    private fun updateMediaSessionState() {
        val state = if (isPlaying) PlaybackStateCompat.STATE_PLAYING
        else PlaybackStateCompat.STATE_PAUSED

        mediaSession.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, currentPosition.toLong(), 1.0f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build()
        )

        currentSong?.let { song ->
            mediaSession.setMetadata(
                android.support.v4.media.MediaMetadataCompat.Builder()
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, song.name)
                    .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                    .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
                    .build()
            )
        }
    }

    override fun onDestroy() {
        prepareJob?.cancel()
        releasePlayer()
        mediaSession.isActive = false
        mediaSession.release()
        scope.cancel()
        super.onDestroy()
    }
}
