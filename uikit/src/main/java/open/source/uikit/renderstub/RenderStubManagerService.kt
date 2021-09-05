package open.source.uikit.renderstub

import android.app.Service
import android.content.Intent
import android.graphics.Canvas
import android.os.*
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.annotation.LayoutRes
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask


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
        private val root = object : FrameLayout(this@RenderStubManagerService) {
            var surface: Surface? = null
                set(value) {
                    field = value
                    invalidate()
                }


            override fun dispatchDraw(canvas: Canvas?) {
                buildDrawingCache()
                val surface = surface
                if (surface != null) {
                    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        surface.lockHardwareCanvas()
                    } else {
                        surface.lockCanvas(null)
                    }
                    try {
                        super.dispatchDraw(recorder)
                    } finally {
                        surface.unlockCanvasAndPost(recorder)
                    }
                } else {
                    super.dispatchDraw(canvas)
                }
            }

            override fun onDescendantInvalidated(child: View, target: View) {
                invalidate()
                super.onDescendantInvalidated(child, target)
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

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            return try {
                invokeOnMainThreadAndAwait {
                    root.dispatchTouchEvent(event)
                }
            } finally {
                event.recycle()
            }
        }

        override fun setSurface(surface: Surface?) {
            mainThread.post {
                root.surface = surface
            }
        }

        override fun setStates(token: IBinder?, surface: Surface?, w: Int, h: Int) {
            mainThread.post {
                window.dismiss()
                root.surface = surface
                window.width = w
                window.height = h
                if (token != null) {
                    window.showAtLocation(
                        token,
                        Gravity.CENTER,
                        Int.MIN_VALUE,
                        Int.MIN_VALUE
                    )
                }
            }
        }

        override fun onAttachedToWindow(token: IBinder) {
            mainThread.post {
                window.showAtLocation(
                    token,
                    Gravity.CENTER,
                    Int.MIN_VALUE,
                    Int.MIN_VALUE
                )
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

        private val mainThread = createAsync(Looper.getMainLooper())

        private fun <T> invokeOnMainThreadAndAwait(callable: Callable<T>): T {
            if (mainThread.looper == Looper.myLooper()) {
                return callable.call()
            }
            val task = FutureTask(callable)
            mainThread.post(task)
            return task.get()
        }

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