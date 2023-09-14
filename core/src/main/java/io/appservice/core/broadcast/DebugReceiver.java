package io.appservice.core.broadcast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import io.appservice.core.util.Logger;

public class DebugReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("IOAPP_Debug", "received debug intent");
        Logger.setup(context, intent);
    }
}
