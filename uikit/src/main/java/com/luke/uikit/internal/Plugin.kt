package com.luke.uikit.internal

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle

internal abstract class Plugin : Application.ActivityLifecycleCallbacks, ComponentCallbacks2 {
    override fun onActivityPaused(activity: Activity) {

    }

    override fun onActivityStarted(activity: Activity) {

    }

    override fun onActivityDestroyed(activity: Activity) {

    }

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {

    }

    override fun onActivityStopped(activity: Activity) {

    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {

    }

    override fun onActivityResumed(activity: Activity) {

    }

    override fun onLowMemory() {

    }

    override fun onConfigurationChanged(newConfig: Configuration) {

    }

    override fun onTrimMemory(level: Int) {

    }
}