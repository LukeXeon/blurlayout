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
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.core.os.HandlerCompat
import com.luke.uikit.R
import com.luke.uikit.shared.SharedBitmapPool
import com.luke.uikit.shared.SharedInitializer
import com.luke.uikit.shared.bitmapPool
import kotlin.math.max
import kotlin.math.min


class BlurLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ViewTreeObserver.OnPreDrawListener {

    @Px
    var cornerRadius: Float = 0f
        set(value) {
            field = max(0f, value)
        }

    var blurSampling: Float = 4f
        set(value) {
            field = max(1f, value)
        }

    @Px
    var blurRadius: Float = 10f
        set(value) {
            field = max(25f, min(0f, value))
        }

    @ColorInt
    var maskColor: Int = Color.TRANSPARENT

    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(
                attrs, R.styleable.BlurLayout, defStyleAttr, 0
            )
            cornerRadius = a.getDimension(R.styleable.BlurLayout_cornerRadius, 0f)
            blurSampling = a.getFloat(R.styleable.BlurLayout_blurSampling, 4f)
            blurRadius = a.getDimension(R.styleable.BlurLayout_blurRadius, 10f)
            maskColor = a.getColor(R.styleable.BlurLayout_maskColor, Color.TRANSPARENT)
            a.recycle()
        }
    }

    private fun hasOtherDirty(view: View): Boolean {
        val p = view.parent as? ViewGroup
        return if (p == null) {
            false
        } else {
            val hasOther = (0 until p.childCount).any {
                val v = p.getChildAt(it)
                v != view && v.isDirty
            }
            if (hasOther) {
                true
            } else {
                hasOtherDirty(p)
            }
        }
    }

    private inner class DrawingTask(
        val width: Int,
        val height: Int,
        val cornerRadius: Float,
        val blurSampling: Float,
        val blurRadius: Float,
        val maskColor: Int,
    ) : Runnable {

        override fun run() {
            val scaledWidth = (width / blurSampling).toInt()
            val scaledHeight = (height / blurSampling).toInt()
            val entry = bitmapPool[scaledWidth, scaledHeight]
            val bitmap = entry.bitmap
            // 在后台慢慢用软件画图来画，防止主线程卡住
            // 因为软件绘制实在是太慢了
            backgroundCanvas.setBitmap(bitmap)
            backgroundCanvas.save()
            // 放大画布来绘制
            backgroundCanvas.scale(1f / blurSampling, 1f / blurSampling)
            synchronized(recorder) {
                try {
                    backgroundCanvas.drawPicture(recorder)
                } catch (e: Exception) {
                    e.printStackTrace()
                    bitmapPool.put(entry)
                    return
                } finally {
                    backgroundCanvas.restore()
                    backgroundCanvas.setBitmap(null)
                }
            }
            processBitmap(blurRadius, bitmap)
            bitmap.prepareToDraw()
            try {
                val canvas = drawer.lockCanvas() ?: return
                draw(canvas, entry)
                drawer.unlockCanvasAndPost(canvas)
            } finally {
                bitmapPool.put(entry)
            }
        }

        private fun processBitmap(blurRadius: Float, bitmap: Bitmap) {
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

        private fun draw(canvas: Canvas, entry: SharedBitmapPool.Entry) {
            if (cornerRadius > 0) {
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
                        shader = entry.shader
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
                    entry.bitmap,
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
                    cornerRadius,
                    cornerRadius,
                    maskPaint.apply {
                        reset()
                        color = maskColor
                        isAntiAlias = true
                    }
                )
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
    private val drawer = TextureView(context)
    private val drawingThread: Handler
    private val rs = RenderScript.create(context)
    private val blur = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))

    init {
        val thread = HandlerThread(
            toString(),
            Process.THREAD_PRIORITY_DISPLAY
        )
        thread.start()
        drawingThread = Handler(thread.looper)
        drawer.isOpaque = false
        addView(drawer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private var recordingCanvas: Canvas? = null

    override fun onPreDraw(): Boolean {
        if (!isDirty || hasOtherDirty(this)) {
            getGlobalVisibleRect(visibleRect)
            val width = visibleRect.width()
            val height = visibleRect.height()
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
                    width,
                    height,
                    cornerRadius,
                    blurSampling,
                    blurRadius,
                    maskColor
                ),
                taskToken,
                0
            )
        }
        if (indexOfChild(drawer) != 0) {
            removeView(drawer)
            addView(drawer, 0)
        }
        return true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onDetachedFromWindow() {
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

