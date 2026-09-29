package org.foxgirls.audioranobe

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.core.CookieStore
import org.foxgirls.audioranobe.core.Prefs
import org.foxgirls.audioranobe.core.Updater
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.offline.OfflineStore
import org.foxgirls.audioranobe.player.PlayerController
import org.foxgirls.audioranobe.push.Push

class App : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        instance = this
        Api.init(CookieStore(this))
        Stores.init(Prefs(this))
        OfflineStore.init(this)
        Updater.init(this)
        PlayerController.init(this)
        Push.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                Stores.badges.foreground = true
                Stores.badges.refresh()
                OfflineStore.syncProgress()
                Updater.checkIfDue()
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
