package com.luke.android.blurlayout.sample

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager

class Test : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        var count = 0
        val layoutInflater =
            application.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val handler = Handler()
        val windowManager = application.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        handler.post(object : Runnable {
            override fun run() {
                var hasError = false
                try {
                    windowManager.addView(
                        layoutInflater.inflate(R.layout.view_tag, null, true).apply {
                            addOnAttachStateChangeListener(object :
                                View.OnAttachStateChangeListener {
                                override fun onViewAttachedToWindow(v: View?) {
                                    Log.d(TAG, "onViewAttachedToWindow")
                                }

                                override fun onViewDetachedFromWindow(v: View?) {
                                    Log.d(TAG, "onViewDetachedFromWindow")
                                }
                            })
                        },
                        WindowManager.LayoutParams().apply {
                            width = 500
                            height = 500
                            type = count
                        })
                } catch (e: Throwable) {
                    hasError = true
                }
                if (!hasError) {
                    Log.d(TAG, "use windows type=$count")
                } else {
                    count++
                    handler.post(this)
                }
            }
        })
    }

    companion object {
        private const val TAG = "TestXXXXX"
    }
}