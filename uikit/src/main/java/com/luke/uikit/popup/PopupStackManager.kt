package com.luke.uikit.popup

import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.luke.uikit.R
import com.luke.uikit.shared.rootViews

object PopupStackManager {

    fun push(
        activity: AppCompatActivity,
        view: View,
        layoutParams: FrameLayout.LayoutParams? = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    ) {
        rootViews[activity]?.push(view, layoutParams)
    }

    fun pop(activity: AppCompatActivity) {
        rootViews[activity]?.pop()
    }
}