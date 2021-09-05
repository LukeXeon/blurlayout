package open.source.uikit.renderstub

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.annotation.AnyThread
import androidx.annotation.LayoutRes
import androidx.annotation.MainThread
import java.lang.reflect.InvocationTargetException


class RenderStubManagerService : Service() {

    private val sessions = HashSet<Session>()

    override fun onBind(intent: Intent?): IBinder {
        return object : IRenderStubManagerService.Stub() {
            override fun openSession(layoutId: Int): IRenderStubSession {
                val session = Session(layoutId)
                synchronized(sessions) {
                    sessions.add(session)
                }
                return session
            }
        }
    }

    override fun startActivity(intent: Intent?) {
        super.startActivity(intent?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    override fun startActivity(intent: Intent?, options: Bundle?) {
        super.startActivity(intent?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }, options)
    }

    override fun startActivities(intents: Array<out Intent>?) {
        intents?.forEach {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        super.startActivities(intents)
    }

    override fun startActivities(intents: Array<out Intent>?, options: Bundle?) {
        intents?.forEach {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        super.startActivities(intents, options)
    }

    private inner class Session(
        @LayoutRes
        layoutId: Int
    ) : IRenderStubSession.Stub(), IBinder.DeathRecipient {
        private val window = PopupWindow()
        private val root = object : FrameLayout(this@RenderStubManagerService),
            ViewTreeObserver.OnPreDrawListener {
            @Volatile
            var surface: Surface? = null
                set(value) {
                    field = value
                    if (value != null) {
                        if (Looper.myLooper() == mainThread.looper) {
                            invalidate()
                        } else {
                            postInvalidate()
                        }
                    }
                }

            @Volatile
            private var pendingTouchEvent: MotionEvent? = null

            @Volatile
            private var dispatchTouchEventResult: Boolean = false

            private val onDrawRunnable = Runnable {
                val surface = surface
                if (surface != null) {
                    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        surface.lockHardwareCanvas()
                    } else {
                        surface.lockCanvas(null)
                    }
                    try {
                        draw(recorder)
                    } finally {
                        surface.unlockCanvasAndPost(recorder)
                    }
                }
            }
            private val dispatchTouchEventRunnable = Runnable {
                dispatchTouchEventResult = super.dispatchTouchEvent(pendingTouchEvent)
                pendingTouchEvent = null
            }

            init {
                viewTreeObserver.addOnPreDrawListener(this)
            }

            @AnyThread
            override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
                if (Looper.myLooper() == mainThread.looper) {
                    return super.dispatchTouchEvent(ev)
                }
                pendingTouchEvent = ev
                mainThread.post(dispatchTouchEventRunnable)
                while (true) {
                    if (pendingTouchEvent == null) {
                        return dispatchTouchEventResult
                    }
                }
            }

            override fun onPreDraw(): Boolean {
                renderThread.post(onDrawRunnable)
                return false
            }

        }

        init {
            window.isClippingEnabled = false
            window.contentView = root
            linkToDeath(this, 0)
            mainThread.postAtFrontOfQueue {
                LayoutInflater.from(this@RenderStubManagerService)
                    .inflate(layoutId, root, true)
            }
        }

        @MainThread
        private fun showWindow(token: IBinder) {
            window.showAtLocation(
                token,
                Gravity.CENTER,
                Int.MIN_VALUE,
                Int.MIN_VALUE
            )
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            try {
                return root.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }

        override fun setSurface(surface: Surface?) {
            root.surface = surface
        }

        override fun setStates(token: IBinder?, surface: Surface?, w: Int, h: Int) {
            mainThread.post {
                window.dismiss()
                root.surface = surface
                window.width = w
                window.height = h
                if (token != null) {
                    showWindow(token)
                }
            }
        }

        override fun onAttachedToWindow(token: IBinder) {
            mainThread.post {
                showWindow(token)
            }
        }

        override fun onDetachedFromWindow() {
            mainThread.post {
                window.dismiss()
            }
        }

        override fun onSizeChanged(w: Int, h: Int) {
            mainThread.post {
                window.width = w
                window.height = h
                window.update()
            }
        }

        override fun close() {
            mainThread.post {
                window.dismiss()
            }
            synchronized(sessions) {
                sessions.remove(this)
            }
        }

        override fun binderDied() {
            close()
        }
    }

    companion object {

        private const val TAG = "RenderStubManager"

        private fun createAsync(looper: Looper): Handler {
            if (Build.VERSION.SDK_INT >= 28) {
                return Handler.createAsync(looper)
            }
            try {
                return Handler::class.java.getDeclaredConstructor(
                    Looper::class.java, Handler.Callback::class.java,
                    Boolean::class.javaPrimitiveType
                ).newInstance(looper, null, true)
            } catch (ignored: IllegalAccessException) {
            } catch (ignored: InstantiationException) {
            } catch (ignored: NoSuchMethodException) {
            } catch (e: InvocationTargetException) {
                val cause = e.cause
                if (cause is RuntimeException) {
                    throw cause
                }
                if (cause is Error) {
                    throw cause
                }
                throw RuntimeException(cause)
            }
            Log.v(
                TAG,
                "Unable to invoke Handler(Looper, Callback, boolean) constructor"
            )
            return Handler(looper)
        }

        private val renderThread = createAsync(
            HandlerThread("RenderStubThread").apply { start() }.looper
        )

        private val mainThread = createAsync(Looper.getMainLooper())

        private val showAtLocationMethod = PopupWindow::class.java
            .getDeclaredMethod(
                "showAtLocation",
                IBinder::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            .apply {
                isAccessible = true
            }

        private fun PopupWindow.showAtLocation(token: IBinder, gravity: Int, x: Int, y: Int) {
            showAtLocationMethod.invoke(this, token, gravity, x, y)
        }

    }

}