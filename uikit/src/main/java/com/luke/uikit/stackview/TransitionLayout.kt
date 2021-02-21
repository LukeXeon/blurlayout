package com.luke.uikit.stackview

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.widget.FrameLayout
import kotlin.math.max
import kotlin.math.min
import com.luke.uikit.R


internal class TransitionLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val path = Path()
    private val pathRectF = RectF()
    private val rect = Rect()
    private val paint = Paint()

    var normalized: Float = 1f
        set(value) {
            if (value != field) {
                invalidate()
            }
            field = max(0f, min(1f, value))
        }


    override fun dispatchDraw(canvas: Canvas) {
        if (normalized == 0f) {
            super.dispatchDraw(canvas)
            return
        }
        canvas.save()
        val rate = 0.15f
        val scale = 1 - normalized * 0.15f
        val x = rate * width / 2 * normalized
        val y = 0.1f * height * normalized
        val a = (0.5f * normalized * 255).toInt()
        val r = normalized * resources.getDimension(R.dimen.uikit_radius)
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        rect.set(0, 0, width, height)
        canvas.clipPath(path.apply {
            reset()
            addRoundRect(pathRectF.apply {
                set(rect)
            }, r, r, Path.Direction.CW)
            close()
        })
        canvas.drawRect(rect, paint.apply {
            reset()
            color = Color.WHITE
        })
        super.dispatchDraw(canvas)
        canvas.restore()
        canvas.drawARGB(a, 0, 0, 0)
    }

}