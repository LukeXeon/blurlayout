package open.source.uikit.ipc

import android.os.IBinder
import android.os.Parcel
import android.os.Parcelable
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class DynamicInvokeHandler(
    private val target: Any
) : Parcelable {
    constructor(parcel: Parcel) : this(parcel.let {
        val classes = parcel.createStringArray()!!
            .map { Class.forName(it) }
            .toTypedArray()
        val obj = IDynamicInvoke.Stub.asInterface(parcel.readStrongBinder())
        Proxy.newProxyInstance(
            DynamicInvokeHandler::class.java.classLoader,
            classes,
            object : InvocationHandler {
                override fun invoke(proxy: Any?, method: Method, args: Array<out Any?>): Any? {
                    return if (method.declaringClass == Any::class.java) {
                        method.invoke(this, args)
                    } else {
                        unpack(obj.dynamicInvoke(
                            method.name,
                            method.parameterTypes.map {
                                it.name
                            }.toTypedArray(),
                            args.toPackArray()
                        ))
                    }
                }
            }
        )
    })

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeStringArray(findProxyInterfaces(target.javaClass))
        parcel.writeStrongBinder(getDynamicInvoke())
    }

    private fun getDynamicInvoke(): IBinder {
        return synchronized(stubCache) {
            stubCache.getOrPut(target) {
                object : IDynamicInvoke.Stub() {
                    override fun dynamicInvoke(
                        methodName: String,
                        parameterTypes: Array<out String>,
                        args: Array<out AnyParcelable?>
                    ): AnyParcelable? {
                        val info = MethodInfo(
                            target.javaClass,
                            methodName,
                            parameterTypes
                        )
                        val method = synchronized(methodCache) {
                            methodCache.getOrPut(info) {
                                target.javaClass.getMethod(
                                    methodName,
                                    *parameterTypes.map {
                                        primitiveTypes[it] ?: Class.forName(it)
                                    }.toTypedArray()
                                )
                            }
                        }
                        return pack(method.invoke(target, args.toUnpackArray()))
                    }
                }
            }
        }
    }

    override fun describeContents(): Int {
        return 0
    }

    private data class MethodInfo(
        private val clazz: Class<*>,
        private val methodName: String,
        private val parameterTypes: Array<out String>
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as MethodInfo

            if (clazz != other.clazz) return false
            if (methodName != other.methodName) return false
            if (!parameterTypes.contentEquals(other.parameterTypes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = clazz.hashCode()
            result = 31 * result + methodName.hashCode()
            result = 31 * result + parameterTypes.contentHashCode()
            return result
        }
    }

    companion object CREATOR : Parcelable.Creator<DynamicInvokeHandler> {

        private val stubCache = WeakHashMap<Any, IDynamicInvoke.Stub>()

        private val findCache = HashMap<Class<*>, Array<String>>()

        private val methodCache = HashMap<MethodInfo, Method>()

        private val primitiveTypes =
            arrayOf(
                Short::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Double::class.javaPrimitiveType,
                Byte::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            ).asSequence()
                .filterNotNull()
                .map {
                    it.name to it
                }.toMap()

        private fun Array<out AnyParcelable?>.toUnpackArray(): Array<out Any?> {
            return map { unpack(it) }.toTypedArray()
        }

        private fun Array<out Any?>.toPackArray(): Array<out AnyParcelable?> {
            return map { pack(it) }.toTypedArray()
        }

        private fun pack(any: Any?): AnyParcelable? {
            if (any == null) return null
            return AnyParcelable(
                when (any) {
                    is Boolean -> BooleanParcelable(any)
                    is Short -> ShortParcelable(any)
                    is Int -> IntParcelable(any)
                    is Long -> LongParcelable(any)
                    is Float -> FloatParcelable(any)
                    is Double -> DoubleParcelable(any)
                    is Byte -> ByteParcelable(any)
                    is String -> StringParcelable(any)
                    is IBinder -> BinderParcelable(any)
                    is Parcelable -> AnyParcelable(any)
                    else -> {
                        throw IllegalArgumentException()
                    }
                }
            )
        }

        private fun unpack(any: AnyParcelable?): Any? {
            if (any == null) return null
            return when (any.value) {
                is BooleanParcelable -> any.value.value
                is ShortParcelable -> any.value.value
                is IntParcelable -> any.value.value
                is LongParcelable -> any.value.value
                is FloatParcelable -> any.value.value
                is DoubleParcelable -> any.value.value
                is ByteParcelable -> any.value.value
                is StringParcelable -> any.value.value
                is BinderParcelable -> any.value.value
                is AnyParcelable -> any.value.value
                else -> {
                    throw IllegalArgumentException()
                }
            }
        }

        private fun findProxyInterfaces(
            clazz: Class<*>
        ): Array<String> {
            return synchronized(findCache) {
                findCache.getOrPut(clazz) {
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
            set.addAll(clazz.interfaces
                .asSequence()
                .filter {
                    it.isAnnotationPresent(DynamicInvokeInterface::class.java)
                }.map {
                    it.name
                }
            )
            return findProxyInterfaces(
                clazz,
                set
            )
        }

        override fun createFromParcel(source: Parcel): DynamicInvokeHandler {
            return DynamicInvokeHandler(source)
        }

        override fun newArray(size: Int): Array<DynamicInvokeHandler?> {
            return arrayOfNulls(size)
        }
    }
}