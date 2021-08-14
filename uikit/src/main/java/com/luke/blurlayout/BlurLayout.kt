package com.luke.blurlayout

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout


class BlurLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val delegate = BlurView(context, attrs, defStyleAttr)

    var cornerRadius: Float
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
        addView(delegate)
    }
}
