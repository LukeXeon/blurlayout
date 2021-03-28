package com.luke.uikit.internal

import android.app.Application
import androidx.annotation.Keep

@Keep
internal class RootViewsInitializer : PluginInitializer<RootViews>() {
    override fun create(context: Application): RootViews {
        return RootViews
    }
}
