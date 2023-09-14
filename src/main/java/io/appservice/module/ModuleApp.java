package io.appservice.module;


import com.crashlytics.android.Crashlytics;

import io.appservice.core.CoreApp;
import io.appservice.core.statemachine.StateMachineList;
import io.appservice.module.logic.Confirm;
import io.appservice.module.logic.TaskLoader;
import io.appservice.module.logic.Tracker;
import io.appservice.module.logic.Updater;
import io.fabric.sdk.android.Fabric;


public class ModuleApp extends CoreApp {

    private Device device = null;


    @Override
    public int getStorageVersion() {
        return 4;
    }

    @Override
    public void init(StateMachineList storage) {
        Fabric.with(this, new Crashlytics());
        KeepAliveJob.start(getApplicationContext());
        storage.add(Tracker.class);
        storage.add(TaskLoader.class);
        storage.add(Updater.class);
        storage.add(Confirm.class);
    }

    public Device getDevice(){
        if ( device == null){
            device = new AndroidDevice(getApplicationContext());
        }
        return device;
    }

}
