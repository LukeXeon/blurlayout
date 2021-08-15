package open.source.uikit.blurlayout

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
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
import androidx.annotation.WorkerThread
import open.source.uikit.R
import open.source.uikit.common.BitmapPool
import open.source.uikit.common.acquireLatestImageCompat
import open.source.uikit.common.createAsync
import open.source.uikit.common.use
import java.io.Closeable
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.max
import kotlin.math.min

class BlurView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private var renderScript: RenderScript? = null
    private var blur: ScriptIntrinsicBlur? = null
    private var recorder: Recorder? = null
    private val processLock = Any()
    private val visibleRect = Rect()
    private val tempOptions = BitmapFactory.Options()
    private var attachInfo: RootAttachInfo?
        get() {
            return rootView.getTag(R.id.root_attach_info) as? RootAttachInfo
        }
        set(value) {
            rootView.setTag(R.id.root_attach_info, value)
        }

    @Volatile
    private var pendingFrame: Bitmap? = null

    @Volatile
    private var frame: Bitmap? = null

    private val nextFrameLock = Any()
    private val drawingRect = Rect()
    private val clipCanvasRectF by lazy { RectF() }
    private val clipCanvasPath by lazy { Path() }
    private val updateFrameRunnable = Runnable {
        var newValue: Bitmap?
        synchronized(nextFrameLock) {
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
            } else {
                invalidate()
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
            field = max(
                25f,
                min(0f, value)
            )
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
            outlineProvider = RoundRectOutlineProvider()
            clipToOutline = true
        }
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    private class RootAttachInfo :
        ViewTreeObserver.OnPreDrawListener {
        val set: MutableSet<BlurView> =
            Collections.newSetFromMap(WeakHashMap<BlurView, Boolean>())

        override fun onPreDraw(): Boolean {
            val start = SystemClock.uptimeMillis()
            if (set.isNotEmpty() && checkDirty(set.first().rootView) == DirtyPath.Dirty) {
                set.forEach {
                    it.visibility = GONE
                }
                set.forEach {
                    it.requestFrame()
                }
                set.forEach {
                    it.visibility = VISIBLE
                }
            }
            Log.d(
                TAG,
                "snapshot=" + (SystemClock.uptimeMillis() - start)
            )
            return true
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private class RoundRectOutlineProvider : ViewOutlineProvider() {

        override fun getOutline(view: View, outline: Outline) {
            val background = view as BlurView
            outline.setRoundRect(0, 0, view.width, view.height, background.cornerRadius)
        }
    }

    private abstract class Recorder : Closeable {

        private val workerThread = HandlerThread(
            super.toString(),
            Process.THREAD_PRIORITY_FOREGROUND
        ).apply { start() }
        protected val worker: Handler by lazy {
            createAsync(
                workerThread.looper
            )
        }

        abstract fun onSizeChanged(width: Int, height: Int)

        abstract fun lockCanvas(): Canvas

        abstract fun unlockCanvasAndPost(canvas: Canvas)

        override fun close() {
            workerThread.quit()
        }

    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private inner class ImageReaderRecorder : Recorder(),
        ImageReader.OnImageAvailableListener {
        private val clipBitmapCanvas = Canvas()
        private val clipBitmapRect = Rect()
        private var imageReader: ImageReader? = null

        override fun onImageAvailable(reader: ImageReader) {
            val image = reader.acquireLatestImageCompat() ?: return
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
            onFrameAvailable(clipBitmap)
        }

        override fun onSizeChanged(width: Int, height: Int) {
            if (width * height > 0) {
                val recorder = imageReader
                val blurSampling = blurSampling
                if (recorder == null || recorder.width != width && recorder.height != height) {
                    val scaledWidth = (width / blurSampling).toInt()
                    val scaledHeight = (height / blurSampling).toInt()
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
                        worker
                    )
                    imageReader = r
                }
            } else {
                imageReader?.close()
                imageReader = null
            }
        }

        override fun lockCanvas(): Canvas {
            val surface = imageReader!!.surface
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                surface.lockHardwareCanvas()
            } else {
                surface.lockCanvas(null)
            }
        }

        override fun unlockCanvasAndPost(canvas: Canvas) {
            imageReader!!.surface.unlockCanvasAndPost(canvas)
        }

        override fun close() {
            super.close()
            imageReader?.close()
            imageReader = null
        }
    }

    private inner class PictureRecorder : Recorder(),
        Runnable {
        private val size = IntArray(2)
        private val tempCanvas = Canvas()
        private val freeQueue =
            ConcurrentLinkedQueue<Picture>()
        private val readyQueue =
            ConcurrentLinkedQueue<Picture>()
        private var current: Picture? = null

        override fun run() {
            var latest: Picture? = null
            while (true) {
                val next = readyQueue.poll()
                if (next == null) {
                    break
                } else {
                    if (latest != null && freeQueue.size < 30) {
                        Log.d(
                            TAG,
                            "recycle picture size=" + freeQueue.size
                        )
                        freeQueue.add(latest)
                    }
                    latest = next
                }
            }
            latest ?: return
            val bitmap = bitmapPool[
                    latest.width,
                    latest.height,
                    Bitmap.Config.ARGB_8888
            ]
            tempCanvas.setBitmap(bitmap)
            tempCanvas.drawPicture(latest)
            tempCanvas.setBitmap(null)
            if (freeQueue.size < 30) {
                Log.d(TAG, "recycle picture size=" + freeQueue.size)
                freeQueue.add(latest)
            }
            onFrameAvailable(bitmap)
        }

        override fun onSizeChanged(width: Int, height: Int) {
            size[0] = (width / blurSampling).toInt()
            size[1] = (height / blurSampling).toInt()
        }

        override fun lockCanvas(): Canvas {
            val picture = freeQueue.poll() ?: Picture().apply {
                Log.d(TAG, "create picture")
            }
            val canvas = picture.beginRecording(size[0], size[1])
            current = picture
            return canvas
        }

        override fun unlockCanvasAndPost(canvas: Canvas) {
            val picture = current!!
            picture.endRecording()
            current = null
            readyQueue.add(picture)
            if (!worker.hasMessages(0, this)) {
                worker.postAtTime(
                    this, this,
                    SystemClock.uptimeMillis()
                )
            }
        }

        override fun close() {
            super.close()
            freeQueue.clear()
            readyQueue.clear()
        }
    }

    private fun updateFrame(bitmap: Bitmap) {
        var postTask: Boolean
        synchronized(nextFrameLock) {
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

    @WorkerThread
    private fun onFrameAvailable(bitmap: Bitmap) {
        val bitmapTime = SystemClock.uptimeMillis()
        synchronized(processLock) {
            val blur = this.blur ?: return
            val input = Allocation.createFromBitmap(
                renderScript,
                bitmap,
                Allocation.MipmapControl.MIPMAP_NONE,
                Allocation.USAGE_SCRIPT
            )
            input.use {
                val output = Allocation.createTyped(
                    renderScript,
                    input.type
                )
                output.use {
                    blur.setInput(input)
                    blur.setRadius(blurRadius)
                    blur.forEach(output)
                    output.copyTo(bitmap)
                }
            }
        }
        val blurTime = SystemClock.uptimeMillis()
        Log.d(TAG, "blur=${blurTime - bitmapTime}")
        val nextFrame =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && layerType == LAYER_TYPE_HARDWARE) {
                val hwBitmap = bitmap.copy(Bitmap.Config.HARDWARE, false)
                bitmapPool.recycle(bitmap)
                hwBitmap
            } else {
                bitmap
            }
        nextFrame.prepareToDraw()
        updateFrame(nextFrame)
        val drawTime = SystemClock.uptimeMillis()
        Log.d(TAG, "draw=${drawTime - blurTime}")
    }

    override fun onDraw(canvas: Canvas) {
        val frame = this.frame
        val cornerRadius = this.cornerRadius
        val blurSampling = this.blurSampling
        if (frame == null || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && !canvas.isHardwareAccelerated
            && frame.config == Bitmap.Config.HARDWARE
        ) {
            return
        }
        val isClip = (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP
                || !canvas.isHardwareAccelerated) && cornerRadius > 0
        if (isClip) {
            canvas.save()
            clipCanvasPath.apply {
                reset()
                addRoundRect(
                    clipCanvasRectF.apply {
                        set(
                            0f,
                            0f,
                            width.toFloat(),
                            height.toFloat()
                        )
                    }, cornerRadius, cornerRadius,
                    Path.Direction.CCW
                )
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
        if (isClip) {
            canvas.restore()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        var info = attachInfo
        if (info == null) {
            info = RootAttachInfo()
            viewTreeObserver.addOnPreDrawListener(info)
            attachInfo = info
        }
        info.set.add(this)
        val rs = RenderScript.create(context)
        val rsb = ScriptIntrinsicBlur.create(
            rs,
            Element.U8_4(rs)
        )
        synchronized(processLock) {
            renderScript = rs
            blur = rsb
        }
        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            ImageReaderRecorder()
        } else {
            PictureRecorder()
        }
    }

    override fun onDetachedFromWindow() {
        val info = attachInfo
        if (info != null) {
            info.set.remove(this)
            if (info.set.isEmpty()) {
                viewTreeObserver.removeOnPreDrawListener(info)
                attachInfo = null
            }
        }
        val frame = this.frame
        if (frame != null) {
            bitmapPool.recycle(frame)
            this.frame = null
        }
        synchronized(nextFrameLock) {
            val pendingFrame = this.pendingFrame
            if (pendingFrame != null) {
                bitmapPool.recycle(pendingFrame)
                this.pendingFrame = null
            }
        }
        recorder?.close()
        recorder = null
        synchronized(processLock) {
            blur?.destroy()
            blur = null
            renderScript?.destroy()
            renderScript = null
        }
        super.onDetachedFromWindow()
    }

    private fun requestFrame() {
        val recorder = recorder ?: return
        getGlobalVisibleRect(visibleRect)
        val width = visibleRect.width()
        val height = visibleRect.height()
        if (width * height > 0) {
            val blurSampling = blurSampling
            // 不需要绘制整个屏幕，只需要绘制View底下那一层就可以了
            val canvas = recorder.lockCanvas()
            // 转换canvas来到View的绝对位置
            canvas.scale(1f / blurSampling, 1f / blurSampling)
            canvas.translate(
                -visibleRect.left.toFloat(),
                -visibleRect.top.toFloat()
            )
            // 防止画到自己
            rootView.draw(canvas)
            // 结束录制
            recorder.unlockCanvasAndPost(canvas)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        recorder?.onSizeChanged(w, h)
    }

    private enum class DirtyPath {
        None,
        Skip,
        Dirty
    }

    companion object {

        private const val TAG = "BlurView"

        private val bitmapPool: BitmapPool
            get() = BitmapPool.default

        private fun checkDirty(view: View): DirtyPath {
            if (view.isDirty) {
                when (view) {
                    is BlurView -> {
                        return DirtyPath.Skip
                    }
                    is ViewGroup -> {
                        var hasSkipPath = false
                        var dirtyCount = 0
                        for (i in 0 until view.childCount) {
                            val child = view.getChildAt(i)
                            val childPath = checkDirty(child)
                            if (childPath == DirtyPath.Dirty) {
                                dirtyCount += 1
                            } else if (childPath == DirtyPath.Skip) {
                                hasSkipPath = true
                            }
                        }
                        if (hasSkipPath && dirtyCount == 0) {
                            return DirtyPath.Skip
                        }
                        return DirtyPath.Dirty
                    }
                    else -> {
                        return DirtyPath.Dirty
                    }
                }
            } else {
                return DirtyPath.None
            }
        }
    }

}