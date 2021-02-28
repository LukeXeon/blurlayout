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

    private val stack = ArrayList<Pair<FrameLayout, FloatArray>>()
    private val path = Path()
    private val pathRectF = RectF()
    private val radius = resources.getDimensionPixelSize(R.dimen.uikit_radius)
    private val marginTop: Float

    init {
        background = ColorDrawable(Color.BLACK)
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.actionBarSize, typedValue, true)
        marginTop = TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics).toFloat()
    }

    fun push(view: View, layoutParams: FrameLayout.LayoutParams?) {
        val wrapper = FrameLayout(context)
        wrapper.setPadding(0,marginTop.toInt(),0,0)
        wrapper.addView(view, layoutParams)
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        val normalized = floatArrayOf(0f)
        val behavior = BottomSheetBehavior<FrameLayout>().apply {
            skipCollapsed = true
            isHideable = true
            state = BottomSheetBehavior.STATE_HIDDEN
            peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO
            addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    normalized[0] = (slideOffset + 1) / 2
                    invalidate()
                }

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                        pop()
                    }
                }
            })
        }
        lp.behavior = behavior
        addView(wrapper, lp)
        wrapper.bringToFront()
        stack.add(wrapper to normalized)
        post { behavior.state = BottomSheetBehavior.STATE_EXPANDED }
    }

    fun pop() {
        if (stack.size > 0) {
            val top = stack.removeAt(stack.size - 1)
            top.first.removeAllViews()
            removeView(top.first)
        }
    }

    private companion object {
        private const val xRate = 0.1f
        private const val yRate = 0.05f
        private const val scaleRate = 0.1f
        private const val alphaRate = 0.5f
    }

    private fun drawChild(
        canvas: Canvas,
        child: View,
        index: Int
    ): Boolean {
        canvas.save()
        var cur = stack.size - 1
        while (cur >= index) {
            val n = stack[cur].second[0]
            val scale = 1 - n * scaleRate
            val x = xRate * width / 2 * n
            val y = yRate * height * n
            canvas.translate(x, y)
            if (cur != 0) {
                canvas.translate(0f, -marginTop * n)
            }
            canvas.scale(scale, scale)
            --cur
        }

        if (index == 0) {
            val n = if (stack.size > 1) 1f else stack[index].second[0]
            val radius = n * this.radius
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
        val n = if (index == stack.size - 1) stack[index].second[0] else 1f
        val alpha = (alphaRate * n * 255).toInt()
        canvas.drawARGB(alpha, 0, 0, 0)
        return result
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (stack.size > 0) {
            val index = stack.indexOfLast { it.first == child }
            if (index != stack.size - 1) {
                return drawChild(canvas, child, index + 1)
            }
        }
        return super.drawChild(canvas, child, drawingTime)
    }
}