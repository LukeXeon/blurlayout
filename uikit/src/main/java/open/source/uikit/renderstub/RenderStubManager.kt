package open.source.uikit.renderstub

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Process
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue
import java.util.*
import kotlin.collections.HashSet
import kotlin.concurrent.thread


class RenderStubManager(private val context: Application) {
    private var service: IRenderStubManagerService? = null
    private val pendingOps = LinkedList<PendingOp>()
    private val queue = ReferenceQueue<Connection>()
    private val records = HashSet<Record>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val s = IRenderStubManagerService.Stub.asInterface(binder)
            for (op in pendingOps) {
                openSession(s, op.layoutId, op.connection)
            }
            pendingOps.clear()
            service = s
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            synchronized(records) {
                // need copy
                for (record in records.toTypedArray()) {
                    record.close()
                }
                records.clear()
            }
            bindService()
        }
    }

    init {
        bindService()
        thread(name = "RenderStubMonitor") {
            Process.setThreadPriority(Process.THREAD_PRIORITY_LOWEST)
            while (true) {
                val record = queue.remove() as Record
                synchronized(records) {
                    if (records.remove(record)) {
                        record.close()
                    }
                }
            }
        }
    }

    private inner class Record(
        referent: Connection,
        q: ReferenceQueue<Connection>,
        private val session: IRenderStubSession
    ) : PhantomReference<Connection>(referent, q),
        IBinder.DeathRecipient,
        Runnable {

        override fun run() {
            get()?.onDisconnect()
        }

        fun close() {
            session.close()
            binderDied()
        }

        override fun binderDied() {
            synchronized(records) {
                records.remove(this)
            }
            mainThread.post(this)
        }
    }

    private class PendingOp(
        val layoutId: Int,
        val connection: Connection
    )

    private fun bindService() {
        context.bindService(
            Intent(context, RenderStubManagerService::class.java),
            connection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun openSession(
        service: IRenderStubManagerService,
        layoutId: Int,
        connection: Connection
    ) {
        val session = service.openSession(layoutId)
        val record = Record(connection, queue, session)
        session.asBinder().linkToDeath(record, 0)
        synchronized(records) {
            records.add(record)
        }
        connection.onConnected(session)
    }

    fun openSession(
        layoutId: Int,
        connection: Connection
    ) {
        if (Looper.myLooper() != mainThread.looper) {
            throw IllegalStateException()
        }
        val s = service
        if (s != null) {
            openSession(s, layoutId, connection)
        } else {
            pendingOps.add(PendingOp(layoutId, connection))
        }
    }

    interface Connection {
        fun onConnected(s: IRenderStubSession)

        fun onDisconnect()
    }

    companion object {
        private val mainThread = Handler(Looper.getMainLooper())
        private val lock = Any()
        private var instance: RenderStubManager? = null

        fun getInstance(context: Context): RenderStubManager {
            synchronized(lock) {
                var s = instance
                if (s == null) {
                    s = RenderStubManager(context.applicationContext as Application)
                    instance = s
                }
                return s
            }
        }
    }

}