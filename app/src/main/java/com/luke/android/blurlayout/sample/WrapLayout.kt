package com.luke.android.blurlayout.sample

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.annotation.IntDef
import androidx.annotation.IntRange
import java.lang.IllegalStateException
import kotlin.math.max


class WrapLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    var baselineAligned: Boolean = false

    @IntRange(from = 1)
    var maxLines: Int = 1
        set(value) {
            field = max(1, value)
        }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {

    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        var currentLineWidth = 0
        var currentLineHeight = 0
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (v.visibility == GONE) {
                continue
            }
            val lp = v.layoutParams as LayoutParams
            if (lp.width == MATCH_PARENT || lp.height == MATCH_PARENT) {
                throw IllegalStateException()
            }
            measureChildWithMargins(
                v,
                widthMeasureSpec,
                widthSize - currentLineWidth,
                heightMeasureSpec,
                heightSize - currentLineHeight
            )
            val childWidth = v.measuredWidth

        }
    }

    override fun generateDefaultLayoutParams(): ViewGroup.LayoutParams {
        return super.generateDefaultLayoutParams()
    }

    override fun generateLayoutParams(attrs: AttributeSet?): ViewGroup.LayoutParams {
        return super.generateLayoutParams(attrs)
    }

    class LayoutParams(c: Context?, attrs: AttributeSet?) : MarginLayoutParams(c, attrs) {

        @SupportGravity
        var gravity: Int = Gravity.CENTER_VERTICAL
            set(value) {
                field = if (value == Gravity.TOP
                    || value == Gravity.CENTER_VERTICAL
                    || value == Gravity.BOTTOM
                ) {
                    value
                } else {
                    Gravity.CENTER_VERTICAL
                }
            }

        @IntDef(Gravity.TOP, Gravity.CENTER_VERTICAL, Gravity.BOTTOM)
        @Retention(AnnotationRetention.SOURCE)
        annotation class SupportGravity
    }
}