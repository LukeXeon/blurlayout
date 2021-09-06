package open.source.uikit.renderstub

import android.app.Service
import android.content.Intent
import android.content.res.Configuration
import android.os.Binder
import android.os.IBinder
import android.os.Looper
import android.os.Process
import android.view.*
import android.widget.PopupWindow
import androidx.annotation.LayoutRes
import androidx.annotation.MainThread
import open.source.uikit.common.createAsync


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

    private inner class Session(
        @LayoutRes
        layoutId: Int
    ) : IRenderStubSession.Stub(), IBinder.DeathRecipient {
        private val window = PopupWindow()
        private val bridge = BridgeView(applicationContext)

        init {
            window.isClippingEnabled = false
            window.contentView = bridge
            linkToDeath(this, 0)
            mainThread.postAtFrontOfQueue {
                LayoutInflater.from(applicationContext)
                    .inflate(layoutId, bridge, true)
            }
        }


        @MainThread
        private fun attach(token: IBinder) {
            window.showAtLocation(
                token,
                Gravity.CENTER,
                Int.MIN_VALUE,
                Int.MIN_VALUE
            )
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            try {
                return bridge.dispatchTouchEvent(event)
            } finally {
                if (Binder.getCallingPid() != Process.myPid()) {
                    event.recycle()
                }
            }
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            return bridge.dispatchKeyEvent(event)
        }

        override fun setSurface(surface: Surface?) {
            mainThread.post {
                bridge.surface = surface
            }
        }

        override fun onConfigurationChanged(configuration: Configuration?) {
            mainThread.post {
                bridge.rootView.dispatchConfigurationChanged(configuration)
            }
        }

        override fun applyStatus(
            token: IBinder?,
            configuration: Configuration?,
            surface: Surface?,
            w: Int,
            h: Int
        ) {
            mainThread.post {
                window.dismiss()
                bridge.surface = surface
                bridge.rootView.dispatchConfigurationChanged(configuration)
                window.width = w
                window.height = h
                if (token != null) {
                    attach(token)
                }
            }
        }

        override fun onAttachedToWindow(token: IBinder) {
            mainThread.post {
                attach(token)
            }
        }

        override fun onDetachedFromWindow() {
            mainThread.post {
                window.dismiss()
            }
        }

        override fun onSizeChanged(w: Int, h: Int) {
            mainThread.post {
                if (window.isShowing) {
                    window.update(w, h)
                } else {
                    window.width = w
                    window.height = h
                }
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