package com.luke.uikit.shared

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import com.luke.uikit.bitmappool.LruBitmapPool
import com.luke.uikit.bitmappool.LruPoolStrategy
import java.util.concurrent.atomic.AtomicBoolean

internal object SharedBitmapPool : ComponentCallbacks2 {
    private val entries = HashMap<Bitmap, Entry>()
    private val bitmaps: LruBitmapPool
    private val isInit = AtomicBoolean()

    init {
        val strategy = LruBitmapPool.getDefaultStrategy()
        val displayMetrics = Resources.getSystem().displayMetrics
        val size = Int.SIZE_BYTES * displayMetrics.widthPixels * displayMetrics.heightPixels
        bitmaps = LruBitmapPool(
            size,
            object : LruPoolStrategy by strategy {
                override fun removeLast(): Bitmap {
                    val bitmap = strategy.removeLast()
                    synchronized(entries) {
                        entries.remove(bitmap)
                    }
                    return bitmap
                }
            },
            setOf(Bitmap.Config.ARGB_8888)
        )
    }

    fun init(context: Context) {
        if (isInit.compareAndSet(false, true)) {
            val application = context.applicationContext as Application
            application.registerComponentCallbacks(this)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {

    }

    override fun onLowMemory() {
        bitmaps.clearMemory()
    }

    override fun onTrimMemory(level: Int) {
        bitmaps.trimMemory(level)
    }

    operator fun get(width: Int, height: Int): Entry {
        val bitmap = bitmaps.getDirty(width, height, Bitmap.Config.ARGB_8888)
        return synchronized(entries) {
            entries.getOrPut(bitmap) { Entry(bitmap) }
        }
    }

    fun put(entry: Entry) {
        bitmaps.put(entry.bitmap)
    }

    class Entry(val bitmap: Bitmap) {
        val shader by lazy {
            BitmapShader(
                bitmap,
                Shader.TileMode.MIRROR,
                Shader.TileMode.MIRROR
            )
        }
    }
}