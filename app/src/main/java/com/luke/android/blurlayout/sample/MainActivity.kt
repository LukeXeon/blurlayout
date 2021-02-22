package com.luke.android.blurlayout.sample

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.luke.uikit.popup.PopupStackManager

class MainActivity : AppCompatActivity() {

    private lateinit var button: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val window: Window = window
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
                        or WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            )
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.TRANSPARENT
        }
        setContentView(R.layout.activity_main)
        button = findViewById(R.id.open_pannel)
        button.setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                val l = this
                PopupStackManager.push(
                    this@MainActivity,
                    layoutInflater.inflate(R.layout.activity_main, null, false).apply {
                        findViewById<View>(R.id.open_pannel).setOnClickListener(l)
                    }
                )
            }
        })
    }
}
