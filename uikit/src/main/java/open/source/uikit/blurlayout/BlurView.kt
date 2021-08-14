package open.source.uikit.blurlayout

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
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import androidx.annotation.FloatRange
import androidx.annotation.Px
import androidx.annotation.RequiresApi
import com.bumptech.glide.Glide
import open.source.uikit.R
import java.util.*
import kotlin.math.max
import kotlin.math.min

class BlurView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr),
    ViewTreeObserver.OnPreDrawListener,
    ImageReader.OnImageAvailableListener {
    private var workerThread: HandlerThread? = null
    private var worker: Handler? = null
    private var renderScript: RenderScript? = null
    private var blur: ScriptIntrinsicBlur? = null
    private var imageReader: ImageReader? = null
    private val processLock = Any()
    private val clipBitmapCanvas = Canvas()
    private val clipBitmapRect = Rect()
    private val visibleRect = Rect()
    private val tempOptions = BitmapFactory.Options()
    private val parentView: ViewGroup?
        get() = parent as? ViewGroup
    private var attachViewSet: MutableSet<BlurView>?
        get() {
            if (isAttachedToWindow) {
                @Suppress("UNCHECKED_CAST")
                return rootView.getTag(R.id.attach_view_set) as? MutableSet<BlurView>
            } else {
                return null
            }
        }
        set(value) {
            if (isAttachedToWindow) {
                rootView.setTag(R.id.attach_view_set, value)
            }
        }
    private var skipDrawing: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    @Volatile
    private var pendingFrame: Bitmap? = null

    @Volatile
    private var frame: Bitmap? = null

    private val frameLock = Any()
    private val drawingRect = Rect()
    private val clipCanvasRectF by lazy { RectF() }
    private val clipCanvasPath by lazy { Path() }
    private val updateFrameRunnable = Runnable {
        var newValue: Bitmap?
        synchronized(frameLock) {
            val oldFrame = frame
            if (oldFrame != null) {
                bitmapPool.recycle(oldFrame)
            }
            newValue = this.pendingFrame
            this.pendingFrame = null
        }
        this.frame = newValue
        invalidate()
    }

    @Px
    @Volatile
    var cornerRadius: Float = 0f
        set(value) {
            field = max(0f, value)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                invalidateOutline()
            }
        }

    @Volatile
    var blurSampling: Float = 4f
        set(value) {
            field = max(1f, value)
            invalidate()
        }

    @Volatile
    var blurRadius: Float = 10f
        set(@FloatRange(from = 0.0, to = 25.0) value) {
            field = max(25f, min(0f, value))
            invalidate()
        }

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(
                attrs, R.styleable.BlurView, defStyleAttr, 0
            )
            cornerRadius = a.getDimension(R.styleable.BlurView_cornerRadius, 0f)
            blurSampling = a.getFloat(R.styleable.BlurView_blurSampling, 4f)
            blurRadius = a.getFloat(R.styleable.BlurView_blurRadius, 10f)
            a.recycle()
        }
        tempOptions.inMutable = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            outlineProvider =
                ClipRoundRectOutlineProvider()
            clipToOutline = true
        }
    }

    private interface BitmapPoolAdapter {
        operator fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap

        fun recycle(bitmap: Bitmap)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private class ClipRoundRectOutlineProvider : ViewOutlineProvider() {

        override fun getOutline(view: View, outline: Outline) {
            val background = view as BlurView
            outline.setRoundRect(0, 0, view.width, view.height, background.cornerRadius)
        }
    }

    override fun setLayerType(layerType: Int, paint: Paint?) {
        setLayerPaint(paint)
    }

    override fun getLayerType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYER_TYPE_HARDWARE
        } else {
            super.getLayerType()
        }
    }

    private fun updateFrame(bitmap: Bitmap) {
        var postTask: Boolean
        synchronized(frameLock) {
            val oldPendingFrame = pendingFrame
            if (oldPendingFrame != null) {
                bitmapPool.recycle(oldPendingFrame)
            }
            postTask = oldPendingFrame == null
            pendingFrame = bitmap
        }
        if (!postTask) {
            return
        }
        post(updateFrameRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        val frame = this.frame
        if (skipDrawing || frame == null) {
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && !canvas.isHardwareAccelerated
            && frame.config == Bitmap.Config.HARDWARE
        ) {
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP && cornerRadius > 0) {
            clipCanvasPath.apply {
                reset()
                addRoundRect(clipCanvasRectF.apply {
                    set(
                        0f,
                        0f,
                        width.toFloat(),
                        height.toFloat()
                    )
                }, cornerRadius, cornerRadius, Path.Direction.CCW)
                close()
            }
            canvas.clipPath(clipCanvasPath)
        }
        val scaledWidth = (width / blurSampling).toInt()
        val scaledHeight = (height / blurSampling).toInt()
        // 经过渲染的Bitmap由于缩放的关系
        // 可能会比View小，所以要做特殊处理，把它放大回去
        canvas.scale(
            blurSampling,
            blurSampling
        )
        canvas.drawBitmap(
            frame,
            drawingRect.apply {
                set(0, 0, scaledWidth, scaledHeight)
            },
            drawingRect,
            null
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        workerThread = HandlerThread(
            toString(),
            Process.THREAD_PRIORITY_FOREGROUND
        ).apply { start() }
        var set = attachViewSet
        if (set == null) {
            set = requireNotNull(Collections.newSetFromMap(WeakHashMap<BlurView, Boolean>()))
            attachViewSet = set
        }
        set.add(this)
        viewTreeObserver.addOnPreDrawListener(this)
        val rs = RenderScript.create(context)
        val rsb = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        synchronized(processLock) {
            renderScript = rs
            blur = rsb
        }
    }

    override fun onDetachedFromWindow() {
        workerThread?.quit()
        workerThread = null
        worker = null
        val set = attachViewSet
        if (set != null) {
            set.remove(this)
            if (set.isEmpty()) {
                attachViewSet = null
            }
        }
        synchronized(frameLock) {
            val frame = this.frame
            if (frame != null) {
                bitmapPool.recycle(frame)
                this.frame = null
            }
            val pendingFrame = this.pendingFrame
            if (pendingFrame != null) {
                bitmapPool.recycle(pendingFrame)
                this.pendingFrame = null
            }
        }
        viewTreeObserver.removeOnPreDrawListener(this)
        imageReader?.close()
        imageReader = null
        synchronized(processLock) {
            blur?.destroy()
            blur = null
            renderScript?.destroy()
            renderScript = null
        }
        super.onDetachedFromWindow()
    }

    override fun onPreDraw(): Boolean {
        val start = SystemClock.uptimeMillis()
        val view = parentView
        val rootView = parentView?.rootView
        val recorder = imageReader
        val attachSet = attachViewSet
        if (view != null && !attachSet.isNullOrEmpty()) {
            val index = view.indexOfChild(this)
            if (index == -1) {
                view.addView(this, 0)
            } else if (index != 0) {
                view.removeView(this)
                view.addView(this, 0)
            }
            if (rootView != null && recorder != null && checkDirty(view)) {
                getGlobalVisibleRect(visibleRect)
                val width = visibleRect.width()
                val height = visibleRect.height()
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
                        -visibleRect.left.toFloat(),
                        -visibleRect.top.toFloat()
                    )
                    // 防止画到自己
                    try {
                        attachSet.forEach {
                            it.skipDrawing = true
                        }
                        rootView.draw(canvas)
                    } finally {
                        attachSet.forEach {
                            it.skipDrawing = false
                        }
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
        val image = reader.acquireLatestImageCompat() ?: return
        val blurRadius = this.blurRadius
        val scaledWidth = reader.width
        val scaledHeight = reader.height
        val startTime = SystemClock.uptimeMillis()
        val bitmap = image.use {
            val plane = image.planes[0]
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
        val clipBitmap = bitmapPool[
                scaledWidth,
                scaledHeight,
                Bitmap.Config.ARGB_8888
        ]
        clipBitmapCanvas.setBitmap(clipBitmap)
        clipBitmapCanvas.drawBitmap(
            bitmap,
            clipBitmapRect.apply {
                set(0, 0, scaledWidth, scaledHeight)
            },
            clipBitmapRect,
            null
        )
        clipBitmapCanvas.setBitmap(null)
        bitmapPool.recycle(bitmap)
        val bitmapTime = SystemClock.uptimeMillis()
        Log.d(TAG, "bitmap=${bitmapTime - startTime}")
        synchronized(processLock) {
            val blur = this.blur ?: return
            val input = Allocation.createFromBitmap(
                renderScript, clipBitmap, Allocation.MipmapControl.MIPMAP_NONE,
                Allocation.USAGE_SCRIPT
            )
            input.use {
                val output = Allocation.createTyped(renderScript, input.type)
                output.use {
                    blur.setInput(input)
                    blur.setRadius(blurRadius)
                    blur.forEach(output)
                    output.copyTo(clipBitmap)
                }
            }
        }
        val blurTime = SystemClock.uptimeMillis()
        Log.d(TAG, "blur=${blurTime - bitmapTime}")
        val nextFrame =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val hwBitmap = clipBitmap.copy(Bitmap.Config.HARDWARE, false)
                bitmapPool.recycle(clipBitmap)
                hwBitmap
            } else {
                clipBitmap
            }
        nextFrame.prepareToDraw()
        updateFrame(nextFrame)
        val drawTime = SystemClock.uptimeMillis()
        Log.d(TAG, "draw=${drawTime - blurTime}")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w * h > 0) {
            val thread = workerThread ?: return
            val recorder = imageReader
            var handler = worker
            if (handler == null) {
                handler = Handler(thread.looper)
                worker = handler
            }
            val blurSampling = blurSampling
            if (recorder == null || recorder.width != w && recorder.height != h) {
                val scaledWidth = (w / blurSampling).toInt()
                val scaledHeight = (h / blurSampling).toInt()
                imageReader?.close()
                @SuppressLint("WrongConstant")
                val r = ImageReader.newInstance(
                    scaledWidth,
                    scaledHeight,
                    PixelFormat.RGBA_8888,
                    30
                )
                r.setOnImageAvailableListener(
                    this,
                    handler
                )
                imageReader = r
            }
        } else {
            imageReader?.close()
            imageReader = null
        }
    }

    companion object {

        private const val TAG = "BlurView"

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
                return@lazy object :
                    BitmapPoolAdapter {
                    override fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap {
                        return innerPool.get(width, height, config)
                    }

                    override fun recycle(bitmap: Bitmap) {
                        innerPool.put(bitmap)
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
                return@lazy object :
                    BitmapPoolAdapter {
                    override fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap {
                        return Bitmap.createBitmap(width, height, config)
                    }

                    override fun recycle(bitmap: Bitmap) {
                        bitmap.recycle()
                    }
                }
            }
        }

        private inline fun <R> Allocation.use(block: (Allocation) -> R): R {
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

        private fun ImageReader.acquireLatestImageCompat(): Image? {
            try {
                return this.acquireLatestImage()
            } catch (e: RuntimeException) {
                /* In API level 23 or below,  it will throw "java.lang.RuntimeException:
           ImageReaderContext is not initialized" when ImageReader is closed. To make the
           behavior consistent as newer API levels,  we make it return null Image instead.*/
                if (!isImageReaderContextNotInitializedException(
                        e
                    )
                ) {
                    throw e // only catch RuntimeException:ImageReaderContext is not initialized
                }
            }
            return null
        }

        private fun isImageReaderContextNotInitializedException(e: RuntimeException): Boolean {
            return "ImageReaderContext is not initialized" == e.message
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

    }
}