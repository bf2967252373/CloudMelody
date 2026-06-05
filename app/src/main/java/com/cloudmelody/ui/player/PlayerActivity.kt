package com.cloudmelody.ui.player

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.cloudmelody.R
import com.cloudmelody.databinding.ActivityPlayerBinding
import com.cloudmelody.model.Song
import com.cloudmelody.service.MusicService
import com.cloudmelody.util.TimeUtils

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private var musicService: MusicService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService()
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
    }

    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
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

        binding.ivCover.setOnClickListener {
            val lyricsVisible = binding.lyricsView.visibility == View.VISIBLE
            if (lyricsVisible) {
                binding.lyricsView.animate().alpha(0f).setDuration(250)
                    .withEndAction {
                        binding.lyricsView.visibility = View.GONE
                        binding.ivCover.visibility    = View.VISIBLE
                    }.start()
            } else {
                binding.ivCover.visibility    = View.INVISIBLE
                binding.lyricsView.visibility = View.VISIBLE
                binding.lyricsView.alpha = 0f
                binding.lyricsView.animate().alpha(1f).setDuration(250).start()
            }
        }
    }

    private fun updateUiFromService() {
        val song: Song = musicService?.currentSong ?: return
        binding.tvTitle.text    = song.name
        binding.tvArtist.text   = song.artist
        binding.tvDuration.text = TimeUtils.formatMs(song.duration)
        updatePlayPauseIcon()
    }

    private fun updatePlayPauseIcon() {
        val isPlaying = musicService?.isPlaying ?: false
        binding.btnPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    override fun onDestroy() {
        if (bound) unbindService(connection)
        super.onDestroy()
    }
}
