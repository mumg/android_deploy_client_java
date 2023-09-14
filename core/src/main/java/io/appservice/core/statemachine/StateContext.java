package io.appservice.core.statemachine;

import android.content.Context;
import android.content.Intent;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import io.appservice.core.timer.TimerManager;


public class StateContext {
    private static final String LOG_TAG = "IOAPP_StateContext";
    protected Map<String, Long> mTimers = new HashMap<>();
    private Map<String, TimerDesc> mTimerDescs = new HashMap<>();
    protected Integer mCurrentState = -1;
    public static final int SAME_STATE = -1;
    private StateEventHandler mHandler;

    public Integer getCurrentState(){
        return mCurrentState;
    }

    public interface StateEventHandler {
        void pushEvent(Context ctx, Intent intent);
    }

    private boolean mForeground = false;

    protected void init(Context ctx) {
    }

    protected void addTimerDesc(TimerDesc desc) {
        mTimerDescs.put(desc.getId(), desc);
    }

    protected void setHandler(StateEventHandler handler) {
        mHandler = handler;
    }

    protected TimerDesc getTimerDesc(String id) {
        return mTimerDescs.get(id);
    }

    private class TimerScope implements TimerManager.Scope {
        private TimerDesc mDesc;

        private TimerScope(TimerDesc desc) {
            mDesc = desc;
        }

        @Override
        public boolean isValid() {
            boolean valid = mDesc.isActive(mCurrentState);
            if ( !valid){
                mTimers.remove(mDesc.getId());
            }
            return valid;
        }
    }

    protected void startTimers(Context ctx) throws Exception{
        for ( Map.Entry<String, TimerDesc>desc : mTimerDescs.entrySet() ){
            if ( desc.getValue().isActive(mCurrentState) && desc.getValue().getTimeout() != Long.MAX_VALUE){
                startTimer(ctx, desc.getKey(), desc.getValue().getTimeout());
            }
        }
    }

    protected boolean startTimer(Context ctx, String id, final Long timeout) throws Exception {
        TimerDesc desc = mTimerDescs.get(id);
        if (desc == null) {
            throw new RuntimeException("Timer handler not declared");
        }
        if (!desc.isActive(mCurrentState)) {
            throw new RuntimeException("Timer " + id + " not available in " + mCurrentState + " state");
        }
        TimerManager.getInstance(ctx).startTimer(getClass().getName(),
                id,
                System.currentTimeMillis() + timeout,
                new TimerScope(desc),
                new TimerManager.Setter() {
                    @Override
                    public void set(String id, Long timestamp) {
                        mTimers.put(id, timestamp);
                    }
                });
        return true;
    }

    protected boolean isForeground() {
        return mForeground;
    }

    protected void setForeground(boolean state) {
        mForeground = state;
    }

    public static class StateContextThreadAborted extends Exception {
        public StateContextThreadAborted() {
            super("StateContextThreadAborted");
        }
    }

    public interface StateContextThread {
        void run(Context ctx) throws Exception;

        void stop();
    }

    protected static class TimerDesc {
        private int mStates[];
        private String mId;
        private Method mMethod;
        private long mTimeout;

        protected TimerDesc(int states[], String id, Method method, long timeout) {
            mStates = states;
            mId = id;
            mMethod = method;
            mTimeout = timeout;
        }

        public String getId() {
            return mId;
        }

        public Method getHandler() {
            return mMethod;
        }

        public boolean isActive(int stateId) {
            for (int state : mStates) {
                if (state == stateId) {
                    return true;
                }
            }
            return false;
        }

        long getTimeout() {
            return mTimeout;
        }
    }

    public void pushEvent(Context ctx, Intent intent) {
        mHandler.pushEvent(ctx, intent);
    }


}
