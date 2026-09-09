package com.penguin.app.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

/**
 * Metadata payload transmitted over Nearby Connections describing a shared photo.
 */
public class TransferPayload implements Serializable {

    public static final String TYPE_PHOTO_METADATA = "PHOTO_METADATA";
    public static final String TYPE_PHOTO_REQUEST = "PHOTO_REQUEST";
    public static final String TYPE_INVENTORY_SYNC = "INVENTORY_SYNC";
    public static final String TYPE_SESSION_END = "SESSION_END";
    public static final String TYPE_MEMBER_LEAVE = "MEMBER_LEAVE";
    public static final String TYPE_MEMBER_JOIN = "MEMBER_JOIN";

    private String type;
    private String photoId;
    private String sessionId;
    private String ownerDeviceId;
    private String ownerName;
    private long capturedAt;
    private long fileSize;
    private long nearbyPayloadId; // Links metadata to Nearby Connections File Payload ID

    public TransferPayload() {
    }

    public static TransferPayload forPhotoMetadata(SharedPhoto photo, long nearbyPayloadId) {
        TransferPayload payload = new TransferPayload();
        payload.type = TYPE_PHOTO_METADATA;
        payload.photoId = photo.getPhotoId();
        payload.sessionId = photo.getSessionId();
        payload.ownerDeviceId = photo.getOwnerDeviceId();
        payload.ownerName = photo.getOwnerName();
        payload.capturedAt = photo.getCapturedAt();
        payload.fileSize = photo.getFileSize();
        payload.nearbyPayloadId = nearbyPayloadId;
        return payload;
    }

    public static TransferPayload forPhotoRequest(String photoId, String sessionId, String requestingDeviceId) {
        TransferPayload payload = new TransferPayload();
        payload.type = TYPE_PHOTO_REQUEST;
        payload.photoId = photoId;
        payload.sessionId = sessionId;
        payload.ownerDeviceId = requestingDeviceId;
        return payload;
    }

    public String toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", type);
            obj.put("photoId", photoId);
            obj.put("sessionId", sessionId);
            obj.put("ownerDeviceId", ownerDeviceId);
            obj.put("ownerName", ownerName);
            obj.put("capturedAt", capturedAt);
            obj.put("fileSize", fileSize);
            obj.put("nearbyPayloadId", nearbyPayloadId);
            return obj.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static TransferPayload fromJson(String jsonStr) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            TransferPayload payload = new TransferPayload();
            payload.type = obj.optString("type", "");
            payload.photoId = obj.optString("photoId", "");
            payload.sessionId = obj.optString("sessionId", "");
            payload.ownerDeviceId = obj.optString("ownerDeviceId", "");
            payload.ownerName = obj.optString("ownerName", "");
            payload.capturedAt = obj.optLong("capturedAt", 0);
            payload.fileSize = obj.optLong("fileSize", 0);
            payload.nearbyPayloadId = obj.optLong("nearbyPayloadId", 0);
            return payload;
        } catch (JSONException e) {
            return null;
        }
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getPhotoId() {
        return photoId;
    }

    public void setPhotoId(String photoId) {
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

    public long getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(long capturedAt) {
        this.capturedAt = capturedAt;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public long getNearbyPayloadId() {
        return nearbyPayloadId;
    }

    public void setNearbyPayloadId(long nearbyPayloadId) {
        this.nearbyPayloadId = nearbyPayloadId;
    }
}
