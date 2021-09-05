package com.luke.android.blurlayout.sample

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.webkit.WebView


class Test @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {
    init {
        settings.javaScriptEnabled = true
        loadUrl("https://m.bilibili.com")
        setBackgroundColor(Color.RED)
    }
}