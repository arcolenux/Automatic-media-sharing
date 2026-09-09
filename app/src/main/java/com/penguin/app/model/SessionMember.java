package com.penguin.app.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a member of a session.
 * Tracks persistent membership even across temporary Nearby disconnections.
 */
@Entity(
    tableName = "session_members",
    primaryKeys = {"sessionId", "deviceId"},
    indices = {@Index("sessionId"), @Index("deviceId")}
)
public class SessionMember implements Serializable {

    @NonNull
    private String sessionId;
    @NonNull
    private String deviceId;
    private String displayName;
    private String endpointId;
    private boolean isNearby;
    private long joinedAt;
    private long lastSeenAt;
    private boolean hasLeft;

    public SessionMember(@NonNull String sessionId, @NonNull String deviceId,
                         String displayName, String endpointId, boolean isNearby,
                         long joinedAt, long lastSeenAt, boolean hasLeft) {
        this.sessionId = sessionId;
        this.deviceId = deviceId;
        this.displayName = displayName;
        this.endpointId = endpointId;
        this.isNearby = isNearby;
        this.joinedAt = joinedAt;
        this.lastSeenAt = lastSeenAt;
        this.hasLeft = hasLeft;
    }

    @NonNull
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(@NonNull String sessionId) {
        this.sessionId = sessionId;
    }

    @NonNull
    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(@NonNull String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEndpointId() {
        return endpointId;
    }

    public void setEndpointId(String endpointId) {
        this.endpointId = endpointId;
    }

    public boolean isNearby() {
        return isNearby;
    }

    public void setNearby(boolean nearby) {
        isNearby = nearby;
    }

    public long getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(long joinedAt) {
        this.joinedAt = joinedAt;
    }

    public long getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(long lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public boolean isHasLeft() {
        return hasLeft;
    }

    public void setHasLeft(boolean hasLeft) {
        this.hasLeft = hasLeft;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SessionMember that = (SessionMember) o;
        return isNearby == that.isNearby &&
                joinedAt == that.joinedAt &&
                lastSeenAt == that.lastSeenAt &&
                hasLeft == that.hasLeft &&
                sessionId.equals(that.sessionId) &&
                deviceId.equals(that.deviceId) &&
                Objects.equals(displayName, that.displayName) &&
                Objects.equals(endpointId, that.endpointId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, deviceId, displayName, endpointId, isNearby, joinedAt, lastSeenAt, hasLeft);
    }
}
