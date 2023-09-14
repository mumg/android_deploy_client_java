package io.appservice.core;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import io.appservice.core.statemachine.StateMachineList;
import io.appservice.core.statemachine.StateProcessQueue;
import io.appservice.core.support.WorkerQueue;
import io.appservice.core.util.Logger;



@SuppressWarnings("unused")
public abstract class CoreApp extends Application {
    private static final String LOG_TAG = "IOAPP_App";

    public static final String PERMISSIONS_CHANGED_ACTION = "io.appservice.core.PERMISSIONS_CHANGED";

    private final Map < Class <?> , Object > mSingletons = new HashMap<>();

    public static <T> T getSingleton(Context ctx, Class <T> clz){
        CoreApp app = getIntance(ctx);
        synchronized (app.mSingletons){
            if ( app.mSingletons.containsKey(clz)){
                return clz.cast(app.mSingletons.get(clz));
            }
            Object instance;
            try {
                Constructor c = clz.getDeclaredConstructor(Context.class);
                c.setAccessible(true);
                instance = c.newInstance(ctx);
            }catch (Exception e){
                try {
                    Constructor c = clz.getDeclaredConstructor();
                    c.setAccessible(true);
                    instance = c.newInstance();
                }catch (Exception ignore){
                    return null;
                }
            }
            T res = clz.cast(instance);
            app.mSingletons.put(clz, res);
            return res;
        }
    }


    @SuppressWarnings("unchecked")
    public static <T extends CoreApp> T getIntance(Context ctx) {
        return (T) ctx.getApplicationContext();
    }

    private String mAppId;
    private StateMachineList mSMList;

    public String getAppId() {
        return mAppId;
    }

    public StateMachineList getSM() {
        return mSMList;
    }

    public abstract void init(StateMachineList list);

    public void start() {

    }

    private WorkerQueue mProcessQueue;

    public StateProcessQueue getBackgroundQueue(){
        return mProcessQueue;
    }

    public abstract int getStorageVersion();

    @Override
    public void onCreate() {
        super.onCreate();

        SharedPreferences prefs = getApplicationContext().getSharedPreferences("app", MODE_PRIVATE);
        if (prefs.contains("id")) {
            mAppId = prefs.getString("id", null);
        }
        if (mAppId == null) {
            mAppId = UUID.randomUUID().toString();
            prefs.edit().putString("id", mAppId).apply();
        }
        Logger.init(getApplicationContext());
        try {
            mSMList = new StateMachineList(getBaseContext(),
                    WorkerQueue.getInstance(getApplicationContext()), getStorageVersion());
            init(mSMList);
            mSMList.register(getApplicationContext());
            WorkerQueue.getInstance(getApplicationContext()).start();
            start();
        } catch (Exception e) {
            Logger.i(LOG_TAG, e.getMessage());
        }
    }


    @Override
    public void onTerminate() {
        try {
            BroadcastManager.getInstance(getApplicationContext()).unregisterAll(getApplicationContext());
        }catch (Exception ignore){}
        Logger.d(LOG_TAG, "AppInstance onTerminate()");
        super.onTerminate();
    }


}