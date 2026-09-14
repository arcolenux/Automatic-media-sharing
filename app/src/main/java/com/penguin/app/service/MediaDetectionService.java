package com.penguin.app.service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.penguin.app.PenguinApplication;
import com.penguin.app.R;
import com.penguin.app.activity.SessionActivity;
import com.penguin.app.model.SharedPhoto;
import com.penguin.app.model.SyncStatus;
import com.penguin.app.notification.PhotoOptOutManager;
import com.penguin.app.observer.CameraContentObserver;
import com.penguin.app.util.FileUtils;

import java.io.File;
import java.util.UUID;

/**
 * Foreground Service that maintains the app's background network presence during an active session.
 * When Auto-Share is enabled, it also monitors MediaStore for newly captured camera photos
 * and hands off valid photos to PhotoOptOutManager.
 */
public class MediaDetectionService extends Service implements CameraContentObserver.PhotoDetectionCallback {

    private static final String TAG = "MediaDetectionService";
    private static final int SERVICE_NOTIFICATION_ID = 999;

    public static final String ACTION_START_SESSION_SERVICE = "com.penguin.app.ACTION_START_SESSION_SERVICE";
    public static final String ACTION_UPDATE_AUTO_SHARE = "com.penguin.app.ACTION_UPDATE_AUTO_SHARE";
    public static final String ACTION_STOP_SESSION_SERVICE = "com.penguin.app.ACTION_STOP_SESSION_SERVICE";

    public static final String EXTRA_SESSION_ID = "extra_session_id";
    public static final String EXTRA_SESSION_NAME = "extra_session_name";
    public static final String EXTRA_AUTO_SHARE_ENABLED = "extra_auto_share_enabled";

    private static volatile boolean isServiceRunning = false;
    private static volatile boolean isAutoShareActive = false;

    private CameraContentObserver cameraObserver;
    private String currentSessionId;
    private String currentSessionName;
    private boolean isObserving = false;

    public static boolean isRunning() {
        return isServiceRunning;
    }

    public static boolean isAutoShareEnabled() {
        return isAutoShareActive;
    }

