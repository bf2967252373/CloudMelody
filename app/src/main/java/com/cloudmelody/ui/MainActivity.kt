package com.cloudmelody.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.cloudmelody.R
import com.cloudmelody.databinding.ActivityMainBinding
import com.cloudmelody.service.MusicService
import com.cloudmelody.ui.home.HomeFragment

/**
 * Bug 修复：
 * 1. 补全 MusicService 绑定逻辑（原代码缺失）
 * 2. onDestroy 时安全解绑，防止 ServiceConnection 泄漏
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    var musicService: MusicService? = null
        private set
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            musicService = (service as MusicService.MusicBinder).getService()
            bound = true
        }
        override fun onServiceDisconnected(name: ComponentName) {
            musicService = null
            bound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 启动并绑定 MusicService
        val intent = Intent(this, MusicService::class.java)
        startService(intent)
        bindService(intent, connection, Context.BIND_AUTO_CREATE)

        if (savedInstanceState == null) showFragment(HomeFragment())
    }

    override fun onDestroy() {
        if (bound) {
            runCatching { unbindService(connection) }
            bound = false
        }
        super.onDestroy()
    }

    fun showFragment(fragment: Fragment, addToBack: Boolean = false) {
        val tx = supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
        if (addToBack) tx.addToBackStack(null)
        tx.commitAllowingStateLoss()
    }
}
