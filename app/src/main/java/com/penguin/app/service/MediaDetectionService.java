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
 * Foreground Service that monitors the device's MediaStore for newly captured camera photos
 * while Auto-Share is active. Hands off valid photos to PhotoOptOutManager.
 */
public class MediaDetectionService extends Service implements CameraContentObserver.PhotoDetectionCallback {

    private static final String TAG = "MediaDetectionService";
    private static final int SERVICE_NOTIFICATION_ID = 999;

    public static final String ACTION_START_DETECTION = "com.penguin.app.ACTION_START_DETECTION";
    public static final String ACTION_STOP_DETECTION = "com.penguin.app.ACTION_STOP_DETECTION";
    public static final String EXTRA_SESSION_ID = "extra_session_id";
    public static final String EXTRA_SESSION_NAME = "extra_session_name";

    private CameraContentObserver cameraObserver;
    private String currentSessionId;
    private String currentSessionName;
    private boolean isObserving = false;

    public static void start(Context context, String sessionId, String sessionName) {
        Intent intent = new Intent(context, MediaDetectionService.class);
        intent.setAction(ACTION_START_DETECTION);
        intent.putExtra(EXTRA_SESSION_ID, sessionId);
        intent.putExtra(EXTRA_SESSION_NAME, sessionName);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stop(Context context) {
        Intent intent = new Intent(context, MediaDetectionService.class);
        intent.setAction(ACTION_STOP_DETECTION);
        context.startService(intent);
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
            if (ACTION_START_DETECTION.equals(action)) {
                currentSessionId = intent.getStringExtra(EXTRA_SESSION_ID);
                currentSessionName = intent.getStringExtra(EXTRA_SESSION_NAME);
                if (currentSessionName == null) {
                    currentSessionName = "Active Session";
                }

                startForegroundServiceNotification();
                registerCameraObserver();

            } else if (ACTION_STOP_DETECTION.equals(action)) {
                unregisterCameraObserver();
                stopForeground(true);
                stopSelf();
            }
        }
        return START_STICKY;
    }

    private void startForegroundServiceNotification() {
        Intent sessionIntent = new Intent(this, SessionActivity.class);
        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, sessionIntent, pendingFlags);

        String contentText = getString(R.string.service_notification_text, currentSessionName);

        Notification notification = new NotificationCompat.Builder(this, PenguinApplication.CHANNEL_SERVICE_ID)
                .setSmallIcon(R.drawable.ic_penguin_logo)
                .setContentTitle(getString(R.string.service_notification_title))
                .setContentText(contentText)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SERVICE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, notification);
        }
    }

    private void registerCameraObserver() {
        if (!isObserving && cameraObserver != null) {
            cameraObserver.setBaselineTimestamp(System.currentTimeMillis());
            getContentResolver().registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    cameraObserver
            );
            isObserving = true;
            Log.d(TAG, "CameraContentObserver registered successfully on EXTERNAL_CONTENT_URI");
        }
    }

    private void unregisterCameraObserver() {
        if (isObserving && cameraObserver != null) {
            getContentResolver().unregisterContentObserver(cameraObserver);
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

        Log.d(TAG, "Processing newly detected camera photo: " + photoFilePath);

        String photoId = UUID.randomUUID().toString();
        String myDeviceId = PenguinApplication.getInstance().getAppDeviceId();
        String myName = PenguinApplication.getInstance().getUserName();

        int[] dims = FileUtils.getImageDimensions(photoFilePath);
        long fileSize = new File(photoFilePath).length();

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
                dims[0],
                dims[1],
                fileSize
        );

        // Enter the 5-second opt-out window
        PhotoOptOutManager.getInstance(this).startOptOutWindow(photo);
    }

    @Override
    public void onDestroy() {
        unregisterCameraObserver();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
