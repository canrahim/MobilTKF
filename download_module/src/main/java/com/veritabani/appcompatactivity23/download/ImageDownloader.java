package com.veritabani.appcompatactivity23.download;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.WebView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Helper class for downloading images.
 */
public class ImageDownloader {
    private static final String TAG = "ImageDownloader";
    private static final String DOWNLOAD_DIRECTORY = "Downloads";
    
    private final Context context;
    private final DownloadManager downloadManager;
    
    public ImageDownloader(Context context) {
        this.context = context.getApplicationContext();
        this.downloadManager = DownloadManager.getInstance(context);
    }
    
    /**
     * Removes the file extension from a filename
     * @param fileName Filename with extension
     * @return Filename without extension
     */
    private String removeExtension(String fileName) {
        if (fileName == null) return "";
        int lastDotPos = fileName.lastIndexOf(".");
        if (lastDotPos > 0) {
            return fileName.substring(0, lastDotPos);
        }
        return fileName;
    }
    
    /**
     * Downloads an image from the given URL.
     *
     * @param imageUrl The URL of the image to download
     * @param webView Optional WebView to get user agent and cookies from (can be null)
     */
    public void downloadImage(String imageUrl, WebView webView) {
        Log.d(TAG, "downloadImage: " + imageUrl);
        boolean isContentUri = imageUrl.startsWith("content://");
        String fileName = "";
        String mimeType = "image/jpeg"; // Default MIME type
        
        // Log the image download request
        Log.d(TAG, "Starting image download from URL: " + imageUrl);

        if (isContentUri) {
            try {
                Uri contentUri = Uri.parse(imageUrl);

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
                Log.e(TAG, "Error processing content URI", e);
            }
        } else {
            try {
                Uri uri = Uri.parse(imageUrl);
                String lastPathSegment = uri.getLastPathSegment();
                if (lastPathSegment != null && !lastPathSegment.isEmpty()) {
                    fileName = lastPathSegment;
                    int queryIndex = fileName.indexOf('?');
                    if (queryIndex > 0) {
                        fileName = fileName.substring(0, queryIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error extracting filename from URL", e);
            }
        }

        if (TextUtils.isEmpty(fileName)) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                    Locale.getDefault()).format(new Date());
            fileName = "IMG_" + timeStamp;
            Log.d(TAG, "Generated timestamp filename: " + fileName);
        }
        
        // Log extracted filename
        Log.d(TAG, "Image filename before extension check: " + fileName);

        String extension = "";
        int lastDotPos = fileName.lastIndexOf(".");
        if (lastDotPos > 0 && lastDotPos < fileName.length() - 1) {
            extension = fileName.substring(lastDotPos + 1).toLowerCase();
        }

        boolean hasImageExtension = extension.equals("jpg") ||
                extension.equals("jpeg") ||
                extension.equals("png") ||
                extension.equals("gif") ||
                extension.equals("bmp") ||
                extension.equals("webp") ||
                extension.equals("bin"); // .bin dosyası olsa bile uzantıyı değiştireceğiz

        if (!hasImageExtension || extension.equals("bin")) {
            if (mimeType.equals("image/jpeg") || mimeType.equals("image/jpg")) {
                // Önce mevcut uzantıyı kaldır
                fileName = removeExtension(fileName);
                fileName += ".jpg";
                // Ensure consistent MIME type
                mimeType = "image/jpeg";
            } else if (mimeType.equals("image/png")) {
                fileName = removeExtension(fileName);
                fileName += ".png";
            } else if (mimeType.equals("image/gif")) {
                fileName = removeExtension(fileName);
                fileName += ".gif";
            } else if (mimeType.equals("image/bmp")) {
                fileName = removeExtension(fileName);
                fileName += ".bmp";
            } else if (mimeType.equals("image/webp")) {
                fileName = removeExtension(fileName);
                fileName += ".webp";
            } else {
                // Default to jpg for unknown image types
                fileName = removeExtension(fileName);
                fileName += ".jpg";
                mimeType = "image/jpeg";
            }
            Log.d(TAG, "Added extension to filename: " + fileName);
        } else if (!extension.equals("bin")) {
            // If file already has extension (but not .bin), ensure the MIME type matches
            if (fileName.toLowerCase().endsWith(".jpg") || fileName.toLowerCase().endsWith(".jpeg")) {
                mimeType = "image/jpeg";
            } else if (fileName.toLowerCase().endsWith(".png")) {
                mimeType = "image/png";
            } else if (fileName.toLowerCase().endsWith(".gif")) {
                mimeType = "image/gif";
            } else if (fileName.toLowerCase().endsWith(".bmp")) {
                mimeType = "image/bmp";
            } else if (fileName.toLowerCase().endsWith(".webp")) {
                mimeType = "image/webp";
            }
        } else {
            // .bin uzantısı var, doğru uzantıyla değiştir
            fileName = removeExtension(fileName);
            if (mimeType.equals("image/jpeg") || mimeType.equals("image/jpg")) {
                fileName += ".jpg";
                mimeType = "image/jpeg";
            } else if (mimeType.equals("image/png")) {
                fileName += ".png";
            } else {
                // Varsayılan olarak jpg kullan
                fileName += ".jpg";
                mimeType = "image/jpeg";
            }
            Log.d(TAG, "Using existing extension, MIME type: " + mimeType);
        }

        if (isContentUri) {
            saveContentUriToFile(Uri.parse(imageUrl), fileName, mimeType);
            return;
        }

        String cleanUrl = imageUrl;
        if (cleanUrl.contains(" ")) {
            cleanUrl = cleanUrl.replace(" ", "%20");
        }

        String userAgent = webView != null 
                ? webView.getSettings().getUserAgentString()
                : "Mozilla/5.0";

        android.app.DownloadManager systemDownloadManager =
                (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(Uri.parse(cleanUrl));
        // Ensure consistent MIME type for JPGs
        if (mimeType.equals("image/jpg")) {
            mimeType = "image/jpeg";
        }
        
        // Log final MIME type
        Log.d(TAG, "Final MIME type for download: " + mimeType);
        request.setMimeType(mimeType);
        
        // Handle different Android versions appropriately
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                // For Android 10+, use MediaStore for better visibility
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName);
                // Ensure MIME type is consistent for JPGs
                if (mimeType.equals("image/jpg")) {
                    mimeType = "image/jpeg";
                }
                values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType);
                values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, 
                        Environment.DIRECTORY_PICTURES + "/" + DOWNLOAD_DIRECTORY);
                        
