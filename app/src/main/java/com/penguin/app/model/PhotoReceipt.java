package com.penguin.app.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

import java.io.Serializable;
import java.util.Objects;

/**
 * Tracks the delivery and receipt status of a specific photo for a specific peer device.
 * Used for per-peer reconciliation and missing-photo catch up.
 */
@Entity(
    tableName = "photo_receipts",
    primaryKeys = {"photoId", "peerDeviceId"},
    indices = {
        @Index("photoId"),
        @Index("peerDeviceId"),
        @Index("status")
    }
)
public class PhotoReceipt implements Serializable {

    @NonNull
    private String photoId;
    @NonNull
    private String peerDeviceId;
    private String peerName;
    private long deliveredAt;
    private SyncStatus status;

    public PhotoReceipt(@NonNull String photoId, @NonNull String peerDeviceId,
                        String peerName, long deliveredAt, SyncStatus status) {
        this.photoId = photoId;
        this.peerDeviceId = peerDeviceId;
        this.peerName = peerName;
        this.deliveredAt = deliveredAt;
        this.status = status;
    }

    @NonNull
    public String getPhotoId() {
        return photoId;
    }

    public void setPhotoId(@NonNull String photoId) {
        this.photoId = photoId;
    }

    @NonNull
    public String getPeerDeviceId() {
        return peerDeviceId;
    }

    public void setPeerDeviceId(@NonNull String peerDeviceId) {
        this.peerDeviceId = peerDeviceId;
    }

    public String getPeerName() {
        return peerName;
    }

    public void setPeerName(String peerName) {
        this.peerName = peerName;
    }

    public long getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(long deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public SyncStatus getStatus() {
        return status;
    }

    public void setStatus(SyncStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PhotoReceipt that = (PhotoReceipt) o;
        return deliveredAt == that.deliveredAt &&
                photoId.equals(that.photoId) &&
                peerDeviceId.equals(that.peerDeviceId) &&
                Objects.equals(peerName, that.peerName) &&
                status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(photoId, peerDeviceId, peerName, deliveredAt, status);
    }
}
