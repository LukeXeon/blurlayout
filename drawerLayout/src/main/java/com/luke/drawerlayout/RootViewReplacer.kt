package com.luke.drawerlayout

import android.app.Activity
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup

class RootViewReplacer : ContentProvider(), Application.ActivityLifecycleCallbacks {

    override fun onCreate(): Boolean {
        val ctx = context
        if (ctx != null) {
            val application = ctx.applicationContext as Application
            application.registerActivityLifecycleCallbacks(this)
        }
        return ctx != null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }

    override fun getType(uri: Uri): String? {
        return null
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        return null
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        return 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        val rootView = activity.window.decorView as ViewGroup
        val contentView = rootView.getChildAt(0)
        if (contentView !is ContentLayout) {
            val childViews = (0 until rootView.childCount)
                .map { rootView.getChildAt(it) }
            rootView.removeAllViews()
            val newContentView = ContentLayout(rootView.context)
            newContentView.setContentViews(childViews)
            rootView.addView(newContentView)
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

    }

}