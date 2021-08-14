package open.source.uikit.common

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import com.bumptech.glide.Glide
import java.util.*

interface BitmapPool {
    operator fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap

    fun recycle(bitmap: Bitmap)

    interface Factory {

        val priority: Int

        fun create(): BitmapPool

    }

    companion object {

        private const val PREFERENCES_NAME = "open.source.uikit.preferences"

        private const val FACTORY_KEY = "factory"

        private const val APP_VERSION_KEY = "app_version"

        private const val NO_FACTORY = ""

        val default: BitmapPool by lazy {
            try {
                @SuppressLint("PrivateApi")
                val activityThreadClass = Class.forName("android.app.ActivityThread")
                val sCurrentActivityThreadField = activityThreadClass
                    .getDeclaredField("sCurrentActivityThread")
                    .apply {
                        isAccessible = true
                    }
                val sCurrentActivityThread = sCurrentActivityThreadField.get(null)
                val mInitialApplicationField = activityThreadClass
                    .getDeclaredField("mInitialApplication")
                    .apply {
                        isAccessible = true
                    }
                val application = mInitialApplicationField
                    .get(sCurrentActivityThread) as Application
                try {
                    val innerPool = Glide.get(application).bitmapPool
                    return@lazy object : BitmapPool {
                        override fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap {
                            return innerPool.get(width, height, config)
                        }

                        override fun recycle(bitmap: Bitmap) {
                            innerPool.put(bitmap)
                        }
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                val preferences = application.getSharedPreferences(
                    PREFERENCES_NAME,
                    Context.MODE_PRIVATE
                )
                val serviceName = preferences.getString(FACTORY_KEY, null)
                val lastTimeAppVersion = preferences.getInt(APP_VERSION_KEY, Int.MIN_VALUE)
                val appVersion = application.packageManager.getPackageInfo(
                    application.packageName,
                    PackageManager.GET_CONFIGURATIONS
                ).versionCode
                if (serviceName == null || appVersion != lastTimeAppVersion) {
                    try {
                        val service = ServiceLoader.load(
                            Factory::class.java,
                            Factory::class.java.classLoader
                        ).iterator().asSequence().maxBy {
                            it.priority
                        }
                        if (service != null) {
                            preferences.edit()
                                .putInt(APP_VERSION_KEY, appVersion)
                                .putString(FACTORY_KEY, service.javaClass.name)
                                .apply()
                            return@lazy service.create()
                        }
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                    preferences.edit()
                        .putInt(APP_VERSION_KEY, appVersion)
                        .putString(FACTORY_KEY, NO_FACTORY)
                        .apply()
                } else if (serviceName != NO_FACTORY) {
                    try {
                        return@lazy (Class.forName(
                            serviceName,
                            false,
                            Factory::class.java.classLoader
                        ).newInstance() as Factory).create()
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                }
            } catch (ex: Throwable) {
                ex.printStackTrace()
            }
            return@lazy object : BitmapPool {
                override fun get(width: Int, height: Int, config: Bitmap.Config): Bitmap {
                    return Bitmap.createBitmap(width, height, config)
                }

                override fun recycle(bitmap: Bitmap) {
                    bitmap.recycle()
                }
            }
        }
    }
}