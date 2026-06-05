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

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var musicService: MusicService? = null
    private var bound = false
    private var userSeeking = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            musicService = (service as MusicService.MusicBinder).getService()
            bound = true
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
        bindMusicService()
        startProgressLoop()
    }

    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
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

        binding.ivCover.setOnClickListener {
            val visible = binding.lyricsView.visibility == View.VISIBLE
            if (visible) {
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
        binding.tvTitle.text = song.name
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

    private fun startProgressLoop() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    if (!userSeeking) {
                        musicService?.let {
                            binding.seekBar?.progress = it.currentPosition
                        }
                    }
                    updatePlayPauseIcon()
                    delay(500)
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
