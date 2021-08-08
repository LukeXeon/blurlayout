package com.luke.uikit.blurlayout

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.luke.uikit.R


class BlurLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val delegate = BlurViewDelegate.attach(this)

    var cornerRadius: Int
        get() = delegate.cornerRadius
        set(value) {
            delegate.cornerRadius = value
        }

    var blurSampling: Float
        get() = delegate.blurSampling
        set(value) {
            delegate.blurSampling = value
        }

    var blurRadius: Float
        get() = delegate.blurRadius
        set(value) {
            delegate.blurRadius = value
        }


    init {
        if (attrs != null) {
            val a = context.obtainStyledAttributes(
                attrs, R.styleable.BlurLayout, defStyleAttr, 0
            )
            cornerRadius = a.getDimensionPixelSize(R.styleable.BlurLayout_uikit_cornerRadius, 0)
            blurSampling = a.getFloat(R.styleable.BlurLayout_uikit_blurSampling, 4f)
            blurRadius = a.getFloat(R.styleable.BlurLayout_uikit_blurRadius, 10f)
            a.recycle()
        }

    }
}
