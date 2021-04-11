package com.luke.uikit.startup

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.startup.Initializer
import com.luke.uikit.stack.StackRootView
import com.luke.uikit.utils.ActivityLifecycleObserver
import java.util.*

@Keep
internal class StackRootViews : Initializer<Unit> {

    override fun create(context: Context) {
        (context.applicationContext as Application)
            .registerActivityLifecycleCallbacks(Companion)
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }


    companion object : ActivityLifecycleObserver() {

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            val rootView = activity.window.decorView as ViewGroup
            val contentView = rootView.getChildAt(0)
            if (contentView !is StackRootView) {
                val stackRootView = StackRootView(
                    rootView.context
                )
                if (activity is AppCompatActivity) {
                    stackRootView.dispatcher = activity.onBackPressedDispatcher
                }
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

        private val activities = WeakHashMap<Activity, StackRootView>()

        @JvmStatic
        operator fun get(activity: Activity): StackRootView? {
            return activities[activity]
        }
    }
}