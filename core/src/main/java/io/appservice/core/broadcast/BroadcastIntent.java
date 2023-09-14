package io.appservice.core.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.appservice.core.BroadcastManager;
import io.appservice.core.util.Logger;

public class BroadcastIntent extends BroadcastReceiver {

    private static  final String LOG_TAG = "IOAPP_BroadcastIntent";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            BroadcastManager mgr = BroadcastManager.getInstance(context);
            mgr.handle(context, intent);
        }catch (Exception e){
            Logger.i(LOG_TAG, "Exception occurred, processing " + intent.getAction() + " exception " + e.getMessage());
        }
    }
}
