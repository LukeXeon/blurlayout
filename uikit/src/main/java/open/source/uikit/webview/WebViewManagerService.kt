package open.source.uikit.webview

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.Surface
import android.webkit.WebView
import android.widget.PopupWindow
import androidx.annotation.MainThread
import java.util.concurrent.FutureTask

class WebViewManagerService : Service() {

    private val mainThread = Handler(Looper.getMainLooper())

    private class Session(
        private val window: PopupWindow,
        private val surface: Surface
    ) : IWebViewSession.Stub() {
    }

    override fun onBind(intent: Intent?): IBinder? {

        return object : IWebViewManagerService.Stub() {
            override fun openSession(
                token: IBinder,
                surface: Surface
            ): IWebViewSession {
                val window = if (Looper.myLooper() == mainThread.looper) {
                    createWindow(token)
                } else {
                    val task = FutureTask<PopupWindow> { createWindow(token) }
                    mainThread.post(task)
                    task.get()
                }
                return Session(window, surface)
            }
        }
    }

    @MainThread
    private fun createWindow(token: IBinder): PopupWindow {
        val window = PopupWindow()
        window.contentView = WebView(application)
        window.height = 0
        window.width = 0
        window.showAtLocation(token, Gravity.CENTER, 0, 0)
        return window
    }

    companion object {

        private fun PopupWindow.showAtLocation(
            token: IBinder,
            gravity: Int,
            x: Int,
            y: Int
        ) {
            showAtLocationMethod.invoke(this, token, gravity, x, y)
        }

        private val showAtLocationMethod by lazy {
            PopupWindow::class.java.getDeclaredMethod(
                "showAtLocation",
                IBinder::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).apply {
                isAccessible = true
            }
        }

        private const val TAG = "WebViewManagerService"
    }
}