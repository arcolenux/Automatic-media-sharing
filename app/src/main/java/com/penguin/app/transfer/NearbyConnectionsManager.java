package com.penguin.app.transfer;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.nearby.Nearby;
import com.google.android.gms.nearby.connection.AdvertisingOptions;
import com.google.android.gms.nearby.connection.ConnectionInfo;
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback;
import com.google.android.gms.nearby.connection.ConnectionResolution;
import com.google.android.gms.nearby.connection.ConnectionsClient;
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes;
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo;
import com.google.android.gms.nearby.connection.DiscoveryOptions;
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback;
import com.google.android.gms.nearby.connection.Payload;
import com.google.android.gms.nearby.connection.PayloadCallback;
import com.google.android.gms.nearby.connection.PayloadTransferUpdate;
import com.google.android.gms.nearby.connection.Strategy;
import com.penguin.app.PenguinApplication;
import com.penguin.app.db.AppDatabase;
import com.penguin.app.model.InventoryPayload;
import com.penguin.app.model.PhotoReceipt;
import com.penguin.app.model.Session;
import com.penguin.app.model.SessionMember;
import com.penguin.app.model.SharedPhoto;
import com.penguin.app.model.SyncStatus;
import com.penguin.app.model.TransferPayload;
import com.penguin.app.util.FileUtils;
import com.penguin.app.util.RoomCodeGenerator;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Google Nearby Connections (P2P_CLUSTER Strategy) for PENGUIN.
 * Handles advertising, discovery, connection lifecycle, roster sync, and payload transmission.
 */
public class NearbyConnectionsManager {

    private static final String TAG = "NearbyConnManager";
    public static final String SERVICE_ID = "com.penguin.app.NEARBY_PHOTO_SHARE";
    public static final Strategy STRATEGY = Strategy.P2P_CLUSTER;

    private static volatile NearbyConnectionsManager instance;

    private final Context context;
    private final ConnectionsClient connectionsClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean isAdvertising = false;
    private boolean isDiscovering = false;
    private String currentSessionId;
    private String currentSessionName;
    private String currentRoomCode;
    private String discoveredHostSessionId;
    private String discoveredHostSessionName;

    // Active Connected Endpoints: endpointId -> PeerInfo
    public static class PeerInfo {
        public final String endpointId;
        public final String deviceId;
        public final String displayName;
        public final String sessionId;
        public final String sessionName;

        public PeerInfo(String endpointId, String deviceId, String displayName, String sessionId, String sessionName) {
            this.endpointId = endpointId;
            this.deviceId = deviceId;
            this.displayName = displayName;
            this.sessionId = sessionId;
            this.sessionName = sessionName;
        }

        public PeerInfo(String endpointId, String deviceId, String displayName, String sessionId) {
            this(endpointId, deviceId, displayName, sessionId, null);
        }
    }

    private final Map<String, PeerInfo> connectedPeers = new ConcurrentHashMap<>();
    private final Map<String, PeerInfo> pendingPeers = new ConcurrentHashMap<>();

    // Map incoming Nearby Payload ID -> Received TransferPayload Metadata
    private final Map<Long, TransferPayload> incomingMetadataMap = new ConcurrentHashMap<>();
    // In-flight incoming files: payloadId -> Payload.File
    private final Map<Long, Payload.File> transferringIncomingFilesMap = new ConcurrentHashMap<>();
    // Completed incoming files awaiting metadata: payloadId -> Payload.File
    private final Map<Long, Payload.File> completedIncomingFilesMap = new ConcurrentHashMap<>();

    // Map outgoing Nearby Payload ID -> SharedPhoto being transferred
    private final Map<Long, SharedPhoto> outgoingPhotosMap = new ConcurrentHashMap<>();

    // Set of processed and relayed photo IDs to prevent duplicate writes and infinite relay loops
    private final java.util.Set<String> processedPhotoIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final java.util.Set<String> relayedPhotoIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Listeners
    public interface NearbyEventListener {
        void onPeerConnected(PeerInfo peer);
        void onPeerDisconnected(String endpointId, PeerInfo peer);
        void onPhotoReceived(SharedPhoto photo);
        void onPhotoTransferStatusChanged(String photoId, SyncStatus status);
        void onSessionEndedByHost();
        void onMemberLeft(String deviceId);
        void onDiscoveryFailed(String reason);
        void onHostDiscovered(String endpointId, String roomCode);
    }

    private final List<NearbyEventListener> listeners = Collections.synchronizedList(new ArrayList<>());

    private NearbyConnectionsManager(Context context) {
        this.context = context.getApplicationContext();
        this.connectionsClient = Nearby.getConnectionsClient(this.context);
    }

    public static NearbyConnectionsManager getInstance(Context context) {
        if (instance == null) {
            synchronized (NearbyConnectionsManager.class) {
                if (instance == null) {
                    instance = new NearbyConnectionsManager(context);
                }
            }
        }
        return instance;
    }

