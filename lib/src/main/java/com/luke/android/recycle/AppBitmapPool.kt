package com.luke.android.recycle

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import java.util.concurrent.atomic.AtomicBoolean

object AppBitmapPool {

    private val isInit = AtomicBoolean()

    private val bitmapPool = kotlin.run {
        val metrics = Resources.getSystem().displayMetrics
        LruBitmapPool(
            Util.getBitmapByteSize(
                metrics.widthPixels,
                metrics.heightPixels,
                Bitmap.Config.ARGB_8888
            ).toLong()
        )
    }

    fun get(c: Context): BitmapPool {
        if (isInit.compareAndSet(false, true)) {
            val app = c.applicationContext as Application
            app.registerComponentCallbacks(object : ComponentCallbacks2 {
                override fun onConfigurationChanged(newConfig: Configuration) {}

                override fun onLowMemory() {
                    bitmapPool.clearMemory()
                }

                override fun onTrimMemory(level: Int) {
                    bitmapPool.trimMemory(level)
                }
            })
        }
        return bitmapPool
    }
}