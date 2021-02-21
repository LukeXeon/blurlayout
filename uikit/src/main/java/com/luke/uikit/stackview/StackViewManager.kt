package com.luke.uikit.stackview

import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.luke.uikit.R
import com.luke.uikit.shared.contentLayouts

object StackViewManager {
    fun show(activity: AppCompatActivity, fragment: Fragment) {
        val view = contentLayouts[activity]
        if (view != null) {
            val fm = activity.supportFragmentManager
            fm.beginTransaction()
                .add(R.id.uikit_bottom_sheet_fragment_container, fragment)
                .runOnCommit {
                    view.setState(true)
                }
                .commit()
        }
    }

    fun hide(activity: AppCompatActivity) {
        val view = contentLayouts[activity]
        val fm = activity.supportFragmentManager
        val fragment = fm.findFragmentById(R.id.uikit_bottom_sheet_fragment_container)
        if (view != null && fragment != null) {
            fm.beginTransaction()
                .remove(fragment)
                .runOnCommit{
                    view.setState(false)
                }
                .commit()
        }
    }
}