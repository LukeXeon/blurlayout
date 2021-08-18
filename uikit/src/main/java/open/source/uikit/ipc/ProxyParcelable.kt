package open.source.uikit.ipc

import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import android.os.RemoteCallbackList
import java.lang.ref.WeakReference
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*
import kotlin.collections.HashSet

class ProxyParcelable(
    private val target: Any
) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.let {
        val interfaces = parcel.createStringArray()!!
            .map { Class.forName(it) }
            .toTypedArray()
        val obj = IDynamicInvoke.Stub
            .asInterface(parcel.readStrongBinder())
        Proxy.newProxyInstance(
            obj.javaClass.classLoader,
            interfaces,
            DynamicInvoker(obj)
        )
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
                        return when (method.returnType) {
                            Byte::class.javaPrimitiveType,
                            Short::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType,
                            Long::class.javaPrimitiveType -> {
                                0.toByte()
                            }
                            Float::class.javaPrimitiveType, Double::class.javaPrimitiveType -> {
                                0f
                            }
                            Boolean::class.javaPrimitiveType -> {
                                false
                            }
                            else -> {
                                null
                            }
                        }
                    }
                    val target = callbackList.getBroadcastItem(0)
                    unpack(
                        target.dynamicInvoke(
                            method.name,
                            method.parameterTypes.map {
                                it.name
                            }.toTypedArray(),
                            args.map { pack(it) }.toTypedArray()
                        )
                    )
                } finally {
                    callbackList.finishBroadcast()
                }
            }
        }
    }

    private class DynamicInvokeHandler(target: Any) : IDynamicInvoke.Stub() {

        private val reference = WeakReference(target)

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
        parcel.writeStringList(loadProxyInterfaces(target.javaClass))
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

        private fun loadDynamicInvoke(target: Any): IBinder {
            return synchronized(handlers) {
                handlers.getOrPut(target) { DynamicInvokeHandler(target) }
            }
        }


        private fun loadProxyInterfaces(
            clazz: Class<*>
        ): List<String> {
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