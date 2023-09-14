package io.appservice.core.timer;

import android.content.Context;
import android.content.Intent;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.appservice.core.util.Logger;

public class TimerShort implements Timer {

    private static final String LOG_TAG = "IOAPP_TimerShort";

    private Context mCtx;
    private static final int TIMER_THRESHOLD = 500;
    private CountDownLatch mLatch;
    private Long mCurrent = Long.MAX_VALUE;
    private Runnable mRunnable = null;

    protected TimerShort(Context ctx){
        mCtx = ctx;
    }

    @Override
    public void start(Long timestamp, Runnable runnable) {
        synchronized (this){
            if ( timestamp >= mCurrent ){
                return;
            }
            Logger.i(LOG_TAG, "timer short start " + (timestamp-System.currentTimeMillis()));
            mCurrent = timestamp;
            mRunnable = runnable;
            if ( mLatch != null ) {
                mLatch.countDown();
                mLatch = null;
            }
        }
        mLatch = new CountDownLatch(1);
        TimerShortJob.enqueueWork(mCtx, TimerShortJob.class, 98457, new Intent().putExtra("timestamp", timestamp));
    }

    @Override
    public void stop(Long timestamp) {
        synchronized (this) {
            if (!mCurrent.equals(timestamp)) {
                return;
            }
            mCurrent = Long.MAX_VALUE;
            mRunnable = null;
            if ( mLatch != null ) {
                mLatch.countDown();
                mLatch = null;
            }
        }
    }

    protected void handle(Intent intent){
        Runnable runnable = mRunnable;
        Long ts = intent.getLongExtra("timestamp", Long.MAX_VALUE);
        if ( !mCurrent.equals(ts)){
            return;
        }
        CountDownLatch latch = mLatch;
        try {
            if (latch != null) {
                long dt = ts - System.currentTimeMillis();
                if (dt > 0) {
                    latch.await(dt, TimeUnit.MILLISECONDS);
                }
            }
            synchronized (this) {
                if (mCurrent.equals(ts)) {
                    mCurrent = Long.MAX_VALUE;
                    mLatch = null;
                    if (runnable != null) {
                        runnable.run();
                    }
                }

            }

        }catch (Exception e){
            Logger.i(LOG_TAG, "Exception: " + e.getMessage());
        }
    }

    @Override
    public void dump(){
        if ( mCurrent != Long.MAX_VALUE ){
            Logger.i(LOG_TAG, "Timer short fire in " + (mCurrent - System.currentTimeMillis()) + "ms");
        }else{
            Logger.i(LOG_TAG, "Timer short is not running");
        }
    }
}
