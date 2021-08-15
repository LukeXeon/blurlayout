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
import android.widget.FrameLayout
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


class BlurLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val backgroundView = BackgroundView(context)
    private var skipDirty: Boolean = true

    var cornerRadius: Float
        get() = backgroundView.cornerRadius
        set(value) {
            backgroundView.cornerRadius = value
        }

    var blurSampling: Float
        get() = backgroundView.blurSampling
        set(value) {
            backgroundView.blurSampling = value
        }

    var blurRadius: Float
        get() = backgroundView.blurRadius
        set(value) {
            backgroundView.blurRadius = value
        }

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(
                attrs, R.styleable.BlurLayout, defStyleAttr, 0
            )
            cornerRadius = a.getDimension(R.styleable.BlurLayout_cornerRadius, 0f)
            blurSampling = a.getFloat(R.styleable.BlurLayout_blurSampling, 4f)
            blurRadius = a.getFloat(R.styleable.BlurLayout_blurRadius, 10f)
            a.recycle()
        }
        addView(backgroundView)
        isChildrenDrawingOrderEnabled = true
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
        val index = indexOfChild(backgroundView)
        return if (index == 0) {
            super.getChildDrawingOrder(childCount, drawingPosition)
        } else {
            when {
                drawingPosition > index -> {
                    drawingPosition
                }
                drawingPosition < index -> {
                    drawingPosition + 1
                }
                else -> {
                    0
                }
            }
        }
    }

    private class BackgroundView(context: Context) : View(context),
        ViewTreeObserver.OnPreDrawListener {
        private var renderScript: RenderScript? = null
        private var blur: ScriptIntrinsicBlur? = null
        private var recorder: Recorder? = null
        private val processLock = Any()
        private val visibleRect = Rect()
        private val tempOptions = BitmapFactory.Options()
        private val parentView: BlurLayout?
            get() = parent as? BlurLayout
        private var attachInfo: RootAttachInfo?
            get() {
                return rootView.getTag(R.id.root_attach_info) as? RootAttachInfo
            }
            set(value) {
                rootView.setTag(R.id.root_attach_info, value)
            }
        var inInvalidate: Boolean = false

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
                field = max(25f, min(0f, value))
                invalidate()
            }

        init {
            tempOptions.inMutable = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                outlineProvider = RoundRectOutlineProvider()
                clipToOutline = true
            }
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }

        private class RootAttachInfo {
            val set: MutableSet<BackgroundView> =
                Collections.newSetFromMap(WeakHashMap<BackgroundView, Boolean>())
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        private class RoundRectOutlineProvider : ViewOutlineProvider() {

            override fun getOutline(view: View, outline: Outline) {
                val background = view as BackgroundView
                outline.setRoundRect(0, 0, view.width, view.height, background.cornerRadius)
            }
        }

        private abstract class Recorder(protected val callback: (Bitmap) -> Unit) : Closeable {

            private val workerThread = HandlerThread(
                super.toString(),
                Process.THREAD_PRIORITY_FOREGROUND
            ).apply { start() }
            protected val worker: Handler by lazy { createAsync(workerThread.looper) }

            abstract fun onSizeChanged(width: Int, height: Int)

            abstract fun lockCanvas(): Canvas

            abstract fun unlockCanvasAndPost(canvas: Canvas)

            override fun close() {
                workerThread.quit()
            }

        }

        @RequiresApi(Build.VERSION_CODES.KITKAT)
        private inner class ImageReaderRecorder(callback: (Bitmap) -> Unit) : Recorder(callback),
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
                callback(clipBitmap)
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

        private inner class PictureRecorder(callback: (Bitmap) -> Unit) : Recorder(callback),
            Runnable {
            private val size = IntArray(2)
            private val tempCanvas = Canvas()
            private val freeQueue = ConcurrentLinkedQueue<Picture>()
            private val completeQueue = ConcurrentLinkedQueue<Picture>()
            private var current: Picture? = null

            override fun run() {
                var latest: Picture? = null
                while (true) {
                    val next = completeQueue.poll()
                    if (next == null) {
                        break
                    } else {
                        if (latest != null && freeQueue.size < 30) {
                            Log.d(TAG, "recycle picture size=" + freeQueue.size)
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
                callback(bitmap)
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
                completeQueue.add(picture)
                if (!worker.hasMessages(0, this)) {
                    worker.postAtTime(this, this, SystemClock.uptimeMillis())
                }
            }

            override fun close() {
                super.close()
                freeQueue.clear()
                completeQueue.clear()
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
                    renderScript, bitmap, Allocation.MipmapControl.MIPMAP_NONE,
                    Allocation.USAGE_SCRIPT
                )
                input.use {
                    val output = Allocation.createTyped(renderScript, input.type)
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
            if (isClip) {
                canvas.restore()
            }
        }

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            var info = attachInfo
            if (info == null) {
                info = RootAttachInfo()
                attachInfo = info
            }
            info.set.add(this)
            viewTreeObserver.addOnPreDrawListener(this)
            val rs = RenderScript.create(context)
            val rsb = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            synchronized(processLock) {
                renderScript = rs
                blur = rsb
            }
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                ImageReaderRecorder(this::onFrameAvailable)
            } else {
                PictureRecorder(this::onFrameAvailable)
            }
        }

        override fun onDetachedFromWindow() {
            val info = attachInfo
            if (info != null) {
                info.set.remove(this)
                if (info.set.isEmpty()) {
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
            viewTreeObserver.removeOnPreDrawListener(this)
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

        override fun invalidate() {
            inInvalidate = true
            super.invalidate()
            inInvalidate = false
        }

        override fun setVisibility(visibility: Int) {
            inInvalidate = true
            super.setVisibility(if (visibility == VISIBLE) VISIBLE else GONE)
            inInvalidate = false
        }

        override fun onPreDraw(): Boolean {
            val start = SystemClock.uptimeMillis()
            val parentView = parentView
            val rootView = this.parentView?.rootView
            val recorder = recorder
            val attachSet = attachInfo?.set
            if (parentView != null && !attachSet.isNullOrEmpty()) {
                if (rootView != null
                    && recorder != null
                    && checkDirty(rootView) == DirtyType.Dirty
                ) {
                    getGlobalVisibleRect(visibleRect)
                    val width = visibleRect.width()
                    val height = visibleRect.height()
                    val blurSampling = blurSampling
                    if (width * height > 0) {
                        // 不需要绘制整个屏幕，只需要绘制View底下那一层就可以了
                        val canvas = recorder.lockCanvas()
                        // 转换canvas来到View的绝对位置
                        canvas.scale(1f / blurSampling, 1f / blurSampling)
                        canvas.translate(
                            -visibleRect.left.toFloat(),
                            -visibleRect.top.toFloat()
                        )
                        // 防止画到自己
                        try {
                            attachSet.forEach {
                                it.visibility = GONE
                            }
                            rootView.draw(canvas)
                        } finally {
                            attachSet.forEach {
                                it.visibility = VISIBLE
                            }
                        }
                        // 结束录制
                        recorder.unlockCanvasAndPost(canvas)
                    }
                }
            }
            Log.d(TAG, "snapshot=" + (SystemClock.uptimeMillis() - start))
            return true
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            recorder?.onSizeChanged(w, h)
        }

    }

    companion object {

        private enum class DirtyType {
            None,
            Dirty,
            Skip
        }

        private val bitmapPool: BitmapPool
            get() = BitmapPool.default

        private fun checkDirty(view: View): DirtyType {
            if (view.isDirty) {
                when (view) {
                    is BlurLayout -> {
                        return if (view.skipDirty) {
                            DirtyType.Skip
                        } else {
                            DirtyType.Dirty
                        }
                    }
                    is ViewGroup -> {
                        var skip = false
                        for (i in 0 until view.childCount) {
                            val child = view.getChildAt(i)
                            val type = checkDirty(child)
                            if (type == DirtyType.Dirty) {
                                return DirtyType.Dirty
                            } else if (type == DirtyType.Skip) {
                                skip = true
                            }
                        }
                        return if (skip) {
                            DirtyType.Skip
                        } else {
                            DirtyType.Dirty
                        }
                    }
                    else -> {
                        return DirtyType.Dirty
                    }
                }
            }
            return DirtyType.None
        }

        private const val TAG = "BlurLayout"
    }
}
