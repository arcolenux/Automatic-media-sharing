package com.penguin.app.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper for inspecting and requesting required Android permissions across API levels.
 */
public final class PermissionHelper {

    private PermissionHelper() {
        // Utility class
    }

    /**
     * Returns true if camera permission is granted.
     */
    public static boolean hasCameraPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Returns true if storage/media permission for reading camera photos is granted.
     */
    public static boolean hasMediaPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * Returns true if notification permission is granted (API 33+), always true on older APIs.
     */
    public static boolean hasNotificationPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Returns true if all Nearby Connections and Bluetooth/Wi-Fi permissions are granted.
     */
    public static boolean hasNearbyPermissions(Context context) {
        for (String permission : getRequiredNearbyPermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if all permissions required for full operation are granted.
     */
    public static boolean hasAllRequiredPermissions(Context context) {
        return hasMediaPermission(context)
                && hasNotificationPermission(context)
                && hasNearbyPermissions(context);
    }

    /**
     * Gets the list of required Nearby permissions based on current Android version.
     */
    public static String[] getRequiredNearbyPermissions() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 & 12L (API 31-32)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            // Android 8.0 - 11 (API 26-30)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
            permissions.add(Manifest.permission.BLUETOOTH);
            permissions.add(Manifest.permission.BLUETOOTH_ADMIN);
            permissions.add(Manifest.permission.ACCESS_WIFI_STATE);
            permissions.add(Manifest.permission.CHANGE_WIFI_STATE);
        }

        return permissions.toArray(new String[0]);
    }

    /**
     * Gets the list of all missing permissions required to start Auto-Share or Nearby sessions.
     */
    public static String[] getMissingPermissions(Context context) {
        List<String> missing = new ArrayList<>();

        // Media Images
        if (!hasMediaPermission(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                missing.add(Manifest.permission.READ_MEDIA_IMAGES);
            } else {
                missing.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        // Notifications
        if (!hasNotificationPermission(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                missing.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // Nearby
        for (String permission : getRequiredNearbyPermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                if (!missing.contains(permission)) {
                    missing.add(permission);
                }
            }
        }

        return missing.toArray(new String[0]);
    }

    public static boolean shouldShowRationale(Activity activity, String[] permissions) {
        for (String perm : permissions) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)) {
                return true;
            }
        }
        return false;
    }
}