    public void addListener(NearbyEventListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(NearbyEventListener listener) {
        listeners.remove(listener);
    }

    public boolean isConnectedToAnyPeer() {
        return !connectedPeers.isEmpty();
    }

    public boolean isAdvertising() {
        return isAdvertising;
    }

    public boolean isDiscovering() {
        return isDiscovering;
    }

    public int getConnectedPeerCount() {
        return connectedPeers.size();
    }

    public Map<String, PeerInfo> getConnectedPeers() {
        return new HashMap<>(connectedPeers);
    }

    public boolean isDeviceConnected(String deviceId) {
        if (deviceId == null) return false;
        for (PeerInfo p : connectedPeers.values()) {
            if (deviceId.equals(p.deviceId)) {
                return true;
            }
        }
        return false;
    }

    public java.util.Set<String> getConnectedDeviceIds() {
        java.util.Set<String> set = new java.util.HashSet<>();
        for (PeerInfo p : connectedPeers.values()) {
            if (p.deviceId != null) {
                set.add(p.deviceId);
            }
        }
        return set;
    }

    public String getCurrentSessionId() {
        return currentSessionId;
    }

    public void setCurrentSessionId(String sessionId) {
        this.currentSessionId = sessionId;
    }

    public String getCurrentSessionName() {
        return currentSessionName;
    }

    public void setCurrentSessionName(String sessionName) {
        this.currentSessionName = sessionName;
    }

    public String getDiscoveredHostSessionId() {
        return discoveredHostSessionId;
    }

    public String getDiscoveredHostSessionName() {
        return discoveredHostSessionName;
    }

    // ==========================================
    // Advertising (Host & Cluster)
    // ==========================================

    public void startAdvertising(Session session) {
        if (session == null) return;
        this.currentSessionId = session.getSessionId();
        this.currentSessionName = session.getSessionName();
        this.currentRoomCode = session.getRoomCode();

        String myDeviceId = PenguinApplication.getInstance().getAppDeviceId();
        String myName = PenguinApplication.getInstance().getUserName();
        // Endpoint Name format: ROOM_CODE|DEVICE_ID|NAME|SESSION_ID|SESSION_NAME
        String endpointName = session.getRoomCode() + "|" + myDeviceId + "|" + myName + "|" + session.getSessionId() + "|" + session.getSessionName();

        AdvertisingOptions advertisingOptions = new AdvertisingOptions.Builder()
                .setStrategy(STRATEGY)
                .build();

        connectionsClient.startAdvertising(
                endpointName,
                SERVICE_ID,
                connectionLifecycleCallback,
                advertisingOptions
        ).addOnSuccessListener(unused -> {
            isAdvertising = true;
            Log.d(TAG, "Started advertising successfully: " + endpointName);
        }).addOnFailureListener(e -> {
            if (e instanceof com.google.android.gms.common.api.ApiException) {
                int code = ((com.google.android.gms.common.api.ApiException) e).getStatusCode();
                if (code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                    isAdvertising = true;
                    Log.d(TAG, "Already advertising, keeping state active: " + endpointName);
                    return;
                }
            }
            isAdvertising = false;
            Log.e(TAG, "Failed to start advertising", e);
        });
    }

    public void stopAdvertising() {
        if (isAdvertising) {
            connectionsClient.stopAdvertising();
            isAdvertising = false;
            Log.d(TAG, "Stopped advertising");
        }
    }

    // ==========================================
    // Discovery (Participants)
    // ==========================================

    public void startDiscovery(String targetRoomCode) {
        this.currentRoomCode = targetRoomCode;

        if (isDiscovering) {
            try {
                connectionsClient.stopDiscovery();
            } catch (Exception ignored) {}
        }

        DiscoveryOptions discoveryOptions = new DiscoveryOptions.Builder()
                .setStrategy(STRATEGY)
                .build();

        connectionsClient.startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                discoveryOptions
        ).addOnSuccessListener(unused -> {
            isDiscovering = true;
            Log.d(TAG, "Started discovery for roomCode: " + targetRoomCode);
        }).addOnFailureListener(e -> {
            if (e instanceof com.google.android.gms.common.api.ApiException) {
                int code = ((com.google.android.gms.common.api.ApiException) e).getStatusCode();
                if (code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                    isDiscovering = true;
                    Log.d(TAG, "Already discovering for roomCode: " + targetRoomCode);
                    return;
                }
            }
            isDiscovering = false;
            Log.e(TAG, "Failed to start discovery", e);
            notifyDiscoveryFailed("Discovery failed: " + e.getMessage());
        });
    }

    public void stopDiscovery() {
        if (isDiscovering) {
            connectionsClient.stopDiscovery();
            isDiscovering = false;
            Log.d(TAG, "Stopped discovery");
        }
    }

    // ==========================================
    // Connection Lifecycle
    // ==========================================

