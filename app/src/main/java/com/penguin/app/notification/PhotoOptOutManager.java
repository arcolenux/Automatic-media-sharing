package com.penguin.app.notification;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.penguin.app.PenguinApplication;
import com.penguin.app.R;
import com.penguin.app.db.AppDatabase;
import com.penguin.app.model.SharedPhoto;
import com.penguin.app.model.SyncStatus;
import com.penguin.app.transfer.TransferManager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the 5-second opt-out privacy window for detected camera photos.
 * Shows heads-up notifications with a "Cancel Sync" action.
 * Automatically approves and transfers the photo if not cancelled within 5 seconds.
 */
public class PhotoOptOutManager {

    private static final String TAG = "PhotoOptOutManager";
    public static final long OPT_OUT_DURATION_MS = 5000;

    private static volatile PhotoOptOutManager instance;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicInteger notificationIdGenerator = new AtomicInteger(1000);

    // Pending Photo Tracker: photoId -> PendingOptOut
    private static class PendingOptOut {
        final SharedPhoto photo;
        final int notificationId;
        final Runnable approvalRunnable;
        boolean cancelled = false;

        PendingOptOut(SharedPhoto photo, int notificationId, Runnable approvalRunnable) {
            this.photo = photo;
            this.notificationId = notificationId;
            this.approvalRunnable = approvalRunnable;
        }
    }

    private final Map<String, PendingOptOut> pendingMap = new ConcurrentHashMap<>();

    private PhotoOptOutManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public static PhotoOptOutManager getInstance(Context context) {
        if (instance == null) {
            synchronized (PhotoOptOutManager.class) {
                if (instance == null) {
                    instance = new PhotoOptOutManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Starts the 5-second opt-out window for a newly detected camera photo.
     */
    public void startOptOutWindow(SharedPhoto photo) {
        if (photo == null) return;

        String photoId = photo.getPhotoId();
        int notificationId = notificationIdGenerator.incrementAndGet();

        Log.d(TAG, "Starting 5-second opt-out window for photoId: " + photoId);

        // 1. Insert photo in database as QUEUED
        AppDatabase.databaseWriteExecutor.execute(() -> {
            photo.setSyncStatus(SyncStatus.QUEUED);
            photo.setOptedOut(false);
            PenguinApplication.getInstance().getDatabase().photoDao().insertPhoto(photo);
        });

        // 2. Post heads-up notification with Cancel Sync action
        showOptOutNotification(photo, notificationId);

        // 3. Schedule 5-second auto-approval
        Runnable approvalRunnable = () -> approvePhoto(photoId);
        PendingOptOut pending = new PendingOptOut(photo, notificationId, approvalRunnable);
        pendingMap.put(photoId, pending);

        handler.postDelayed(approvalRunnable, OPT_OUT_DURATION_MS);
    }

    /**
     * Cancels the photo sync before the 5-second window expires.
     */
    public void cancelPhotoSync(String photoId, int notificationId) {
        PendingOptOut pending = pendingMap.remove(photoId);
        if (pending != null) {
            pending.cancelled = true;
            handler.removeCallbacks(pending.approvalRunnable);
        }

        dismissNotification(notificationId);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            PenguinApplication.getInstance().getDatabase().photoDao().setOptedOut(photoId, true);
            Log.d(TAG, "Photo " + photoId + " successfully CANCELLED / OPTED-OUT by user");
        });
    }

    /**
     * Approves and transfers the photo after 5 seconds of no cancellation.
     */
    private void approvePhoto(String photoId) {
        PendingOptOut pending = pendingMap.remove(photoId);
        if (pending == null || pending.cancelled) {
            return;
        }

        dismissNotification(pending.notificationId);
        Log.d(TAG, "Photo " + photoId + " APPROVED after 5-second window. Triggering transfer...");

        TransferManager.getInstance(context).shareApprovedPhoto(pending.photo);
    }

    private void showOptOutNotification(SharedPhoto photo, int notificationId) {
        Intent cancelIntent = new Intent(context, PhotoOptOutReceiver.class);
        cancelIntent.setAction(PhotoOptOutReceiver.ACTION_CANCEL_SYNC);
        cancelIntent.putExtra(PhotoOptOutReceiver.EXTRA_PHOTO_ID, photo.getPhotoId());
        cancelIntent.putExtra(PhotoOptOutReceiver.EXTRA_NOTIFICATION_ID, notificationId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent cancelPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId,
                cancelIntent,
                flags
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, PenguinApplication.CHANNEL_OPT_OUT_ID)
                .setSmallIcon(R.drawable.ic_penguin_logo)
                .setContentTitle(context.getString(R.string.opt_out_title))
                .setContentText(context.getString(R.string.opt_out_text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setTimeoutAfter(OPT_OUT_DURATION_MS)
                .addAction(
                        R.drawable.ic_close,
                        context.getString(R.string.cancel_sync),
                        cancelPendingIntent
                );

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        } catch (SecurityException e) {
            Log.e(TAG, "Notification permission missing or denied", e);
        }
    }

    private void dismissNotification(int notificationId) {
        try {
            NotificationManagerCompat.from(context).cancel(notificationId);
        } catch (Exception ignored) {
        }
    }
}
