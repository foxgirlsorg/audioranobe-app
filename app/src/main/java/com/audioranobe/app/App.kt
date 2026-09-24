package com.audioranobe.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.audioranobe.app.core.Api
import com.audioranobe.app.core.CookieStore
import com.audioranobe.app.core.Prefs
import com.audioranobe.app.data.Stores
import com.audioranobe.app.player.PlayerController

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Api.init(CookieStore(this))
        Stores.init(Prefs(this))
        PlayerController.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Stores.badges.foreground = true
                Stores.badges.refresh()
            }

            override fun onStop(owner: LifecycleOwner) {
                Stores.badges.foreground = false
            }
        })
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { Api.client }
        .components { add(SvgDecoder.Factory()) }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(200L * 1024 * 1024).build() }
        .crossfade(180)
        .build()

    companion object {
        lateinit var instance: App
            private set
    }
}