                android.net.Uri uri = context.getContentResolver().insert(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                
                if (uri != null) {
                    Log.d(TAG, "Using MediaStore destination for Android 10+: " + uri);
                    request.setDestinationUri(uri);
                } else {
                    // Fallback to legacy method
                    Log.d(TAG, "MediaStore URI is null, using legacy destination");
                    request.setDestinationInExternalPublicDir(
                            Environment.DIRECTORY_PICTURES,
                            DOWNLOAD_DIRECTORY + File.separator + fileName);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error setting MediaStore destination", e);
                // Fallback to legacy method
                request.setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_PICTURES,
                        DOWNLOAD_DIRECTORY + File.separator + fileName);
            }
        } else {
            // For older Android versions, use the legacy method
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES,
                    DOWNLOAD_DIRECTORY + File.separator + fileName);
        }
        
        // Set user agent if available
        if (userAgent != null && !userAgent.isEmpty()) {
            request.addRequestHeader("User-Agent", userAgent);
        }
        
        // Get cookies if WebView is provided
        if (webView != null) {
            String cookies = CookieManager.getInstance().getCookie(imageUrl);
            if (cookies != null && !cookies.isEmpty()) {
                request.addRequestHeader("Cookie", cookies);
            }
        }
        
        // Set notification visibility
        request.setNotificationVisibility(
                android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        
        final String finalFileName = fileName;
        final String finalMimeType = mimeType;
        
        try {
            // Enqueue the download and get the ID
            final long downloadId = systemDownloadManager.enqueue(request);
            
            // Register BroadcastReceiver to listen for download completion
            downloadManager.registerDownloadCompleteReceiver(
                    context,
                    downloadId,
                    finalFileName,
                    finalMimeType);
                
            // Log the download ID
            Log.d(TAG, "Image download request enqueued with ID: " + downloadId);
            
            // Add to our download manager's active downloads
            this.downloadManager.addActiveDownload(downloadId, fileName);
            
            Log.d(TAG, "Started image download: " + fileName + " (ID: " + downloadId + ")");
            
        } catch (Exception e) {
            Log.e(TAG, "Error starting image download", e);
        }
    }

    /**
     * Saves a content URI to a file.
     */
    private void saveContentUriToFile(Uri contentUri, String fileName, String mimeType) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(contentUri);
            if (inputStream == null) {
                Log.e(TAG, "Failed to open input stream for content URI");
                return;
            }

            File downloadsDir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_DIRECTORY);
            if (!downloadsDir.exists()) {
                if (!downloadsDir.mkdirs()) {
                    Log.e(TAG, "Failed to create downloads directory");
                    inputStream.close();
                    return;
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

            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri fileUri = Uri.fromFile(outputFile);
            mediaScanIntent.setData(fileUri);
            context.sendBroadcast(mediaScanIntent);

        } catch (Exception e) {
            Log.e(TAG, "Error saving content URI to file", e);
        }
    }
}