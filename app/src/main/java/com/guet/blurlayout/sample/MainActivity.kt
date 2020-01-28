package com.guet.blurlayout.sample

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val photo = findViewById<ImageView>(R.id.photo)
        val image = findViewById<ImageView>(R.id.image)
        Glide.with(this).load(R.drawable.background)
            .into(photo)
        Glide.with(this).load(R.raw.gif)
            .into(image)
    }
}
