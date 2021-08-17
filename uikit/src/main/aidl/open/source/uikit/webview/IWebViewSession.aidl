// IWebViewSession.aidl
package open.source.uikit.webview;
import android.view.Surface;
import open.source.uikit.ipc.ProxyParcelable;

interface IWebViewSession {
    void setSurface(in Surface surface);

    String getUrl();

    boolean zoomIn();

    boolean zoomOut();

    void addJavascriptInterface(in ProxyParcelable obj, in String interfaceName);
}