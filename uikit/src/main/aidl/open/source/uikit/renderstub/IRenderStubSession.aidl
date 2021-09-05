// IRenderStubSession.aidl
package open.source.uikit.renderstub;
import android.view.Surface;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.os.IBinder;
import android.content.res.Configuration;

// Declare any non-default types here with import statements

interface IRenderStubSession {
    boolean dispatchTouchEvent(in MotionEvent event);

    boolean dispatchKeyEvent(in KeyEvent event);

    void setSurface(in Surface surface);

    void applyStatus(
        in IBinder token,
        in Configuration configuration,
        in Surface surface,
        int w,
        int h
    );

    void onConfigurationChanged(in Configuration configuration);

    void onAttachedToWindow(in IBinder token);

    void onDetachedFromWindow();

    void onSizeChanged(int w, int h);

    void close();
}