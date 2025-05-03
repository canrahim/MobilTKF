package com.veritabani.appcompatactivity23.download;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Helper class for working with content URIs.
 */
public class ContentUriHelper {
    private static final String TAG = "ContentUriHelper";
    private static final String DOWNLOAD_DIRECTORY = "Downloads";
    
    private final Context context;
    
    public ContentUriHelper(Context context) {
        this.context = context.getApplicationContext();
    }
    
    /**
     * Checks if a URL is a content URI.
     *
     * @param url The URL to check
     * @return True if the URL is a content URI, false otherwise
     */
    public boolean isContentUri(String url) {
        return url != null && url.startsWith("content://");
    }
    
    /**
     * Extracts metadata from a content URI.
     *
     * @param contentUri The content URI
     * @return An array containing [fileName, mimeType]
     */
    public String[] extractContentUriMetadata(Uri contentUri) {
        String fileName = "";
        String mimeType = "";
        
        try {
            Cursor cursor = context.getContentResolver().query(
                    contentUri,
                    new String[]{
                            android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                            android.provider.MediaStore.MediaColumns.MIME_TYPE
                    },
                    null, null, null);

            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex =
                        cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }

                int mimeIndex =
                        cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.MIME_TYPE);
                if (mimeIndex != -1) {
                    String contentMimeType = cursor.getString(mimeIndex);
                    if (!TextUtils.isEmpty(contentMimeType)) {
                        mimeType = contentMimeType;
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting content URI metadata", e);
        }
        
        return new String[]{fileName, mimeType};
    }
    
    /**
     * Saves a content URI to a file.
     *
     * @param contentUri The content URI
     * @param fileName The file name to save as
     * @param mimeType The MIME type of the file
     * @return True if the save was successful, false otherwise
     */
    public boolean saveContentUriToFile(Uri contentUri, String fileName, String mimeType) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(contentUri);
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for content URI");
                return false;
            }

            File downloadsDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_DIRECTORY);
            if (!downloadsDir.exists()) {
                if (!downloadsDir.mkdirs()) {
                    Log.e(TAG, "Failed to create downloads directory");
                    inputStream.close();
                    return false;
                }
            }

            File outputFile = new File(downloadsDir, fileName);
            FileOutputStream outputStream = new FileOutputStream(outputFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.flush();
            outputStream.close();
            inputStream.close();

            // Notify the system about the new file
            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri fileUri = Uri.fromFile(outputFile);
            mediaScanIntent.setData(fileUri);
            context.sendBroadcast(mediaScanIntent);
            
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error saving content URI to file", e);
            return false;
        }
    }
}