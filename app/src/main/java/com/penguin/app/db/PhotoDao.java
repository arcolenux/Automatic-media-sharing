package com.penguin.app.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.penguin.app.model.PhotoReceipt;
import com.penguin.app.model.SharedPhoto;
import com.penguin.app.model.SyncStatus;

import java.util.List;

/**
 * Data Access Object for Shared Photos and Photo Receipts.
 */
@Dao
public interface PhotoDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPhoto(SharedPhoto photo);

    @Update
    void updatePhoto(SharedPhoto photo);

    @Delete
    void deletePhoto(SharedPhoto photo);

    @Query("SELECT * FROM shared_photos WHERE photoId = :photoId LIMIT 1")
    SharedPhoto getPhotoById(String photoId);

    @Query("SELECT * FROM shared_photos WHERE sessionId = :sessionId AND optedOut = 0 ORDER BY capturedAt DESC")
    LiveData<List<SharedPhoto>> getPhotosForSessionLive(String sessionId);

    @Query("SELECT * FROM shared_photos WHERE sessionId = :sessionId AND optedOut = 0 ORDER BY capturedAt DESC")
    List<SharedPhoto> getPhotosForSession(String sessionId);

    @Query("SELECT * FROM shared_photos WHERE sessionId = :sessionId AND (syncStatus = 'QUEUED' OR syncStatus = 'FAILED') AND optedOut = 0")
    List<SharedPhoto> getPendingPhotosForSession(String sessionId);

    @Query("SELECT photoId FROM shared_photos WHERE sessionId = :sessionId AND optedOut = 0")
    List<String> getKnownPhotoIdsForSession(String sessionId);

    @Query("UPDATE shared_photos SET syncStatus = :status, syncedAt = :syncedAt WHERE photoId = :photoId")
    void updateSyncStatus(String photoId, SyncStatus status, long syncedAt);

    @Query("UPDATE shared_photos SET optedOut = :optedOut, syncStatus = 'CANCELLED' WHERE photoId = :photoId")
    void setOptedOut(String photoId, boolean optedOut);

    @Query("UPDATE shared_photos SET retryCount = retryCount + 1, syncStatus = :status WHERE photoId = :photoId")
    void recordRetryAttempt(String photoId, SyncStatus status);

    @Query("SELECT COUNT(*) FROM shared_photos WHERE sessionId = :sessionId AND optedOut = 0")
    int getPhotoCountForSession(String sessionId);

    @Query("SELECT COUNT(*) FROM shared_photos WHERE sessionId = :sessionId AND optedOut = 0")
    LiveData<Integer> getPhotoCountForSessionLive(String sessionId);

    // --- Photo Receipts (Per-Peer Tracking) ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdateReceipt(PhotoReceipt receipt);

    @Query("SELECT * FROM photo_receipts WHERE photoId = :photoId")
    List<PhotoReceipt> getReceiptsForPhoto(String photoId);

    @Query("SELECT p.* FROM shared_photos p WHERE p.sessionId = :sessionId AND p.optedOut = 0 AND p.photoId NOT IN (SELECT r.photoId FROM photo_receipts r WHERE r.peerDeviceId = :peerDeviceId AND r.status = 'SYNCED')")
    List<SharedPhoto> getPhotosNotDeliveredToPeer(String sessionId, String peerDeviceId);

    @Query("UPDATE photo_receipts SET status = 'SYNCED', deliveredAt = :deliveredAt WHERE photoId = :photoId AND peerDeviceId = :peerDeviceId")
    void markDeliveredToPeer(String photoId, String peerDeviceId, long deliveredAt);
}
