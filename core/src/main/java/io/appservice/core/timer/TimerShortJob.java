package io.appservice.core.timer;

import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;

import io.appservice.core.CoreApp;
import io.appservice.core.util.Logger;

public class TimerShortJob extends JobIntentService {
    private static final String LOG_TAG = "IOAPP_TimerShortJob";

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":Timer");
        }
        try {
            if ( wakeLock != null ){
                wakeLock.acquire(10*60*1000L /*10 minutes*/);
            }
        }catch (Exception e){
            Logger.i(LOG_TAG, "Exception wakeLock acquire " + e.getMessage());
        }
        TimerShort ts = CoreApp.getSingleton(getApplicationContext(), TimerShort.class);
        if (ts != null) {
            ts.handle(intent);
        }
        try {
            if (wakeLock != null) {
                wakeLock.release();
            }
        }catch (Exception e){
            Logger.i(LOG_TAG, "Exception wakeLock release " + e.getMessage());
        }
    }
}
