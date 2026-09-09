package com.penguin.app;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.penguin.app.db.AppDatabase;

import java.io.File;
import java.util.UUID;

/**
 * Main Application class for PENGUIN.
 * Initializes notification channels, device identity, and local storage.
 */
public class PenguinApplication extends Application {

    public static final String CHANNEL_SERVICE_ID = "penguin_service_channel";
    public static final String CHANNEL_OPT_OUT_ID = "penguin_opt_out_channel";

    private static final String PREFS_NAME = "penguin_prefs";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_USER_NAME = "user_name";

    private static PenguinApplication instance;
    private String deviceId;
    private String userName;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        initDeviceId();
        createNotificationChannels();
        initStorageDirectory();
    }

    public static PenguinApplication getInstance() {
        return instance;
    }

    public AppDatabase getDatabase() {
        return AppDatabase.getInstance(this);
    }

    private void initDeviceId() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        deviceId = prefs.getString(KEY_DEVICE_ID, null);
        if (deviceId == null) {
            deviceId = "PENGUIN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply();
        }

        userName = prefs.getString(KEY_USER_NAME, null);
        if (userName == null) {
            userName = "Peer-" + deviceId.substring(deviceId.length() - 4);
            prefs.edit().putString(KEY_USER_NAME, userName).apply();
        }
    }

    public String getAppDeviceId() {
        return deviceId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String name) {
        this.userName = name;
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_USER_NAME, name)
                .apply();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                // Foreground service channel
                NotificationChannel serviceChannel = new NotificationChannel(
                        CHANNEL_SERVICE_ID,
                        getString(R.string.notification_channel_detection),
                        NotificationManager.IMPORTANCE_LOW
                );
                serviceChannel.setDescription(getString(R.string.notification_channel_detection_desc));
                serviceChannel.setShowBadge(false);
                manager.createNotificationChannel(serviceChannel);

                // High-priority 5-second opt-out notification channel
                NotificationChannel optOutChannel = new NotificationChannel(
                        CHANNEL_OPT_OUT_ID,
                        getString(R.string.notification_channel_opt_out),
                        NotificationManager.IMPORTANCE_HIGH
                );
                optOutChannel.setDescription(getString(R.string.notification_channel_opt_out_desc));
                optOutChannel.enableVibration(true);
                optOutChannel.setShowBadge(true);
                manager.createNotificationChannel(optOutChannel);
            }
        }
    }

    private void initStorageDirectory() {
        File dir = getSharedPhotosDirectory();
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    public File getSharedPhotosDirectory() {
        File base = getExternalFilesDir(null);
        if (base == null) {
            base = getFilesDir();
        }
        return new File(base, "shared_photos");
    }
}
