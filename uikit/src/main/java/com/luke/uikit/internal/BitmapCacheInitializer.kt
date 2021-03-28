package com.luke.uikit.internal

import android.app.Application
import androidx.annotation.Keep

@Keep
internal class BitmapCacheInitializer : PluginInitializer<BitmapCache>() {
    override fun create(context: Application): BitmapCache {
        return BitmapCache
    }
}