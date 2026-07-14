package org.servalproject.permissions;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public final class RuntimePermissionGate {
    private RuntimePermissionGate() {
    }

    public static boolean hasPermissions(Context context, String[] permissions) {
        if (context == null || permissions == null || permissions.length == 0) {
            return true;
        }

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static String[] getMissingPermissions(Context context, String[] permissions) {
        if (context == null || permissions == null || permissions.length == 0) {
            return new String[0];
        }

        List<String> missingPermissions = new ArrayList<String>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }

        return missingPermissions.toArray(new String[0]);
    }

    public static void requestPermissions(Activity activity, String[] permissions, int requestCode) {
        if (activity == null || permissions == null || permissions.length == 0) {
            return;
        }

        ActivityCompat.requestPermissions(activity, permissions, requestCode);
    }

    public static boolean shouldShowAnyRequestPermissionRationale(Activity activity, String[] permissions) {
        if (activity == null || permissions == null || permissions.length == 0) {
            return false;
        }

        for (String permission : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                return true;
            }
        }

        return false;
    }

    public static boolean ensurePermissions(Activity activity, String[] permissions, int requestCode) {
        if (activity == null || permissions == null || permissions.length == 0) {
            return true;
        }

        String[] missingPermissions = getMissingPermissions(activity, permissions);
        if (missingPermissions.length > 0) {
            requestPermissions(activity, missingPermissions, requestCode);
            return false;
        }

        return true;
    }
}

