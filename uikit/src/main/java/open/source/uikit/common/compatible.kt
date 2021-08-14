package open.source.uikit.common

import android.annotation.SuppressLint
import android.app.Application
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.renderscript.Allocation
import android.util.Log
import androidx.annotation.RequiresApi
import java.lang.reflect.InvocationTargetException

private const val TAG = "compatible"

internal const val PREFERENCES_NAME = "open.source.uikit.preferences"

internal val application by lazy {
    try {
        @SuppressLint("PrivateApi")
        val activityThreadClass = Class.forName("android.app.ActivityThread")
        val sCurrentActivityThreadField = activityThreadClass
            .getDeclaredField("sCurrentActivityThread")
            .apply {
                isAccessible = true
            }
        val sCurrentActivityThread = sCurrentActivityThreadField.get(null)
        val mInitialApplicationField = activityThreadClass
            .getDeclaredField("mInitialApplication")
            .apply {
                isAccessible = true
            }
        return@lazy mInitialApplicationField
            .get(sCurrentActivityThread) as Application
    } catch (e: Throwable) {
        e.printStackTrace()
        return@lazy null
    }
}

fun createAsync(looper: Looper): Handler {
    if (Build.VERSION.SDK_INT >= 28) {
        return Handler.createAsync(looper)
    }
    try {
        return Handler::class.java.getDeclaredConstructor(
            Looper::class.java, Handler.Callback::class.java,
            Boolean::class.javaPrimitiveType
        ).newInstance(looper, null, true)
    } catch (ignored: IllegalAccessException) {
    } catch (ignored: InstantiationException) {
    } catch (ignored: NoSuchMethodException) {
    } catch (e: InvocationTargetException) {
        val cause = e.cause
        if (cause is java.lang.RuntimeException) {
            throw cause
        }
        if (cause is Error) {
            throw cause
        }
        throw RuntimeException(cause)
    }
    Log.v(
        TAG,
        "Unable to invoke Handler(Looper, Callback, boolean) constructor"
    )
    return Handler(looper)
}

inline fun <R> Allocation.use(block: (Allocation) -> R): R {
    var exception: Throwable? = null
    try {
        return block(this)
    } catch (e: Throwable) {
        exception = e
        throw e
    } finally {
        when (exception) {
            null -> destroy()
            else -> try {
                destroy()
            } catch (closeException: Throwable) {
                exception.addSuppressed(closeException)
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.KITKAT)
fun ImageReader.acquireLatestImageCompat(): Image? {
    try {
        return this.acquireLatestImage()
    } catch (e: RuntimeException) {
        /* In API level 23 or below,  it will throw "java.lang.RuntimeException:
   ImageReaderContext is not initialized" when ImageReader is closed. To make the
   behavior consistent as newer API levels,  we make it return null Image instead.*/
        if (!isImageReaderContextNotInitializedException(e)) {
            throw e // only catch RuntimeException:ImageReaderContext is not initialized
        }
    }
    return null
}

private fun isImageReaderContextNotInitializedException(e: RuntimeException): Boolean {
    return "ImageReaderContext is not initialized" == e.message
}