    private final EndpointDiscoveryCallback endpointDiscoveryCallback = new EndpointDiscoveryCallback() {
        @Override
        public void onEndpointFound(@NonNull String endpointId, @NonNull DiscoveredEndpointInfo info) {
            String endpointName = info.getEndpointName();
            Log.d(TAG, "Endpoint found: " + endpointId + ", name: " + endpointName);

            String[] parts = endpointName != null ? endpointName.split("\\|") : new String[0];
            if (parts.length >= 1) {
                String roomCode = parts[0];
                if (currentRoomCode != null && RoomCodeGenerator.normalize(currentRoomCode).equalsIgnoreCase(RoomCodeGenerator.normalize(roomCode))) {
                    String devId = parts.length >= 2 ? parts[1] : ("PEER-" + endpointId);
                    String devName = parts.length >= 3 ? parts[2] : "Nearby Peer";
                    if (parts.length >= 4 && parts[3] != null && !parts[3].isEmpty()) {
                        discoveredHostSessionId = parts[3];
                        currentSessionId = parts[3];
                    }
                    if (parts.length >= 5 && parts[4] != null && !parts[4].isEmpty()) {
                        discoveredHostSessionName = parts[4];
                        currentSessionName = parts[4];
                    }

                    pendingPeers.put(endpointId, new PeerInfo(endpointId, devId, devName, currentSessionId, currentSessionName));

                    Log.d(TAG, "Matching host found for roomCode " + roomCode + ", deviceId=" + devId + ", sessionId=" + currentSessionId + ", sessionName=" + currentSessionName + ", requesting connection to " + endpointId);
                    notifyHostDiscovered(endpointId, roomCode);

                    String myDeviceId = PenguinApplication.getInstance().getAppDeviceId();
                    String myName = PenguinApplication.getInstance().getUserName();
                    String myEndpointName = RoomCodeGenerator.normalize(roomCode) + "|" + myDeviceId + "|" + myName + "|" + (currentSessionId != null ? currentSessionId : "") + "|" + (currentSessionName != null ? currentSessionName : "");

                    connectionsClient.requestConnection(myEndpointName, endpointId, connectionLifecycleCallback)
                            .addOnSuccessListener(unused -> Log.d(TAG, "Connection requested to " + endpointId))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed requesting connection to " + endpointId, e));
                }
            }
        }

        @Override
        public void onEndpointLost(@NonNull String endpointId) {
            Log.d(TAG, "Endpoint lost: " + endpointId);
            pendingPeers.remove(endpointId);
        }
    };

    private final ConnectionLifecycleCallback connectionLifecycleCallback = new ConnectionLifecycleCallback() {
        @Override
        public void onConnectionInitiated(@NonNull String endpointId, @NonNull ConnectionInfo connectionInfo) {
            Log.d(TAG, "Connection initiated with " + endpointId + " (" + connectionInfo.getEndpointName() + ")");
            String endpointName = connectionInfo.getEndpointName();
            if (endpointName != null) {
                String[] parts = endpointName.split("\\|");
                String devId = parts.length >= 2 ? parts[1] : ("PEER-" + endpointId);
                String devName = parts.length >= 3 ? parts[2] : "Nearby Peer";
                String sessId = parts.length >= 4 ? parts[3] : currentSessionId;
                String sessName = parts.length >= 5 ? parts[4] : currentSessionName;
                if (sessId != null && !sessId.isEmpty() && (currentSessionId == null || currentSessionId.isEmpty())) {
                    currentSessionId = sessId;
                }
                if (sessName != null && !sessName.isEmpty() && (currentSessionName == null || currentSessionName.isEmpty())) {
                    currentSessionName = sessName;
                }
                pendingPeers.put(endpointId, new PeerInfo(endpointId, devId, devName, currentSessionId, currentSessionName));
            }

            // Auto-accept connection in PENGUIN
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                    .addOnSuccessListener(unused -> Log.d(TAG, "Accepted connection with " + endpointId))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed accepting connection with " + endpointId, e));
        }

        @Override
        public void onConnectionResult(@NonNull String endpointId, @NonNull ConnectionResolution resolution) {
            if (resolution.getStatus().getStatusCode() == ConnectionsStatusCodes.STATUS_OK) {
                Log.d(TAG, "Connected successfully to " + endpointId);

                PeerInfo peer = pendingPeers.remove(endpointId);
                if (peer == null) {
                    peer = new PeerInfo(endpointId, "PEER-" + endpointId, "Nearby Peer", currentSessionId, currentSessionName);
                }
                connectedPeers.put(endpointId, peer);

                // Send member join / handshake payload with our identity and session ID
                sendMemberJoinPayload(endpointId);

                // If we are host or already have members, broadcast our roster
                broadcastCurrentMemberRoster();

                // Trigger inventory exchange reconciliation
                sendInventorySync(endpointId);
            } else {
                Log.w(TAG, "Connection rejected or failed to " + endpointId + ": " + resolution.getStatus());
                pendingPeers.remove(endpointId);
            }
        }

        @Override
        public void onDisconnected(@NonNull String endpointId) {
            Log.d(TAG, "Disconnected from " + endpointId);
            PeerInfo peer = connectedPeers.remove(endpointId);
            if (peer != null) {
                // Update Room SessionMember nearby status to false
                if (currentSessionId != null) {
                    AppDatabase.databaseWriteExecutor.execute(() -> {
                        PenguinApplication.getInstance().getDatabase()
                                .sessionDao()
                                .setMemberNearbyStatus(currentSessionId, peer.deviceId, false, null, System.currentTimeMillis());

                        // Broadcast updated roster to all remaining peers
                        broadcastCurrentMemberRoster();
                    });
                }
                notifyPeerDisconnected(endpointId, peer);
            }
        }
    };

    // ==========================================
    // Payload Callback & Handling
    // ==========================================

