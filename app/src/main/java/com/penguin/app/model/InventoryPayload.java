package com.penguin.app.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Payload used to exchange photo inventories between peers upon reconnection.
 */
public class InventoryPayload implements Serializable {

    private String sessionId;
    private String deviceId;
    private List<String> knownPhotoIds = new ArrayList<>();

    public InventoryPayload() {
    }

    public InventoryPayload(String sessionId, String deviceId, List<String> knownPhotoIds) {
        this.sessionId = sessionId;
        this.deviceId = deviceId;
        if (knownPhotoIds != null) {
            this.knownPhotoIds = knownPhotoIds;
        }
    }

    public String toJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", TransferPayload.TYPE_INVENTORY_SYNC);
            obj.put("sessionId", sessionId);
            obj.put("deviceId", deviceId);
            JSONArray array = new JSONArray();
            for (String id : knownPhotoIds) {
                array.put(id);
            }
            obj.put("photoIds", array);
            return obj.toString();
        } catch (JSONException e) {
            return "{}";
        }
    }

    public static InventoryPayload fromJson(String jsonStr) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            InventoryPayload payload = new InventoryPayload();
            payload.sessionId = obj.optString("sessionId", "");
            payload.deviceId = obj.optString("deviceId", "");
            JSONArray array = obj.optJSONArray("photoIds");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    payload.knownPhotoIds.add(array.getString(i));
                }
            }
            return payload;
        } catch (JSONException e) {
            return null;
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public List<String> getKnownPhotoIds() {
        return knownPhotoIds;
    }

    public void setKnownPhotoIds(List<String> knownPhotoIds) {
        this.knownPhotoIds = knownPhotoIds;
    }
}
