package io.appservice.module;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.Arrays;

import io.appservice.core.util.Logger;
import io.appservice.webview.WebViewJobService;

public class AppService  extends Service {
    private static final String LOG_TAG = "IOAPP_Service";
    private Binder mBinder = new IAppService.Stub() {


        @Override
        public boolean show(String id,
                            String url,
                            int closeSize,
                            String closePosition,
                            int [] margins,
                            String intent,
                            String pkg,
                            String cls,
                            long timeout) throws RemoteException {
            Logger.i(LOG_TAG, "Show webview " + url + " cs=" + closeSize + " cp=" + closePosition + " m=" + Arrays.toString(margins) + " id=" + id );
            WebViewJobService.WebViewTask task = new WebViewJobService.WebViewTask(url,
                    closeSize,
                    WebViewJobService.WebViewTask.ClosePosition.valueOf(closePosition),
                    margins,
                    intent,
                    pkg,
                    cls,
                    id,
                    timeout);
            WebViewJobService.show(getApplicationContext(), task);
            return true;
        }

        @Override
        public void hide() throws RemoteException {
            Logger.i(LOG_TAG, "Hide webview");
            WebViewJobService.hide(getApplicationContext());
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Logger.d(LOG_TAG, "onStartCommand");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Logger.d(LOG_TAG, "onBind");
        return mBinder;
    }

}
