package com.luke.android.blurlayout.sample

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var button: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        View(this).windowToken
        val manager= getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }


    companion object {
        private const val TAG = "MainActivity"
    }
}


