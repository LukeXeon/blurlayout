package com.luke.uikit.internal

import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import com.luke.uikit.bitmappool.LruBitmapPool
import com.luke.uikit.bitmappool.LruPoolStrategy
import java.util.*

internal object BitmapCache : ComponentCallbacks2 {
    private val entries = HashMap<Bitmap, Item>()
    private val bitmaps: LruBitmapPool
    private val mainThread = Handler(Looper.getMainLooper())

    init {
        val displayMetrics = Resources.getSystem().displayMetrics
        val size = Int.SIZE_BYTES * displayMetrics.widthPixels * displayMetrics.heightPixels
        val strategy = LruBitmapPool.getDefaultStrategy()
        bitmaps = LruBitmapPool(size, object : LruPoolStrategy by strategy {
            override fun removeLast(): Bitmap {
                val bitmap = strategy.removeLast()
                synchronized(entries) {
                    entries.remove(bitmap)
                }
                return bitmap
            }
        }, setOf(Bitmap.Config.ARGB_8888))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {

    }

    override fun onLowMemory() {
        bitmaps.clearMemory()
    }

    override fun onTrimMemory(level: Int) {
        bitmaps.trimMemory(level)
    }

    @JvmStatic
    operator fun get(width: Int, height: Int): Item {
        val bitmap = bitmaps[width, height, Bitmap.Config.ARGB_8888]
        return synchronized(entries) {
            entries.getOrPut(bitmap) { Item(bitmap) }
        }
    }

    @JvmStatic
    fun put(item: Item) {
        if (Looper.myLooper() == mainThread.looper) {
            mainThread.post { bitmaps.put(item.bitmap) }
        } else {
            bitmaps.put(item.bitmap)
        }
    }

    class Item(val bitmap: Bitmap) {
        val shader by lazy {
            BitmapShader(
                bitmap,
                Shader.TileMode.MIRROR,
                Shader.TileMode.MIRROR
            )
        }
    }
}