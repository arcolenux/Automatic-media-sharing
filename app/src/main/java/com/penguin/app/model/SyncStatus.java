package com.penguin.app.model;

/**
 * Represents the synchronization status of a shared photo.
 */
public enum SyncStatus {
    QUEUED,
    SYNCING,
    SYNCED,
    FAILED,
    CANCELLED
}
