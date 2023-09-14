package io.appservice.core.support;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.app.JobIntentService;

import io.appservice.core.util.Logger;

public class WorkerService extends JobIntentService {
    private static final String LOG_TAG = "IOAPP_StateMachineJobService";


    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        PowerManager pm = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);

        PowerManager.WakeLock wakeLock = null;
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, getPackageName() + ":Foreground");
        }

        try {
            if ( wakeLock != null ){
                wakeLock.acquire(10*60*1000L /*10 minutes*/);
            }
        }catch (Exception e){
            Logger.i(LOG_TAG, "Exception wakeLock acquire " + e.getMessage());
        }
        WorkerQueue.getInstance(getApplicationContext()).foregroundRun();
        try {
            if (wakeLock != null) {
                wakeLock.release();
            }
        }catch (Exception e){
            Logger.i(LOG_TAG, "Exception wakeLock release " + e.getMessage());
        }
    }

    @Override
    public boolean onStopCurrentWork() {
        WorkerQueue.getInstance(getApplicationContext()).foregroundStop();
        return true;
    }
}
