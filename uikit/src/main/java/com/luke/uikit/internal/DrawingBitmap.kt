package com.luke.uikit.internal

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Shader

class DrawingBitmap(val bitmap: Bitmap) {
    val shader by lazy {
        BitmapShader(
            bitmap,
            Shader.TileMode.MIRROR,
            Shader.TileMode.MIRROR
        )
    }
}