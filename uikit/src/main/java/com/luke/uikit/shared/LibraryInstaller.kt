package com.luke.uikit.shared

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import com.luke.uikit.popup.StackRootView

class LibraryInstaller : ContentProvider() {

    override fun onCreate(): Boolean {
        val ctx = context
        if (ctx != null) {
            val application = ctx.applicationContext as Application
            application.registerActivityLifecycleCallbacks(StackRootView)
            application.registerComponentCallbacks(BitmapCache)
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

}