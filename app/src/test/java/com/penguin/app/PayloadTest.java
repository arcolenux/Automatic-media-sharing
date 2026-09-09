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
    public void testInventoryPayload_serialization() {
        List<String> photoIds = Arrays.asList("id-1", "id-2", "id-3");
        InventoryPayload inventory = new InventoryPayload("session-1", "device-1", photoIds);

        String json = inventory.toJson();
        assertNotNull(json);

        InventoryPayload parsed = InventoryPayload.fromJson(json);
        assertNotNull(parsed);
        assertEquals("session-1", parsed.getSessionId());
        assertEquals("device-1", parsed.getDeviceId());
        assertEquals(3, parsed.getKnownPhotoIds().size());
        assertTrue(parsed.getKnownPhotoIds().contains("id-1"));
        assertTrue(parsed.getKnownPhotoIds().contains("id-2"));
        assertTrue(parsed.getKnownPhotoIds().contains("id-3"));
    }
}
