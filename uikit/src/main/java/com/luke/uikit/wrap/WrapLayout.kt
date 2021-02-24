package com.luke.uikit.wrap

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.IntRange
import androidx.annotation.Px
import com.luke.uikit.R
import kotlin.math.max
import kotlin.math.min

class WrapLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {
    var maxLines: Int = 1
        set(@IntRange(from = 1) value) {
            val v = max(1, value)
            if (v != field) {
                requestLayout()
            }
            field = v
        }

    @Px
    var horizontalSpacing: Int = 0
        set(@IntRange(from = 0) value) {
            val v = max(0, value)
            if (v != field) {
                requestLayout()
            }
            field = v
        }

    @Px
    var verticalSpacing: Int = 0
        set(@IntRange(from = 0) value) {
            val v = max(0, value)
            if (v != field) {
                requestLayout()
            }
            field = v
        }

    private val lines = ArrayList<View?>()
    private val hiddenViews = ArrayList<Pair<View, Int>>()

    init {
        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.WrapLayout)
            maxLines = typedArray.getInt(R.styleable.WrapLayout_maxLines, 1)
            horizontalSpacing =
                typedArray.getDimensionPixelSize(R.styleable.WrapLayout_horizontalSpacing, 0)
            verticalSpacing =
                typedArray.getDimensionPixelSize(R.styleable.WrapLayout_verticalSpacing, 0)
            typedArray.recycle()
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var childViewMaxHeight = 0
        var currentLineLeft = horizontalSpacing
        var currentLineTop = verticalSpacing
        for (view in lines) {
            if (view != null) {
                childViewMaxHeight = max(view.measuredHeight, childViewMaxHeight)
                view.layout(
                    currentLineLeft,
                    currentLineTop,
                    currentLineLeft + view.measuredWidth,
                    currentLineTop + view.measuredHeight
                )
                currentLineLeft += view.measuredWidth
                currentLineLeft += horizontalSpacing
            } else {
                //end a line
                currentLineTop += childViewMaxHeight
                currentLineTop += verticalSpacing
                childViewMaxHeight = 0
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        for (entry in hiddenViews) {
            if (entry.first.parent == this) {
                entry.first.visibility = entry.second
            }
        }
        lines.clear()
        hiddenViews.clear()
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        var measuredWidth = 0
        var measuredHeight = 0
        var lineCount = 0
        if (childCount > 0) {
            if (widthMode == MeasureSpec.UNSPECIFIED) {
                //不受限制，此模式下只有一行
                var childViewMaxHeight = 0
                for (index in 0 until childCount) {
                    val childView = getChildAt(index)
                    if (childView.visibility == View.GONE) {
                        continue
                    }
                    measureChild(childView, widthMeasureSpec, heightMeasureSpec)
                    val childWidth = childView.measuredWidth
                    val childHeight = childView.measuredHeight
                    measuredWidth += childWidth
                    measuredWidth += horizontalSpacing
                    childViewMaxHeight = max(childHeight, childViewMaxHeight)
                    lines.add(childView)
                }
                lines.add(null)//行尾标记
                measuredHeight = verticalSpacing * 2 + childViewMaxHeight
            } else {
                var childViewMaxHeight = 0
                var currentLineWidth = horizontalSpacing
                for (index in 0 until childCount) {
                    val childView = getChildAt(index)
                    if (lineCount == maxLines) {
                        hiddenViews.add(childView to childView.visibility)
                        childView.visibility = View.GONE
                        continue
                    }
                    if (childView.visibility == View.GONE) {
                        continue
                    }
                    measureChild(childView, widthMeasureSpec, heightMeasureSpec)
                    val childWidth = childView.measuredWidth
                    val childHeight = childView.measuredHeight
                    childViewMaxHeight = max(childHeight, childViewMaxHeight)
                    val newCurrentLineWidth = currentLineWidth + childWidth + horizontalSpacing
                    if (newCurrentLineWidth <= widthSize) {
                        currentLineWidth = newCurrentLineWidth
                        lines.add(childView)
                    } else {
                        measuredWidth = max(currentLineWidth, measuredWidth)
                        measuredHeight += childViewMaxHeight + verticalSpacing
                        lines.add(null)//行尾标记
                        lines.add(childView)
                        childViewMaxHeight = 0
                        currentLineWidth = min(widthSize, horizontalSpacing + childWidth)
                        ++lineCount
                    }
                }
                measuredHeight = verticalSpacing
            }
        }
        measuredWidth = max(paddingStart + measuredWidth + paddingEnd, suggestedMinimumWidth)
        measuredHeight = max(paddingTop + measuredHeight + paddingBottom, suggestedMinimumHeight)
        setMeasuredDimension(
            View.resolveSize(measuredWidth, widthMeasureSpec),
            View.resolveSize(measuredHeight, heightMeasureSpec)
        )
    }

}