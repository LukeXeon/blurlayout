package open.source.uikit.webview

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.os.Build
import android.os.IBinder
import android.view.Surface
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW
import android.view.WindowManager.LayoutParams.LAST_SYSTEM_WINDOW
import android.webkit.WebView
import android.widget.FrameLayout

class NonBlockingWebService : Service() {
    private lateinit var container: Container
    private val views = HashMap<WebView, Surface>()

    override fun onCreate() {
        val windowManager = application
            .getSystemService(Context.WINDOW_SERVICE)
                as WindowManager
        val layoutParams = WindowManager.LayoutParams()
        val container = Container(application)
        for (t in FIRST_SYSTEM_WINDOW..LAST_SYSTEM_WINDOW) {
            runCatching { windowManager.removeViewImmediate(container) }
            if (
                runCatching {
                    windowManager.addView(
                        container,
                        layoutParams.apply { type = t })
                }.isSuccess
            ) {
                break
            }
        }
    }

    private fun update(view: WebView) {
        val surface = views.getValue(view)
        val canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            surface.lockHardwareCanvas()
        } else {
            surface.lockCanvas(null)
        }
        view.draw(canvas)
        surface.unlockCanvasAndPost(canvas)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private inner class Container(context: Context) : FrameLayout(context) {
        override fun onLayout(
            changed: Boolean,
            l: Int,
            t: Int,
            r: Int,
            b: Int
        ) {
            super.onLayout(
                changed,
                0,
                0,
                Int.MAX_VALUE,
                Int.MAX_VALUE
            )
        }

        override fun drawChild(c: Canvas, child: View, drawingTime: Long): Boolean {

            return false
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(0, 0)
        }
    }

    companion object {
        private const val TAG = "NonBlockingWebService"
    }
}