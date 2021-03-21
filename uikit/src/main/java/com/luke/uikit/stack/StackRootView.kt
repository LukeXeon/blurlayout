package com.luke.uikit.stack

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.luke.uikit.R
import com.luke.uikit.internal.RootViews
import java.util.*

class StackRootView @JvmOverloads constructor(
    context: Context,
    val dispatcher: OnBackPressedDispatcher? = null
) : CoordinatorLayout(context) {

    internal val stack = ArrayList<StackItem>()
    private val path = Path()
    private val pathRectF = RectF()
    private val radius = resources.getDimensionPixelSize(R.dimen.uikit_radius)
    private val topHeight: Float
    private var hasTransitionRunning: Boolean = false

    init {
        background = ColorDrawable(Color.BLACK)
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.actionBarSize, typedValue, true)
        topHeight = TypedValue.complexToDimensionPixelSize(
            typedValue.data, resources.displayMetrics
        ).toFloat()
    }

    fun push(view: View, layoutParams: FrameLayout.LayoutParams?) {
        PushTransition(view, layoutParams).run()
    }

    private fun pop() {
        PopTransition().run()
    }

    internal class StackItem(
        val view: FrameLayout,
        val callback: OnBackPressedCallback? = null
    ) {
        var normalized: Float = 0f
    }

    private inner class PushTransition(
        val view: View, val layoutParams: FrameLayout.LayoutParams?
    ) : Runnable {
        override fun run() {
            if (hasTransitionRunning) {
                post(this)
                return
            }
            hasTransitionRunning = true
            val wrapper = FrameLayout(context)
            wrapper.setPadding(0, topHeight.toInt(), 0, 0)
            wrapper.addView(view, layoutParams)
            wrapper.isClickable = true
            val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            val behavior = BottomSheetBehavior<FrameLayout>()
            val item = StackItem(
                wrapper, if (dispatcher != null) {
                    object : OnBackPressedCallback(true) {
                        override fun handleOnBackPressed() {
                            pop()
                        }
                    }.apply {
                        dispatcher.addCallback(this)
                    }
                } else null
            )
            params.behavior = behavior
            behavior.skipCollapsed = true
            behavior.isHideable = true
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
            behavior.peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO
            behavior.addBottomSheetCallback(object : BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {

                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    if (slideOffset == -1f) {
                        hasTransitionRunning = false
                        if (stack.size > 0) {
                            val top = stack.removeAt(stack.size - 1)
                            top.view.removeAllViews()
                            removeView(top.view)
                        }
                    }
                    item.normalized = (slideOffset + 1) / 2
                    invalidate()
                }
            })
            behavior.addBottomSheetCallback(object : BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    if (slideOffset == 1f) {
                        hasTransitionRunning = false
                        behavior.removeBottomSheetCallback(this)
                    }
                }

                override fun onStateChanged(bottomSheet: View, newState: Int) {

                }
            })
            wrapper.bringToFront()
            stack.add(item)
            addView(wrapper, params)
            post { behavior.state = BottomSheetBehavior.STATE_EXPANDED }
        }
    }

    private inner class PopTransition : Runnable {
        override fun run() {
            if (hasTransitionRunning) {
                post(this)
                return
            }
            if (stack.size > 0) {
                hasTransitionRunning = true
                val last = stack.last()
                val behavior = BottomSheetBehavior.from(last.view)
                last.callback?.remove()
                behavior.state = BottomSheetBehavior.STATE_HIDDEN
            }
        }
    }

    private fun drawChild(
        canvas: Canvas,
        child: View,
        index: Int
    ): Boolean {
        canvas.save()
        var cur = stack.size - 1
        while (cur >= index) {
            val n = stack[cur].normalized
            val scale = 1 - n * scaleRate
            val x = xRate * width / 2 * n
            val y = yRate * height * n
            canvas.translate(x, y)
            if (cur != 0) {
                canvas.translate(0f, -topHeight * n)
            }
            canvas.scale(scale, scale)
            --cur
        }

        if (index == 0) {
            val n = if (stack.size > 1) 1f else stack[index].normalized
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
        val n = if (index == stack.size - 1) stack[index].normalized else 1f
        val alpha = (alphaRate * n * 255).toInt()
        canvas.drawARGB(alpha, 0, 0, 0)
        return result
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        if (stack.size > 0) {
            val index = stack.indexOfLast { it.view == child }
            if (index != stack.size - 1) {
                return drawChild(canvas, child, index + 1)
            }
        }
        return super.drawChild(canvas, child, drawingTime)
    }

    companion object {
        private const val xRate = 0.1f
        private const val yRate = 0.05f
        private const val scaleRate = 0.1f
        private const val alphaRate = 0.5f

        fun push(
            activity: Activity,
            view: View,
            layoutParams: FrameLayout.LayoutParams? = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        ) {
            RootViews[activity]?.push(view, layoutParams)
        }

        fun pop(activity: Activity) {
            RootViews[activity]?.pop()
        }
    }
}