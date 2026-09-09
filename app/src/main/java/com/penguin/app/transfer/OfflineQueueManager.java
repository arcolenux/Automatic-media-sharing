package com.penguin.app.transfer;

import android.content.Context;
import android.util.Log;

import com.penguin.app.PenguinApplication;
import com.penguin.app.db.AppDatabase;
import com.penguin.app.model.SharedPhoto;
import com.penguin.app.model.SyncStatus;
import com.penguin.app.util.FileUtils;

import java.util.List;

/**
 * Manages the offline / local retry queue for photos awaiting Nearby peer availability.
 * Retries up to 5 attempts when peers become available. Never deletes photos on failure.
 */
public class OfflineQueueManager {

    private static final String TAG = "OfflineQueueManager";
    public static final int MAX_RETRY_ATTEMPTS = 5;

    private static volatile OfflineQueueManager instance;
    private final Context context;
    private final NearbyConnectionsManager nearbyManager;

    private OfflineQueueManager(Context context) {
        this.context = context.getApplicationContext();
        this.nearbyManager = NearbyConnectionsManager.getInstance(this.context);
    }

    public static OfflineQueueManager getInstance(Context context) {
        if (instance == null) {
            synchronized (OfflineQueueManager.class) {
                if (instance == null) {
                    instance = new OfflineQueueManager(context);
                }
            }
        }
        return instance;
    }

    /**
     * Enqueues a photo for transfer. If peers are currently connected, begins transfer immediately.
     */
    public void enqueuePhoto(SharedPhoto photo) {
        if (photo == null || photo.isOptedOut()) return;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            photo.setSyncStatus(SyncStatus.QUEUED);
            PenguinApplication.getInstance().getDatabase().photoDao().insertPhoto(photo);

            if (nearbyManager.isConnectedToAnyPeer()) {
                Log.d(TAG, "Peers available, attempting immediate transfer for photoId: " + photo.getPhotoId());
                nearbyManager.sendPhotoToAllConnectedPeers(photo);
            } else {
                Log.d(TAG, "No peers currently connected. Photo " + photo.getPhotoId() + " remains QUEUED");
            }
        });
    }

    /**
     * Called when a peer connects or reconnects to flush queued and eligible failed photos.
     */
    public void flushQueueForSession(String sessionId) {
        if (sessionId == null) return;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<SharedPhoto> pendingPhotos = PenguinApplication.getInstance().getDatabase()
                    .photoDao().getPendingPhotosForSession(sessionId);

            Log.d(TAG, "Flushing offline queue: " + pendingPhotos.size() + " pending photos found for session: " + sessionId);

            for (SharedPhoto photo : pendingPhotos) {
                if (photo.getRetryCount() < MAX_RETRY_ATTEMPTS && FileUtils.isValidImageFile(photo.getLocalFilePath())) {
                    Log.d(TAG, "Retrying transfer for photo: " + photo.getPhotoId() + " (Attempt #" + (photo.getRetryCount() + 1) + ")");
                    nearbyManager.sendPhotoToAllConnectedPeers(photo);
                }
            }
        });
    }

    /**
     * Manually triggers a retry for a specific photo (e.g. user taps retry on a failed gallery item).
     */
    public void retryPhoto(String photoId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            SharedPhoto photo = PenguinApplication.getInstance().getDatabase().photoDao().getPhotoById(photoId);
            if (photo != null && FileUtils.isValidImageFile(photo.getLocalFilePath())) {
                photo.setSyncStatus(SyncStatus.QUEUED);
                PenguinApplication.getInstance().getDatabase().photoDao().updateSyncStatus(photoId, SyncStatus.QUEUED, 0);
                nearbyManager.sendPhotoToAllConnectedPeers(photo);
            }
        });
    }
}
