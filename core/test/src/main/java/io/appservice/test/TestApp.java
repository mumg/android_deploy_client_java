package io.appservice.test;

import android.content.Intent;

import io.appservice.core.CoreApp;
import io.appservice.core.statemachine.StateMachineList;
import io.appservice.core.util.Logger;
import io.appservice.test.logic.Test1;
import io.appservice.test.logic.Test2;
import io.appservice.test.logic.Test3;
import io.appservice.test.logic.Test4;


public class TestApp extends CoreApp {

    @Override
    public int getStorageVersion(){
        return 1;
    }

    @Override
    public void init(StateMachineList storage) {
        Intent intent = new Intent();
        intent.putExtra("debug", true);
        Logger.setup(getApplicationContext(), intent);
        storage.add(Test1.class);
        storage.add(Test2.class);
        storage.add(Test3.class);
        storage.add(Test4.class);
    }

}