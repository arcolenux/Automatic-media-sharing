package com.penguin.app.db;

import androidx.room.TypeConverter;

import com.penguin.app.model.SyncStatus;

/**
 * Room type converters for custom data types.
 */
public class Converters {

    @TypeConverter
    public static String fromSyncStatus(SyncStatus status) {
        return status == null ? null : status.name();
    }

    @TypeConverter
    public static SyncStatus toSyncStatus(String status) {
        if (status == null) {
            return null;
        }
        try {
            return SyncStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return SyncStatus.QUEUED;
        }
    }
}
