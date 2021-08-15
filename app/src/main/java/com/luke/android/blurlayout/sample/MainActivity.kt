package com.luke.android.blurlayout.sample

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import open.source.uikit.webview.NonBlockingWebService

class MainActivity : AppCompatActivity() {

    private lateinit var button: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

    }

    override fun onResume() {
        super.onResume()
        startService(Intent(this, NonBlockingWebService::class.java))
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}


