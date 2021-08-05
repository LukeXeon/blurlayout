package com.luke.uikit.blurlayout

import android.graphics.*
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
import java.io.InputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min

class BlurViewDelegate : ViewTreeObserver.OnPreDrawListener,
    ImageReader.OnImageAvailableListener,
    View.OnAttachStateChangeListener,
    View.OnLayoutChangeListener {
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private var backgroundLayout: ViewGroup? = null

    @Volatile
    private var backgroundView: TextureView? = null
    private var renderScript: RenderScript? = null
    private var blur: ScriptIntrinsicBlur? = null
    private var imageReader: ImageReader? = null
    private var recordingCanvas: Canvas? = null
    private val processLock = Any()
    private val tempPaint = Paint()
    private val tempDrawingRectF = RectF()
    private val tempDrawingRect = Rect()
    private val tempVisibleRect = Rect()
    private val updateBounds = Runnable {
        val view = currentView ?: return@Runnable
        val handler = handler ?: return@Runnable
        val width = view.width
        val height = view.height
        val recorder = imageReader
        if (width * height > 0) {
            if (recorder == null || recorder.width != width && recorder.height != height) {
                imageReader?.close()
                val r = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 60)
                r.setOnImageAvailableListener(this, handler)
                imageReader = r
            }
        } else {
            imageReader?.close()
            imageReader = null
        }
    }
    private val tempOptions = BitmapFactory.Options()
    private val currentView: ViewGroup?
        get() = backgroundLayout?.parent as? ViewGroup

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

    override fun onViewAttachedToWindow(v: View) {
        v.viewTreeObserver.addOnPreDrawListener(this)
        val p = v as ViewGroup
        val application = v.context.applicationContext
        val layout = object : ViewGroup(application) {

            override fun drawChild(canvas: Canvas?, child: View?, drawingTime: Long): Boolean {
                if (canvas != recordingCanvas) {
                    return super.drawChild(canvas, child, drawingTime)
                }
                return false
            }

            override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
                getChildAt(0).layout(l, t, r, b)
            }

            override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
                return Int.MIN_VALUE
            }
        }
        layout.setWillNotDraw(false)
        val view = TextureView(application)
        layout.addView(view)
        p.addView(
            layout,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        val rs = RenderScript.create(application)
        val rsb = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        val t = HandlerThread(
            toString(),
            Process.THREAD_PRIORITY_FOREGROUND
        ).apply { start() }
        val h = Handler(t.looper)
        backgroundLayout = layout
        backgroundView = view
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
        val layout = backgroundLayout
        if (layout?.parent == v && v is ViewGroup) {
            v.removeView(layout)
        }
        backgroundLayout = null
        backgroundView = null
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
        val layout = backgroundLayout
        if (view != null && layout != null) {
            val index = view.indexOfChild(layout)
            if (index == -1) {
                view.addView(
                    layout, 0,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            } else if (index != 0) {
                view.removeView(layout)
                view.addView(
                    layout, 0,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
            if (rootView != null && recorder != null && checkDirty(layout)) {
                view.getGlobalVisibleRect(tempVisibleRect)
                val width = tempVisibleRect.width()
                val height = tempVisibleRect.height()
                val blurSampling = blurSampling
                val scaledWidth = (width / blurSampling).toInt()
                val scaledHeight = (height / blurSampling).toInt()
                if (scaledWidth * scaledHeight > 0) {
                    // 不需要绘制整个屏幕，只需要绘制View底下那一层就可以了
                    val surface = recorder.surface
                    val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        surface.lockHardwareCanvas()
                    } else {
                        surface.lockCanvas(null)
                    }
                    // 转换canvas来到View的绝对位置
                    canvas.translate(
                        -tempVisibleRect.left.toFloat(),
                        -tempVisibleRect.top.toFloat()
                    )
                    // 设置recordingCanvas用来识别，防止画到自己
                    try {
                        recordingCanvas = canvas
                        rootView.draw(canvas)
                    } finally {
                        recordingCanvas = null
                    }
                    // 结束录制
                    surface.unlockCanvasAndPost(canvas)
                }
            }
        }
        return true
    }

    override fun onImageAvailable(reader: ImageReader) {
        val backgroundView = this.backgroundView ?: return
        val image = reader.acquireLatestImage() ?: return
        val blurRadius = this.blurRadius
        val cornerRadius = this.cornerRadius
        val blurSampling = this.blurSampling
        val width = reader.width
        val height = reader.height
        tempDrawingRect.set(0, 0, width, height)
        val bitmap = image.use {
            val plane = image.planes[0]
            val bytes = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            ).apply {
                copyPixelsFromBuffer(bytes)
                reconfigure(width, height, Bitmap.Config.ARGB_8888)
            }
        }
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
        val canvas = backgroundView.lockCanvas()
        if (canvas != null) {
            if (cornerRadius > 0) {
                val backgroundShader = BitmapShader(
                    bitmap,
                    Shader.TileMode.MIRROR,
                    Shader.TileMode.MIRROR
                )
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
                            width.toFloat() / blurSampling,
                            height.toFloat() / blurSampling
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
            backgroundView.unlockCanvasAndPost(canvas)
        }
        bitmap.recycle()
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
        v.removeCallbacks(updateBounds)
        v.postDelayed(updateBounds, 200)
    }

    companion object {

        private const val TAG = "BlurViewDelegate"

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