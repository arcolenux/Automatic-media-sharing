package com.penguin.app.util;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.HashMap;
import java.util.Map;

/**
 * QR Code generation and payload parsing utility.
 */
public final class QRCodeUtil {

    private static final String SCHEME = "penguin";
    private static final String HOST = "join";
    private static final String PARAM_CODE = "code";

    private QRCodeUtil() {
        // Utility class
    }

    /**
     * Builds the QR payload URI string for a room code: penguin://join?code=XXXXXX
     */
    public static String buildQRPayload(String roomCode) {
        return new Uri.Builder()
                .scheme(SCHEME)
                .authority(HOST)
                .appendQueryParameter(PARAM_CODE, RoomCodeGenerator.normalize(roomCode))
                .build()
                .toString();
    }

    /**
     * Extracts a 6-character room code from a scanned string or deep link URI.
     */
    public static String extractRoomCode(String scannedContent) {
        if (scannedContent == null || scannedContent.trim().isEmpty()) {
            return null;
        }

        String trimmed = scannedContent.trim();

        // 1. Direct 6-character room code
        if (RoomCodeGenerator.isValidRoomCode(trimmed)) {
            return RoomCodeGenerator.normalize(trimmed);
        }

        // 2. URI format: penguin://join?code=XXXXXX
        try {
            Uri uri = Uri.parse(trimmed);
            if (SCHEME.equalsIgnoreCase(uri.getScheme())) {
                String code = uri.getQueryParameter(PARAM_CODE);
                if (code != null && RoomCodeGenerator.isValidRoomCode(code)) {
                    return RoomCodeGenerator.normalize(code);
                }
            }
        } catch (Exception ignored) {
        }

        // 3. Fallback regex or substring search if URL has code parameter
        if (trimmed.contains("code=")) {
            int idx = trimmed.indexOf("code=");
            String candidate = trimmed.substring(idx + 5);
            if (candidate.length() >= 6) {
                candidate = candidate.substring(0, 6);
                if (RoomCodeGenerator.isValidRoomCode(candidate)) {
                    return RoomCodeGenerator.normalize(candidate);
                }
            }
        }

        return null;
    }

    /**
     * Generates a QR Code bitmap from the room code.
     */
    public static Bitmap generateQRCodeBitmap(String roomCode, int width, int height) throws WriterException {
        String payload = buildQRPayload(roomCode);

        Map<EncodeHintType, Object> hints = new HashMap<>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(payload, BarcodeFormat.QR_CODE, width, height, hints);

        int matrixWidth = bitMatrix.getWidth();
        int matrixHeight = bitMatrix.getHeight();
        int[] pixels = new int[matrixWidth * matrixHeight];

        // Dark teal/charcoal styling with crisp high-contrast for scanning
        int colorDark = Color.parseColor("#181A1D");
        int colorLight = Color.parseColor("#F1F2F0");

        for (int y = 0; y < matrixHeight; y++) {
            int offset = y * matrixWidth;
            for (int x = 0; x < matrixWidth; x++) {
                pixels[offset + x] = bitMatrix.get(x, y) ? colorDark : colorLight;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(matrixWidth, matrixHeight, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, matrixWidth, 0, 0, matrixWidth, matrixHeight);
        return bitmap;
    }
}
