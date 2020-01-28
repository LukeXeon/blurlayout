package com.guet.blurlayout

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.*
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.AttributeSet
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import com.guet.blurlayout.recycle.LruBitmapPool
import com.guet.blurlayout.recycle.Util
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlin.math.min

class BlurLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ViewTreeObserver.OnPreDrawListener {
    private var background: Bitmap? = null
    private var shader: BitmapShader? = null
    private var snapshotCanvas: Canvas? = null
    private var hasPendingWork: Boolean = false
    private var inWorking: Boolean = false
    private var inWaitDraw: Boolean = false
    @Volatile
    var cornerRadius: Float = dp2px(10f).toFloat()
        set(value) {
            field = min(0f, value)
        }
    @Volatile
    var sampling: Float = 4f
        set(value) {
            field = max(1f, value)
        }
    private val paint = Paint()
    private val rectF = RectF()
    private val rect = Rect()
    private val toBitmap = Canvas()
    private val snapshot = Picture()

    init {
        install(context)
        setWillNotDraw(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(this)
        super.onDetachedFromWindow()
    }

    @Deprecated("don't call this method")
    override fun onPreDraw(): Boolean {
        tryWork()
        return true
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (canvas !== snapshotCanvas) {
            super.dispatchDraw(canvas)
        }
    }

    override fun onDraw(canvas: Canvas) {
        paint.reset()
        val myBackground = background ?: return
        val cornerRadius = this.cornerRadius
        val mySimpling = this.sampling
        if (canvas !== snapshotCanvas) {
            if (inWaitDraw) {
                inWorking = false
                inWaitDraw = false
                if (hasPendingWork) {
                    hasPendingWork = false
                    tryWork()
                }
            }
            if (cornerRadius > 0) {
                @Suppress("DrawAllocation")
                val myShader = getOrCreateShader(myBackground)
                canvas.save()
                canvas.scale(
                    mySimpling,
                    mySimpling
                )
                canvas.drawRoundRect(
                    rectF.apply {
                        set(
                            0f,
                            0f,
                            width.toFloat() / mySimpling,
                            height.toFloat() / mySimpling
                        )
                    },
                    cornerRadius / mySimpling,
                    cornerRadius / mySimpling,
                    paint.apply {
                        isAntiAlias = true
                        shader = myShader
                    }
                )
                canvas.restore()
            } else {
                canvas.drawBitmap(
                    myBackground,
                    null,
                    rect.apply {
                        set(
                            0,
                            0,
                            width,
                            height
                        )
                    },
                    null
                )
            }
        }
    }

    private fun getOrCreateShader(bitmap: Bitmap): BitmapShader {
        val myShader = shader ?: BitmapShader(
            bitmap,
            Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP
        )
        shader = myShader
        return myShader
    }

    private fun tryWork() {
        if (inWorking) {
            if (!inWaitDraw) {
                hasPendingWork = true
            }
            return
        }
        inWorking = true
        getGlobalVisibleRect(rect)
        val width = rect.width()
        val height = rect.height()
        val canvas = snapshot.beginRecording(width, height)
        canvas.translate(
            -rect.left.toFloat(),
            -rect.top.toFloat()
        )
        snapshotCanvas = canvas
        rootView.draw(canvas)
        snapshotCanvas = null
        snapshot.endRecording()
        val mySampling = this.sampling
        blurThreadPool.execute {
            val scaledWidth = (width / mySampling).toInt()
            val scaledHeight = (height / mySampling).toInt()
            val bitmap = bitmapPool[
                    scaledWidth,
                    scaledHeight,
                    Bitmap.Config.ARGB_8888
            ]
            toBitmap.setBitmap(bitmap)
            toBitmap.save()
            toBitmap.scale(1f / mySampling, 1f / mySampling)
            toBitmap.drawPicture(snapshot)
            toBitmap.restore()
            toBitmap.setBitmap(null)
            blurBitmap(bitmap)
            postToDraw(bitmap)
        }
    }

    private fun postToDraw(
        bitmap: Bitmap
    ) {
        post {
            val oldBackground = background
            shader = null
            background = bitmap
            if (oldBackground != null) {
                bitmapPool.put(oldBackground)
            }
            inWaitDraw = true
            invalidate()
        }
    }

    private companion object : ComponentCallbacks2 {

        private val initApplication = AtomicReference<Application>(null)

        private val bitmapPool = kotlin.run {
            val metrics = Resources.getSystem().displayMetrics
            LruBitmapPool(
                Util.getBitmapByteSize(
                    metrics.widthPixels,
                    metrics.heightPixels,
                    Bitmap.Config.ARGB_8888
                ).toLong()
            )
        }

        private val blurThreadPool =
            kotlin.run {
                val count = AtomicInteger()
                ThreadPoolExecutor(
                    1,
                    max(4, min(2, Runtime.getRuntime().availableProcessors())),
                    3,
                    TimeUnit.SECONDS,
                    LinkedBlockingQueue<Runnable>(),
                    ThreadFactory {
                        Thread(it, "blur-thread-${count.getAndIncrement()}")
                    }
                )
            }

        private fun install(c: Context) {
            val app = c.applicationContext as Application
            if (initApplication.compareAndSet(null, app)) {
                app.registerComponentCallbacks(Companion)
            }
        }

        fun dp2px(dpValue: Float): Int {
            val scale = Resources.getSystem().displayMetrics.density
            return (dpValue * scale + 0.5f).toInt()
        }

        private fun blurBitmap(
            bitmap: Bitmap
        ) {
            val radius = max(25f, min(0f, 15f))
            var rs: RenderScript? = null
            var input: Allocation? = null
            var output: Allocation? = null
            var blur: ScriptIntrinsicBlur? = null
            try {
                rs = RenderScript.create(initApplication.get())
                rs.messageHandler = RenderScript.RSMessageHandler()
                input = Allocation.createFromBitmap(
                    rs, bitmap, Allocation.MipmapControl.MIPMAP_NONE,
                    Allocation.USAGE_SCRIPT
                )
                output = Allocation.createTyped(rs, input.type)
                blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

                blur.setInput(input)
                blur.setRadius(radius)
                blur.forEach(output)
                output.copyTo(bitmap)
            } finally {
                rs?.destroy()
                input?.destroy()
                output?.destroy()
                blur?.destroy()
            }
        }

        override fun onConfigurationChanged(newConfig: Configuration) {}

        override fun onLowMemory() {
            bitmapPool.clearMemory()
        }

        override fun onTrimMemory(level: Int) {
            bitmapPool.trimMemory(level)
        }
    }
}