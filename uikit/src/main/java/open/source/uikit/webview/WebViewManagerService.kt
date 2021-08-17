package open.source.uikit.webview

import android.annotation.SuppressLint
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
import open.source.uikit.ipc.ProxyParcelable
import java.util.concurrent.FutureTask

class WebViewManagerService : Service() {

    private class Session(
        private val window: PopupWindow
    ) : IWebViewSession.Stub() {
        private val webView: WebView = TODO()
        private var surface: Surface? = null

        override fun setSurface(surface: Surface?) {
            blockOnMainThread {
                this.surface = surface
            }
        }

        override fun zoomOut(): Boolean {
            return blockOnMainThread { webView.zoomOut() }
        }

        override fun zoomIn(): Boolean {
            return blockOnMainThread { webView.zoomOut() }
        }

        override fun getUrl(): String? {
            return blockOnMainThread { webView.url }
        }

        @SuppressLint("JavascriptInterface")
        override fun addJavascriptInterface(
            obj: ProxyParcelable,
            interfaceName: String
        ) {
            blockOnMainThread {
                webView.addJavascriptInterface(obj, interfaceName)
            }
        }

    }

    override fun onBind(intent: Intent?): IBinder? {

        return object : IWebViewManagerService.Stub() {
            override fun openSession(
                token: IBinder
            ): IWebViewSession {
                val window = blockOnMainThread { createWindow(token) }
                return Session(window)
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

        private val mainThread = Handler(Looper.getMainLooper())

        private fun <T> blockOnMainThread(action: () -> T): T {
            val task = FutureTask<T>(action)
            mainThread.post(task)
            return task.get()
        }

        private fun PopupWindow.showAtLocation(
            token: IBinder,
            gravity: Int,
            x: Int,
            y: Int
        ) {
            showAtLocationMethod.invoke(this, token, gravity, x, y)
        }

        private val showAtLocationMethod = PopupWindow::class.java.getDeclaredMethod(
            "showAtLocation",
            IBinder::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType
        ).apply {
            isAccessible = true
        }

        private const val TAG = "WebViewManagerService"
    }
}