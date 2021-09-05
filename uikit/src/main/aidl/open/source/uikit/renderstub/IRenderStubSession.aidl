// IRenderStubSession.aidl
package open.source.uikit.renderstub;
import android.view.Surface;
import android.view.MotionEvent;
import android.os.IBinder;

// Declare any non-default types here with import statements

interface IRenderStubSession {
    boolean dispatchTouchEvent(in MotionEvent event);

    void setSurface(in Surface surface);

    void setStates(in IBinder token, in Surface surface, int w, int h);

    void onAttachedToWindow(in IBinder token);

    void onDetachedFromWindow();

    void onSizeChanged(int w, int h);

    void close();
}