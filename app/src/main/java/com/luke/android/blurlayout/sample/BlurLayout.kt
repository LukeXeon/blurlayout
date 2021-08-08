package com.luke.android.blurlayout.sample

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.FloatRange
import androidx.annotation.Px
import kotlin.math.max
import kotlin.math.min


class BlurLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {


    @Px
    @Volatile
    var cornerRadius: Int = 0
        set(value) {
            field = max(0, value)
        }

    @Volatile
    var blurSampling: Float = 4f
        set(value) {
            field = max(1f, value)
        }

    @Volatile
    var blurRadius: Float = 10f
        set(@FloatRange(from = 0.0, to = 25.0) value) {
            field = max(25f, min(0f, value))
        }


    init {
    }


    companion object {

    }
}