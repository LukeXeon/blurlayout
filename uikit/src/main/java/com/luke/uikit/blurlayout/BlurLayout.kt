package com.luke.uikit.blurlayout

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Process
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.annotation.Px
import androidx.annotation.WorkerThread
import androidx.core.os.HandlerCompat
import com.luke.uikit.R
import com.luke.uikit.startup.BitmapCache
import com.luke.uikit.stack.StackRootView
import kotlin.math.max
import kotlin.math.min


class BlurLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr),
    ViewTreeObserver.OnPreDrawListener {

    @Px
    var cornerRadius: Int = 0
        set(value) {
            field = max(0, value)
            invalidate()
        }

    var blurSampling: Float = 4f
        set(value) {
            field = max(1f, value)
        }

    var blurRadius: Float = 10f
        set(@FloatRange(from = 0.0, to = 25.0) value) {
            field = max(25f, min(0f, value))
        }

    @ColorInt
    var maskColor: Int = Color.TRANSPARENT
        set(value) {
            field = value
            invalidate()
        }

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(
                attrs, R.styleable.BlurLayout, defStyleAttr, 0
            )
            cornerRadius = a.getDimensionPixelSize(R.styleable.BlurLayout_uikit_cornerRadius, 0)
            blurSampling = a.getFloat(R.styleable.BlurLayout_uikit_blurSampling, 4f)
            blurRadius = a.getFloat(R.styleable.BlurLayout_uikit_blurRadius, 10f)
            maskColor = a.getColor(R.styleable.BlurLayout_uikit_maskColor, Color.TRANSPARENT)
            a.recycle()
        }
        setWillNotDraw(false)
    }

    private inner class DrawingTask(
        val scaledWidth: Int,
        val scaledHeight: Int,
        val blurSampling: Float,
        val blurRadius: Float,
    ) : Runnable {

        override fun run() {
            val item = BitmapCache.pool[scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888]
            // 在后台慢慢用软件画图来画，防止主线程卡住
            // 因为软件绘制实在是太慢了
            backgroundCanvas.setBitmap(item)
            backgroundCanvas.save()
            // 放大画布来绘制
            backgroundCanvas.scale(1f / blurSampling, 1f / blurSampling)
            synchronized(recorder) {
                try {
                    backgroundCanvas.drawPicture(recorder)
                } catch (e: Exception) {
                    e.printStackTrace()
                    BitmapCache.pool.put(item)
                    return
                } finally {
                    backgroundCanvas.restore()
                    backgroundCanvas.setBitmap(null)
                }
            }
            processBitmap(item)
            item.prepareToDraw()
            post {
                clearBackground()
                background = item
            }
        }

        @WorkerThread
        private fun processBitmap(bitmap: Bitmap) {
            val radius = max(25f, min(0f, blurRadius))
            var input: Allocation? = null
            var output: Allocation? = null
            try {
                input = Allocation.createFromBitmap(
                    rs, bitmap, Allocation.MipmapControl.MIPMAP_NONE,
                    Allocation.USAGE_SCRIPT
                )
                output = Allocation.createTyped(rs, input.type)
                blur.setInput(input)
                blur.setRadius(radius)
                blur.forEach(output)
                output.copyTo(bitmap)
            } finally {
                input?.destroy()
                output?.destroy()
            }
        }
    }

    private val taskToken = Any()
    private val recorder = Picture()
    private val backgroundCanvas = Canvas()
    private val visibleRect = Rect()
    private val bitmapPaint = Paint()
    private val maskPaint = Paint()
    private val drawingRectF = RectF()
    private val drawingRect = Rect()
    private val drawingThread: Handler
    private val rs = RenderScript.create(context)
    private val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

    private var backgroundShader: BitmapShader? = null
    private var background: Bitmap? = null
        set(value) {
            field = value
            invalidate()
        }
    private var recordingCanvas: Canvas? = null
    private var isPaused: Boolean = false

    init {
        val thread = HandlerThread(
            toString(),
            Process.THREAD_PRIORITY_DISPLAY
        )
        thread.start()
        drawingThread = Handler(thread.looper)
    }

    fun onPause() {
        clearBackground()
        isPaused = true
    }

    fun onResume() {
        clearBackground()
        isPaused = false
    }

    private fun clearBackground() {
        backgroundShader = null
        val old = background
        if (old != null) {
            BitmapCache.pool.put(old)
        }
        background = null
    }

    private fun checkDraw(): Boolean {
        if (isPaused) {
            return false
        }

        if (!isDirty) {
            return StackRootView.checkTop(this)
        }
        var c: View? = this
        var p: ViewGroup? = this.parent as? ViewGroup
        while (p != null && p !is StackRootView) {
            for (index in 0 until p.childCount) {
                val v = p.getChildAt(index)
                if (v != c && v.isDirty) {
                    return StackRootView.checkTop(p)
                }
            }
            c = p
            p = p.parent as? ViewGroup
        }
        return false
    }

    override fun onPreDraw(): Boolean {
        if (checkDraw()) {
            getGlobalVisibleRect(visibleRect)
            val width = visibleRect.width()
            val height = visibleRect.height()
            val blurSampling = blurSampling
            val scaledWidth = (width / blurSampling).toInt()
            val scaledHeight = (height / blurSampling).toInt()
            if (scaledWidth * scaledHeight == 0) {
                return true
            }
            synchronized(recorder) {
                // 使用Picture来记录绘制内容
                // 因为它只记录绘制的操作，所以这比直接用Canvas要更快
                // 不需要绘制整个屏幕，只需要绘制View底下那一层就可以了
                val canvas = recorder.beginRecording(width, height)
                // 转换canvas来到View的绝对位置
                canvas.translate(
                    -visibleRect.left.toFloat(),
                    -visibleRect.top.toFloat()
                )
                // 设置recordingCanvas用来识别，防止画到自己
                recordingCanvas = canvas
                rootView.draw(canvas)
                recordingCanvas = null
                // 结束录制
                recorder.endRecording()
            }
            drawingThread.removeCallbacksAndMessages(taskToken)
            HandlerCompat.postDelayed(
                drawingThread, DrawingTask(
                    scaledWidth,
                    scaledHeight,
                    blurSampling,
                    blurRadius,
                ),
                taskToken,
                0
            )
        }
        return true
    }

    private fun doDraw(canvas: Canvas, item: Bitmap) {
        if (cornerRadius > 0) {
            if (backgroundShader == null) {
                backgroundShader = BitmapShader(
                    item,
                    Shader.TileMode.MIRROR,
                    Shader.TileMode.MIRROR
                )
            }
            val backgroundShader = backgroundShader ?: return
            canvas.save()
            // 经过渲染的Bitmap由于缩放的关系
            // 可能会比View小，所以要做特殊处理，把它放大回去
            canvas.scale(
                blurSampling,
                blurSampling
            )
            canvas.drawRoundRect(
                drawingRectF.apply {
                    set(
                        0f,
                        0f,
                        width.toFloat() / blurSampling,
                        height.toFloat() / blurSampling
                    )
                },
                cornerRadius / blurSampling,
                cornerRadius / blurSampling,
                bitmapPaint.apply {
                    reset()
                    isFilterBitmap = true
                    isAntiAlias = true
                    shader = backgroundShader
                }
            )
            canvas.drawRoundRect(
                drawingRectF.apply {
                    set(
                        0f,
                        0f,
                        width.toFloat() / blurSampling,
                        height.toFloat() / blurSampling
                    )
                },
                cornerRadius / blurSampling,
                cornerRadius / blurSampling,
                maskPaint.apply {
                    reset()
                    color = maskColor
                    isAntiAlias = true
                }
            )
            canvas.restore()
        } else {
            canvas.drawBitmap(
                item,
                null,
                drawingRect.apply {
                    set(
                        0,
                        0,
                        width,
                        height
                    )
                },
                bitmapPaint.apply {
                    reset()
                    isFilterBitmap = true
                    isAntiAlias = true
                }
            )
            canvas.drawRoundRect(
                drawingRectF.apply {
                    set(
                        0f,
                        0f,
                        width.toFloat(),
                        height.toFloat()
                    )
                },
                cornerRadius.toFloat(),
                cornerRadius.toFloat(),
                maskPaint.apply {
                    reset()
                    color = maskColor
                    isAntiAlias = true
                }
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (canvas != recordingCanvas) {
            doDraw(canvas, background ?: return)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        clearBackground()
        viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onDetachedFromWindow() {
        clearBackground()
        viewTreeObserver.removeOnPreDrawListener(this)
        drawingThread.removeCallbacksAndMessages(taskToken)
        super.onDetachedFromWindow()
    }

    override fun dispatchDraw(canvas: Canvas?) {
        if (canvas != recordingCanvas) {
            super.dispatchDraw(canvas)
        }
    }
}

