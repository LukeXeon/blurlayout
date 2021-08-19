package open.source.uikit.ipc

import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import android.os.Process
import android.os.RemoteCallbackList
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*
import kotlin.collections.HashSet

class ProxyParcelable(
    private val target: Any?
) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.run {
        val pid = readInt()
        val interfaces = createStringArray()
        val binder = readStrongBinder()
        if (binder == null || interfaces.isNullOrEmpty()) {
            null
        } else {
            val obj = IDynamicInvoke.Stub
                .asInterface(binder)
            if (pid == Process.myPid() && obj is DynamicInvokeHandler) {
                obj.reference.get()
            } else {
                Proxy.newProxyInstance(
                    obj.javaClass.classLoader,
                    interfaces.map { Class.forName(it) }
                        .toTypedArray(),
                    DynamicInvoker(obj)
                )
            }
        }
    })

    private class DynamicInvoker(target: IDynamicInvoke) : InvocationHandler {

        private val callbackList = RemoteCallbackList<IDynamicInvoke>()

        init {
            callbackList.register(target)
        }

        override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>): Any? {
            return if (method.declaringClass == Any::class.java) {
                method.invoke(this, args)
            } else {
                try {
                    if (callbackList.beginBroadcast() == 0) {
                        return safeReturn(method.returnType, null)
                    }
                    val target = callbackList.getBroadcastItem(0)
                    safeReturn(
                        method.returnType, unpack(
                            target.dynamicInvoke(
                                method.name,
                                method.parameterTypes.map {
                                    it.name
                                }.toTypedArray(),
                                args.map { pack(it) }.toTypedArray()
                            )
                        )
                    )
                } finally {
                    callbackList.finishBroadcast()
                }
            }
        }
    }

    private class DynamicInvokeHandler(target: Any) : IDynamicInvoke.Stub() {

        val reference = WeakReference(target)

        override fun dynamicInvoke(
            methodName: String,
            parameterTypes: Array<out String>,
            args: Array<out AnyParcelable>
        ): AnyParcelable? {
            val target = reference.get() ?: return null
            return pack(
                target.javaClass.getMethod(
                    methodName,
                    *parameterTypes.map { primitiveTypes[it] ?: Class.forName(it) }.toTypedArray()
                ).invoke(target, *args.map { unpack(it) }.toTypedArray())
            )
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(Process.myPid())
        parcel.writeStringList(loadProxyInterfaces(target?.javaClass))
        parcel.writeStrongBinder(loadDynamicInvoke(target))
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ProxyParcelable> {

        private val handlers = WeakHashMap<Any, DynamicInvokeHandler>()

        private val proxyInterfaces = WeakHashMap<Class<*>, List<String>>()

        private val primitiveTypes =
            arrayOf(
                Short::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Long::class.javaPrimitiveType!!,
                Float::class.javaPrimitiveType!!,
                Double::class.javaPrimitiveType!!,
                Char::class.javaPrimitiveType!!,
                Byte::class.javaPrimitiveType!!,
                Boolean::class.javaPrimitiveType!!
            ).asSequence().map {
                it.name to it
            }.toMap()

        private val collect = HashSet<Class<*>>()

        private fun pack(any: Any?): AnyParcelable {
            return AnyParcelable(any)
        }

        private fun unpack(any: AnyParcelable?): Any? {
            return any?.value
        }

        private fun loadDynamicInvoke(target: Any?): IBinder? {
            target ?: return null
            return synchronized(handlers) {
                handlers.getOrPut(target) { DynamicInvokeHandler(target) }
            }
        }

        private fun safeReturn(returnType: Class<*>, value: Any?): Any? {
            if (value != null) {
                return value
            }
            return when (returnType) {
                Byte::class.javaPrimitiveType,
                Short::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Char::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Double::class.javaPrimitiveType -> {
                    0.toByte()
                }
                Boolean::class.javaPrimitiveType -> {
                    false
                }
                else -> {
                    null
                }
            }
        }

        private fun loadProxyInterfaces(
            clazz: Class<*>?
        ): List<String>? {
            clazz ?: return null
            return synchronized(proxyInterfaces) {
                proxyInterfaces.getOrPut(clazz) {
                    try {
                        collect.clear()
                        var current: Class<*>? = clazz
                        while (current != null && current.classLoader != Any::class.java.classLoader) {
                            collect.addAll(current.interfaces)
                            current = clazz.superclass
                        }
                        collect.map { it.name }
                    } finally {
                        collect.clear()
                    }.apply {
                        require(isNotEmpty())
                    }
                }
            }
        }

        override fun createFromParcel(source: Parcel): ProxyParcelable {
            return ProxyParcelable(source)
        }

        override fun newArray(size: Int): Array<ProxyParcelable?> {
            return arrayOfNulls(size)
        }
    }
}