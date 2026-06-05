package com.cloudmelody.ui.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cloudmelody.R
import com.cloudmelody.databinding.ActivityPlayerBinding
import com.cloudmelody.service.MusicService
import com.cloudmelody.util.TimeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bug 修复：
 * 1. 原代码 updateUiFromService 在 service 未绑定时直接 return 不更新任何内容
 * 2. 进度条无任何更新循环，永远显示 0
 * 3. 缺少 repeatOnLifecycle 保护，Activity pause 后仍消耗资源
 */
class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var musicService: MusicService? = null
    private var bound = false
    private var userSeeking = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            musicService = (service as MusicService.MusicBinder).getService()
            bound = true
            // Bug 修复：绑定完成后立刻刷新 UI（原代码此处为空）
            updateUiFromService()
        }
        override fun onServiceDisconnected(name: ComponentName) {
            musicService = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupControls()
        bindService(Intent(this, MusicService::class.java), connection, Context.BIND_AUTO_CREATE)
        // Bug 修复：启动进度条轮询协程
        startProgressLoop()
    }

    private fun setupControls() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnPlayPause.setOnClickListener {
            musicService?.togglePlayPause()
            updatePlayPauseIcon()
        }
        binding.btnNext.setOnClickListener {
            musicService?.skipNext()
            updateUiFromService()
        }
        binding.btnPrev.setOnClickListener {
            musicService?.skipPrev()
            updateUiFromService()
        }
        binding.seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {}
            override fun onStartTrackingTouch(sb: SeekBar?) { userSeeking = true }
            override fun onStopTrackingTouch(sb: SeekBar?) {
                sb?.progress?.let { musicService?.seekTo(it) }
                userSeeking = false
            }
        })

        // 点击封面切换歌词视图
        binding.ivCover.setOnClickListener {
            val lyricsVisible = binding.lyricsView.visibility == View.VISIBLE
            if (lyricsVisible) {
                binding.lyricsView.animate().alpha(0f).setDuration(250).withEndAction {
                    binding.lyricsView.visibility = View.GONE
                    binding.ivCover.visibility = View.VISIBLE
                }.start()
            } else {
                binding.ivCover.visibility = View.INVISIBLE
                binding.lyricsView.visibility = View.VISIBLE
                binding.lyricsView.alpha = 0f
                binding.lyricsView.animate().alpha(1f).setDuration(250).start()
            }
        }
    }

    private fun updateUiFromService() {
        val song = musicService?.currentSong ?: return
        binding.tvTitle.text  = song.name
        binding.tvArtist.text = song.artist
        binding.tvDuration.text = TimeUtils.formatMs(song.duration)
        binding.seekBar?.max = song.duration.toInt().coerceAtLeast(0)
        updatePlayPauseIcon()
    }

    private fun updatePlayPauseIcon() {
        val playing = musicService?.isPlaying ?: false
        binding.btnPlayPause.setImageResource(
            if (playing) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    /**
     * Bug 修复：原代码完全没有进度更新循环，seekBar 永远停在 0。
     * 使用 repeatOnLifecycle 确保 Activity 不可见时自动停止，避免资源浪费。
     */
    private fun startProgressLoop() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    if (!userSeeking) {
                        musicService?.let { svc ->
                            binding.seekBar?.progress = svc.currentPosition
                            binding.tvPosition?.text = TimeUtils.formatMs(svc.currentPosition.toLong())
                        }
                    }
                    updatePlayPauseIcon()
                    delay(500L)
                }
            }
        }
    }

    override fun onDestroy() {
        if (bound) {
            runCatching { unbindService(connection) }
            bound = false
        }
        super.onDestroy()
    }
}
