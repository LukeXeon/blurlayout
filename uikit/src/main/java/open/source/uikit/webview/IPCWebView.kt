package open.source.uikit.webview

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.os.IBinder
import android.os.Message
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebViewClient

open class IPCWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr) {
    private var surface: Surface? = null
    private var session: IWebViewSession? = null
    private val callbacks = object : ServiceConnection, SurfaceTextureListener {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            session = IWebViewManagerService.Stub.asInterface(service)
                .runCatching { openSession(applicationWindowToken) }
                .getOrNull()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            session = null
            if (isAvailable) {
                bindService()
            }
        }

        override fun onSurfaceTextureSizeChanged(
            surfaceTexture: SurfaceTexture?,
            width: Int,
            height: Int
        ) {

        }

        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture?) {
        }

        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture?): Boolean {
            session?.runCatching {
                setSurface(null)
            }
            surface?.release()
            surface = null
            return true
        }

        override fun onSurfaceTextureAvailable(
            surfaceTexture: SurfaceTexture,
            width: Int,
            height: Int
        ) {
            val surface = Surface(surfaceTexture)
            session?.runCatching {
                setSurface(surface)
            }
            this@IPCWebView.surface = surface
        }
    }

    init {
        super.setSurfaceTextureListener(callbacks)
    }

    @Deprecated("internal use", level = DeprecationLevel.HIDDEN)
    final override fun setSurfaceTextureListener(listener: SurfaceTextureListener?) {
    }

    private fun bindService() {
        context.bindService(
            Intent(context, WebViewManagerService::class.java),
            callbacks,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        bindService()
    }

    override fun onDetachedFromWindow() {
        context.unbindService(callbacks)
        super.onDetachedFromWindow()
    }


    // START FAKE PUBLIC METHODS
    open fun setHorizontalScrollbarOverlay(overlay: Boolean) {}

    open fun setVerticalScrollbarOverlay(overlay: Boolean) {}

    open fun overlayHorizontalScrollbar(): Boolean {
        return false
    }

    open fun overlayVerticalScrollbar(): Boolean {
        return false
    }

    open fun savePassword(
        host: String?,
        username: String?,
        password: String?
    ) {
    }

    open fun setHttpAuthUsernamePassword(
        host: String?, realm: String?,
        username: String?, password: String?
    ) {
    }

    open fun getHttpAuthUsernamePassword(
        host: String?,
        realm: String?
    ): Array<String?>? {
        return null
    }

    open fun destroy() {}

    open fun enablePlatformNotifications() {}

    open fun disablePlatformNotifications() {}

    open fun loadUrl(url: String?) {}

    open fun loadData(
        data: String?,
        mimeType: String?,
        encoding: String?
    ) {
    }

    open fun loadDataWithBaseURL(
        baseUrl: String?, data: String?,
        mimeType: String?, encoding: String?, failUrl: String?
    ) {
    }

    open fun stopLoading() {}

    open fun reload() {}

    open fun canGoBack(): Boolean {
        return false
    }

    open fun goBack() {}

    open fun canGoForward(): Boolean {
        return false
    }

    open fun goForward() {}

    open fun canGoBackOrForward(steps: Int): Boolean {
        return false
    }

    open fun goBackOrForward(steps: Int) {}

    open fun pageUp(top: Boolean): Boolean {
        return false
    }

    open fun pageDown(bottom: Boolean): Boolean {
        return false
    }

    open fun clearView() {}

    open fun captureBitmap(): Bitmap? {
        return bitmap
    }

    open fun getScale(): Float {
        return 0f
    }

    open fun setInitialScale(scaleInPercent: Int) {}

    open fun invokeZoomPicker() {}

    open fun requestFocusNodeHref(hrefMsg: Message?) {}

    open fun requestImageRef(msg: Message?) {}

    open fun getUrl(): String? {
        return null
    }

    open fun getTitle(): String? {
        return null
    }

    open fun getFavicon(): Bitmap? {
        return null
    }

    open fun getProgress(): Int {
        return 0
    }

    open fun getContentHeight(): Int {
        return 0
    }

    open fun pauseTimers() {}

    open fun resumeTimers() {}

    open fun clearCache() {}

    open fun clearFormData() {}

    open fun clearHistory() {}

    open fun clearSslPreferences() {}

    open fun findAddress(addr: String?): String? {
        return null
    }

    open fun documentHasImages(response: Message?) {}

    open fun setWebViewClient(client: WebViewClient?) {}

    open fun setDownloadListener(listener: DownloadListener?) {}

    open fun setWebChromeClient(client: WebChromeClient?) {}

    open fun addJavascriptInterface(obj: Any?, interfaceName: String?) {}

    open fun getZoomControls(): View? {
        return null
    }

    open fun zoomIn(): Boolean {
        return false
    }

    open fun zoomOut(): Boolean {
        return false
    }
}