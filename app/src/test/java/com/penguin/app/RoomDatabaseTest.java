package com.penguin.app;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.penguin.app.db.AppDatabase;
import com.penguin.app.db.PhotoDao;
import com.penguin.app.db.SessionDao;
import com.penguin.app.model.PhotoReceipt;
import com.penguin.app.model.Session;
import com.penguin.app.model.SessionMember;
import com.penguin.app.model.SharedPhoto;
import com.penguin.app.model.SyncStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit test for Room database DAOs, Session, Photo and Receipt entities.
 */
@RunWith(RobolectricTestRunner.class)
public class RoomDatabaseTest {

    private AppDatabase db;
    private SessionDao sessionDao;
    private PhotoDao photoDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        sessionDao = db.sessionDao();
        photoDao = db.photoDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void testSessionDao_insertAndRetrieve() {
        Session session = new Session(
                "s1",
                "Beach Trip",
                "X8K9M2",
                "device-host",
                1700000000000L,
                0,
                true,
                1,
                true
        );

        sessionDao.insertSession(session);
        Session retrieved = sessionDao.getSessionById("s1");
        assertNotNull(retrieved);
        assertEquals("Beach Trip", retrieved.getSessionName());
        assertEquals("X8K9M2", retrieved.getRoomCode());
        assertTrue(retrieved.isActive());
        assertTrue(retrieved.isHost());

        Session active = sessionDao.getActiveSession();
        assertNotNull(active);
        assertEquals("s1", active.getSessionId());

        sessionDao.deactivateAllSessions(1700000010000L);
        assertNull(sessionDao.getActiveSession());
    }

    @Test
    public void testSessionMember_persistentMembershipAcrossDisconnection() {
        SessionMember member1 = new SessionMember("s1", "dev-1", "Alice", "end-1", true, 1000L, 1000L, false);
        SessionMember member2 = new SessionMember("s1", "dev-2", "Bob", "end-2", true, 1000L, 1000L, false);

        sessionDao.insertOrUpdateMember(member1);
        sessionDao.insertOrUpdateMember(member2);

        assertEquals(2, sessionDao.getActiveMembersCount("s1"));

        // Bob walks away out of Nearby range: isNearby = false
        sessionDao.setMemberNearbyStatus("s1", "dev-2", false, null, 2000L);

        // Bob is STILL an active session member!
        assertEquals(2, sessionDao.getActiveMembersCount("s1"));
        List<SessionMember> members = sessionDao.getMembersForSession("s1");
        assertEquals(2, members.size());

        List<SessionMember> nearby = sessionDao.getNearbyMembers("s1");
        assertEquals(1, nearby.size());
        assertEquals("dev-1", nearby.get(0).getDeviceId());

        // Bob voluntarily leaves
        sessionDao.markMemberLeft("s1", "dev-2", 3000L);
        assertEquals(1, sessionDao.getActiveMembersCount("s1"));
    }

    @Test
    public void testPhotoDao_syncAndOptOutStates() {
        SharedPhoto photo = new SharedPhoto(
                "p1",
                "s1",
                "dev-1",
                "Alice",
                "/storage/dcim/camera/p1.jpg",
                1000L
        );
        photoDao.insertPhoto(photo);

        SharedPhoto retrieved = photoDao.getPhotoById("p1");
        assertNotNull(retrieved);
        assertEquals(SyncStatus.QUEUED, retrieved.getSyncStatus());
        assertFalse(retrieved.isOptedOut());

        // Update status to SYNCED
        photoDao.updateSyncStatus("p1", SyncStatus.SYNCED, 2000L);
        retrieved = photoDao.getPhotoById("p1");
        assertEquals(SyncStatus.SYNCED, retrieved.getSyncStatus());
        assertEquals(2000L, retrieved.getSyncedAt());

        // Test opt-out
        photoDao.setOptedOut("p1", true);
        retrieved = photoDao.getPhotoById("p1");
        assertTrue(retrieved.isOptedOut());
        assertEquals(SyncStatus.CANCELLED, retrieved.getSyncStatus());

        // Opted-out photos are excluded from gallery queries
        assertEquals(0, photoDao.getPhotosForSession("s1").size());
    }

    @Test
    public void testPhotoReceipt_perPeerSynchronization() {
        SharedPhoto p1 = new SharedPhoto("p1", "s1", "dev-1", "Alice", "/storage/p1.jpg", 1000L);
        SharedPhoto p2 = new SharedPhoto("p2", "s1", "dev-1", "Alice", "/storage/p2.jpg", 2000L);
        photoDao.insertPhoto(p1);
        photoDao.insertPhoto(p2);

        // Bob has not received p1 or p2 yet
        List<SharedPhoto> undelivered = photoDao.getPhotosNotDeliveredToPeer("s1", "dev-bob");
        assertEquals(2, undelivered.size());

        // Deliver p1 to Bob
        PhotoReceipt receipt = new PhotoReceipt("p1", "dev-bob", "Bob", 3000L, SyncStatus.SYNCED);
        photoDao.insertOrUpdateReceipt(receipt);

        // Bob is now only missing p2
        undelivered = photoDao.getPhotosNotDeliveredToPeer("s1", "dev-bob");
        assertEquals(1, undelivered.size());
        assertEquals("p2", undelivered.get(0).getPhotoId());
    }
}
