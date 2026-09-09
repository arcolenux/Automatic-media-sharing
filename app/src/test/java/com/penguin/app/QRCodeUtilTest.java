package com.penguin.app;

import com.penguin.app.util.QRCodeUtil;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Unit tests for QRCodeUtil.
 */
@RunWith(RobolectricTestRunner.class)
public class QRCodeUtilTest {

    @Test
    public void testBuildQRPayload() {
        String payload = QRCodeUtil.buildQRPayload("X8K9M2");
        assertEquals("penguin://join?code=X8K9M2", payload);
    }

    @Test
    public void testExtractRoomCode_directCode() {
        assertEquals("X8K9M2", QRCodeUtil.extractRoomCode("X8K9M2"));
        assertEquals("ABC234", QRCodeUtil.extractRoomCode("abc234"));
    }

    @Test
    public void testExtractRoomCode_uriFormat() {
        assertEquals("X8K9M2", QRCodeUtil.extractRoomCode("penguin://join?code=X8K9M2"));
        assertEquals("ABC234", QRCodeUtil.extractRoomCode("penguin://join?code=abc234"));
    }

    @Test
    public void testExtractRoomCode_invalid() {
        assertNull(QRCodeUtil.extractRoomCode(null));
        assertNull(QRCodeUtil.extractRoomCode(""));
        assertNull(QRCodeUtil.extractRoomCode("invalid_format"));
        assertNull(QRCodeUtil.extractRoomCode("penguin://join?code=INVALID_1"));
    }
}
