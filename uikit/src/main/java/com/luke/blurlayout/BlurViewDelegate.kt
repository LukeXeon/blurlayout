package com.luke.blurlayout

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.*
import android.media.Image
import android.media.ImageReader
import android.os.*
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import androidx.annotation.FloatRange
import androidx.annotation.Px
import androidx.annotation.WorkerThread
import com.bumptech.glide.Glide
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.reflect.KProperty

class BlurViewDelegate
@JvmOverloads
constructor(
    attachView: FrameLayout,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewTreeObserver.OnPreDrawListener,
    ImageReader.OnImageAvailableListener,
    View.OnAttachStateChangeListener,
    View.OnLayoutChangeListener {
    private var workerThread: HandlerThread? = null
    private var worker: Handler? = null

    @Volatile
    private var background: BackgroundLayout? = null
    private var renderScript: RenderScript? = null
    private var blur: ScriptIntrinsicBlur? = null
    private var imageReader: ImageReader? = null
    private val processLock = Any()
    private val drawingLock = Any()
    private val tempPaint = Paint()
    private val tempDrawingRectF = RectF()
    private val tempCanvas = Canvas()
    private val tempSrcRect = Rect()
    private val tempDestRect = Rect()
    private val tempVisibleRect = Rect()
    private val tempLayoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    )
    private val tempOptions = BitmapFactory.Options()
    private val currentView: ViewGroup?
        get() = background?.parent as? FrameLayout

    @Px
    @Volatile
    var cornerRadius: Int = 0
        set(value) {
            field = max(0, value)
            background?.invalidate()
        }

    @Volatile
    var blurSampling: Float = 4f
        set(value) {
            field = max(1f, value)
            background?.invalidate()
        }

    @Volatile
    var blurRadius: Float = 10f
        set(@FloatRange(from = 0.0, to = 25.0) value) {
            field = max(25f, min(0f, value))
            background?.invalidate()
        }

    init {
        tempOptions.inMutable = true
        attachView.addOnAttachStateChangeListener(this)
        attachView.addOnLayoutChangeListener(this)
        if (attachView.isAttachedToWindow) {
            this.onViewAttachedToWindow(attachView)
        }
        if (attachView.isLaidOut) {
            this.onLayoutChange(
                attachView,
                attachView.left,
                attachView.top,
                attachView.right,
                attachView.bottom,
                attachView.left,
                attachView.top,
                attachView.right,
                attachView.bottom
            )
        }
        if (attrs != null) {
            val a = attachView.context.obtainStyledAttributes(
                attrs, R.styleable.BlurView, defStyleAttr, 0
            )
            cornerRadius = a.getDimensionPixelSize(R.styleable.BlurView_cornerRadius, 0)
            blurSampling = a.getFloat(R.styleable.BlurView_blurSampling, 4f)
            blurRadius = a.getFloat(R.styleable.BlurView_blurRadius, 10f)
            a.recycle()
        }
    }


    private interface BitmapPool {
        operator fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap

        fun recycle(bitmap: Bitmap)
    }

    private class NonBitmapPool : BitmapPool {
        override fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap {
            return Bitmap.createBitmap(width, height, config)
        }

        override fun recycle(bitmap: Bitmap) {
            bitmap.recycle()
        }
    }

    private class BackgroundLayout(context: Context) : ViewGroup(context) {
        private val texture = TextureView(context)
        var skipDrawing: Boolean = false
            set(value) {
                field = value
                invalidate()
            }

        @WorkerThread
        fun lockCanvas(): Canvas? {
            return texture.lockCanvas()
        }

        @WorkerThread
        fun unlockCanvasAndPost(canvas: Canvas) {
            return texture.unlockCanvasAndPost(canvas)
        }

        init {
            texture.isOpaque = false
            attachViewToParent(
                texture,
                0,
                LayoutParams(
                    LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT
                )
            )
        }

        override fun dispatchDraw(canvas: Canvas?) {
            if (!skipDrawing) {
                super.dispatchDraw(canvas)
            }
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            texture.layout(l, t, r, b)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            texture.measure(widthMeasureSpec, heightMeasureSpec)
            setMeasuredDimension(texture.measuredWidth, texture.measuredHeight)
        }
    }

    override fun onViewAttachedToWindow(v: View) {
        v.viewTreeObserver.addOnPreDrawListener(this)
        val p = v as ViewGroup
        val application = v.context.applicationContext
        val view = BackgroundLayout(application)
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
        background = view
        renderScript = rs
        blur = rsb
        workerThread = t
        worker = h
    }

    override fun onViewDetachedFromWindow(v: View) {
        v.viewTreeObserver.removeOnPreDrawListener(this)
        workerThread?.quitSafely()
        workerThread = null
        worker = null
        imageReader?.close()
        imageReader = null
        synchronized(drawingLock) {
            val background = background
            if (background?.parent == v && v is ViewGroup) {
                v.removeView(background)
            }
        }
        background = null
        synchronized(processLock) {
            blur?.destroy()
            blur = null
            renderScript?.destroy()
            renderScript = null
        }
    }

    override fun onPreDraw(): Boolean {
        val start = SystemClock.uptimeMillis()
        val view = currentView
        val rootView = currentView?.rootView
        val recorder = imageReader
        val background = background
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
        Log.d(TAG, "snapshot=" + (SystemClock.uptimeMillis() - start))
        return true
    }

    override fun onImageAvailable(reader: ImageReader) {
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
                val bitmap = bitmapPool[
                        scaledWidth + rowPadding / pixelStride,
                        scaledHeight,
                        Bitmap.Config.ARGB_8888
                ]
                bitmap.copyPixelsFromBuffer(bytes)
                return@use bitmap
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
            val blurTime = SystemClock.uptimeMillis()
            Log.d(TAG, "blur=${blurTime - bitmapTime}")
            synchronized(drawingLock) {
                val backgroundView = this.background ?: return
                val canvas = backgroundView.lockCanvas()
                if (canvas != null) {
                    tempDestRect.set(
                        0,
                        0,
                        canvas.width,
                        canvas.height
                    )
                    if (cornerRadius > 0) {
                        val clipBitmap = bitmapPool[
                                scaledWidth,
                                scaledHeight,
                                Bitmap.Config.RGB_565
                        ]
                        tempCanvas.setBitmap(clipBitmap)
                        tempCanvas.drawBitmap(
                            bitmap,
                            tempSrcRect.apply {
                                set(0, 0, scaledWidth, scaledHeight)
                            },
                            tempSrcRect,
                            null
                        )
                        tempCanvas.setBitmap(null)
                        clipBitmap.prepareToDraw()
                        bitmapPool.recycle(bitmap)
                        val backgroundShader = loadShaderCache(clipBitmap)
                        canvas.save()
                        // 经过渲染的Bitmap由于缩放的关系
                        // 可能会比View小，所以要做特殊处理，把它放大回去
                        canvas.scale(
                            blurSampling,
                            blurSampling
                        )
                        canvas.drawRoundRect(
                            tempDrawingRectF.apply {
                                set(tempSrcRect)
                            },
                            cornerRadius / blurSampling,
                            cornerRadius / blurSampling,
                            tempPaint.apply {
                                reset()
                                shader = backgroundShader
                            }
                        )
                        canvas.restore()
                        bitmapPool.recycle(clipBitmap)
                    } else {
                        bitmap.prepareToDraw()
                        canvas.drawBitmap(
                            bitmap,
                            tempSrcRect.apply {
                                set(0, 0, scaledWidth, scaledHeight)
                            },
                            tempDestRect,
                            tempPaint.apply { reset() }
                        )
                        bitmapPool.recycle(bitmap)
                    }
                    backgroundView.unlockCanvasAndPost(canvas)
                }
            }
            val drawTime = SystemClock.uptimeMillis()
            Log.d(TAG, "draw=${drawTime - blurTime}")
            image = reader.acquireLatestImage()
        }
    }

    @SuppressLint("WrongConstant")
    override fun onLayoutChange(
        view: View,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int
    ) {
        val handler = worker ?: return
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
                r.setOnImageAvailableListener(this, handler)
                imageReader = r
            }
        } else {
            imageReader?.close()
            imageReader = null
        }
    }

    companion object {

        private const val TAG = "BlurViewDelegate"

        private val bitmapPool by lazy {
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
                val context = mInitialApplicationField.get(sCurrentActivityThread) as Application
                val innerPool = Glide.get(context).bitmapPool
                return@lazy object : BitmapPool {
                    override fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap {
                        return innerPool.get(width, height, config)
                    }

                    override fun recycle(bitmap: Bitmap) {
                        innerPool.put(bitmap)
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                return@lazy NonBitmapPool()
            }
        }

        private val shaderCaches by lazy { LinkedList<WeakReference<BitmapShader>>() }

        private val BitmapShader.bitmap: Bitmap by object : Any(), () -> Field {

            override fun invoke(): Field {
                return BitmapShader::class.java.getDeclaredField("mBitmap").apply {
                    isAccessible = true
                }
            }

            private val field by lazy(this)

            operator fun getValue(shader: BitmapShader, property: KProperty<*>): Bitmap {
                return field.get(shader) as Bitmap
            }
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

        private fun loadShaderCache(bitmap: Bitmap): BitmapShader {
            if (bitmapPool is NonBitmapPool) {
                return BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
            }
            synchronized(shaderCaches) {
                val it = shaderCaches.iterator()
                while (it.hasNext()) {
                    val ref = it.next()
                    val shader = ref.get()
                    if (shader == null) {
                        it.remove()
                    } else if (shader.bitmap == bitmap) {
                        return shader
                    }
                }
                Log.d(
                    TAG, "create shader for bitmap=$bitmap," +
                            "current cache size=" + shaderCaches.size
                )
                val shader = BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
                shaderCaches.addFirst(WeakReference(shader))
                return shader
            }
        }
    }
}