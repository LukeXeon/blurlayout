package com.luke.uikit.shadow

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import com.google.android.material.shape.MaterialShapeDrawable
import com.luke.uikit.R
import java.util.*

class ShadowLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), ViewTreeObserver.OnPreDrawListener {
    private val rect = Rect()
    private val shadowCache = ArrayList<MaterialShapeDrawable>()

    init {
        super.setClipChildren(false)
        setWillNotDraw(false)
    }

    override fun setClipChildren(clipChildren: Boolean) {
        super.setClipChildren(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnPreDrawListener(this)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnPreDrawListener(this)
        super.onDetachedFromWindow()
    }

    override fun onPreDraw(): Boolean {
        if (!isDirty) {
            return true
        }
        if (childCount > shadowCache.size) {
            shadowCache.ensureCapacity(childCount)
            while (childCount > shadowCache.size) {
                shadowCache.add(
                    MaterialShapeDrawable().apply {
                    shadowCompatibilityMode = MaterialShapeDrawable.SHADOW_COMPAT_MODE_ALWAYS
                    callback = this@ShadowLayout
                    fillColor = ColorStateList.valueOf(Color.TRANSPARENT)
                })
            }
        }
//        while (shadowCache.size > childCount) {
//            shadowCache.removeLast()
//        }
        shadowCache.trimToSize()
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            val drawable = shadowCache[index]
            val layoutParams = child.layoutParams
            if (layoutParams is LayoutParams) {
                child.getBoundRect(rect)
                drawable.setCornerSize(layoutParams.cornerRadius)
                drawable.elevation = layoutParams.shadowElevation
                drawable.setShadowColor(layoutParams.shadowColor)
                drawable.bounds = rect
            }
        }
        return true
    }

    private fun View.getBoundRect(rect: Rect) {
        rect.set(
            this.x.toInt(),
            this.y.toInt(),
            (this.x + this.width).toInt(),
            (this.y + this.height).toInt()
        )
    }

    override fun verifyDrawable(who: Drawable): Boolean {
        return super.verifyDrawable(who) || shadowCache.contains(who)
    }

    override fun onDraw(canvas: Canvas) {
        for (index in 0 until shadowCache.size) {
            shadowCache[index].draw(canvas)
        }
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
                        R.styleable.ShadowLayout_Layout_uikit_cornerRadius,
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