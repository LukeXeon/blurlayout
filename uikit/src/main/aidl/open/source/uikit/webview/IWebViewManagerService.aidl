// IWebViewManagerService.aidl
package open.source.uikit.webview;
import android.os.IBinder;
import android.view.Surface;
import open.source.uikit.webview.IWebViewSession;

// Declare any non-default types here with import statements

interface IWebViewManagerService {

    IWebViewSession openSession(in IBinder token, in Surface surface);

}