    private final PayloadCallback payloadCallback = new PayloadCallback() {
        @Override
        public void onPayloadReceived(@NonNull String endpointId, @NonNull Payload payload) {
            long payloadId = payload.getId();

            if (payload.getType() == Payload.Type.BYTES) {
                byte[] bytes = payload.asBytes();
                if (bytes != null) {
                    String json = new String(bytes, StandardCharsets.UTF_8);
                    handleBytesPayload(endpointId, json);
                }
            } else if (payload.getType() == Payload.Type.FILE) {
                Log.d(TAG, "Receiving incoming file payload ID: " + payloadId + " from " + endpointId);
                Payload.File file = payload.asFile();
                if (file != null) {
                    transferringIncomingFilesMap.put(payloadId, file);
                }
            }
        }

        @Override
        public void onPayloadTransferUpdate(@NonNull String endpointId, @NonNull PayloadTransferUpdate update) {
            long payloadId = update.getPayloadId();

            if (update.getStatus() == PayloadTransferUpdate.Status.SUCCESS) {
                Log.d(TAG, "Payload transfer SUCCESS for ID: " + payloadId);

                // Check if this was an incoming file
                if (transferringIncomingFilesMap.containsKey(payloadId)) {
                    Payload.File filePayload = transferringIncomingFilesMap.remove(payloadId);

                    if (incomingMetadataMap.containsKey(payloadId)) {
                        // Metadata already arrived -> process file immediately
                        TransferPayload metadata = incomingMetadataMap.remove(payloadId);
                        processIncomingPhotoFile(filePayload, metadata);
                    } else {
                        // Metadata hasn't arrived yet -> save in completed map
                        completedIncomingFilesMap.put(payloadId, filePayload);
                    }
                }

                // Check if this was an outgoing photo file
                SharedPhoto outgoingPhoto = outgoingPhotosMap.remove(payloadId);
                if (outgoingPhoto != null) {
                    PeerInfo peer = connectedPeers.get(endpointId);
                    if (peer != null) {
                        recordOutgoingSuccess(outgoingPhoto, peer);
                    }
                }

            } else if (update.getStatus() == PayloadTransferUpdate.Status.FAILURE) {
                Log.w(TAG, "Payload transfer FAILED for ID: " + payloadId + " to/from " + endpointId);
                transferringIncomingFilesMap.remove(payloadId);
                completedIncomingFilesMap.remove(payloadId);
                incomingMetadataMap.remove(payloadId);

                SharedPhoto outgoingPhoto = outgoingPhotosMap.remove(payloadId);
                if (outgoingPhoto != null) {
                    recordOutgoingFailure(outgoingPhoto);
                }
            }
        }
    };

    private void handleBytesPayload(String endpointId, String json) {
        try {
            TransferPayload metadata = TransferPayload.fromJson(json);
            if (metadata == null || metadata.getType() == null) {
                return;
            }

            switch (metadata.getType()) {
                case TransferPayload.TYPE_PHOTO_METADATA:
                    long nearbyPayloadId = metadata.getNearbyPayloadId();
                    Log.d(TAG, "Received photo metadata for photoId: " + metadata.getPhotoId() + ", nearbyPayloadId: " + nearbyPayloadId);

                    if (completedIncomingFilesMap.containsKey(nearbyPayloadId)) {
                        // File transfer already completed -> process now!
                        Payload.File filePayload = completedIncomingFilesMap.remove(nearbyPayloadId);
                        processIncomingPhotoFile(filePayload, metadata);
                    } else {
                        // File still in transit -> store metadata to be consumed on SUCCESS
                        incomingMetadataMap.put(nearbyPayloadId, metadata);
                    }
                    break;

                case TransferPayload.TYPE_PHOTO_REQUEST:
                    Log.d(TAG, "Received photo request for photoId: " + metadata.getPhotoId() + " from " + endpointId);
                    sendRequestedPhoto(endpointId, metadata.getPhotoId());
                    break;

                case TransferPayload.TYPE_INVENTORY_SYNC:
                    InventoryPayload inventory = InventoryPayload.fromJson(json);
                    if (inventory != null) {
                        handleInventorySync(endpointId, inventory);
                    }
                    break;

                case TransferPayload.TYPE_MEMBER_JOIN:
                    handleMemberJoin(endpointId, metadata);
                    break;

                case TransferPayload.TYPE_MEMBER_ROSTER:
                    handleMemberRoster(metadata);
                    break;

                case TransferPayload.TYPE_MEMBER_LEAVE:
                    handleMemberLeave(metadata.getOwnerDeviceId());
                    break;

                case TransferPayload.TYPE_SESSION_END:
                    handleSessionEnd();
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling bytes payload", e);
        }
    }

    private void handleMemberJoin(String endpointId, TransferPayload metadata) {
        String deviceId = metadata.getOwnerDeviceId();
        String displayName = metadata.getOwnerName();
        String sessionId = metadata.getSessionId();
        String sessionName = metadata.getSessionName();

        if (sessionId != null && !sessionId.isEmpty()) {
            this.currentSessionId = sessionId;
        }
        if (sessionName != null && !sessionName.isEmpty()) {
            this.currentSessionName = sessionName;
            AppDatabase.databaseWriteExecutor.execute(() -> {
                PenguinApplication.getInstance().getDatabase().sessionDao().updateSessionName(sessionId, sessionName);
            });
        }

        PeerInfo peer = new PeerInfo(endpointId, deviceId, displayName, currentSessionId, currentSessionName);
        connectedPeers.put(endpointId, peer);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            SessionMember member = new SessionMember(
                    currentSessionId != null ? currentSessionId : "",
                    deviceId,
                    displayName,
                    endpointId,
                    true,
                    System.currentTimeMillis(),
                    System.currentTimeMillis(),
                    false
            );
            PenguinApplication.getInstance().getDatabase().sessionDao().insertOrUpdateMember(member);

            // Update session active member count
            int count = PenguinApplication.getInstance().getDatabase().sessionDao().getActiveMembersCount(currentSessionId);
            PenguinApplication.getInstance().getDatabase().sessionDao().updateMemberCount(currentSessionId, count);

            // Broadcast updated roster to all peers
            broadcastCurrentMemberRoster();
        });

