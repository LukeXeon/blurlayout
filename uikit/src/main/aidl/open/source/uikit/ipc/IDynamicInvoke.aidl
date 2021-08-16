// IDynamicInvoke.aidl
package open.source.uikit.ipc;
import open.source.uikit.ipc.AnyParcelable;
// Declare any non-default types here with import statements

interface IDynamicInvoke {
    AnyParcelable dynamicInvoke(in String methodName, in String[] parameterTypes, in AnyParcelable[] args);
}