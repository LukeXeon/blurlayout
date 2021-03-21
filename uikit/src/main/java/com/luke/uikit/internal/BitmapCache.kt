package com.luke.uikit.internal

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Bitmap.Config.*
import android.os.Handler
import android.os.Looper
import com.luke.uikit.bitmappool.LruBitmapPool
import com.luke.uikit.bitmappool.LruBitmapPool.getDefaultStrategy
import com.luke.uikit.bitmappool.LruPoolStrategy
import java.util.*
import java.util.Collections.newSetFromMap

internal object BitmapCache : Plugin() {
    private val entries = newSetFromMap(WeakHashMap<DrawingBitmap, Boolean>())
    private val bitmaps: LruBitmapPool
    private val handler = object : ThreadLocal<Handler>() {
        override fun initialValue(): Handler? {
            return Looper.myLooper()?.let { Handler(it) }
        }
    }

    init {
        val displayMetrics = Resources.getSystem().displayMetrics
        val size = Int.SIZE_BYTES * displayMetrics.widthPixels * displayMetrics.heightPixels
        bitmaps = LruBitmapPool(size, object : ProxyStrategy(getDefaultStrategy()) {
            override fun removeLast(): Bitmap {
                val bitmap = super.removeLast()
                synchronized(entries) {
                    entries.remove(entries.find { it.bitmap == bitmap })
                }
                return bitmap
            }
        }, setOf(ARGB_8888))
    }

    override fun onLowMemory() {
        bitmaps.clearMemory()
    }

    override fun onTrimMemory(level: Int) {
        bitmaps.trimMemory(level)
    }

    @JvmStatic
    operator fun get(width: Int, height: Int): DrawingBitmap {
        val bitmap = bitmaps[width, height, ARGB_8888]
        return synchronized(entries) {
            (entries.find { it.bitmap == bitmap } ?: DrawingBitmap(bitmap)).also {
                entries.add(it)
            }
        }
    }

    @JvmStatic
    fun put(item: DrawingBitmap) {
        bitmaps.put(item.bitmap)
    }

    @JvmStatic
    fun putDelay(item: DrawingBitmap) {
        val h = handler.get()
        if (h != null) {
            h.post { put(item) }
        } else {
            put(item)
        }
    }

    private open class ProxyStrategy(strategy: LruPoolStrategy) : LruPoolStrategy by strategy

}