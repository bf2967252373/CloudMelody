package com.cloudmelody

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Bug 修复：
 * 1. 补全 ImageLoaderFactory 实现，提供磁盘缓存 + 内存缓存配置
 * 2. 原代码 onCreate 为空实现，无任何初始化
 */
class CloudMelodyApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    /** Coil 全局 ImageLoader：带内存缓存（20%）+ 磁盘缓存（50MB）*/
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .okHttpClient {
                OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)
                    .build()
            }
            .crossfade(true)
            .build()

    companion object {
        lateinit var instance: CloudMelodyApp
            private set
    }
}
