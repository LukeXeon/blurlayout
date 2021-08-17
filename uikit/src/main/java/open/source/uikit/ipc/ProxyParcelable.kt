package open.source.uikit.ipc

import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
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
        val interfaces = parcel.createStringArray()
            ?.map { Class.forName(it) }
            ?.toTypedArray()
            ?: emptyArray()
        val obj = IDynamicInvoke.Stub
            .asInterface(parcel.readStrongBinder())
        Proxy.newProxyInstance(
            ProxyParcelable::class.java.classLoader,
            interfaces,
            DynamicInvoker(obj)
        )
    })

    private class DynamicInvoker(private val target: IDynamicInvoke) : InvocationHandler {
        override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>): Any? {
            return if (method.declaringClass == Any::class.java) {
                method.invoke(this, args)
            } else {
                unpack(
                    target.dynamicInvoke(
                        method.name,
                        method.parameterTypes.map {
                            it.name
                        }.toTypedArray(),
                        args.map { pack(it) }.toTypedArray()
                    )
                )
            }
        }
    }

    private class DynamicInvokeHandler(target: Any) : IDynamicInvoke.Stub() {

        private val reference = WeakReference(target)

        override fun dynamicInvoke(
            methodName: String,
            parameterTypes: Array<out String>,
            args: Array<out AnyParcelable>
        ): AnyParcelable {
            val target = reference.get() ?: throw NullPointerException()
            return pack(
                target.javaClass.getMethod(
                    methodName,
                    *parameterTypes.map { primitiveTypes[it] ?: Class.forName(it) }.toTypedArray()
                ).invoke(target, *args.map { pack(it) }.toTypedArray())
            )
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeStringArray(loadProxyInterfaces(target.javaClass))
        parcel.writeStrongBinder(loadDynamicInvoke())
    }

    private fun loadDynamicInvoke(): IBinder {
        return synchronized(handlers) {
            handlers.getOrPut(target) { DynamicInvokeHandler(target) }
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ProxyParcelable> {

        private val handlers = WeakHashMap<Any, DynamicInvokeHandler>()

        private val proxyInterfaces = WeakHashMap<Class<*>, Array<String>>()

        private val primitiveTypes =
            arrayOf(
                Short::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Double::class.javaPrimitiveType,
                Byte::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            ).asSequence().filterNotNull()
                .map {
                    it.name to it
                }.toMap()

        private fun pack(any: Any?): AnyParcelable {
            return AnyParcelable(any)
        }

        private fun unpack(any: AnyParcelable?): Any? {
            return any?.value
        }

        private fun loadProxyInterfaces(
            clazz: Class<*>
        ): Array<String> {
            return synchronized(proxyInterfaces) {
                proxyInterfaces.getOrPut(clazz) {
                    findProxyInterfaces(
                        clazz,
                        HashSet()
                    )
                }
            }
        }

        private fun findProxyInterfaces(
            clazz: Class<*>,
            set: HashSet<String>
        ): Array<String> {
            if (clazz.classLoader == Any::class.java.classLoader) {
                return set.toTypedArray()
            }
            set.addAll(
                clazz.interfaces.asSequence()
                    .filter {
                        it.isAnnotationPresent(InvocationInterface::class.java)
                    }.map {
                        it.name
                    }
            )
            return findProxyInterfaces(
                clazz,
                set
            )
        }

        override fun createFromParcel(source: Parcel): ProxyParcelable {
            return ProxyParcelable(source)
        }

        override fun newArray(size: Int): Array<ProxyParcelable?> {
            return arrayOfNulls(size)
        }
    }
}