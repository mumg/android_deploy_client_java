package io.appservice.core.statemachine;

import android.content.Context;
import android.content.Intent;
import android.util.Pair;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.appservice.core.util.Logger;

public class State {

    private static final String LOG_TAG = "IOAPP_State";

    private final Map<String, Pair<Method, Boolean>> mEventHandlers = new HashMap<>();
    private Method mEntry = null;
    private Method mExit = null;
    private boolean mForeground = false;
    private boolean mStore = true;


    protected State() {

    }

    protected void setEntry(Method entry) {
        this.mEntry = entry;
    }

    protected void setExit(Method exit) {
        this.mExit = exit;
    }

    protected void setForeground(boolean state) {
        mForeground = state;
    }

    protected boolean isForeground() {
        return mForeground;
    }

    protected void setStore(boolean state) {
        mStore = state;
    }

    protected boolean doStore() {
        return mStore;
    }

    protected void addEventHandler(String id, Boolean external, Method handler) {
        mEventHandlers.put(id, new Pair<>(handler, external));
    }

    protected Integer entry(StateContext ctx, Context context) throws Exception {
        ctx.setForeground(mForeground);
        if (mEntry != null) {
            return (Integer) mEntry.invoke(ctx, context);
        }
        return StateContext.SAME_STATE;
    }

    protected void exit(StateContext ctx, Context context) throws Exception {
        if (mExit != null) {
            mExit.invoke(ctx, context);
        }
    }


    protected Integer handle(StateContext context, Context ctx, Intent intent) throws Exception {
        if (intent == null || intent.getAction() == null) {
            return StateContext.SAME_STATE;
        }
        Method handler = null;
        synchronized (mEventHandlers) {
            Pair<Method, Boolean> hdl = mEventHandlers.get(intent.getAction());
            if (hdl == null) {
                hdl = mEventHandlers.get("*");
            }
            if (hdl != null) {
                handler = hdl.first;
            }
        }
        if (handler != null) {
            return (Integer) handler.invoke(context, ctx, intent);
        }else{
            Logger.e(LOG_TAG, "Unhandled event " + intent.getAction() + " in state " + context.getCurrentState() + " for " + context.getClass().getName());
        }
        return StateContext.SAME_STATE;
    }

    protected void getEvents(Set<String> external, Set<String> local) {
        if (mEventHandlers.size() > 0) {
            for (Map.Entry<String, Pair<Method, Boolean>> event : mEventHandlers.entrySet()) {
                if (event.getValue().second || event.getKey().startsWith("android.")) {
                    external.add(event.getKey());
                } else {
                    local.add(event.getKey());
                }
            }
        }
    }

}

