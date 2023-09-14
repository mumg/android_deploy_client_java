package io.appservice.module;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.appservice.module.liberty.R;

import static android.content.Context.MODE_PRIVATE;

public class AndroidDevice implements Device {
    private Context mContext;
    private UUID mId;

    public AndroidDevice(Context ctx){
        mContext = ctx;
        SharedPreferences settings = ctx.getSharedPreferences("settings", MODE_PRIVATE);
        String id = settings.getString("id", null);
        if ( id == null ){
            id = UUID.randomUUID().toString();
            SharedPreferences.Editor edit = settings.edit();
            edit.putString("id", id);
            edit.apply();
        }
        mId = UUID.fromString(id);

    }

    @Override
    public Info getDeviceInfo() {

        Info info = new Info();
        try {
            info.version = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionName;
        }catch (Exception e){
            info.version = "0000000";
        }
        info.cpu = Build.CPU_ABI;
        info.device = Build.DEVICE;
        info.hw = Build.HARDWARE;
        info.manufacturer = Build.MANUFACTURER;
        info.sn = Build.SERIAL;
        info.model = Build.MODEL;
        info.release = Build.VERSION.RELEASE;
        info.sdk = String.valueOf(Build.VERSION.SDK_INT);
        info.android_id = Settings.Secure.getString(mContext.getContentResolver(), Settings.Secure.ANDROID_ID);
        return info;
    }

    @Override
    public String getCustomerId() {
        return  mContext.getString(R.string.customer);
    }

    @Override
    public String getDeviceId() {
        return mId.toString();
    }

    @Override
    public String getPackageName() {
        return mContext.getPackageName();
    }

    private String getProperty(String name) {
        ArrayList<String> processList = new ArrayList<String>();
        String line;
        Pattern pattern = Pattern.compile("\\[(.+)\\]: \\[(.+)\\]");
        Matcher m;

        try {
            Process p = Runtime.getRuntime().exec("getprop");
            BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
            while ((line = input.readLine()) != null) {
                processList.add(line);
                m = pattern.matcher(line);
                if (m.find()) {
                    MatchResult result = m.toMatchResult();
                    if(result.group(1).equals(name))
                        return result.group(2);
                }
            }
            input.close();
        } catch (Exception err) {
            err.printStackTrace();
        }

        return null;
    }

    @Override
    public String getCarrier() {
        return getProperty("gsm.operator.alpha");
    }
}

