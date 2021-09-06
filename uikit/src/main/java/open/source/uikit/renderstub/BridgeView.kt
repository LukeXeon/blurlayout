package open.source.uikit.renderstub

import android.content.Context
import android.graphics.Canvas
import android.os.Build
import android.os.HandlerThread
import android.os.Looper
import android.util.AttributeSet
import android.view.*
import android.widget.FrameLayout
import androidx.annotation.AnyThread
import open.source.uikit.common.createAsync

class BridgeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr),
    ViewTreeObserver.OnPreDrawListener,
    ViewTreeObserver.OnScrollChangedListener,
    ViewTreeObserver.OnGlobalFocusChangeListener {

    private abstract class EventDispatcher<T> : Runnable {

        @Volatile
        private var pendingEvent: T? = null

        @Volatile
        private var result: Boolean = false

        protected abstract fun handle(ev: T): Boolean

        override fun run() {
            result = handle(pendingEvent!!)
            pendingEvent = null
        }

        fun dispatch(ev: T): Boolean {
            if (Looper.myLooper() == mainThread.looper) {
                return handle(ev)
            }
            pendingEvent = ev
            mainThread.post(this)
            while (true) {
                if (pendingEvent == null) {
                    return result
                }
            }
        }
    }

    private class SurfaceHolder : Runnable {
        @Volatile
        var surface: Surface? = null

        @Volatile
        private var pendingCanvas: Canvas? = null

        fun lockCanvas(): Canvas? {
            val surface = surface
            if (surface != null && pendingCanvas == null) {
                return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    surface.lockHardwareCanvas()
                } else {
                    surface.lockCanvas(null)
                }
            }
            return null
        }

        fun unlockCanvasAndPost(canvas: Canvas) {
            pendingCanvas = canvas
            renderThread.post(this)
        }

        override fun run() {
            surface?.unlockCanvasAndPost(pendingCanvas)
            pendingCanvas = null
        }
    }

    var surface: Surface?
        get() {
            return surfaceHolder.surface
        }
        set(value) {
            surfaceHolder.surface = value
            if (value != null) {
                invalidate()
            }
        }

    private val touchEventDispatcher = object : EventDispatcher<MotionEvent>() {
        override fun handle(ev: MotionEvent): Boolean {
            return super@BridgeView.dispatchTouchEvent(ev)
        }
    }

    private val keyEventDispatcher = object : EventDispatcher<KeyEvent>() {
        override fun handle(ev: KeyEvent): Boolean {
            return super@BridgeView.dispatchKeyEvent(ev)
        }
    }

    private val surfaceHolder = SurfaceHolder()

    init {
        id = View.generateViewId()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(this)
        viewTreeObserver.addOnPreDrawListener(this)
        viewTreeObserver.addOnScrollChangedListener(this)
    }

    override fun onDetachedFromWindow() {
        viewTreeObserver.removeOnGlobalFocusChangeListener(this)
        viewTreeObserver.removeOnPreDrawListener(this)
        viewTreeObserver.removeOnScrollChangedListener(this)
        super.onDetachedFromWindow()
    }

    override fun onScrollChanged() {
        onPreDraw()
    }

    override fun onPreDraw(): Boolean {
        val canvas = surfaceHolder.lockCanvas()
        if (canvas != null) {
            draw(canvas)
            surfaceHolder.unlockCanvasAndPost(canvas)
        }
        return false
    }

    override fun onGlobalFocusChanged(
        oldFocus: View?,
        newFocus: View
    ) {
        if (newFocus.findViewById<View>(id) != this) {

        }
    }

    @AnyThread
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return touchEventDispatcher.dispatch(ev)
    }

    @AnyThread
    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        return keyEventDispatcher.dispatch(ev)
    }

    companion object {
        private val renderThread = createAsync(
            HandlerThread("RenderStubThread").apply { start() }.looper
        )

        private val mainThread = createAsync(Looper.getMainLooper())
    }


}