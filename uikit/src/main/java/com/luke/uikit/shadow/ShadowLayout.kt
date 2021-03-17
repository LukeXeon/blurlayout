package com.luke.uikit.shadow

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import com.google.android.material.shape.MaterialShapeDrawable
import com.luke.uikit.R
import java.util.*

class ShadowLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
    private val rect = Rect()
    private val drawableCache = ArrayList<MaterialShapeDrawable>()

    init {
        super.setClipChildren(false)
    }

    override fun setClipChildren(clipChildren: Boolean) {
        super.setClipChildren(false)
    }

    private fun updateCache() {
        if (childCount > drawableCache.size) {
            drawableCache.ensureCapacity(childCount)
            while (childCount > drawableCache.size) {
                drawableCache.add(MaterialShapeDrawable().apply {
                    shadowCompatibilityMode = MaterialShapeDrawable.SHADOW_COMPAT_MODE_ALWAYS
                    callback = this@ShadowLayout
                    fillColor = ColorStateList.valueOf(Color.TRANSPARENT)
                })
            }
        }
        while (drawableCache.size > childCount) {
            drawableCache.removeLast()
        }
        drawableCache.trimToSize()
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val drawable = drawableCache[index]
            val layoutParams = child.layoutParams
            if (layoutParams is LayoutParams) {
                child.getHitRect(rect)
                drawable.setCornerSize(layoutParams.cornerRadius)
                drawable.elevation = layoutParams.shadowElevation
                drawable.setShadowColor(layoutParams.shadowColor)
                drawable.bounds = rect
            }
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateCache()
    }

    override fun dispatchDraw(canvas: Canvas) {
        for (index in 0 until drawableCache.size) {
            drawableCache[index].draw(canvas)
        }
        super.dispatchDraw(canvas)
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return LayoutParams(context, attrs)
    }

    override fun generateLayoutParams(lp: ViewGroup.LayoutParams): LayoutParams {
        return LayoutParams(lp)
    }

    override fun generateDefaultLayoutParams(): LayoutParams {
        return LayoutParams(MATCH_PARENT, MATCH_PARENT)
    }

    class LayoutParams : FrameLayout.LayoutParams {

        constructor(source: ViewGroup.LayoutParams) : super(source)

        constructor(c: Context, attrs: AttributeSet?) : super(c, attrs) {
            if (attrs != null) {
                val array = c.obtainStyledAttributes(attrs, R.styleable.ShadowLayout_Layout)
                shadowElevation =
                    array.getDimensionPixelSize(
                        R.styleable.ShadowLayout_Layout_uikit_shadowElevation,
                        0
                    ).toFloat()
                cornerRadius =
                    array.getDimensionPixelSize(
                        R.styleable.ShadowLayout_Layout_uikit_shadowCornerRadius,
                        0
                    ).toFloat()
                shadowColor =
                    array.getColor(R.styleable.ShadowLayout_Layout_uikit_shadowColor, Color.GRAY)
                array.recycle()
            }
        }

        constructor(width: Int, height: Int) : super(width, height)

        var shadowElevation: Float = 0f
        var cornerRadius: Float = 0f
        var shadowColor: Int = Color.GRAY
    }
}