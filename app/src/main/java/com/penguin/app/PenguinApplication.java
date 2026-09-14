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

    private static final String PREF_PEER_NICKNAME_PREFIX = "peer_nickname_";

    public String getPeerCustomNickname(String deviceId) {
        if (deviceId == null) return null;
        return getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_PEER_NICKNAME_PREFIX + deviceId, null);
    }

    public void setPeerCustomNickname(String deviceId, String customName) {
        if (deviceId == null) return;
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit();
        if (customName == null || customName.trim().isEmpty()) {
            editor.remove(PREF_PEER_NICKNAME_PREFIX + deviceId);
        } else {
            editor.putString(PREF_PEER_NICKNAME_PREFIX + deviceId, customName.trim());
        }
        editor.apply();
    }

    public String getEffectiveMemberName(String deviceId, String defaultDisplayName) {
        if (deviceId != null && deviceId.equals(this.deviceId)) {
            return this.userName != null && !this.userName.isEmpty() ? this.userName : "You";
        }
        String custom = getPeerCustomNickname(deviceId);
        if (custom != null && !custom.trim().isEmpty()) {
            return custom.trim();
        }
        if (defaultDisplayName != null && !defaultDisplayName.trim().isEmpty()) {
            return defaultDisplayName.trim();
        }
        if (deviceId != null) {
            return "Peer " + deviceId.substring(Math.max(0, deviceId.length() - 4));
        }
        return "Nearby Peer";
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

    public File getSharedPhotosDirectory(String sessionName) {
        File picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES);
        File baseDir = new File(picturesDir, "PENGUIN");
        if (!baseDir.exists()) {
            boolean created = baseDir.mkdirs();
            if (!created) {
                File fallback = getExternalFilesDir(null);
                if (fallback == null) {
                    fallback = getFilesDir();
                }
                baseDir = new File(fallback, "shared_photos");
                if (!baseDir.exists()) {
                    baseDir.mkdirs();
                }
            }
        }

        if (sessionName != null && !sessionName.trim().isEmpty()) {
            String safeSessionName = sessionName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
            File sessionDir = new File(baseDir, safeSessionName);
            if (!sessionDir.exists()) {
                sessionDir.mkdirs();
            }
            if (sessionDir.exists()) {
                return sessionDir;
            }
        }

        return baseDir;
    }

    public File getSharedPhotosDirectory() {
        return getSharedPhotosDirectory(null);
    }
}
