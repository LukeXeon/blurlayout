package com.luke.uikit.internal

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.ViewGroup
import com.luke.uikit.stack.StackRootView
import java.util.*

internal object RootViews : Application.ActivityLifecycleCallbacks {

    val activities = WeakHashMap<Activity, StackRootView>()

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        val rootView = activity.window.decorView as ViewGroup
        val contentView = rootView.getChildAt(0)
        if (contentView !is StackRootView) {
            val stackRootView = StackRootView(rootView.context)
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

    override fun onActivityStarted(activity: Activity) {
    }

    override fun onActivityResumed(activity: Activity) {
    }

    override fun onActivityPaused(activity: Activity) {
    }

    override fun onActivityStopped(activity: Activity) {
    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
    }

    override fun onActivityDestroyed(activity: Activity) {
        activities.remove(activity)
    }
}