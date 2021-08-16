package open.source.uikit.webview

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.SurfaceTexture
import android.os.IBinder
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView

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
}