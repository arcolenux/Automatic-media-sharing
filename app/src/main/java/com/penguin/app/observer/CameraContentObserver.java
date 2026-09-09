package com.penguin.app.observer;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import com.penguin.app.util.FileUtils;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Monitors MediaStore.Images.Media.EXTERNAL_CONTENT_URI for newly captured camera photos.
 * Only accepts DCIM/Camera photos captured after the established baseline timestamp.
 * Rejects screenshots, downloads, messaging media, and older photos.
 */
public class CameraContentObserver extends ContentObserver {

    private static final String TAG = "CameraObserver";
    private static final long DEBOUNCE_DELAY_MS = 500;

    private final Context context;
    private final ContentResolver contentResolver;
    private final PhotoDetectionCallback callback;
    private final Handler handler;

    private long baselineTimestampSeconds;
    private final Set<String> processedPhotoPaths = new HashSet<>();
    private Runnable pendingQueryRunnable;

    public interface PhotoDetectionCallback {
        void onNewCameraPhotoDetected(String photoFilePath, long dateTakenMillis);
    }

    public CameraContentObserver(Context context, Handler handler, PhotoDetectionCallback callback) {
        super(handler);
        this.context = context.getApplicationContext();
        this.contentResolver = this.context.getContentResolver();
        this.handler = handler != null ? handler : new Handler(Looper.getMainLooper());
        this.callback = callback;
        this.baselineTimestampSeconds = System.currentTimeMillis() / 1000;
    }

    public void setBaselineTimestamp(long timestampMillis) {
        this.baselineTimestampSeconds = timestampMillis / 1000;
        processedPhotoPaths.clear();
        Log.d(TAG, "Camera detection baseline established at: " + baselineTimestampSeconds);
    }

    @Override
    public void onChange(boolean selfChange, Uri uri) {
        super.onChange(selfChange, uri);
        Log.d(TAG, "MediaStore onChange triggered: " + uri);

        // 500ms debounce to allow the camera app to finish writing file to disk
        if (pendingQueryRunnable != null) {
            handler.removeCallbacks(pendingQueryRunnable);
        }

        pendingQueryRunnable = this::queryLatestCameraPhotos;
        handler.postDelayed(pendingQueryRunnable, DEBOUNCE_DELAY_MS);
    }

    private void queryLatestCameraPhotos() {
        String[] projection;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection = new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.RELATIVE_PATH,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.SIZE
            };
        } else {
            projection = new String[]{
                    MediaStore.Images.Media._ID,
                    MediaStore.Images.Media.DATA,
                    MediaStore.Images.Media.DATE_ADDED,
                    MediaStore.Images.Media.DATE_TAKEN,
                    MediaStore.Images.Media.MIME_TYPE,
                    MediaStore.Images.Media.SIZE
            };
        }

        // Query photos added after baseline timestamp
        String selection = MediaStore.Images.Media.DATE_ADDED + " >= ?";
        String[] selectionArgs = new String[]{String.valueOf(baselineTimestampSeconds)};
        String sortOrder = MediaStore.Images.Media.DATE_ADDED + " DESC";

        try (Cursor cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
        )) {
            if (cursor == null) {
                return;
            }

            int dataIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
            int relativePathIndex = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                    ? cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH) : -1;
            int dateTakenIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN);
            int dateAddedIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATE_ADDED);

            while (cursor.moveToNext()) {
                String filePath = dataIndex != -1 ? cursor.getString(dataIndex) : null;
                String relativePath = relativePathIndex != -1 ? cursor.getString(relativePathIndex) : null;
                long dateTaken = dateTakenIndex != -1 ? cursor.getLong(dateTakenIndex) : 0;
                long dateAdded = dateAddedIndex != -1 ? cursor.getLong(dateAddedIndex) * 1000 : System.currentTimeMillis();

                if (dateTaken <= 0) {
                    dateTaken = dateAdded;
                }

                if (filePath == null && relativePath == null) {
                    continue;
                }

                if (isCameraPhoto(filePath, relativePath)) {
                    if (filePath != null && !processedPhotoPaths.contains(filePath)) {
                        if (FileUtils.isValidImageFile(filePath)) {
                            processedPhotoPaths.add(filePath);
                            Log.d(TAG, "Valid new camera photo detected: " + filePath);
                            if (callback != null) {
                                callback.onNewCameraPhotoDetected(filePath, dateTaken);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error querying MediaStore for camera photos", e);
        }
    }

    /**
     * Strictly verifies that the photo originates from DCIM/Camera and is not a screenshot,
     * download, WhatsApp image, Telegram image, or imported asset.
     */
    private boolean isCameraPhoto(String filePath, String relativePath) {
        String pathToCheck = filePath != null ? filePath.toLowerCase() : "";
        String relToCheck = relativePath != null ? relativePath.toLowerCase() : "";

        // Reject explicit non-camera directories
        if (pathToCheck.contains("screenshot") || relToCheck.contains("screenshot")) {
            return false;
        }
        if (pathToCheck.contains("download") || relToCheck.contains("download")) {
            return false;
        }
        if (pathToCheck.contains("whatsapp") || relToCheck.contains("whatsapp")) {
            return false;
        }
        if (pathToCheck.contains("telegram") || relToCheck.contains("telegram")) {
            return false;
        }
        if (pathToCheck.contains("instagram") || relToCheck.contains("instagram")) {
            return false;
        }
        if (pathToCheck.contains("pictures") && !pathToCheck.contains("camera")) {
            return false;
        }

        // Accept DCIM/Camera, DCIM/100ANDRO, or standard DCIM camera paths
        return pathToCheck.contains("dcim/camera")
                || pathToCheck.contains("dcim/100andro")
                || relToCheck.startsWith("dcim/camera")
                || relToCheck.startsWith("dcim/");
    }
}
