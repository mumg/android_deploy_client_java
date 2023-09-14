package io.appservice.test;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.appservice.module.IAppService;

public class TestWebViewActivity extends Activity{

    private static final String INTENT = "io.appservice.test.RESPONSE";

    private static Map< String , Integer > mResponses;

    static{
        mResponses = new HashMap<>();
        mResponses.put("Pending", 0 );
        mResponses.put("Showing", 0);
        mResponses.put("Error", 2);
        mResponses.put("Done", 1);
        mResponses.put("Timeout", 1);
    }

    private ServiceConnection mConnection;
    private IAppService mAppService;
    private Handler  mHandler = new Handler();
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Integer result = mResponses.get(intent.getStringExtra("status"));
            if ( result != null ){
                if ( result == 0){
                    return;
                }
                if ( result == 2 ){
                    finishActivity(-2);
                }
                if ( result == 1){
                    finishActivity(0);
                }
            }


        }
    };

    @Override
    public void finishActivity(int requestCode) {
        String intent = getIntent().getStringExtra("intent");
        if (intent != null ){
            getApplicationContext().sendBroadcast(new Intent(intent).putExtra("result", requestCode));
        }
        finish();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.debug);
        mConnection =  new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                Log.i("IOAPP_Test", "Service connected");
                mAppService = IAppService.Stub.asInterface(service);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if ( mAppService != null ){
                            try {
                                mAppService.show(UUID.randomUUID().toString(),
                                        "https://google.com",
                                        2,
                                        "rt",
                                        new int[]{10, 10, 10, 10},
                                        INTENT,
                                        null,
                                        null,
                                        30000);
                            }catch (Exception e){
                                Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
                });
            }
            public void onServiceDisconnected(ComponentName className) {
                Log.i("IOAPP_Test", "Service disconnected");
                mAppService = null;
            }
        };
        String pkg = Utils.findPackageName(getApplicationContext(), "io\\.appservice\\.module.*");
        if ( pkg == null){
            finishActivity(-1);
        }else{
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(pkg, "io.appservice.module.AppService"));
            getApplicationContext().bindService(intent, mConnection,
                    Context.BIND_AUTO_CREATE);
            getApplicationContext().registerReceiver(mReceiver, new IntentFilter(INTENT));
        }
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        getApplicationContext().unregisterReceiver(mReceiver);
        getApplicationContext().unbindService(mConnection);
    }
}