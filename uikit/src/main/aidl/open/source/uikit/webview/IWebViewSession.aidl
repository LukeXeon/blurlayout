// IWebViewSession.aidl
package open.source.uikit.webview;
import android.view.Surface;
import open.source.uikit.ipc.DynamicInvokeHandler;

interface IWebViewSession {
    void setSurface(in Surface surface);

    String getUrl();

    boolean zoomIn();

    boolean zoomOut();

    void addJavascriptInterface(in DynamicInvokeHandler obj,in String interfaceName);
}