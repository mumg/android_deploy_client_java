package io.appservice.core.timer;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;

import java.util.HashSet;
import java.util.Set;

import io.appservice.core.BroadcastManager;
import io.appservice.core.broadcast.BroadcastIntent;
import io.appservice.core.util.Logger;

import static android.content.Context.ALARM_SERVICE;

public class TimerLong implements Timer {

    private static final int RC = 9584;
    private static final String LOG_TAG = "IOAPP_TimerLong";
    private Context mCtx;
    private static final String ALARM_ACTION = "io.appservice.core.ALARM";
    private static final int TIMER_THRESHOLD = 30000;
    public static final int MIN_BACKGROUND_TIMEOUT = 30000;
    private Long mCurrent = Long.MAX_VALUE;
    private Runnable mRunnable;

    private TimerLong(Context ctx) throws Exception {
        mCtx = ctx;
        BroadcastManager mgr = BroadcastManager.getInstance(ctx);
        Set<String> actions = new HashSet<>();
        actions.add(ALARM_ACTION);
        mgr.register(ctx, actions, null, new BroadcastManager.Handler() {
            @Override
            public void handle(Context ctx, Intent intent) {
                Runnable run = mRunnable;
                mCurrent = Long.MAX_VALUE;
                if ( run  != null ){
                    run.run();
                }
            }
        });
    }

    @Override
    public void start(Long timestamp, Runnable runnable) {
        synchronized (this) {
            Long delta = timestamp - System.currentTimeMillis();
            if (delta < MIN_BACKGROUND_TIMEOUT) {
                return;
            }
            if (mCurrent != Long.MAX_VALUE && Math.abs(mCurrent - timestamp) < TIMER_THRESHOLD) {
                return;
            }
            mCurrent = timestamp;
            mRunnable = runnable;
            long timeout = timestamp - System.currentTimeMillis();
            if (timeout <= 0) {
                Intent intent = new Intent(mCtx, BroadcastIntent.class);
                intent.setAction(ALARM_ACTION);
                LocalBroadcastManager.getInstance(mCtx).sendBroadcast(intent);
                return;
            }
            Logger.i(LOG_TAG, "startLong timeout=" + (timestamp - System.currentTimeMillis()));
            Intent intent = new Intent(mCtx, BroadcastIntent.class);
            intent.setAction(ALARM_ACTION);
            AlarmManager am = (AlarmManager) mCtx.getSystemService(ALARM_SERVICE);
            if (am == null) {
                return;
            }
            PendingIntent pendingIntent = PendingIntent.getBroadcast(mCtx, RC, intent, PendingIntent.FLAG_NO_CREATE);
            if (pendingIntent != null) {
                am.cancel(pendingIntent);
            }
            pendingIntent = PendingIntent.getBroadcast(mCtx, RC, intent, 0);
            am.set(AlarmManager.RTC_WAKEUP, timestamp, pendingIntent);
        }

    }

    @Override
    public void stop(Long timestamp) {
        synchronized (this) {
            if (!mCurrent.equals(timestamp)) {
                return;
            }
            mCurrent = Long.MAX_VALUE;
            AlarmManager am = (AlarmManager) mCtx.getSystemService(ALARM_SERVICE);
            if (am == null) {
                return;
            }
            Intent intent = new Intent(mCtx, BroadcastIntent.class);
            intent.setAction(ALARM_ACTION);

            PendingIntent pendingIntent = PendingIntent.getBroadcast(mCtx, RC, intent, PendingIntent.FLAG_NO_CREATE);
            if (pendingIntent != null) {
                am.cancel(pendingIntent);
            }
        }
    }

    @Override
    public void dump(){
        if ( mCurrent != Long.MAX_VALUE ){
            Logger.i(LOG_TAG, "Timer long fire in " + (mCurrent - System.currentTimeMillis()) + "ms");
        }else{
            Logger.i(LOG_TAG, "Timer long is not running");
        }
    }
}
