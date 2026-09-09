package com.penguin.app.transfer;

import android.content.Context;
import android.util.Log;

import com.penguin.app.model.Session;
import com.penguin.app.model.SharedPhoto;
import com.penguin.app.model.SyncStatus;

/**
 * Top-level coordinator for transfers, peer lifecycle, and queue synchronization.
 */
public class TransferManager implements NearbyConnectionsManager.NearbyEventListener {

    private static final String TAG = "TransferManager";
    private static volatile TransferManager instance;

    private final Context context;
    private final NearbyConnectionsManager nearbyManager;
    private final OfflineQueueManager queueManager;
    private Session activeSession;

    private TransferManager(Context context) {
        this.context = context.getApplicationContext();
        this.nearbyManager = NearbyConnectionsManager.getInstance(this.context);
        this.queueManager = OfflineQueueManager.getInstance(this.context);
        this.nearbyManager.addListener(this);
    }

    public static TransferManager getInstance(Context context) {
        if (instance == null) {
            synchronized (TransferManager.class) {
                if (instance == null) {
                    instance = new TransferManager(context);
                }
            }
        }
        return instance;
    }

    public void setActiveSession(Session session) {
        this.activeSession = session;
    }

    public Session getActiveSession() {
        return activeSession;
    }

    public void startHostSession(Session session) {
        this.activeSession = session;
        nearbyManager.startAdvertising(session);
    }

    public void startJoiningSession(String roomCode) {
        nearbyManager.startDiscovery(roomCode);
    }

    public void shareApprovedPhoto(SharedPhoto photo) {
        if (photo == null || photo.isOptedOut()) {
            Log.d(TAG, "Photo is null or opted out, skipping transfer");
            return;
        }

        Log.d(TAG, "Sharing approved photo: " + photo.getPhotoId());
        queueManager.enqueuePhoto(photo);
    }

    public void endCurrentSession() {
        if (activeSession != null) {
            nearbyManager.sendEndSessionToAll();
            nearbyManager.stopAllEndpoints();
            activeSession = null;
        }
    }

    public void leaveCurrentSession() {
        if (activeSession != null) {
            nearbyManager.sendLeaveToAll();
            nearbyManager.stopAllEndpoints();
            activeSession = null;
        }
    }

    public void retryPhoto(String photoId) {
        queueManager.retryPhoto(photoId);
    }

    // ==========================================
    // Nearby Event Listener Callbacks
    // ==========================================

    @Override
    public void onPeerConnected(NearbyConnectionsManager.PeerInfo peer) {
        Log.d(TAG, "Peer connected: " + peer.displayName + " (" + peer.deviceId + "). Flushing queue...");
        if (activeSession != null) {
            queueManager.flushQueueForSession(activeSession.getSessionId());
        }
    }

    @Override
    public void onPeerDisconnected(String endpointId, NearbyConnectionsManager.PeerInfo peer) {
        Log.d(TAG, "Peer disconnected: " + (peer != null ? peer.displayName : endpointId));
    }

    @Override
    public void onPhotoReceived(SharedPhoto photo) {
        Log.d(TAG, "Photo received and persisted: " + photo.getPhotoId());
    }

    @Override
    public void onPhotoTransferStatusChanged(String photoId, SyncStatus status) {
        Log.d(TAG, "Photo status updated for " + photoId + ": " + status);
    }

    @Override
    public void onSessionEndedByHost() {
        Log.d(TAG, "Host ended the session");
        activeSession = null;
    }

    @Override
    public void onMemberLeft(String deviceId) {
        Log.d(TAG, "Member left session: " + deviceId);
    }

    @Override
    public void onDiscoveryFailed(String reason) {
        Log.e(TAG, "Discovery failed: " + reason);
    }

    @Override
    public void onHostDiscovered(String endpointId, String roomCode) {
        Log.d(TAG, "Host discovered: " + endpointId + " for code: " + roomCode);
    }
}
