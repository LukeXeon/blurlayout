package com.luke.uikit.popup

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.luke.uikit.R
import kotlin.math.max
import kotlin.math.min

internal class StackRootView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CoordinatorLayout(context, attrs, defStyleAttr) {

    private val stack = ArrayList<FrameLayout>()
    private val path = Path()
    private val pathRectF = RectF()
    private val rect = Rect()
    private val paint = Paint()
    private val radius = resources.getDimension(R.dimen.uikit_radius)
    private val marginTop: Int

    init {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.actionBarSize, typedValue, true)
        marginTop = typedValue.getDimension(resources.displayMetrics).toInt()
    }

    private var normalized: Float = 0f
        set(value) {
            if (value != field) {
                invalidate()
            }
            field = max(0f, min(1f, value))
        }


    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        val index = stack.indexOf(child)
        return drawChild(
            canvas,
            child,
            drawingTime,
            index
        )
    }

    private fun drawChild(
        canvas: Canvas,
        child: View,
        drawingTime: Long,
        index: Int
    ): Boolean {
        canvas.save()
        val normalized = if (index == stack.size - 1) this.normalized else 1f
        val scale = 1 - normalized * 0.1f
        val alpha = (0.5f * normalized * 255).toInt()
        val x = 0.1f * width / 2 * normalized
        val y = 0.05f * height * normalized
        repeat(stack.size - (index + 1)) {
            canvas.translate(x, y)
            canvas.scale(scale, scale)
        }
        rect.set(0, 0, width, height)
        if (index == -1) {
            val r = normalized * radius
            canvas.clipPath(path.apply {
                reset()
                addRoundRect(pathRectF.apply {
                    set(rect)
                }, r, r, Path.Direction.CW)
                close()
            })
        }
        canvas.drawRect(rect, paint.apply {
            reset()
            color = Color.WHITE
        })
        val result = super.drawChild(canvas, child, drawingTime)
        canvas.restore()
        if (index != -1) {
            canvas.drawARGB(alpha, 0, 0, 0)
        }
        return result
    }
}