package com.luke.uikit.shared

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import com.luke.uikit.popup.StackRootView
import java.util.*

internal class SharedRootViews : Application.ActivityLifecycleCallbacks {
    private val activities = WeakHashMap<AppCompatActivity, StackRootView>()

    operator fun get(activity: AppCompatActivity): StackRootView? = activities[activity]

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is AppCompatActivity) {
            return
        }
        val rootView = activity.window.decorView as ViewGroup
        val contentView = rootView.getChildAt(0)
        if (contentView !is StackRootView) {
            val newContentView = StackRootView(rootView.context)
            val childView = (0 until rootView.childCount).map {
                rootView.getChildAt(it)
            }
            rootView.removeAllViews()
            childView.forEach {
                newContentView.addView(it)
            }
            rootView.addView(newContentView)
            activities[activity] = newContentView
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