    public static void start(Context context, String sessionId, String sessionName, boolean isAutoShareEnabled) {
        if (context == null) return;
        Intent intent = new Intent(context, MediaDetectionService.class);
        intent.setAction(ACTION_START_SESSION_SERVICE);
        intent.putExtra(EXTRA_SESSION_ID, sessionId);
        intent.putExtra(EXTRA_SESSION_NAME, sessionName);
        intent.putExtra(EXTRA_AUTO_SHARE_ENABLED, isAutoShareEnabled);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error starting MediaDetectionService", e);
        }
    }

    public static void setAutoShareEnabled(Context context, boolean isEnabled) {
        isAutoShareActive = isEnabled;
        if (context == null) return;
        Intent intent = new Intent(context, MediaDetectionService.class);
        intent.setAction(ACTION_UPDATE_AUTO_SHARE);
        intent.putExtra(EXTRA_AUTO_SHARE_ENABLED, isEnabled);
        try {
            if (isServiceRunning) {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error sending auto-share update to service", e);
        }
    }

    public static void stop(Context context) {
        isServiceRunning = false;
        isAutoShareActive = false;
        if (context != null) {
            Intent intent = new Intent(context, MediaDetectionService.class);
            intent.setAction(ACTION_STOP_SESSION_SERVICE);
            try {
                context.startService(intent);
            } catch (Exception e) {
                Log.e(TAG, "Error starting stop action on service", e);
            }

            android.app.NotificationManager notificationManager =
                    (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.cancel(SERVICE_NOTIFICATION_ID);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        cameraObserver = new CameraContentObserver(this, new Handler(Looper.getMainLooper()), this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_SESSION_SERVICE.equals(action)) {
                isServiceRunning = true;
                currentSessionId = intent.getStringExtra(EXTRA_SESSION_ID);
                currentSessionName = intent.getStringExtra(EXTRA_SESSION_NAME);
                if (currentSessionName == null || currentSessionName.isEmpty()) {
                    currentSessionName = "Active Session";
                }
                isAutoShareActive = intent.getBooleanExtra(EXTRA_AUTO_SHARE_ENABLED, false);

                startForegroundServiceNotification();

                if (isAutoShareActive) {
                    registerCameraObserver();
                } else {
                    unregisterCameraObserver();
                }

            } else if (ACTION_UPDATE_AUTO_SHARE.equals(action)) {
                isAutoShareActive = intent.getBooleanExtra(EXTRA_AUTO_SHARE_ENABLED, false);
                if (isAutoShareActive) {
                    registerCameraObserver();
                } else {
                    unregisterCameraObserver();
                }
                updateForegroundNotification();

            } else if (ACTION_STOP_SESSION_SERVICE.equals(action)) {
                isServiceRunning = false;
                isAutoShareActive = false;
                unregisterCameraObserver();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                } else {
                    stopForeground(true);
                }
                android.app.NotificationManager notificationManager =
                        (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                if (notificationManager != null) {
                    notificationManager.cancel(SERVICE_NOTIFICATION_ID);
                }
                stopSelf();
            }
        }
        return START_STICKY;
    }

    private Notification buildServiceNotification() {
        Intent sessionIntent = new Intent(this, SessionActivity.class);
        sessionIntent.putExtra(SessionActivity.EXTRA_SESSION_ID, currentSessionId);
        sessionIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, sessionIntent, pendingFlags);

        String sessName = currentSessionName != null && !currentSessionName.isEmpty() ? currentSessionName : "Active Session";
        String contentTitle = isAutoShareActive
                ? getString(R.string.service_notification_title)
                : getString(R.string.service_notification_title_paused, sessName);

        String contentText = isAutoShareActive
                ? getString(R.string.service_notification_text, sessName)
                : getString(R.string.service_notification_text_paused);

        return new NotificationCompat.Builder(this, PenguinApplication.CHANNEL_SERVICE_ID)
                .setSmallIcon(R.drawable.ic_penguin_logo)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void startForegroundServiceNotification() {
        Notification notification = buildServiceNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SERVICE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, notification);
        }
        android.app.NotificationManager notificationManager =
                (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(SERVICE_NOTIFICATION_ID, notification);
        }
    }

    private void updateForegroundNotification() {
        if (!isServiceRunning) return;
        Notification notification = buildServiceNotification();
        android.app.NotificationManager notificationManager =
                (android.app.NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(SERVICE_NOTIFICATION_ID, notification);
        }
    }

    private void registerCameraObserver() {
        if (!isObserving && cameraObserver != null) {
            cameraObserver.setBaselineTimestamp(System.currentTimeMillis());
            try {
                getContentResolver().registerContentObserver(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        true,
                        cameraObserver
                );
                isObserving = true;
                Log.d(TAG, "CameraContentObserver registered successfully on EXTERNAL_CONTENT_URI");
            } catch (Exception e) {
                Log.e(TAG, "Failed registering CameraContentObserver", e);
            }
        }
    }

    private void unregisterCameraObserver() {
        if (isObserving && cameraObserver != null) {
            try {
                getContentResolver().unregisterContentObserver(cameraObserver);
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering CameraContentObserver", e);
            }
            isObserving = false;
            Log.d(TAG, "CameraContentObserver unregistered");
        }
    }

    @Override
    public void onNewCameraPhotoDetected(String photoFilePath, long dateTakenMillis) {
        if (currentSessionId == null) {
            Log.w(TAG, "Photo detected but currentSessionId is null");
            return;
        }

        if (!isAutoShareActive) {
            Log.d(TAG, "Photo detected but Auto-Share is currently paused");
            return;
        }

        Log.d(TAG, "Camera photo captured: " + photoFilePath + " at " + dateTakenMillis);

        File originalFile = new File(photoFilePath);
        if (!originalFile.exists() || originalFile.length() == 0) {
            Log.w(TAG, "Photo file does not exist or is 0 bytes: " + photoFilePath);
            return;
        }

        String photoId = UUID.randomUUID().toString();
        String localUri = originalFile.toURI().toString();
        long fileSize = originalFile.length();
        String myDeviceId = PenguinApplication.getInstance().getAppDeviceId();
        String myName = PenguinApplication.getInstance().getUserName();

        SharedPhoto photo = new SharedPhoto(
                photoId,
                currentSessionId,
                myDeviceId,
                myName,
                photoFilePath,
                dateTakenMillis,
                0,
                SyncStatus.QUEUED,
                0,
                false,
                0,
                0,
                fileSize
        );

        Log.d(TAG, "Launching 5-second opt-out countdown for photo: " + photoId);
        PhotoOptOutManager.getInstance(this).startOptOutWindow(photo);
    }

    @Override
    public void onDestroy() {
        unregisterCameraObserver();
        isServiceRunning = false;
        isAutoShareActive = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
