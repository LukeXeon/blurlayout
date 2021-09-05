package open.source.uikit.renderstub

import android.content.Context
import android.graphics.SurfaceTexture
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import open.source.uikit.R


class ViewRenderStub @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr) {
    private var surface: Surface? = null
    private var session: IRenderStubSession? = null
    private val callbacks = object : SurfaceTextureListener, RenderStubManager.Connection {
        override fun onSurfaceTextureAvailable(
            surfaceTexture: SurfaceTexture?,
            width: Int,
            height: Int
        ) {
            val s = Surface(surfaceTexture)
            session?.runCatching { setSurface(s) }
            surface = s
        }

        override fun onSurfaceTextureSizeChanged(
            surfaceTexture: SurfaceTexture?,
            width: Int,
            height: Int
        ) {

        }

        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture?): Boolean {
            session?.runCatching { setSurface(null) }
            surface?.release()
            surface = null
            return true
        }

        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture?) {
        }

        override fun onConnected(s: IRenderStubSession) {
            s.runCatching { setStates(applicationWindowToken, surface, width, height) }
            session = s
        }

        override fun onDisconnect() {
            session = null
        }
    }

    init {
        val array = context.obtainStyledAttributes(attrs, R.styleable.ViewRenderStub)
        val layoutId = array.getResourceId(R.styleable.ViewRenderStub_android_layout, 0)
        if (layoutId != 0) {
            RenderStubManager.getInstance(context).openSession(layoutId, callbacks)
        }
        array.recycle()
        super.setSurfaceTextureListener(callbacks)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val s = session
        if (s != null) {
            val start = SystemClock.uptimeMillis()
            try {
                return s.dispatchTouchEvent(event)
            } catch (e: Throwable) {

            }
            Log.d(TAG, "dispatchTouchEvent time:" + (SystemClock.uptimeMillis() - start))
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        session?.runCatching { onAttachedToWindow(applicationWindowToken) }
    }

    override fun onDetachedFromWindow() {
        session?.runCatching { onDetachedFromWindow() }
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        session?.runCatching { onSizeChanged(w, h) }
    }

    override fun setSurfaceTexture(surfaceTexture: SurfaceTexture?) {
        throw UnsupportedOperationException()
    }

    override fun setSurfaceTextureListener(listener: SurfaceTextureListener?) {
        throw UnsupportedOperationException()
    }

    companion object {
        private const val TAG = "ViewRenderStub"
    }
}