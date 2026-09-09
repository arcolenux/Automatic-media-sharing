package com.penguin.app.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a group photo sharing session.
 */
@Entity(tableName = "sessions")
public class Session implements Serializable {

    @PrimaryKey
    @NonNull
    private String sessionId;
    private String sessionName;
    private String roomCode;
    private String hostDeviceId;
    private long createdAt;
    private long endedAt;
    private boolean isActive;
    private int memberCount;
    private boolean isHost;

    public Session(@NonNull String sessionId, String sessionName, String roomCode,
                   String hostDeviceId, long createdAt, long endedAt,
                   boolean isActive, int memberCount, boolean isHost) {
        this.sessionId = sessionId;
        this.sessionName = sessionName;
        this.roomCode = roomCode;
        this.hostDeviceId = hostDeviceId;
        this.createdAt = createdAt;
        this.endedAt = endedAt;
        this.isActive = isActive;
        this.memberCount = memberCount;
        this.isHost = isHost;
    }

    @Ignore
    public Session(@NonNull String sessionId, String sessionName, String roomCode,
                   String hostDeviceId, boolean isHost) {
        this(sessionId, sessionName, roomCode, hostDeviceId, System.currentTimeMillis(), 0, true, 1, isHost);
    }

    @NonNull
    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(@NonNull String sessionId) {
        this.sessionId = sessionId;
    }

    public String getSessionName() {
        return sessionName;
    }

    public void setSessionName(String sessionName) {
        this.sessionName = sessionName;
    }

    public String getRoomCode() {
        return roomCode;
    }

    public void setRoomCode(String roomCode) {
        this.roomCode = roomCode;
    }

    public String getHostDeviceId() {
        return hostDeviceId;
    }

    public void setHostDeviceId(String hostDeviceId) {
        this.hostDeviceId = hostDeviceId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(long endedAt) {
        this.endedAt = endedAt;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public int getMemberCount() {
        return memberCount;
    }

    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }

    public boolean isHost() {
        return isHost;
    }

    public void setHost(boolean host) {
        isHost = host;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Session session = (Session) o;
        return createdAt == session.createdAt &&
                endedAt == session.endedAt &&
                isActive == session.isActive &&
                memberCount == session.memberCount &&
                isHost == session.isHost &&
                sessionId.equals(session.sessionId) &&
                Objects.equals(sessionName, session.sessionName) &&
                Objects.equals(roomCode, session.roomCode) &&
                Objects.equals(hostDeviceId, session.hostDeviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sessionId, sessionName, roomCode, hostDeviceId, createdAt, endedAt, isActive, memberCount, isHost);
    }
}
