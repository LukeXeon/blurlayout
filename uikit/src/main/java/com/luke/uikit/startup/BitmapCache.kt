package com.luke.uikit.startup

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader
import androidx.annotation.Keep
import androidx.startup.Initializer
import com.bumptech.glide.Glide
import com.luke.uikit.bitmap.pool.BitmapPool
import com.luke.uikit.bitmap.pool.LruBitmapPool
import java.lang.ref.WeakReference
import java.util.*

@Keep
internal class BitmapCache : Initializer<Unit> {

    companion object {
        private val shaderCache = WeakHashMap<Bitmap, WeakReference<BitmapShader>>()

        fun getShader(bitmap: Bitmap): BitmapShader {
            var shader = shaderCache[bitmap]?.get()
            if (shader == null) {
                shader = BitmapShader(
                    bitmap,
                    Shader.TileMode.MIRROR,
                    Shader.TileMode.MIRROR
                )
                val ref = WeakReference(shader)
                shaderCache[bitmap] = ref
            }
            return shader
        }

        lateinit var pool: BitmapPool
            private set
    }

    override fun create(context: Context) {
        try {
            val bitmapPool = Glide.get(context).bitmapPool
            pool = object : BitmapPool {
                override fun getMaxSize(): Int {
                    return bitmapPool.maxSize.toInt()
                }

                override fun setSizeMultiplier(sizeMultiplier: Float) {
                    bitmapPool.setSizeMultiplier(sizeMultiplier)
                }

                override fun put(bitmap: Bitmap?) {
                    bitmapPool.put(bitmap)
                }

                override fun get(width: Int, height: Int, config: Bitmap.Config?): Bitmap {
                    return bitmapPool.get(width, height, config)
                }

                override fun getDirty(width: Int, height: Int, config: Bitmap.Config?): Bitmap {
                    return bitmapPool.getDirty(width, height, config)
                }

                override fun clearMemory() {
                    bitmapPool.clearMemory()
                }

                override fun trimMemory(level: Int) {
                    bitmapPool.trimMemory(level)
                }

            }
        } catch (e: Throwable) {
            val displayMetrics = context.resources.displayMetrics
            val size = Int.SIZE_BYTES * displayMetrics.widthPixels * displayMetrics.heightPixels
            val bitmapPool = LruBitmapPool(size)
            context.registerComponentCallbacks(object : ComponentCallbacks2 {
                override fun onConfigurationChanged(newConfig: Configuration) {

                }

                override fun onLowMemory() {
                    bitmapPool.clearMemory()
                }

                override fun onTrimMemory(level: Int) {
                    bitmapPool.trimMemory(level)
                }

            })
            pool = bitmapPool
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}