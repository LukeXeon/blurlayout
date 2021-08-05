package com.luke.uikit.blurlayout

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.*
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.FloatRange
import androidx.annotation.Px
import com.luke.uikit.bitmap.pool.LruBitmapPool
import java.lang.ref.SoftReference
import java.util.*
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

class BlurViewDelegate private constructor() : ViewTreeObserver.OnPreDrawListener,
    ImageReader.OnImageAvailableListener,
    View.OnAttachStateChangeListener,
    View.OnLayoutChangeListener {
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    @Volatile
    private var recorderLayout: RecorderLayout? = null
    private var renderScript: RenderScript? = null
    private var blur: ScriptIntrinsicBlur? = null
    private var imageReader: ImageReader? = null
    private val processLock = Any()
    private val tempPaint = Paint()
    private val tempDrawingRectF = RectF()
    private val tempDrawingRect = Rect()
    private val tempVisibleRect = Rect()
    private val tempLayoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    private val updateBounds = UpdateBoundsHandler()
    private val tempOptions = BitmapFactory.Options()
    private val currentView: ViewGroup?
        get() = recorderLayout?.parent as? ViewGroup

    @Px
    @Volatile
    var cornerRadius: Int = 0
        set(value) {
            field = max(0, value)
        }

    @Volatile
    var blurSampling: Float = 4f
        set(value) {
            field = max(1f, value)
        }

    @Volatile
    var blurRadius: Float = 10f
        set(@FloatRange(from = 0.0, to = 25.0) value) {
            field = max(25f, min(0f, value))
        }

    init {
        tempOptions.inMutable = true
    }

    private inner class UpdateBoundsHandler : MessageQueue.IdleHandler {
        override fun queueIdle(): Boolean {
            val view = currentView
            val handler = handler
            if (view != null && handler != null) {
                val width = view.width
                val height = view.height
                val recorder = imageReader
                val blurSampling = blurSampling
                if (width * height > 0) {
                    if (recorder == null || recorder.width != width && recorder.height != height) {
                        val scaledWidth = (width / blurSampling).toInt()
                        val scaledHeight = (height / blurSampling).toInt()
                        imageReader?.close()
                        val r = ImageReader.newInstance(
                            scaledWidth,
                            scaledHeight,
                            PixelFormat.RGBA_8888,
                            60
                        )
                        r.setOnImageAvailableListener(this@BlurViewDelegate, handler)
                        imageReader = r
                    }
                } else {
                    imageReader?.close()
                    imageReader = null
                }
            }
            return false
        }
    }

    private object MemoryMonitor : ComponentCallbacks2 {

        val isAttached = AtomicBoolean()

        override fun onConfigurationChanged(newConfig: Configuration) {

        }

        override fun onLowMemory() {
            synchronized(shaders) {
                shaders.clear()
            }
            bitmapPool.clearMemory()
        }

        override fun onTrimMemory(level: Int) {
            bitmapPool.trimMemory(level)
        }
    }

    private class RecorderLayout(context: Context) : ViewGroup(context) {
        val texture = TextureView(context)
        var skipDrawing: Boolean = false
            set(value) {
                field = value
                invalidate()
            }

        init {
            texture.isOpaque = false
            addView(texture)
        }

        override fun dispatchDraw(canvas: Canvas?) {
            if (!skipDrawing) {
                super.dispatchDraw(canvas)
            }
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            texture.layout(l, t, r, b)
        }

    }

    override fun onViewAttachedToWindow(v: View) {
        v.viewTreeObserver.addOnPreDrawListener(this)
        val p = v as ViewGroup
        val application = v.context.applicationContext
        if (MemoryMonitor.isAttached.compareAndSet(false, true)) {
            application.unregisterComponentCallbacks(MemoryMonitor)
        }
        val view = RecorderLayout(application)
        p.addView(
            view,
            tempLayoutParams
        )
        val rs = RenderScript.create(application)
        val rsb = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        val t = HandlerThread(
            toString(),
            Process.THREAD_PRIORITY_FOREGROUND
        ).apply { start() }
        val h = Handler(t.looper)
        recorderLayout = view
        renderScript = rs
        blur = rsb
        thread = t
        handler = h
    }

    override fun onViewDetachedFromWindow(v: View) {
        v.viewTreeObserver.removeOnPreDrawListener(this)
        thread?.quit()
        thread = null
        handler = null
        imageReader?.close()
        imageReader = null
        val background = recorderLayout
        if (background?.parent == v && v is ViewGroup) {
            v.removeView(background)
        }
        recorderLayout = null
        synchronized(processLock) {
            blur?.destroy()
            blur = null
            renderScript?.destroy()
            renderScript = null
        }
    }

    override fun onPreDraw(): Boolean {
        val view = currentView
        val rootView = currentView?.rootView
        val recorder = imageReader
        val background = recorderLayout
        if (view != null && background != null) {
            val index = view.indexOfChild(background)
            if (index == -1) {
                view.addView(
                    background, 0,
                    tempLayoutParams
                )
            } else if (index != 0) {
                view.removeView(background)
                view.addView(
                    background, 0,
                    tempLayoutParams
                )
            }
            if (rootView != null && recorder != null && checkDirty(view)) {
                view.getGlobalVisibleRect(tempVisibleRect)
                val width = tempVisibleRect.width()
                val height = tempVisibleRect.height()
                val blurSampling = blurSampling
                if (width * height > 0) {
                    // 不需要绘制整个屏幕，只需要绘制View底下那一层就可以了
                    val surface = recorder.surface
                    val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        surface.lockHardwareCanvas()
                    } else {
                        surface.lockCanvas(null)
                    }
                    // 转换canvas来到View的绝对位置
                    canvas.scale(1f / blurSampling, 1f / blurSampling)
                    canvas.translate(
                        -tempVisibleRect.left.toFloat(),
                        -tempVisibleRect.top.toFloat()
                    )
                    // 防止画到自己
                    try {
                        background.skipDrawing = true
                        rootView.draw(canvas)
                    } finally {
                        background.skipDrawing = false
                    }
                    // 结束录制
                    surface.unlockCanvasAndPost(canvas)
                }
            }
        }
        return true
    }

    override fun onImageAvailable(reader: ImageReader) {
        val backgroundView = this.recorderLayout ?: return
        val blurRadius = this.blurRadius
        val cornerRadius = this.cornerRadius
        val blurSampling = this.blurSampling
        val scaledWidth = reader.width
        val scaledHeight = reader.height
        var image: Image? = reader.acquireLatestImage()
        while (image != null) {
            val startTime = SystemClock.uptimeMillis()
            val bitmap = image.use {
                val plane = it.planes[0]
                val bytes = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * scaledWidth
                bitmapPool.getDirty(
                    scaledWidth + rowPadding / pixelStride,
                    scaledHeight,
                    Bitmap.Config.ARGB_8888
                ).apply {
                    copyPixelsFromBuffer(bytes)
                    reconfigure(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
                }
            }
            val bitmapTime = SystemClock.uptimeMillis()
            Log.d(TAG, "bitmap=${bitmapTime - startTime}")
            synchronized(processLock) {
                val blur = this.blur ?: return
                val input = Allocation.createFromBitmap(
                    renderScript, bitmap, Allocation.MipmapControl.MIPMAP_NONE,
                    Allocation.USAGE_SCRIPT
                )
                AutoCloseable { input.destroy() }.use {
                    val output = Allocation.createTyped(renderScript, input.type)
                    AutoCloseable { output.destroy() }.use {
                        blur.setInput(input)
                        blur.setRadius(blurRadius)
                        blur.forEach(output)
                        output.copyTo(bitmap)
                    }
                }
            }
            bitmap.prepareToDraw()
            val blurTime = SystemClock.uptimeMillis()
            Log.d(TAG, "blur=${blurTime - bitmapTime}")
            val canvas = backgroundView.texture.lockCanvas()
            if (canvas != null) {
                tempDrawingRect.set(
                    0,
                    0,
                    canvas.width,
                    canvas.height
                )
                if (cornerRadius > 0) {
                    val backgroundShader = obtainShader(bitmap)
                    canvas.save()
                    // 经过渲染的Bitmap由于缩放的关系
                    // 可能会比View小，所以要做特殊处理，把它放大回去
                    canvas.scale(
                        blurSampling,
                        blurSampling
                    )
                    canvas.drawRoundRect(
                        tempDrawingRectF.apply {
                            set(
                                0f,
                                0f,
                                scaledWidth.toFloat(),
                                scaledHeight.toFloat()
                            )
                        },
                        cornerRadius / blurSampling,
                        cornerRadius / blurSampling,
                        tempPaint.apply {
                            reset()
                            isFilterBitmap = true
                            isAntiAlias = true
                            shader = backgroundShader
                        }
                    )
                    canvas.restore()
                } else {
                    canvas.drawBitmap(
                        bitmap,
                        null,
                        tempDrawingRect,
                        tempPaint.apply {
                            reset()
                            isFilterBitmap = true
                            isAntiAlias = true
                        }
                    )
                }
                backgroundView.texture.unlockCanvasAndPost(canvas)
            }
            val drawTime = SystemClock.uptimeMillis()
            Log.d(TAG, "draw=${drawTime - blurTime}")
            bitmapPool.put(bitmap)
            image = reader.acquireLatestImage()
        }
    }

    override fun onLayoutChange(
        v: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int
    ) {
        mainQueue.removeIdleHandler(updateBounds)
        mainQueue.addIdleHandler(updateBounds)
    }

    companion object {

        private const val TAG = "BlurViewDelegate"

        private val bitmapPool: LruBitmapPool

        private val shaders = LinkedList<SoftReference<BitmapShader>>()

        private val getBitmap: (BitmapShader) -> Bitmap by lazy {
            val field = BitmapShader::class.java.getDeclaredField("mBitmap").apply {
                isAccessible = true
            }
            return@lazy {
                field.get(it) as Bitmap
            }
        }

        private val mainQueue by lazy {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Looper.getMainLooper().queue
            } else {
                val mainLooper = Looper.getMainLooper()
                if (Looper.myLooper() == mainLooper) {
                    Looper.myQueue()
                } else {
                    val task = FutureTask { Looper.myQueue() }
                    Handler(mainLooper).postAtFrontOfQueue(task)
                    task.get()
                }
            }
        }

        init {
            val displayMetrics = Resources.getSystem().displayMetrics
            val size = Int.SIZE_BYTES * displayMetrics.widthPixels * displayMetrics.heightPixels
            bitmapPool = LruBitmapPool(size)
        }

        private fun checkDirty(view: View): Boolean {
            if (!view.isDirty) {
                return true
            }
            var c: View? = view
            var p: ViewGroup? = view.parent as? ViewGroup
            while (p != null) {
                for (index in 0 until p.childCount) {
                    val v = p.getChildAt(index)
                    if (v != c && v.isDirty) {
                        return true
                    }
                }
                c = p
                p = p.parent as? ViewGroup
            }
            return false
        }

        private fun obtainShader(bitmap: Bitmap): BitmapShader {
            synchronized(shaders) {
                val it = shaders.iterator()
                if (it.hasNext()) {
                    val ref = it.next()
                    val shader = ref.get()
                    if (shader == null) {
                        it.remove()
                    } else if (getBitmap(shader) == bitmap) {
                        return shader
                    }
                }
                return BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
            }
        }

        @JvmStatic
        fun attach(view: View): BlurViewDelegate {
            val delegate = BlurViewDelegate()
            view.addOnAttachStateChangeListener(delegate)
            view.addOnLayoutChangeListener(delegate)
            if (view.isAttachedToWindow) {
                delegate.onViewAttachedToWindow(view)
            }
            if (view.isLaidOut) {
                delegate.onLayoutChange(
                    view,
                    view.left,
                    view.top,
                    view.right,
                    view.bottom,
                    view.left,
                    view.top,
                    view.right,
                    view.bottom
                )
            }
            return delegate
        }
    }
}



