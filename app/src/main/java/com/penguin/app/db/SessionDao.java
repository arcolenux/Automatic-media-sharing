package com.penguin.app.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.penguin.app.model.Session;
import com.penguin.app.model.SessionMember;

import java.util.List;

/**
 * Data Access Object for Sessions and Session Members.
 */
@Dao
public interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertSession(Session session);

    @Update
    void updateSession(Session session);

    @Delete
    void deleteSession(Session session);

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId LIMIT 1")
    Session getSessionById(String sessionId);

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId LIMIT 1")
    LiveData<Session> getSessionByIdLive(String sessionId);

    @Query("SELECT * FROM sessions WHERE isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    Session getActiveSession();

    @Query("SELECT * FROM sessions WHERE isActive = 1 ORDER BY createdAt DESC LIMIT 1")
    LiveData<Session> getActiveSessionLive();

    @Query("SELECT * FROM sessions WHERE roomCode = :roomCode AND isActive = 1 LIMIT 1")
    Session getActiveSessionByRoomCode(String roomCode);

    @Query("UPDATE sessions SET isActive = 0, endedAt = :endedAt WHERE isActive = 1")
    void deactivateAllSessions(long endedAt);

    @Query("UPDATE sessions SET memberCount = :count WHERE sessionId = :sessionId")
    void updateMemberCount(String sessionId, int count);

    // --- Session Members ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrUpdateMember(SessionMember member);

    @Query("SELECT * FROM session_members WHERE sessionId = :sessionId AND hasLeft = 0 ORDER BY joinedAt ASC")
    List<SessionMember> getMembersForSession(String sessionId);

    @Query("SELECT * FROM session_members WHERE sessionId = :sessionId AND hasLeft = 0 ORDER BY joinedAt ASC")
    LiveData<List<SessionMember>> getMembersForSessionLive(String sessionId);

    @Query("SELECT COUNT(*) FROM session_members WHERE sessionId = :sessionId AND hasLeft = 0")
    int getActiveMembersCount(String sessionId);

    @Query("SELECT * FROM session_members WHERE sessionId = :sessionId AND isNearby = 1 AND hasLeft = 0")
    List<SessionMember> getNearbyMembers(String sessionId);

    @Query("UPDATE session_members SET isNearby = :isNearby, endpointId = :endpointId, lastSeenAt = :lastSeenAt WHERE sessionId = :sessionId AND deviceId = :deviceId")
    void setMemberNearbyStatus(String sessionId, String deviceId, boolean isNearby, String endpointId, long lastSeenAt);

    @Query("UPDATE session_members SET isNearby = 0 WHERE sessionId = :sessionId")
    void setAllMembersNotNearby(String sessionId);

    @Query("UPDATE session_members SET hasLeft = 1, isNearby = 0, lastSeenAt = :leftAt WHERE sessionId = :sessionId AND deviceId = :deviceId")
    void markMemberLeft(String sessionId, String deviceId, long leftAt);
}
