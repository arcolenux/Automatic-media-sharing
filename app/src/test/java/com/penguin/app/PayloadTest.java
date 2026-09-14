package com.penguin.app;

import com.penguin.app.model.InventoryPayload;
import com.penguin.app.model.SharedPhoto;
import com.penguin.app.model.TransferPayload;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for TransferPayload and InventoryPayload serialization.
 */
@RunWith(RobolectricTestRunner.class)
public class PayloadTest {

    @Test
    public void testTransferPayload_photoMetadataSerialization() {
        SharedPhoto photo = new SharedPhoto(
                "photo-123",
                "session-abc",
                "device-xyz",
                "Alice",
                "/storage/dcim/camera/photo.jpg",
                1700000000000L
        );
        photo.setFileSize(2048000L);

        TransferPayload payload = TransferPayload.forPhotoMetadata(photo, 999888L);
        String json = payload.toJson();
        assertNotNull(json);

        TransferPayload parsed = TransferPayload.fromJson(json);
        assertNotNull(parsed);
        assertEquals(TransferPayload.TYPE_PHOTO_METADATA, parsed.getType());
        assertEquals("photo-123", parsed.getPhotoId());
        assertEquals("session-abc", parsed.getSessionId());
        assertEquals("device-xyz", parsed.getOwnerDeviceId());
        assertEquals("Alice", parsed.getOwnerName());
        assertEquals(1700000000000L, parsed.getCapturedAt());
        assertEquals(2048000L, parsed.getFileSize());
        assertEquals(999888L, parsed.getNearbyPayloadId());
    }

    @Test
    public void testTransferPayload_memberRosterSerialization() {
        String rosterJson = "[{\"deviceId\":\"dev1\",\"displayName\":\"Host\",\"endpointId\":\"ep1\",\"isNearby\":true}]";
        TransferPayload roster = TransferPayload.forMemberRoster("session-123", "Trip 2026", rosterJson);

        String json = roster.toJson();
        assertNotNull(json);

        TransferPayload parsed = TransferPayload.fromJson(json);
        assertNotNull(parsed);
        assertEquals(TransferPayload.TYPE_MEMBER_ROSTER, parsed.getType());
        assertEquals("session-123", parsed.getSessionId());
        assertEquals("Trip 2026", parsed.getSessionName());
        assertEquals(rosterJson, parsed.getExtraData());
    }

    @Test
    public void testTransferPayload_memberJoinSerialization() {
        TransferPayload join = new TransferPayload();
        join.setType(TransferPayload.TYPE_MEMBER_JOIN);
        join.setSessionId("session-xyz");
        join.setSessionName("Beach Party");
        join.setOwnerDeviceId("dev-peer");
        join.setOwnerName("Bob");

        String json = join.toJson();
        assertNotNull(json);

        TransferPayload parsed = TransferPayload.fromJson(json);
        assertNotNull(parsed);
        assertEquals(TransferPayload.TYPE_MEMBER_JOIN, parsed.getType());
        assertEquals("session-xyz", parsed.getSessionId());
        assertEquals("Beach Party", parsed.getSessionName());
        assertEquals("dev-peer", parsed.getOwnerDeviceId());
        assertEquals("Bob", parsed.getOwnerName());
    }
}
