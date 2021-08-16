package com.luke.android.blurlayout.sample

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import open.source.uikit.webview.IWebViewManagerService
import open.source.uikit.webview.WebViewManagerService

class MainActivity : AppCompatActivity() {

    private lateinit var button: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

    }

    override fun onResume() {
        super.onResume()
        bindService(Intent(this, WebViewManagerService::class.java), object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                IWebViewManagerService.Stub.asInterface(service)
                    .openSession(findViewById<View>(R.id.root).applicationWindowToken, null)
            }

            override fun onServiceDisconnected(name: ComponentName?) {

            }
        }, Context.BIND_AUTO_CREATE)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}


