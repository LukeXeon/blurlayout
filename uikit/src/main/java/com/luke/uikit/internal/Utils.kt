package com.luke.uikit.internal

import android.view.View
import android.view.ViewGroup
import com.luke.uikit.stack.StackRootView

internal fun isStackTop(view: View): Boolean {
    var c: View? = null
    var p: View? = view
    while (p != null) {
        if (p is StackRootView) {
            return if (p.stack.isEmpty()) {
                true
            } else {
                p.stack.last() == c
            }
        }
        c = p
        p = p.parent as? View
    }
    return true
}

internal fun hasOtherDirty(view: View): Boolean {
    var c: View? = view
    var p: ViewGroup? = view.parent as? ViewGroup
    while (p != null) {
        for (index in 0 until p.childCount) {
            val v = p.getChildAt(index)
            if (v != c && v.isDirty) {
                return true
            }
        }
        c = p
        p = p.parent as? ViewGroup
    }
    return false
}