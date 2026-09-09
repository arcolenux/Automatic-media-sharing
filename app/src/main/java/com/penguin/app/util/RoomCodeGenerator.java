package com.penguin.app.util;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Generates and validates unambiguous 6-character room codes.
 * Excludes easily confused characters: I, L, O, 0, 1.
 */
public final class RoomCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private RoomCodeGenerator() {
        // Utility class
    }

    /**
     * Generates a random 6-character room code.
     */
    public static String generateRoomCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            int index = RANDOM.nextInt(ALPHABET.length());
            sb.append(ALPHABET.charAt(index));
        }
        return sb.toString();
    }

    /**
     * Validates if the given string is a valid 6-character room code.
     */
    public static boolean isValidRoomCode(String code) {
        if (code == null) {
            return false;
        }
        String cleanCode = code.trim().toUpperCase(Locale.US);
        if (cleanCode.length() != CODE_LENGTH) {
            return false;
        }
        for (int i = 0; i < cleanCode.length(); i++) {
            char c = cleanCode.charAt(i);
            if (ALPHABET.indexOf(c) == -1) {
                return false;
            }
        }
        return true;
    }

    /**
     * Normalizes a room code input (trimmed and uppercase).
     */
    public static String normalize(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().toUpperCase(Locale.US);
    }
}
