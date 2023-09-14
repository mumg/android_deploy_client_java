package io.appservice.core.statemachine;

import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

import io.appservice.core.util.Logger;

public class StateMachineList {

    private static final String LOG_TAG = "IOAPP_StateMachineList";

    private List<StateMachine> mMachines = new ArrayList<>();
    private StateProcessQueue mQueue;
    private StateContextStorage mStorage;
    private Context mCtx;

    public StateMachineList(Context ctx, StateProcessQueue queue, int version) {
        mCtx = ctx;
        mStorage = new StateContextStorage(ctx, version);
        mQueue = queue;
    }

    public void add(Class<? extends StateContext> context) {
        try {
            StateMachine sm = new StateMachine();
            sm.init(mCtx, mQueue, mStorage, context);
            mMachines.add(sm);
        } catch (Exception e) {
            Logger.i(LOG_TAG, "Could not add context " + context.getName());
        }
    }

    public boolean isForeground() {
        for (StateMachine sm : mMachines) {
            if (sm.isForeground()) {
                return true;
            }
        }
        return false;
    }

    public void register(Context ctx) throws Exception{
        for (StateMachine sm : mMachines) {
            sm.register(ctx);
        }
    }
}
