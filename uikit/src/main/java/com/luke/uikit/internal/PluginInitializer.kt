package com.luke.uikit.internal

import android.app.Application
import android.content.Context
import androidx.startup.Initializer

internal class PluginInitializer : Initializer<Unit> {

    private val plugins = arrayOf(BitmapCache, RootViews)

    override fun create(context: Context) {
        val app = context.applicationContext as Application
        for (plugin in plugins) {
            app.registerActivityLifecycleCallbacks(plugin)
            app.registerComponentCallbacks(plugin)
        }
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}