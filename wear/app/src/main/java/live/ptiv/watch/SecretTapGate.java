package live.ptiv.watch;

import android.os.Handler;
import android.os.SystemClock;

/**
 * Hidden tap-sequence gate for P.T. Architech.
 *
 * Only call tap() for taps on the Pearl orb/logo, never transport controls.
 * The seventh tap is held briefly so eleven consecutive taps can override it.
 * Both callbacks are synchronous on the same Android main thread.
 */
final class SecretTapGate {
    interface Listener {
        void openPerformerMode();
        void openServicesMode();
    }
    static final long MAX_GAP_MS=600L;
    static final long DECISION_DELAY_MS=720L;
    private final Handler handler;
    private final Listener listener;
    private int count=0;
    private long lastTapMs=0L;
    private final Runnable decide=new Runnable(){
        @Override public void run(){
            int finished=count;
            count=0;
            if(finished==7)listener.openPerformerMode();
        }
    };
    SecretTapGate(Handler handler, Listener listener){
        this.handler=handler;
        this.listener=listener;
    }
    void tap(){
        long now=SystemClock.uptimeMillis();
        if(count>0 && now-lastTapMs>MAX_GAP_MS)count=0;
        handler.removeCallbacks(decide);
        lastTapMs=now;
        count++;
        if(count==11){
            count=0;
            listener.openServicesMode();
        }else if(count<=10){
            handler.postDelayed(decide,DECISION_DELAY_MS);
        }else{
            count=0;
        }
    }
    void reset(){
        handler.removeCallbacks(decide);
        count=0;
    }
}