        notifyPeerConnected(peer);
    }

    private void handleMemberRoster(TransferPayload metadata) {
        String sessionId = metadata.getSessionId();
        String sessionName = metadata.getSessionName();
        String rosterJson = metadata.getExtraData();

        if (sessionId != null && !sessionId.isEmpty()) {
            this.currentSessionId = sessionId;
        }
        if (sessionName != null && !sessionName.isEmpty()) {
            this.currentSessionName = sessionName;
        }

        if (rosterJson == null || rosterJson.isEmpty() || currentSessionId == null) {
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                if (sessionName != null && !sessionName.isEmpty()) {
                    PenguinApplication.getInstance().getDatabase().sessionDao().updateSessionName(currentSessionId, sessionName);
                }
                PenguinApplication.getInstance().getDatabase().sessionDao().deletePlaceholderMembers(currentSessionId);
                JSONArray array = new JSONArray(rosterJson);
                String myDeviceId = PenguinApplication.getInstance().getAppDeviceId();

                for (int i = 0; i < array.length(); i++) {
                    JSONObject obj = array.getJSONObject(i);
                    String deviceId = obj.optString("deviceId");
                    String displayName = obj.optString("displayName");
                    String endpointId = obj.optString("endpointId");
                    boolean isNearbyInRoster = obj.optBoolean("isNearby", true);
                    boolean hasLeftInRoster = obj.optBoolean("hasLeft", false);

                    if (deviceId != null && !deviceId.isEmpty() && !deviceId.startsWith("PEER-")) {
                        boolean isMe = deviceId.equals(myDeviceId);
                        boolean isNearbyToMe = isMe || isDeviceConnected(deviceId) || (isConnectedToAnyPeer() && isNearbyInRoster);

                        SessionMember existing = PenguinApplication.getInstance().getDatabase().sessionDao()
                                .getMember(currentSessionId, deviceId);

                        long joinedAt = existing != null ? existing.getJoinedAt() : System.currentTimeMillis();
                        boolean hasLeft = (existing != null && existing.isHasLeft()) || hasLeftInRoster;

                        SessionMember member = new SessionMember(
                                currentSessionId,
                                deviceId,
                                displayName,
                                endpointId,
                                isNearbyToMe,
                                joinedAt,
                                System.currentTimeMillis(),
                                hasLeft
                        );
                        PenguinApplication.getInstance().getDatabase().sessionDao().insertOrUpdateMember(member);
                    }
                }

                int count = PenguinApplication.getInstance().getDatabase().sessionDao().getActiveMembersCount(currentSessionId);
                PenguinApplication.getInstance().getDatabase().sessionDao().updateMemberCount(currentSessionId, count);
                Log.d(TAG, "Successfully synced roster with " + count + " active members for session " + currentSessionId);
            } catch (Exception e) {
                Log.e(TAG, "Failed parsing roster json", e);
            }
        });
    }

    public void broadcastCurrentMemberRoster() {
        if (currentSessionId == null) return;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            if (currentSessionName == null || currentSessionName.isEmpty()) {
                Session s = PenguinApplication.getInstance().getDatabase().sessionDao().getSessionById(currentSessionId);
                if (s != null) {
                    currentSessionName = s.getSessionName();
                }
            }
            PenguinApplication.getInstance().getDatabase().sessionDao().deletePlaceholderMembers(currentSessionId);
            List<SessionMember> members = PenguinApplication.getInstance().getDatabase()
                    .sessionDao().getMembersForSession(currentSessionId);
            if (members == null || members.isEmpty()) return;

            try {
                String myDeviceId = PenguinApplication.getInstance().getAppDeviceId();
                java.util.Set<String> connectedDevIds = getConnectedDeviceIds();

                JSONArray array = new JSONArray();
                for (SessionMember m : members) {
                    if (m.getDeviceId() != null && m.getDeviceId().startsWith("PEER-")) continue;
                    boolean isNearby = m.getDeviceId().equals(myDeviceId) || connectedDevIds.contains(m.getDeviceId());
                    JSONObject obj = new JSONObject();
                    obj.put("deviceId", m.getDeviceId());
                    obj.put("displayName", m.getDisplayName());
                    obj.put("endpointId", m.getEndpointId());
                    obj.put("isNearby", isNearby);
                    obj.put("hasLeft", m.isHasLeft());
                    array.put(obj);
                }

                TransferPayload rosterPayload = TransferPayload.forMemberRoster(
                        currentSessionId,
                        currentSessionName,
                        array.toString()
                );
                byte[] bytes = rosterPayload.toJson().getBytes(StandardCharsets.UTF_8);

                for (String endpointId : connectedPeers.keySet()) {
                    sendBytes(endpointId, bytes);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error building roster broadcast", e);
            }
        });
    }

    private void handleMemberLeave(String deviceId) {
        Log.d(TAG, "Member left voluntarily: " + deviceId);
        if (currentSessionId != null) {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                PenguinApplication.getInstance().getDatabase().sessionDao()
                        .markMemberLeft(currentSessionId, deviceId, System.currentTimeMillis());

                int count = PenguinApplication.getInstance().getDatabase().sessionDao().getActiveMembersCount(currentSessionId);
                PenguinApplication.getInstance().getDatabase().sessionDao().updateMemberCount(currentSessionId, count);

                broadcastCurrentMemberRoster();
            });
        }
        notifyMemberLeft(deviceId);
    }

    private void handleSessionEnd() {
        Log.d(TAG, "Session ended by host");
        if (currentSessionId != null) {
            AppDatabase.databaseWriteExecutor.execute(() -> {
                PenguinApplication.getInstance().getDatabase().sessionDao()
                        .deactivateAllSessions(System.currentTimeMillis());
            });
        }
        stopAllEndpoints();
        com.penguin.app.service.MediaDetectionService.stop(context);
        notifySessionEndedByHost();
    }

    private void handleInventorySync(String endpointId, InventoryPayload inventory) {
        String targetSessionId = inventory.getSessionId();
        if (targetSessionId == null || targetSessionId.isEmpty()) {
            targetSessionId = currentSessionId;
        }
        if (targetSessionId == null) return;

        final String activeSessionId = targetSessionId;
        Log.d(TAG, "Received inventory sync from " + inventory.getDeviceId() + " with " + inventory.getKnownPhotoIds().size() + " photos");

        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<String> myKnownPhotoIds = PenguinApplication.getInstance().getDatabase()
                    .photoDao().getKnownPhotoIdsForSession(activeSessionId);

            // 1. Identify photos I have that peer is missing -> Send them!
            for (String myPhotoId : myKnownPhotoIds) {
                if (!inventory.getKnownPhotoIds().contains(myPhotoId)) {
                    Log.d(TAG, "Peer " + endpointId + " is missing photo " + myPhotoId + ", transferring to peer");
                    sendRequestedPhoto(endpointId, myPhotoId);
                }
            }

            // 2. Identify photos peer has that I am missing -> Request them!
            for (String peerPhotoId : inventory.getKnownPhotoIds()) {
                if (!myKnownPhotoIds.contains(peerPhotoId)) {
                    Log.d(TAG, "I am missing photo " + peerPhotoId + ", requesting from peer " + endpointId);
                    TransferPayload req = TransferPayload.forPhotoRequest(
                            peerPhotoId,
                            activeSessionId,
                            PenguinApplication.getInstance().getAppDeviceId()
                    );
                    sendBytes(endpointId, req.toJson().getBytes(StandardCharsets.UTF_8));
                }
            }
        });
    }

    private void processIncomingPhotoFile(Payload.File filePayload, TransferPayload metadata) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String photoId = metadata.getPhotoId();
                if (photoId == null || photoId.isEmpty()) {
                    return;
                }

                // Deduplication Check 1: In-memory tracker
                if (processedPhotoIds.contains(photoId)) {
                    Log.d(TAG, "Photo " + photoId + " already processed in memory. Skipping duplicate write/relay.");
                    return;
                }

                // Deduplication Check 2: Database existence
                SharedPhoto existing = PenguinApplication.getInstance().getDatabase().photoDao().getPhotoById(photoId);
                if (existing != null && FileUtils.isValidImageFile(existing.getLocalFilePath())) {
                    processedPhotoIds.add(photoId);
                    Log.d(TAG, "Photo " + photoId + " already exists in database. Skipping duplicate write/relay.");
                    return;
                }

                processedPhotoIds.add(photoId);

                String targetFileName = photoId + ".jpg";
                String sessionName = metadata.getSessionName();
                if (sessionName == null || sessionName.isEmpty()) {
                    sessionName = currentSessionName;
                }
                if ((sessionName == null || sessionName.isEmpty()) && currentSessionId != null) {
                    Session s = PenguinApplication.getInstance().getDatabase().sessionDao().getSessionById(currentSessionId);
                    if (s != null) {
                        sessionName = s.getSessionName();
                    }
                }

                String savedFilePath = FileUtils.copyReceivedPayloadFile(context, filePayload, targetFileName, sessionName);

                if (!FileUtils.isValidImageFile(savedFilePath)) {
                    Log.e(TAG, "Saved photo file is invalid or empty: " + savedFilePath);
                    return;
                }

                int[] dims = FileUtils.getImageDimensions(savedFilePath);
                long fileSize = new File(savedFilePath).length();

                String effectiveSessionId = metadata.getSessionId();
                if (effectiveSessionId == null || effectiveSessionId.isEmpty()) {
                    effectiveSessionId = currentSessionId != null ? currentSessionId : "";
                }

                SharedPhoto photo = new SharedPhoto(
                        photoId,
                        effectiveSessionId,
                        metadata.getOwnerDeviceId(),
                        metadata.getOwnerName(),
                        savedFilePath,
                        metadata.getCapturedAt() > 0 ? metadata.getCapturedAt() : System.currentTimeMillis(),
                        System.currentTimeMillis(),
                        SyncStatus.SYNCED,
                        0,
                        false,
                        dims[0],
                        dims[1],
                        fileSize
                );

                PenguinApplication.getInstance().getDatabase().photoDao().insertPhoto(photo);
                Log.d(TAG, "Successfully saved received photo: " + photo.getPhotoId() + " to " + savedFilePath);

                notifyPhotoReceived(photo);

                // Relay photo to other connected peers in the session (mesh relay)
                relayPhotoToOtherPeers(photo, metadata.getOwnerDeviceId());

            } catch (Exception e) {
                Log.e(TAG, "Failed saving received photo file for photoId: " + metadata.getPhotoId(), e);
            }
        });
    }

    private void relayPhotoToOtherPeers(SharedPhoto photo, String originalSenderDeviceId) {
        if (photo == null || photo.getPhotoId() == null) return;

        // Ensure each photo is relayed at most ONCE by this peer to prevent infinite ping-pong loops
        if (!relayedPhotoIds.add(photo.getPhotoId())) {
            Log.d(TAG, "Photo " + photo.getPhotoId() + " already relayed to peers. Skipping duplicate relay.");
            return;
        }

        File file = new File(photo.getLocalFilePath());
        for (Map.Entry<String, PeerInfo> entry : connectedPeers.entrySet()) {
            String endpointId = entry.getKey();
            PeerInfo peer = entry.getValue();

            // Do not relay back to the original photo owner
            if (peer.deviceId != null && peer.deviceId.equalsIgnoreCase(originalSenderDeviceId)) {
                continue;
            }

            Log.d(TAG, "Relaying photo " + photo.getPhotoId() + " to peer " + peer.displayName + " (" + endpointId + ")");
            sendPhotoToEndpoint(endpointId, file, photo);
        }
    }

    // ==========================================
    // Sending Operations
    // ==========================================

    public void sendPhotoToAllConnectedPeers(SharedPhoto photo) {
        if (photo == null || !FileUtils.isValidImageFile(photo.getLocalFilePath())) {
            Log.w(TAG, "Cannot send invalid photo");
            return;
        }

        List<String> endpointIds = new ArrayList<>(connectedPeers.keySet());
        if (endpointIds.isEmpty()) {
            Log.d(TAG, "No connected peers nearby. Photo marked QUEUED");
            updatePhotoStatus(photo.getPhotoId(), SyncStatus.QUEUED);
            return;
        }

        File file = new File(photo.getLocalFilePath());
        updatePhotoStatus(photo.getPhotoId(), SyncStatus.SYNCING);

        for (String endpointId : endpointIds) {
            sendPhotoToEndpoint(endpointId, file, photo);
        }
    }

    public void sendPhotoToEndpoint(String endpointId, File file, SharedPhoto photo) {
        try {
            Payload filePayload = Payload.fromFile(file);
            long payloadId = filePayload.getId();

            outgoingPhotosMap.put(payloadId, photo);

            // 1. Send Metadata Bytes Payload with payloadId reference and session name
            TransferPayload metadata = TransferPayload.forPhotoMetadata(photo, payloadId, currentSessionName);
            if (metadata.getSessionId() == null || metadata.getSessionId().isEmpty()) {
                metadata.setSessionId(currentSessionId);
            }
            if (metadata.getSessionName() == null || metadata.getSessionName().isEmpty()) {
                metadata.setSessionName(currentSessionName);
            }
            byte[] metadataBytes = metadata.toJson().getBytes(StandardCharsets.UTF_8);
            Payload bytesPayload = Payload.fromBytes(metadataBytes);

            connectionsClient.sendPayload(endpointId, bytesPayload).addOnSuccessListener(unused -> {
                // 2. Send File Payload
                connectionsClient.sendPayload(endpointId, filePayload).addOnFailureListener(e -> {
                    Log.e(TAG, "Failed sending file payload to " + endpointId, e);
                    outgoingPhotosMap.remove(payloadId);
                    recordOutgoingFailure(photo);
                });
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Failed sending metadata payload to " + endpointId, e);
                outgoingPhotosMap.remove(payloadId);
                recordOutgoingFailure(photo);
            });

        } catch (FileNotFoundException e) {
            Log.e(TAG, "File not found for photo sending: " + photo.getLocalFilePath(), e);
            recordOutgoingFailure(photo);
        }
    }

    private void sendRequestedPhoto(String endpointId, String photoId) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            SharedPhoto photo = PenguinApplication.getInstance().getDatabase().photoDao().getPhotoById(photoId);
            if (photo != null && FileUtils.isValidImageFile(photo.getLocalFilePath())) {
                sendPhotoToEndpoint(endpointId, new File(photo.getLocalFilePath()), photo);
            }
        });
    }

    public void sendMemberJoinPayload(String endpointId) {
        TransferPayload joinPayload = new TransferPayload();
        joinPayload.setType(TransferPayload.TYPE_MEMBER_JOIN);
        joinPayload.setSessionId(currentSessionId != null ? currentSessionId : "");
        joinPayload.setSessionName(currentSessionName != null ? currentSessionName : "");
        joinPayload.setOwnerDeviceId(PenguinApplication.getInstance().getAppDeviceId());
        joinPayload.setOwnerName(PenguinApplication.getInstance().getUserName());

        sendBytes(endpointId, joinPayload.toJson().getBytes(StandardCharsets.UTF_8));
    }

    public void sendInventorySync(String endpointId) {
        if (currentSessionId == null) return;
        AppDatabase.databaseWriteExecutor.execute(() -> {
            List<String> knownIds = PenguinApplication.getInstance().getDatabase()
                    .photoDao().getKnownPhotoIdsForSession(currentSessionId);

            InventoryPayload inventory = new InventoryPayload(
                    currentSessionId,
                    PenguinApplication.getInstance().getAppDeviceId(),
                    knownIds
            );
            sendBytes(endpointId, inventory.toJson().getBytes(StandardCharsets.UTF_8));
        });
    }

    public void sendBytes(String endpointId, byte[] bytes) {
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
                .addOnFailureListener(e -> Log.e(TAG, "Failed sending bytes to " + endpointId, e));
    }

    public void sendEndSessionToAll() {
        TransferPayload payload = new TransferPayload();
        payload.setType(TransferPayload.TYPE_SESSION_END);
        payload.setSessionId(currentSessionId);
        payload.setOwnerDeviceId(PenguinApplication.getInstance().getAppDeviceId());

        byte[] bytes = payload.toJson().getBytes(StandardCharsets.UTF_8);
        for (String endpointId : connectedPeers.keySet()) {
            sendBytes(endpointId, bytes);
        }
    }

    public void sendLeaveToAll() {
        TransferPayload payload = new TransferPayload();
        payload.setType(TransferPayload.TYPE_MEMBER_LEAVE);
        payload.setSessionId(currentSessionId);
        payload.setOwnerDeviceId(PenguinApplication.getInstance().getAppDeviceId());

        byte[] bytes = payload.toJson().getBytes(StandardCharsets.UTF_8);
        for (String endpointId : connectedPeers.keySet()) {
            sendBytes(endpointId, bytes);
        }
    }

    public void stopAllEndpoints() {
        stopAdvertising();
        stopDiscovery();
        connectionsClient.stopAllEndpoints();
        connectedPeers.clear();
        incomingMetadataMap.clear();
        transferringIncomingFilesMap.clear();
        completedIncomingFilesMap.clear();
        outgoingPhotosMap.clear();
        processedPhotoIds.clear();
        relayedPhotoIds.clear();
        currentSessionId = null;
        currentSessionName = null;
        currentRoomCode = null;
        discoveredHostSessionId = null;
        discoveredHostSessionName = null;
        Log.d(TAG, "Stopped all Nearby endpoints and cleaned up state");
    }

    private void recordOutgoingSuccess(SharedPhoto photo, PeerInfo peer) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            PhotoReceipt receipt = new PhotoReceipt(
                    photo.getPhotoId(),
                    peer.deviceId,
                    peer.displayName,
                    System.currentTimeMillis(),
                    SyncStatus.SYNCED
            );
            PenguinApplication.getInstance().getDatabase().photoDao().insertOrUpdateReceipt(receipt);

            // Check if photo is now delivered to all known active members
            PenguinApplication.getInstance().getDatabase().photoDao()
                    .updateSyncStatus(photo.getPhotoId(), SyncStatus.SYNCED, System.currentTimeMillis());

            notifyPhotoStatusChanged(photo.getPhotoId(), SyncStatus.SYNCED);
        });
    }

    private void recordOutgoingFailure(SharedPhoto photo) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            PenguinApplication.getInstance().getDatabase().photoDao()
                    .recordRetryAttempt(photo.getPhotoId(), SyncStatus.FAILED);
            notifyPhotoStatusChanged(photo.getPhotoId(), SyncStatus.FAILED);
        });
    }

    private void updatePhotoStatus(String photoId, SyncStatus status) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            PenguinApplication.getInstance().getDatabase().photoDao()
                    .updateSyncStatus(photoId, status, status == SyncStatus.SYNCED ? System.currentTimeMillis() : 0);
            notifyPhotoStatusChanged(photoId, status);
        });
    }

    // ==========================================
    // Event Notification Helpers
    // ==========================================

    private void notifyPeerConnected(PeerInfo peer) {
        mainHandler.post(() -> {
            for (NearbyEventListener l : new ArrayList<>(listeners)) {
                l.onPeerConnected(peer);
            }
        });
    }

    private void notifyPeerDisconnected(String endpointId, PeerInfo peer) {
        mainHandler.post(() -> {
            for (NearbyEventListener l : new ArrayList<>(listeners)) {
                l.onPeerDisconnected(endpointId, peer);
            }
        });
    }

    private void notifyPhotoReceived(SharedPhoto photo) {
        mainHandler.post(() -> {
            for (NearbyEventListener l : new ArrayList<>(listeners)) {
                l.onPhotoReceived(photo);
            }
        });
    }

    private void notifyPhotoStatusChanged(String photoId, SyncStatus status) {
        mainHandler.post(() -> {
            for (NearbyEventListener l : new ArrayList<>(listeners)) {
                l.onPhotoTransferStatusChanged(photoId, status);
            }
        });
    }

    private void notifySessionEndedByHost() {
        mainHandler.post(() -> {
            for (NearbyEventListener l : new ArrayList<>(listeners)) {
                l.onSessionEndedByHost();
            }
        });
    }

    private void notifyMemberLeft(String deviceId) {
        mainHandler.post(() -> {
            for (NearbyEventListener l : new ArrayList<>(listeners)) {
                l.onMemberLeft(deviceId);
            }
        });
    }

    private void notifyDiscoveryFailed(String reason) {
        mainHandler.post(() -> {
            for (NearbyEventListener l : new ArrayList<>(listeners)) {
                l.onDiscoveryFailed(reason);
            }
        });
    }

    private void notifyHostDiscovered(String endpointId, String roomCode) {
        mainHandler.post(() -> {
            for (NearbyEventListener l : new ArrayList<>(listeners)) {
                l.onHostDiscovered(endpointId, roomCode);
            }
        });
    }
}
