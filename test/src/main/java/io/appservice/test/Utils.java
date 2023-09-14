package io.appservice.test;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import java.util.List;

public class Utils {
    public static String findPackageName(Context ctx, String regexp) {
        List<ApplicationInfo> packages;
        PackageManager pm;

        pm = ctx.getPackageManager();
        packages = pm.getInstalledApplications(0);
        for (ApplicationInfo packageInfo : packages) {
            if (packageInfo.packageName.matches(regexp)) {
                return packageInfo.packageName;
            }
        }
        return null;
    }
}
