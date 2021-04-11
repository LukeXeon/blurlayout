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
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.res.ResourcesCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback
import com.luke.uikit.R
import com.luke.uikit.internal.StackRootViews
import java.util.*

class StackRootView(
    context: Context
) : CoordinatorLayout(context) {

    private val stack = ArrayList<StackItem>()
    private val path = Path()
    private val pathRectF = RectF()
    private val radius = resources.getDimensionPixelSize(R.dimen.uikit_radius)
    private val topHeight: Float
    private var hasTransitionRunning: Boolean = false

    var dispatcher: OnBackPressedDispatcher? = null

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
            val dispatcher = dispatcher
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
            val scale =
                1 - n * ResourcesCompat.getFloat(
                    resources, R.dimen.uikit_stack_layout_scale_rate
                )
            val x = ResourcesCompat.getFloat(
                resources, R.dimen.uikit_stack_layout_x_rate
            ) * width / 2 * n
            val y = ResourcesCompat.getFloat(
                resources, R.dimen.uikit_stack_layout_y_rate
            ) * height * n
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
        val alpha = (ResourcesCompat.getFloat(
            resources, R.dimen.uikit_stack_layout_alpha_rate
        ) * n * 255).toInt()
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
        fun push(
            activity: Activity,
            view: View,
            layoutParams: FrameLayout.LayoutParams? = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        ) {
            StackRootViews[activity]?.push(view, layoutParams)
        }

        fun pop(activity: Activity) {
            StackRootViews[activity]?.pop()
        }

        internal fun checkStackTop(v: View): Boolean {
            var c2: View = v
            var p2: ViewGroup? = v.parent as? ViewGroup
            while (p2 != null && p2 !is StackRootView) {
                c2 = p2
                p2 = p2.parent as? ViewGroup
            }
            return if (p2 is StackRootView) {
                if (p2.stack.isNotEmpty()) {
                    p2.stack.last().view == c2
                } else {
                    true
                }
            } else {
                true
            }
        }
    }
}