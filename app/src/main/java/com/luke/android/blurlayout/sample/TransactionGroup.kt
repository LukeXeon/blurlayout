package com.luke.android.blurlayout.sample

import android.content.Context
import android.util.AttributeSet
import androidx.constraintlayout.widget.Group
import androidx.core.view.ViewCompat

class TransactionGroup @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : Group(context, attrs, defStyleAttr) {

    override fun setTranslationX(translationX: Float) {
        super.setTranslationX(translationX)
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
    }
}