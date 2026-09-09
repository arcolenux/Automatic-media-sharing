package com.penguin.app;

import com.penguin.app.util.RoomCodeGenerator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for RoomCodeGenerator.
 */
public class RoomCodeGeneratorTest {

    @Test
    public void testGenerateRoomCode_formatAndLength() {
        for (int i = 0; i < 100; i++) {
            String code = RoomCodeGenerator.generateRoomCode();
            assertNotNull(code);
            assertEquals(6, code.length());
            assertTrue(RoomCodeGenerator.isValidRoomCode(code));

            // Verify absence of ambiguous characters
            assertFalse(code.contains("0"));
            assertFalse(code.contains("1"));
            assertFalse(code.contains("I"));
            assertFalse(code.contains("L"));
            assertFalse(code.contains("O"));
        }
    }

    @Test
    public void testIsValidRoomCode_validCodes() {
        assertTrue(RoomCodeGenerator.isValidRoomCode("ABC234"));
        assertTrue(RoomCodeGenerator.isValidRoomCode("X8K9M2"));
        assertTrue(RoomCodeGenerator.isValidRoomCode("ZZZZZZ"));
        assertTrue(RoomCodeGenerator.isValidRoomCode("234567"));
        assertTrue(RoomCodeGenerator.isValidRoomCode("abc234")); // Normalizes case
    }

    @Test
    public void testIsValidRoomCode_invalidCodes() {
        assertFalse(RoomCodeGenerator.isValidRoomCode(null));
        assertFalse(RoomCodeGenerator.isValidRoomCode(""));
        assertFalse(RoomCodeGenerator.isValidRoomCode("ABC")); // Too short
        assertFalse(RoomCodeGenerator.isValidRoomCode("ABC2345")); // Too long
        assertFalse(RoomCodeGenerator.isValidRoomCode("ABCI34")); // Contains 'I'
        assertFalse(RoomCodeGenerator.isValidRoomCode("ABC034")); // Contains '0'
        assertFalse(RoomCodeGenerator.isValidRoomCode("ABC134")); // Contains '1'
        assertFalse(RoomCodeGenerator.isValidRoomCode("ABCL34")); // Contains 'L'
        assertFalse(RoomCodeGenerator.isValidRoomCode("ABCO34")); // Contains 'O'
        assertFalse(RoomCodeGenerator.isValidRoomCode("ABC-34")); // Contains special char
    }

    @Test
    public void testNormalize() {
        assertEquals("ABC234", RoomCodeGenerator.normalize("  abc234  "));
        assertEquals("", RoomCodeGenerator.normalize(null));
    }
}
