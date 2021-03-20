package com.luke.uikit.internal

import android.app.Activity
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.luke.uikit.stack.StackRootView
import java.util.*

internal object RootViews : Plugin() {

    val activities = WeakHashMap<Activity, StackRootView>()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        val rootView = activity.window.decorView as ViewGroup
        val contentView = rootView.getChildAt(0)
        if (contentView !is StackRootView) {
            val stackRootView = StackRootView(
                rootView.context,
                if (activity is AppCompatActivity) activity.onBackPressedDispatcher else null
            )
            val childView = (0 until rootView.childCount).map {
                rootView.getChildAt(it)
            }
            rootView.removeAllViews()
            for (it in childView) {
                stackRootView.addView(it)
            }
            rootView.addView(stackRootView)
            activities[activity] = stackRootView

        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        activities.remove(activity)
    }
}