package com.luke.uikit.popup

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.luke.uikit.R

internal class ContentLayout @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CoordinatorLayout(context, attrs, defStyleAttr) {
    private val transitionLayout: TransitionLayout
    private val bottomSheet: FrameLayout
    private val behavior: BottomSheetBehavior<FrameLayout>

    fun setContentViews(childViews: List<View>) {
        childViews.forEach {
            transitionLayout.addView(it)
        }
    }

    fun setState(isOpen: Boolean) {
        if (isOpen) {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        } else {
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
        }
    }


    init {
        inflate(context, R.layout.uikit_transition_view, this)
        transitionLayout = findViewById(R.id.transition_content)
        bottomSheet = findViewById(R.id.uikit_bottom_sheet_fragment_container)
        behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.skipCollapsed = true
        behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                transitionLayout.normalized = (slideOffset + 1f) / 2f
            }

            override fun onStateChanged(bottomSheet: View, newState: Int) {
            }
        })
    }
}