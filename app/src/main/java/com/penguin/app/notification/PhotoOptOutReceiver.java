package com.penguin.app.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * BroadcastReceiver triggered when the user taps "Cancel Sync" on the 5-second opt-out notification.
 */
public class PhotoOptOutReceiver extends BroadcastReceiver {

    public static final String ACTION_CANCEL_SYNC = "com.penguin.app.ACTION_CANCEL_SYNC";
    public static final String EXTRA_PHOTO_ID = "extra_photo_id";
    public static final String EXTRA_NOTIFICATION_ID = "extra_notification_id";

    private static final String TAG = "PhotoOptOutReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;

        String action = intent.getAction();
        if (ACTION_CANCEL_SYNC.equals(action)) {
            String photoId = intent.getStringExtra(EXTRA_PHOTO_ID);
            int notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0);

            Log.d(TAG, "User clicked Cancel Sync for photoId: " + photoId);

            if (photoId != null) {
                PhotoOptOutManager.getInstance(context).cancelPhotoSync(photoId, notificationId);
            }
        }
    }
}
