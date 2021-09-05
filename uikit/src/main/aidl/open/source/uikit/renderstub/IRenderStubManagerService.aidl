// IRenderStubSession.aidl
package open.source.uikit.renderstub;
import android.view.Surface;
import android.view.MotionEvent;
import android.os.IBinder;
import open.source.uikit.renderstub.IRenderStubSession;

// Declare any non-default types here with import statements

interface IRenderStubManagerService {
    IRenderStubSession openSession(int layoutId);
}