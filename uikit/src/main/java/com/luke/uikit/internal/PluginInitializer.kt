package com.luke.uikit.internal

import android.app.Application
import android.content.Context
import androidx.startup.Initializer

internal abstract class PluginInitializer<T : Plugin> : Initializer<T> {

    abstract fun create(context: Application): T

    final override fun create(context: Context): T {
        val app = context.applicationContext as Application
        val plugin = create(app)
        app.registerActivityLifecycleCallbacks(plugin)
        app.registerComponentCallbacks(plugin)
        return plugin
    }

    override fun dependencies(): MutableList<Class<out Initializer<*>>> {
        return mutableListOf()
    }
}