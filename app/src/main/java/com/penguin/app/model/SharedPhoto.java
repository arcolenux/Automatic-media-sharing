package com.penguin.app.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a photo shared or captured within a session.
 */
@Entity(
    tableName = "shared_photos",
    indices = {
        @Index("sessionId"),
        @Index("syncStatus"),
        @Index("capturedAt")
    }
)
public class SharedPhoto implements Serializable {

    @PrimaryKey
    @NonNull
    private String photoId;
    private String sessionId;
    private String ownerDeviceId;
    private String ownerName;
    private String localFilePath;
    private long capturedAt;
    private long syncedAt;
    private SyncStatus syncStatus;
    private int retryCount;
    private boolean optedOut;
    private int width;
    private int height;
    private long fileSize;

    public SharedPhoto(@NonNull String photoId, String sessionId, String ownerDeviceId,
                       String ownerName, String localFilePath, long capturedAt,
                       long syncedAt, SyncStatus syncStatus, int retryCount,
                       boolean optedOut, int width, int height, long fileSize) {
        this.photoId = photoId;
        this.sessionId = sessionId;
        this.ownerDeviceId = ownerDeviceId;
        this.ownerName = ownerName;
        this.localFilePath = localFilePath;
        this.capturedAt = capturedAt;
        this.syncedAt = syncedAt;
        this.syncStatus = syncStatus;
        this.retryCount = retryCount;
        this.optedOut = optedOut;
        this.width = width;
        this.height = height;
        this.fileSize = fileSize;
    }

    @Ignore
    public SharedPhoto(@NonNull String photoId, String sessionId, String ownerDeviceId,
                       String ownerName, String localFilePath, long capturedAt) {
        this(photoId, sessionId, ownerDeviceId, ownerName, localFilePath, capturedAt,
             0, SyncStatus.QUEUED, 0, false, 0, 0, 0);
    }

    @NonNull
    public String getPhotoId() {
        return photoId;
    }

    public void setPhotoId(@NonNull String photoId) {
        this.photoId = photoId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getOwnerDeviceId() {
        return ownerDeviceId;
    }

    public void setOwnerDeviceId(String ownerDeviceId) {
        this.ownerDeviceId = ownerDeviceId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getLocalFilePath() {
        return localFilePath;
    }

    public void setLocalFilePath(String localFilePath) {
        this.localFilePath = localFilePath;
    }

    public long getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(long capturedAt) {
        this.capturedAt = capturedAt;
    }

    public long getSyncedAt() {
        return syncedAt;
    }

    public void setSyncedAt(long syncedAt) {
        this.syncedAt = syncedAt;
    }

    public SyncStatus getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(SyncStatus syncStatus) {
        this.syncStatus = syncStatus;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public boolean isOptedOut() {
        return optedOut;
    }

    public void setOptedOut(boolean optedOut) {
        this.optedOut = optedOut;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SharedPhoto that = (SharedPhoto) o;
        return capturedAt == that.capturedAt &&
                syncedAt == that.syncedAt &&
                retryCount == that.retryCount &&
                optedOut == that.optedOut &&
                width == that.width &&
                height == that.height &&
                fileSize == that.fileSize &&
                photoId.equals(that.photoId) &&
                Objects.equals(sessionId, that.sessionId) &&
                Objects.equals(ownerDeviceId, that.ownerDeviceId) &&
                Objects.equals(ownerName, that.ownerName) &&
                Objects.equals(localFilePath, that.localFilePath) &&
                syncStatus == that.syncStatus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(photoId, sessionId, ownerDeviceId, ownerName, localFilePath,
                capturedAt, syncedAt, syncStatus, retryCount, optedOut, width, height, fileSize);
    }
}
