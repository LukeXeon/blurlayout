package com.luke.uikit.internal

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import android.os.Handler
import android.os.Looper
import com.luke.uikit.bitmappool.LruBitmapPool
import com.luke.uikit.bitmappool.LruPoolStrategy
import java.util.*

internal object BitmapCache : Plugin() {
    private val entries = Collections.newSetFromMap(WeakHashMap<Item, Boolean>())
    private val bitmaps: LruBitmapPool
    private val handler = object : ThreadLocal<Handler>() {
        override fun initialValue(): Handler? {
            return Looper.myLooper()?.let { Handler(it) }
        }
    }

    init {
        val displayMetrics = Resources.getSystem().displayMetrics
        val size = Int.SIZE_BYTES * displayMetrics.widthPixels * displayMetrics.heightPixels
        val strategy = LruBitmapPool.getDefaultStrategy()
        bitmaps = LruBitmapPool(size, object : LruPoolStrategy by strategy {
            override fun removeLast(): Bitmap {
                val bitmap = strategy.removeLast()
                synchronized(entries) {
                    entries.remove(entries.find { it.bitmap == bitmap })
                }
                return bitmap
            }
        }, setOf(Bitmap.Config.ARGB_8888))
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
            val item = entries.find { it.bitmap == bitmap } ?: Item(bitmap)
            entries.add(item)
            item
        }
    }

    @JvmStatic
    fun put(item: Item) {
        bitmaps.put(item.bitmap)
    }

    @JvmStatic
    fun putDelay(item: Item) {
        val h = handler.get()
        if (h != null) {
            h.post { put(item) }
        } else {
            put(item)
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