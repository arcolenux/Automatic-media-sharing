package com.penguin.app.util;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import androidx.exifinterface.media.ExifInterface;

import com.penguin.app.PenguinApplication;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * File management utilities for moving, reading and validating photo files.
 */
public final class FileUtils {

    private FileUtils() {
        // Utility class
    }

    /**
     * Copies a payload file received from Nearby Connections to the permanent shared photo directory.
     * Safely checks for Uri, java.io.File, and ParcelFileDescriptor from Google Nearby Connections Payload.File.
     *
     * @param context Application context
     * @param filePayload Received Payload.File from Nearby Connections
     * @param targetFileName Name of destination file (e.g. photoId.jpg)
     * @return Absolute file path of the copied photo
     */
    public static String copyReceivedPayloadFile(Context context, com.google.android.gms.nearby.connection.Payload.File filePayload, String targetFileName) throws IOException {
        return copyReceivedPayloadFile(context, filePayload, targetFileName, null);
    }

    public static String copyReceivedPayloadFile(Context context, com.google.android.gms.nearby.connection.Payload.File filePayload, String targetFileName, String sessionName) throws IOException {
        if (filePayload == null) {
            throw new IOException("Cannot copy null Payload.File");
        }

        File targetDir = PenguinApplication.getInstance().getSharedPhotosDirectory(sessionName);
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        File destinationFile = new File(targetDir, targetFileName);

        InputStream in = null;
        if (filePayload.asUri() != null) {
            try {
                in = context.getContentResolver().openInputStream(filePayload.asUri());
            } catch (Exception ignored) {
            }
        }
        if (in == null && filePayload.asJavaFile() != null && filePayload.asJavaFile().exists()) {
            in = new FileInputStream(filePayload.asJavaFile());
        }
        if (in == null && filePayload.asParcelFileDescriptor() != null) {
            in = new FileInputStream(filePayload.asParcelFileDescriptor().getFileDescriptor());
        }

        if (in == null) {
            throw new IOException("Failed to obtain input stream from Payload.File");
        }

        try (InputStream input = in;
             OutputStream out = new FileOutputStream(destinationFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }

        // Notify Android MediaStore so the photo appears in Gallery / Google Photos
        scanMediaFile(context, destinationFile);

        return destinationFile.getAbsolutePath();
    }

    /**
     * Copies a payload file given a Uri fallback.
     */
    public static String copyReceivedPayloadFile(Context context, Uri incomingUri, String targetFileName) throws IOException {
        return copyReceivedPayloadFile(context, incomingUri, targetFileName, null);
    }

    public static String copyReceivedPayloadFile(Context context, Uri incomingUri, String targetFileName, String sessionName) throws IOException {
        File targetDir = PenguinApplication.getInstance().getSharedPhotosDirectory(sessionName);
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        File destinationFile = new File(targetDir, targetFileName);

        try (InputStream in = context.getContentResolver().openInputStream(incomingUri);
             OutputStream out = new FileOutputStream(destinationFile)) {
            if (in == null) {
                throw new IOException("Failed to open input stream from payload Uri: " + incomingUri);
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }

        // Notify Android MediaStore so the photo appears in Gallery / Google Photos
        scanMediaFile(context, destinationFile);

        return destinationFile.getAbsolutePath();
    }

    /**
     * Copies a raw File to the shared photos directory.
     */
    public static String copyFileToSharedDir(Context context, File sourceFile, String targetFileName) throws IOException {
        return copyFileToSharedDir(context, sourceFile, targetFileName, null);
    }

    public static String copyFileToSharedDir(Context context, File sourceFile, String targetFileName, String sessionName) throws IOException {
        File targetDir = PenguinApplication.getInstance().getSharedPhotosDirectory(sessionName);
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        File destinationFile = new File(targetDir, targetFileName);

        try (InputStream in = new FileInputStream(sourceFile);
             OutputStream out = new FileOutputStream(destinationFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
        }

        // Notify Android MediaStore so the photo appears in Gallery / Google Photos
        scanMediaFile(context, destinationFile);

        return destinationFile.getAbsolutePath();
    }

    private static void scanMediaFile(Context context, File file) {
        if (context != null && file != null && file.exists()) {
            try {
                android.media.MediaScannerConnection.scanFile(
                        context.getApplicationContext(),
                        new String[]{file.getAbsolutePath()},
                        new String[]{"image/jpeg"},
                        null
                );
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Reads image width and height without decoding the full bitmap into memory.
     */
    public static int[] getImageDimensions(String filePath) {
        if (filePath == null || !new File(filePath).exists()) {
            return new int[]{0, 0};
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);

        int width = options.outWidth;
        int height = options.outHeight;

        try {
            ExifInterface exif = new ExifInterface(filePath);
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                return new int[]{height, width};
            }
        } catch (IOException ignored) {
        }

        return new int[]{width, height};
    }

    /**
     * Checks if a local file exists and has non-zero size.
     */
    public static boolean isValidImageFile(String filePath) {
        if (filePath == null) return false;
        File file = new File(filePath);
        return file.exists() && file.isFile() && file.length() > 0;
    }
}
