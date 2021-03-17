package com.luke.uikit.internal

import android.view.View
import android.view.ViewGroup
import com.luke.uikit.stack.StackRootView

internal fun isStackRootEmpty(view: View): Boolean {
    var v: View? = view
    while (v != null) {
        if (v is StackRootView) {
            return v.isStackEmpty
        }
        v = v.parent as? View
    }
    return true
}

internal fun hasOtherDirty(view: View): Boolean {
    val p = view.parent as? ViewGroup
    return if (p == null) {
        false
    } else {
        val hasOther = (0 until p.childCount).any {
            val v = p.getChildAt(it)
            v != view && v.isDirty
        }
        if (hasOther) {
            true
        } else {
            hasOtherDirty(p)
        }
    }
}