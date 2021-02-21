package com.luke.drawerlayout

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior

internal class ContentLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CoordinatorLayout(context, attrs, defStyleAttr) {
    private val transitionLayout: TransitionLayout
    private val bottomSheet: FrameLayout

    fun setContentViews(childViews: List<View>) {
        childViews.forEach {
            transitionLayout.addView(it)
        }
    }

    init {
        inflate(context, R.layout.uikit_transition_view, this)
        transitionLayout = findViewById(R.id.transition_content)
        bottomSheet = findViewById(R.id.bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.peekHeight = Int.MAX_VALUE
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                transitionLayout.normalized = (slideOffset + 1f) / 2f
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
            }
        })
    }
}