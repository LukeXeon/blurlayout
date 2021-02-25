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
        var currentLineHeight = 0
        var currentLineLeft = horizontalSpacing
        var currentLineTop = verticalSpacing
        for (view in lines) {
            if (view != null) {
                currentLineHeight = max(view.measuredHeight, currentLineHeight)
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
                currentLineTop += currentLineHeight
                currentLineTop += verticalSpacing
                currentLineLeft = horizontalSpacing
                currentLineHeight = 0
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
                //插入行尾标记
                lines.add(null)
                measuredHeight = verticalSpacing * 2 + childViewMaxHeight
            } else {
                var currentLineHeight = 0
                var currentLineWidth = horizontalSpacing
                for (index in 0 until childCount) {
                    val childView = getChildAt(index)
                    if (childView.visibility == View.GONE) {
                        //这种case可能会缺少换行，在最后进行检查
                        continue
                    }
                    if (lineCount == maxLines) {
                        hiddenViews.add(childView to childView.visibility)
                        childView.visibility = View.GONE
                        //这种case可能会缺少换行，在最后进行检查
                        continue
                    }
                    measureChild(childView, widthMeasureSpec, heightMeasureSpec)
                    val childWidth = childView.measuredWidth
                    val childHeight = childView.measuredHeight
                    val newCurrentLineWidth = currentLineWidth + childWidth + horizontalSpacing
                    //判断是否需要换行
                    if (newCurrentLineWidth <= widthSize) {
                        currentLineWidth = newCurrentLineWidth
                        currentLineHeight = max(childHeight, currentLineHeight)
                        lines.add(childView)
                    } else {
                        ++lineCount
                        //插入行尾标记
                        lines.add(null)
                        //更新总宽
                        measuredWidth = max(currentLineWidth, measuredWidth)
                        //更新总高
                        measuredHeight += currentLineHeight + verticalSpacing
                        //判断是否是最后一行
                        if (lineCount != maxLines) {
                            //不是最后一行，上一行塞不下的最后一个View成为下一行的第一个View
                            lines.add(childView)
                            //下一行的起始宽
                            currentLineWidth = min(widthSize, horizontalSpacing + childWidth)
                            //下一行的起始高
                            currentLineHeight = childHeight
                        }

                    }
                }
                //检查是否换行
                if (lines.size > 0 && lines.last() != null) {
                    //插入行尾标记
                    lines.add(null)
                    //更新总宽
                    measuredWidth = maxOf(currentLineWidth, measuredWidth, horizontalSpacing * 2)
                    //更新总高
                    if (currentLineHeight > 0) {
                        measuredHeight += currentLineHeight + verticalSpacing
                    }
                }
                //最后加一个spacing上下对齐
                measuredHeight += verticalSpacing
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