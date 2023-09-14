package io.appservice.test.logic;

import android.content.Context;

import io.appservice.core.statemachine.StateContext;
import io.appservice.core.statemachine.annotations.StateContextSettings;
import io.appservice.core.statemachine.annotations.StateEntry;
import io.appservice.core.statemachine.annotations.StateExit;
import io.appservice.core.statemachine.annotations.StateThread;
import io.appservice.core.statemachine.annotations.StateTimer;
import io.appservice.core.util.Logger;

@StateContextSettings(
        store = false
)
public class Test4 extends StateContext {

    private static final String LOG_TAG = "IOAPP_Test";

    private static final int INIT = 0;
    private static final int BACKGROUND = 1;
    private static final int FOREGROUND = 2;
    private static final int FOREGROUND2 = 3;

    @StateEntry(states = {INIT})
    private Integer initEntry(Context ctx){
        return FOREGROUND;
    }

    @StateEntry(states = {BACKGROUND})
    private Integer bacgroundEntry(Context ctx){
        Logger.i(LOG_TAG, "Background entry");
        return SAME_STATE;
    }

    @StateTimer(states = {BACKGROUND}, timeout = 90000, id = "timer")
    private Integer backgroundTimer(Context ctx){
        Logger.i(LOG_TAG, "Background timer fired");
        return FOREGROUND;
    }

    @StateExit(states = {BACKGROUND})
    private void backgroundExit(Context ctx){
        Logger.i(LOG_TAG, "Background exit");
    }

    @StateEntry(states = {FOREGROUND}, foreground = true)
    private Integer foregroundEntry(Context ctx){
        Logger.i(LOG_TAG, "Begin foreground");
        return SAME_STATE;
    }

    @StateExit(states = {FOREGROUND})
    private void foregroundExit(Context ctx){
        Logger.i(LOG_TAG, "End foreground");
    }

    @StateThread(states = {FOREGROUND})
    private class foregroundThread implements StateContextThread{

        private final Object mWait = new Object();

        @Override
        public void run(Context ctx) {
            try {
                Logger.i(LOG_TAG, "Start foreground thread");
                synchronized (mWait) {
                    mWait.wait();
                }
                Logger.i(LOG_TAG, "Stop foreground thread");
            } catch (Exception e){
                Logger.i(LOG_TAG, "Exception in foreground thread " + e.getMessage());
            }
        }

        @Override
        public void stop() {
            synchronized (mWait){
                mWait.notify();
            }
        }
    }

    @StateTimer(states = {FOREGROUND}, timeout = 4000, id = "timer2")
    private Integer foregroundTimer(Context ctx){
        Logger.i(LOG_TAG, "Foreground timer fired");
        return FOREGROUND2;
    }

    @StateThread(states = {FOREGROUND2},
        onSuccessState = BACKGROUND
    )
    private class foreground2Thread implements StateContextThread{

        @Override
        public void run(Context ctx) {
            try {
                Logger.i(LOG_TAG, "Start foreground2 thread");
                Thread.sleep(1000);
                Logger.i(LOG_TAG, "Stop foreground2 thread");
            } catch (Exception e){
                Logger.i(LOG_TAG, "Exception in foreground2 thread " + e.getMessage());
            }
        }

        @Override
        public void stop() {

        }
    }

}
