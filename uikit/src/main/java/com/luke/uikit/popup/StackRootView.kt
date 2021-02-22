package com.luke.uikit.popup

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.luke.uikit.R
import kotlin.math.max
import kotlin.math.min

internal class StackRootView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CoordinatorLayout(context, attrs, defStyleAttr) {

    private val stack = ArrayList<FrameLayout>()
    private val callback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            normalized = slideOffset
        }

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                pop()
            }
        }
    }
    private val path = Path()
    private val pathRectF = RectF()
    private val radius = resources.getDimension(R.dimen.uikit_radius)
    private val marginTop: Float

    init {
        background = ColorDrawable(Color.BLACK)
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.actionBarSize, typedValue, true)
        marginTop = typedValue.getDimension(resources.displayMetrics)
    }

    private var normalized: Float = 0f
        set(value) {
            if (value != field) {
                invalidate()
            }
            field = max(0f, min(1f, value))
        }

    fun push(view: View, layoutParams: FrameLayout.LayoutParams?) {
        val wrapper = FrameLayout(context)
        wrapper.addView(view, layoutParams)
        wrapper.background = ContextCompat.getDrawable(
            context,
            R.drawable.uikit_bottom_sheet_background
        )
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        val behavior = BottomSheetBehavior<FrameLayout>().apply {
            skipCollapsed = true
            isHideable = true
            state = BottomSheetBehavior.STATE_HIDDEN
        }
        lp.behavior = behavior
        lp.topMargin = marginTop.toInt()
        addView(wrapper, lp)
        wrapper.bringToFront()
        if (stack.size > 0) {
            BottomSheetBehavior.from(stack.last())
                .removeBottomSheetCallback(callback)
        }
        stack.add(wrapper)
        behavior.addBottomSheetCallback(callback)
        post { behavior.state = BottomSheetBehavior.STATE_EXPANDED }
    }

    fun pop() {
        if (stack.size > 0) {
            val top = stack.removeAt(stack.size - 1)
            BottomSheetBehavior.from(top)
                .removeBottomSheetCallback(callback)
            top.removeAllViews()
            removeView(top)
            if (stack.size > 0) {
                BottomSheetBehavior.from(stack.last())
                    .addBottomSheetCallback(callback)
            }
            normalized = 1f
        }
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        val index = stack.indexOf(child)
        if (index == stack.size - 1) {
            return super.drawChild(canvas, child, drawingTime)
        }
        canvas.save()
        //top
        val marginTop = if (stack.size >= 1 && index == stack.size - 2) {
            this.marginTop * stack.size - 1 * this.normalized
        } else {
            0f
        }
        val scale = 1 - normalized * 0.1f
        val x = 0.1f * width / 2 * normalized
        val y = 0.05f * height * normalized - 0f
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        val innerX = 0.1f * width / 2
        val innerY = 0.05f * height
        val innerScale = 0.9f
        //inner
        repeat(11) {
            canvas.translate(innerX, innerY)
            canvas.scale(innerScale, innerScale)
        }
        if (index == -1) {
            val radius = normalized * this.radius
            canvas.clipPath(path.apply {
                reset()
                addRoundRect(pathRectF.apply {
                    set(0f, 0f, width.toFloat(), height.toFloat())
                }, radius, radius, Path.Direction.CW)
                close()
            })
        }
        val result = super.drawChild(canvas, child, drawingTime)
        canvas.restore()
        val normalized = if (index == stack.size - 2) {
            this.normalized
        } else {
            1f
        }
        val alpha = (0.5f * normalized * 255).toInt()
        canvas.drawARGB(alpha, 0, 0, 0)
        return result
    }
}