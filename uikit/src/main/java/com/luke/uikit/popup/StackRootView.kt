package com.luke.uikit.popup

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.luke.uikit.R
import java.util.*
import kotlin.collections.ArrayList

class StackRootView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CoordinatorLayout(context, attrs, defStyleAttr) {

    private val stack = ArrayList<Pair<FrameLayout, FloatArray>>()
    private val path = Path()
    private val pathRectF = RectF()
    private val radius = resources.getDimensionPixelSize(R.dimen.uikit_radius)
    private val topHeight: Float

    init {
        background = ColorDrawable(Color.BLACK)
        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.actionBarSize, typedValue, true)
        topHeight = TypedValue.complexToDimensionPixelSize(
            typedValue.data, resources.displayMetrics
        ).toFloat()
    }

    fun push(view: View, layoutParams: FrameLayout.LayoutParams?) {
        val wrapper = FrameLayout(context)
        wrapper.setPadding(0, topHeight.toInt(), 0, 0)
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

    companion object : Application.ActivityLifecycleCallbacks {
        private const val xRate = 0.1f
        private const val yRate = 0.05f
        private const val scaleRate = 0.1f
        private const val alphaRate = 0.5f

        private val activities = WeakHashMap<Activity, StackRootView>()

        @Deprecated("", level = DeprecationLevel.HIDDEN)
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            val rootView = activity.window.decorView as ViewGroup
            val contentView = rootView.getChildAt(0)
            if (contentView !is StackRootView) {
                val stackRootView = StackRootView(rootView.context)
                val childView = (0 until rootView.childCount).map {
                    rootView.getChildAt(it)
                }
                rootView.removeAllViews()
                for (it in childView) {
                    stackRootView.addView(it)
                }
                rootView.addView(stackRootView)
                activities[activity] = stackRootView
            }
        }

        @Deprecated("", level = DeprecationLevel.HIDDEN)
        override fun onActivityStarted(activity: Activity) {
        }

        @Deprecated("", level = DeprecationLevel.HIDDEN)
        override fun onActivityResumed(activity: Activity) {
        }

        @Deprecated("", level = DeprecationLevel.HIDDEN)
        override fun onActivityPaused(activity: Activity) {
        }

        @Deprecated("", level = DeprecationLevel.HIDDEN)
        override fun onActivityStopped(activity: Activity) {
        }

        @Deprecated("", level = DeprecationLevel.HIDDEN)
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        }

        @Deprecated("", level = DeprecationLevel.HIDDEN)
        override fun onActivityDestroyed(activity: Activity) {
            activities.remove(activity)
        }

        fun push(
            activity: Activity,
            view: View,
            layoutParams: FrameLayout.LayoutParams? = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        ) {
            activities[activity]?.push(view, layoutParams)
        }

        fun pop(activity: Activity) {
            activities[activity]?.pop()
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
            val n = stack[cur].second[0]
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