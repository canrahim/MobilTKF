package com.veritabani.appcompatactivity23.download;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.veritabani.appcompatactivity23.download.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.provider.MediaStore;

import android.view.ViewGroup;
import android.widget.ImageView;
import com.bumptech.glide.Glide;

/**
 * Manages download operations for the browser.
 */
public class DownloadManager {
    private static final String TAG = "DownloadManager";
    private static final String DOWNLOAD_DIRECTORY = "Downloads";
    private static final long DOWNLOAD_NOTIFICATION_THRESHOLD = 2 * 1024 * 1024; // 2MB

    private static DownloadManager instance;
    private final Context applicationContext;
    private Context currentActivityContext;
    private final Map<Long, String> activeDownloads = new HashMap<>();
    private BroadcastReceiver downloadReceiver;
    private final Executor executor = Executors.newSingleThreadExecutor();
    private final ThreadLocal<Context> contextRef = new ThreadLocal<>();

    public static synchronized DownloadManager getInstance(Context context) {
        if (instance == null) {
            instance = new DownloadManager(context);
        }
        return instance;
    }

    private DownloadManager(Context context) {
        this.applicationContext = context.getApplicationContext();
        this.currentActivityContext = context; // Store the initial context (might be an Activity)
        contextRef.set(this.applicationContext);
        registerDownloadReceiver();
    }
    
    /**
     * Updates the current activity context.
     * Should be called in Activity.onResume() to ensure we always have a valid Activity context.
     *
     * @param context The new context (should be an Activity)
     */
    public void updateContext(Context context) {
        if (context != null) {
            this.currentActivityContext = context;
            Log.d(TAG, "Context updated to: " + context.getClass().getSimpleName());
        }
    }
    
    /**
     * Adds a download to the active downloads map.
     *
     * @param downloadId The download ID
     * @param fileName The file name
     */
    public void addActiveDownload(long downloadId, String fileName) {
        activeDownloads.put(downloadId, fileName);
    }

    private void registerDownloadReceiver() {
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    long downloadId = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                    if (downloadId != -1) {
                        handleDownloadCompleted(downloadId);
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        applicationContext.registerReceiver(downloadReceiver, filter);
    }

    public void unregisterDownloadReceiver() {
        if (downloadReceiver != null) {
            try {
                applicationContext.unregisterReceiver(downloadReceiver);
                downloadReceiver = null;
            } catch (Exception e) {
                Log.e(TAG, "Error unregistering download receiver", e);
            }
        }
    }

    private void handleDownloadCompleted(long downloadId) {
        String fileName = activeDownloads.get(downloadId);
        activeDownloads.remove(downloadId);

        android.app.DownloadManager downloadManager = (android.app.DownloadManager) applicationContext.getSystemService(Context.DOWNLOAD_SERVICE);
        try {
            android.app.DownloadManager.Query query = new android.app.DownloadManager.Query();
            query.setFilterById(downloadId);
            Cursor cursor = downloadManager.query(query);

            if (cursor.moveToFirst()) {
                int statusIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS);
                int status = cursor.getInt(statusIndex);

                if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                    int uriIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_LOCAL_URI);
                    String uriString = cursor.getString(uriIndex);
                    
                    // Get MIME type index
                    int mimeTypeIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_MEDIA_TYPE);
                    String mimeType = null;
                    
                    if (mimeTypeIndex != -1) {
                        mimeType = cursor.getString(mimeTypeIndex);
                    }
                    
                    // If no MIME type from download, try to determine from filename
                    if (mimeType == null || mimeType.isEmpty() || mimeType.equals("application/octet-stream")) {
                        // Check if this is a JPG file first based on the original filename
                        if (fileName != null && (fileName.toLowerCase().endsWith(".jpg") || fileName.toLowerCase().endsWith(".jpeg"))) {
                            mimeType = "image/jpeg";
                            Log.d(TAG, "Setting MIME type to image/jpeg based on filename");
                        } else {
                            mimeType = getMimeTypeFromFileName(fileName);
                        }
                    }
                    
                    // Additional logging
                    Log.d(TAG, "Download completed - URI: " + uriString + ", MIME: " + mimeType);
                    
                    // Ensure we have a URI to work with
                    if (uriString != null) {
                        // Handle file opening on UI thread
                        final String finalMimeType = mimeType;
                        new Handler(Looper.getMainLooper()).post(() -> {
                            offerToOpenFile(uriString, finalMimeType);
                        });
                    } else {
                        Log.e(TAG, "Downloaded file URI is null");
                        showToast(applicationContext.getString(R.string.download_completed, fileName));
                    }
                } else if (status == android.app.DownloadManager.STATUS_FAILED) {
                    int reasonColumnIndex = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_REASON);
                    if (reasonColumnIndex != -1) {
                        int reason = cursor.getInt(reasonColumnIndex);
                        Log.e(TAG, "Download failed with reason: " + reason);
                    }
                    showToast(applicationContext.getString(R.string.download_failed));
                }
            }
            cursor.close();
        } catch (Exception e) {
            Log.e(TAG, "Error handling download completion", e);
            showToast(applicationContext.getString(R.string.download_failed));
        }
    }

    public void downloadFile(String url, String fileName, String mimeType, String userAgent, String contentDisposition) {
        // Log download parameters for debugging
        Log.d(TAG, "Download request - URL: " + url);
        Log.d(TAG, "Original filename: " + fileName);
        Log.d(TAG, "Original MIME type: " + mimeType);
        Log.d(TAG, "Content-Disposition: " + contentDisposition);
        
        // Permission check
        if (!checkStoragePermission()) {
            Log.e(TAG, "Storage permission not granted, can't download");
            showToastOnUiThread("İndirme için depolama izni gerekli");
            return;
        }
        
        // İndirme işlemini arkaplanda yap
        final String finalUrl = url;
        final String finalFileName = fileName;
        final String finalUserAgent = userAgent;
        final String finalContentDisposition = contentDisposition;
        final String finalMimeTypeOuter = mimeType;
        
        executor.execute(() -> {
            try {
                Context context = contextRef.get();
                if (context == null) {
                    context = this.applicationContext;
                    contextRef.set(context);
                }
                
                final Context finalContext = context;
                
                // Define variables for filename processing
                String processedFileName = finalFileName;
                boolean fileNameModified = false;
                
                android.app.DownloadManager downloadManager = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(Uri.parse(finalUrl));
                
                // İndirme performansı ayarları
                request.setAllowedNetworkTypes(
                        android.app.DownloadManager.Request.NETWORK_WIFI | 
                        android.app.DownloadManager.Request.NETWORK_MOBILE);
                request.setAllowedOverMetered(true);  // Metered ağlarda indirmeye izin ver
                request.setAllowedOverRoaming(true);  // Roaming'de indirmeye izin ver
                
                request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                
                // Dosya adını doğru şekilde ayarla
                String localFinalFileName;
                String localMimeType = finalMimeTypeOuter;
                
                if (finalFileName == null || finalFileName.isEmpty()) {
                    // Try to extract from content-disposition first
                    String extractedName = extractFilenameFromContentDisposition(finalContentDisposition);
                    
                    if (extractedName != null && !extractedName.isEmpty()) {
                        localFinalFileName = extractedName;
                        Log.d(TAG, "Using filename from Content-Disposition: " + localFinalFileName);
                    } else {
                        // Use URLUtil as fallback
                        localFinalFileName = URLUtil.guessFileName(finalUrl, finalContentDisposition, localMimeType);
                        Log.d(TAG, "Using URLUtil guessed filename: " + localFinalFileName);
                    }
                } else {
                    localFinalFileName = finalFileName;
                    Log.d(TAG, "Using provided filename: " + localFinalFileName);
                }
                
                // Handle MIME types correctly
                if (localMimeType != null) {
                    if (localMimeType.equals("application/pdf") &&
                            !localFinalFileName.toLowerCase().endsWith(".pdf")) {
                        localFinalFileName = localFinalFileName + ".pdf";
                        Log.d(TAG, "Added .pdf extension to filename: " + localFinalFileName);
                    } else if ((localMimeType.equals("image/jpeg") || localMimeType.equals("image/jpg")) &&
                            !(localFinalFileName.toLowerCase().endsWith(".jpg") || localFinalFileName.toLowerCase().endsWith(".jpeg"))) {
                        localFinalFileName = localFinalFileName + ".jpg";
                        Log.d(TAG, "Added .jpg extension to filename: " + localFinalFileName);
                        
                        // Normalize MIME type to image/jpeg
                        localMimeType = "image/jpeg";
                    } else if (localMimeType.equals("image/png") &&
                            !localFinalFileName.toLowerCase().endsWith(".png")) {
                        localFinalFileName = localFinalFileName + ".png";
                        Log.d(TAG, "Added .png extension to filename: " + localFinalFileName);
                    }
                }
                
                // Try to improve the MIME type if it's not specific enough
                if (localMimeType == null || localMimeType.isEmpty() || localMimeType.equals("application/octet-stream")) {
                    String betterMimeType = determineBetterMimeType(finalUrl, localFinalFileName);
                    
                    if (betterMimeType != null && !betterMimeType.isEmpty()) {
                        localMimeType = betterMimeType;
                        
                        Log.d(TAG, "Improved MIME type to: " + localMimeType);
                    }
                }
                
                localFinalFileName = ensureCorrectFileExtension(localFinalFileName, localMimeType);
                
                // Force specific MIME types for common file extensions
                if (localFinalFileName.toLowerCase().endsWith(".pdf")) {
                    localMimeType = "application/pdf";
                } else if (localFinalFileName.toLowerCase().endsWith(".jpg") || localFinalFileName.toLowerCase().endsWith(".jpeg")) {
                    localMimeType = "image/jpeg";
                } else if (localFinalFileName.toLowerCase().endsWith(".png")) {
                    localMimeType = "image/png";
                }
                
                Log.d(TAG, "Final filename for download: " + localFinalFileName);
                Log.d(TAG, "Final MIME type for download: " + localMimeType);
                
                // Set filename for download
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, localFinalFileName);
                } else {
                    request.setDestinationInExternalPublicDir(DOWNLOAD_DIRECTORY, localFinalFileName);
                }
                
                // Set MIME type if available
                if (localMimeType != null && !localMimeType.isEmpty()) {
                    // Convert "image/jpg" to "image/jpeg" for consistency
                    if (localMimeType.equals("image/jpg")) {
                        localMimeType = "image/jpeg";
                    }
                    
                    request.setMimeType(localMimeType);
                    Log.d(TAG, "Set download MIME type: " + localMimeType);
                }
                
                // Set user agent if available
                if (finalUserAgent != null && !finalUserAgent.isEmpty()) {
                    request.addRequestHeader("User-Agent", finalUserAgent);
                    Log.d(TAG, "Set User-Agent: " + finalUserAgent);
                }
                
                // Get cookies for the URL
                String cookies = CookieManager.getInstance().getCookie(finalUrl);
                if (cookies != null && !cookies.isEmpty()) {
                    request.addRequestHeader("Cookie", cookies);
                    Log.d(TAG, "Added cookies to request");
                }
                
                // If available, use provided Accept header
                if (localMimeType != null && (localMimeType.startsWith("image/"))) {
                    request.addRequestHeader("Accept", "image/*");
                } else if (localMimeType != null && !localMimeType.isEmpty()) {
                    request.addRequestHeader("Accept", localMimeType);
                } else {
                    request.addRequestHeader("Accept", "*/*");
                }
                
                // Start download
                long downloadId = downloadManager.enqueue(request);
                Log.d(TAG, "Download enqueued with ID: " + downloadId);
                
                // Keep track of active downloads
                addActiveDownload(downloadId, localFinalFileName);
                
                final String finalLocalMimeType = localMimeType;
                final String finalLocalFileName = localFinalFileName;
                
                // Show toast on UI thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    showToast(finalContext.getString(R.string.download_started, finalLocalFileName));
                });
                
                // Notify for media files
                if (finalLocalMimeType != null && finalLocalMimeType.startsWith("image/")) {
                    registerDownloadCompleteReceiver(
                            finalContext,
                            downloadId,
                            finalLocalFileName, finalLocalMimeType);
                } else if (finalLocalMimeType != null && finalLocalMimeType.startsWith("video/")) {
                    registerDownloadCompleteReceiver(
                            finalContext,
                            downloadId,
                            finalLocalFileName, finalLocalMimeType);
                } else if (finalLocalMimeType != null && finalLocalMimeType.startsWith("audio/")) {
                    registerDownloadCompleteReceiver(
                            finalContext,
                            downloadId,
                            finalLocalFileName, finalLocalMimeType);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error starting download", e);
                
                // Show error on UI thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    showToast("İndirme başlatılamadı: " + e.getMessage());
                });
            }
        });
    }

    /**
     * Content-Disposition'dan dosya adı çıkarır
     */
    public String extractFilenameFromContentDisposition(String contentDisposition) {
        if (contentDisposition == null || contentDisposition.isEmpty()) {
            return null;
        }

        try {
            Pattern filenamePattern = Pattern.compile(
                    "filename\\*?=['\"]?(?:UTF-\\d['\"]*)?([^;\\r\\n\"']*)['\"]?;?",
                    Pattern.CASE_INSENSITIVE);
            Matcher matcher = filenamePattern.matcher(contentDisposition);

            if (matcher.find()) {
                String fileName = matcher.group(1);

                fileName = fileName.replaceAll("%20", " ")
                        .replaceAll("%[0-9a-fA-F]{2}", "")
                        .trim();

                if (fileName.startsWith("\"") && fileName.endsWith("\"")) {
                    fileName = fileName.substring(1, fileName.length() - 1);
                }

                fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
                return fileName;
            }

            // Yedek desen
            Pattern fallbackPattern = Pattern.compile(
                    "filename=['\"]?([^;\\r\\n\"']*)['\"]?",
                    Pattern.CASE_INSENSITIVE);
            Matcher fallbackMatcher = fallbackPattern.matcher(contentDisposition);

            if (fallbackMatcher.find()) {
                String fileName = fallbackMatcher.group(1).trim();
                fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
                return fileName;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing content disposition: " + contentDisposition, e);
        }

        return null;
    }

    /**
     * URL'den dosya adı çıkarır
     */
    public String extractFilenameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "download";
        }

        try {
            String cleanUrl = url;
            int queryIndex = url.indexOf('?');
            if (queryIndex > 0) {
                cleanUrl = url.substring(0, queryIndex);
            }

            String[] segments = cleanUrl.split("/");
            if (segments.length > 0) {
                String lastSegment = segments[segments.length - 1];
                if (!lastSegment.isEmpty()) {
                    return lastSegment.replaceAll("[\\\\/:*?\"<>|]", "_");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting filename from URL: " + url, e);
        }

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return "download_" + timeStamp;
    }

    /**
     * MIME tipi belirler
     */
    public String determineMimeType(String url, String providedMimeType, String fileName) {
        if (providedMimeType != null && !providedMimeType.isEmpty() &&
                !providedMimeType.equals("application/octet-stream") &&
                !providedMimeType.equals("application/force-download")) {
            return providedMimeType;
        }

        String extension = getFileExtension(fileName);
        if (!extension.isEmpty()) {
            String mimeFromExt =
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
            if (mimeFromExt != null) {
                return mimeFromExt;
            }
        }

        String urlExtension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (urlExtension != null && !urlExtension.isEmpty()) {
            String mimeFromUrl =
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(urlExtension);
            if (mimeFromUrl != null) {
                return mimeFromUrl;
            }
        }

        if (url.toLowerCase().contains(".pdf") ||
                (fileName != null && fileName.toLowerCase().endsWith(".pdf"))) {
            return "application/pdf";
        } else if (url.toLowerCase().contains(".doc") ||
                (fileName != null && fileName.toLowerCase().endsWith(".doc"))) {
            return "application/msword";
        } else if (url.toLowerCase().contains(".docx") ||
                (fileName != null && fileName.toLowerCase().endsWith(".docx"))) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (url.toLowerCase().contains(".xls") ||
                (fileName != null && fileName.toLowerCase().endsWith(".xls"))) {
            return "application/vnd.ms-excel";
        } else if (url.toLowerCase().contains(".xlsx") ||
                (fileName != null && fileName.toLowerCase().endsWith(".xlsx"))) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (url.toLowerCase().contains(".zip") ||
                (fileName != null && fileName.toLowerCase().endsWith(".zip"))) {
            return "application/zip";
        }

        return "application/octet-stream";
    }
    
    /**
     * Dosya uzantısını alır
     */
    public String getFileExtension(String fileName) {
        if (fileName == null || fileName.isEmpty() || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
    }
    
    /**
     * Removes the file extension from a filename
     * @param fileName Filename with or without extension
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
     * Try to determine a better MIME type from URL or filename for binary files
     */
    private String determineBetterMimeType(String url, String fileName) {
        // If the file has a specific extension, trust that over the content type
        if (fileName != null) {
            String lowerFileName = fileName.toLowerCase();
            
            // Check for specific image extensions
            if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
                Log.d(TAG, "Using image/jpeg MIME type based on .jpg extension in filename");
                return "image/jpeg";
            } else if (lowerFileName.endsWith(".png")) {
                return "image/png";
            } else if (lowerFileName.endsWith(".gif")) {
                return "image/gif";
            } else if (lowerFileName.endsWith(".pdf")) {
                return "application/pdf";
            }
        }
        
        // Check URL for clues
        String lowerUrl = url != null ? url.toLowerCase() : "";
        
        // PDF file indicators
        if (lowerUrl.contains(".pdf") || lowerUrl.contains("pdf=true") || 
            lowerUrl.contains("format=pdf")) {
            return "application/pdf";
        } 
        // Image file indicators
        else if (lowerUrl.contains(".jpg") || lowerUrl.contains(".jpeg") ||
                 lowerUrl.contains("format=jpg") || lowerUrl.contains("format=jpeg")) {
            return "image/jpeg";
        } 
        else if (lowerUrl.contains(".png") || lowerUrl.contains("format=png")) {
            return "image/png";
        } 
        else if (lowerUrl.contains(".gif") || lowerUrl.contains("format=gif")) {
            return "image/gif";
        }
        // Document format indicators
        else if (lowerUrl.contains(".doc")) {
            return "application/msword";
        } 
        else if (lowerUrl.contains(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        else if (lowerUrl.contains(".xls")) {
            return "application/vnd.ms-excel";
        }
        else if (lowerUrl.contains(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        
        // Additional check for special URL patterns that often contain PDFs
        if (lowerUrl.contains("/document/") || lowerUrl.contains("/viewdoc") || 
            lowerUrl.contains("/download/") || lowerUrl.contains("/attachments/")) {
            return "application/pdf";
        }
        
        // Default fallback
        return "application/octet-stream";
    }
    
    /**
     * Dosya uzantısını MIME türüne göre düzenler
     */
    public String ensureCorrectFileExtension(String fileName, String mimeType) {
        if (fileName == null || fileName.isEmpty()) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss",
                    Locale.getDefault()).format(new Date());
            fileName = "download_" + timeStamp;
        }

        Map<String, String> mimeToExtMap = new HashMap<>();
        mimeToExtMap.put("application/pdf", ".pdf");
        mimeToExtMap.put("application/msword", ".doc");
        mimeToExtMap.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx");
        mimeToExtMap.put("application/vnd.ms-excel", ".xls");
        mimeToExtMap.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx");
        mimeToExtMap.put("application/vnd.ms-powerpoint", ".ppt");
        mimeToExtMap.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx");
        mimeToExtMap.put("text/plain", ".txt");
        mimeToExtMap.put("text/html", ".html");
        mimeToExtMap.put("image/jpeg", ".jpg");
        mimeToExtMap.put("image/png", ".png");
        mimeToExtMap.put("image/gif", ".gif");
        mimeToExtMap.put("application/zip", ".zip");
        mimeToExtMap.put("application/x-rar-compressed", ".rar");
        mimeToExtMap.put("audio/mpeg", ".mp3");
        mimeToExtMap.put("video/mp4", ".mp4");

        // First, if the file ends with .bin, we should try to determine a better extension
        String tempFileName = fileName;
        if (tempFileName.toLowerCase().endsWith(".bin")) {
            // Remove the .bin extension first
            tempFileName = tempFileName.substring(0, tempFileName.length() - 4);
            Log.d(TAG, "Removed .bin extension: " + tempFileName);
            
            // Check if the original file was supposed to be a JPG from the MIME type
            // This is the main fix for the jpg->bin conversion issue
            if (mimeType != null && (mimeType.equals("image/jpeg") || mimeType.equals("image/jpg"))) {
                tempFileName = tempFileName + ".jpg";
                Log.d(TAG, "Restored JPG extension after .bin removal: " + tempFileName);
                return tempFileName;
            }
        }
        
        String expectedExtension = mimeToExtMap.get(mimeType);

        if (expectedExtension == null && mimeType != null) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (ext != null) {
                expectedExtension = "." + ext;
            }
        }
        
        // For image types, ensure proper extension
        if (mimeType != null && mimeType.startsWith("image/")) {
            if (mimeType.equals("image/jpeg") || mimeType.equals("image/jpg")) {
                expectedExtension = ".jpg";
            } else if (mimeType.equals("image/png")) {
                expectedExtension = ".png";
            } else if (mimeType.equals("image/gif")) {
                expectedExtension = ".gif";
            }
        }

        if (expectedExtension != null) {
            // Check if file already has the correct extension
            if (!tempFileName.toLowerCase().endsWith(expectedExtension)) {
                // Remove existing extension if any
                int lastDotIndex = tempFileName.lastIndexOf(".");
                if (lastDotIndex > 0) {
                    tempFileName = tempFileName.substring(0, lastDotIndex);
                }
                
                // Add the proper extension
                tempFileName = tempFileName + expectedExtension;
                Log.d(TAG, "Applied proper extension: " + tempFileName);
            }
        }

        return tempFileName;
    }
    
    /**
     * İndirme onay dialogu - güvenli context işleme ile
     */
    public void showDownloadConfirmationDialog(final String url,
                                                final String fileName,
                                                final String mimeType,
                                                final String userAgent,
                                                final String contentDisposition,
                                                final String sizeInfo,
                                                final boolean isImage) {
        // Get a valid activity context
        Context dialogContext = getValidActivityContext();
        if (dialogContext == null) {
            // If no valid activity context, download directly without showing dialog
            Log.d(TAG, "No valid activity context, downloading directly: " + fileName);
            downloadFile(url, fileName, mimeType, userAgent, contentDisposition);
            return;
        }
        
        // UI thread üzerinde dialog göster
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> {
            try {
                String title = isImage 
                        ? dialogContext.getString(R.string.download_image) 
                        : dialogContext.getString(R.string.download_file);
                
                String message = (sizeInfo != null) 
                        ? fileName + " (" + sizeInfo + ")" 
                        : fileName;
                
                // Use standard AlertDialog since we have a valid Activity context
                AlertDialog.Builder builder = new AlertDialog.Builder(dialogContext);
                builder.setTitle(title)
                       .setMessage(message)
                       .setPositiveButton(dialogContext.getString(R.string.download_file), (dialog, which) -> {
                           if (isImage) {
                               ImageDownloader imageDownloader = new ImageDownloader(dialogContext);
                               imageDownloader.downloadImage(url, null);
                           } else {
                               downloadFile(url, fileName, mimeType, userAgent, contentDisposition);
                           }
                       })
                       .setNegativeButton(dialogContext.getString(R.string.download_cancel), (dialog, which) -> {
                           dialog.dismiss();
                       });
                
                // Görselse resmi göster
                if (isImage) {
                    try {
                        ImageView imageView = new ImageView(dialogContext);
                        imageView.setLayoutParams(new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        ));
                        imageView.setAdjustViewBounds(true);
                        
                        // Glide ile önizleme göster
                        Glide.with(dialogContext)
                             .load(url)
                             .centerCrop()
                             .into(imageView);
                        
                        builder.setView(imageView);
                    } catch (Exception e) {
                        Log.e(TAG, "Error setting image view", e);
                    }
                }
                
                Dialog dialog = builder.create();
                dialog.show();
                
            } catch (Exception e) {
                Log.e(TAG, "Error showing download dialog: " + e.getMessage());
                // If dialog fails, just download directly
                downloadFile(url, fileName, mimeType, userAgent, contentDisposition);
            }
        });
    }
    
    /**
     * Get a valid activity context for showing dialogs
     */
    private Context getValidActivityContext() {
        // Check if the currentActivityContext is valid
        if (currentActivityContext instanceof Activity) {
            Activity activity = (Activity) currentActivityContext;
            if (!activity.isFinishing() && !activity.isDestroyed()) {
                return currentActivityContext;
            }
        }
        
        // No valid activity context available
        return null;
    }
    
    /**
     * Özel HTTP bağlantısı ile dosya indirme
     */
    public void startCustomDownload(final String url, final String fileName) {
        // Check permissions first
        if (!checkStoragePermission()) {
            Log.e(TAG, "Storage permission not granted, can't download");
            showToastOnUiThread("İndirme için depolama izni gerekli");
            return;
        }
        
        final String userAgent = "Mozilla/5.0 (Linux; Android 10; Mobile)";
        final String referer = "";

        new Thread(() -> {
            HttpURLConnection connection = null;
            InputStream input = null;
            FileOutputStream output = null;
            
            // Define variables for filename processing
            String processedFileName = fileName;
            boolean fileNameModified = false;

            try {
                URL urlObj = new URL(url);
                connection = (HttpURLConnection) urlObj.openConnection();

                String cookies = CookieManager.getInstance().getCookie(url);
                if (cookies != null) {
                    connection.setRequestProperty("Cookie", cookies);
                }

                connection.setRequestProperty("User-Agent", userAgent);
                if (!referer.isEmpty()) {
                    connection.setRequestProperty("Referer", referer);
                }

                connection.setRequestProperty("Accept", "image/*, */*");
                connection.setInstanceFollowRedirects(true);
                connection.connect();
                Log.d(TAG, "HTTP Response code: " + connection.getResponseCode());

                Map<String, List<String>> headers = connection.getHeaderFields();
                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                    if (entry.getKey() != null) {
                        Log.d(TAG, "Header: " + entry.getKey() + " = " + entry.getValue());
                    }
                }

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    showToastOnUiThread("İndirme hatası: " + responseCode);
                    return;
                }

                String contentType = connection.getContentType();
                Log.d(TAG, "Content Type: " + contentType);

                int contentLength = connection.getContentLength();
                Log.d(TAG, "Content Length: " + contentLength);

                String contentDisposition = connection.getHeaderField("Content-Disposition");
                String fileNameFromHeader = null;
                if (contentDisposition != null) {
                    Log.d(TAG, "Content-Disposition: " + contentDisposition);
                    int fileNameIndex = contentDisposition.indexOf("filename");
                    if (fileNameIndex >= 0) {
                        int equalsIndex = contentDisposition.indexOf("=", fileNameIndex);
                        if (equalsIndex > 0) {
                            fileNameFromHeader =
                                    contentDisposition.substring(equalsIndex + 1).trim();
                            fileNameFromHeader =
                                    fileNameFromHeader.replaceAll("^\"|\"$|^\\s+|\\s+$|;$", "");
                            Log.d(TAG, "File name from Content-Disposition: " + fileNameFromHeader);
                        }
                    }
                }

                // Determine proper filename with correct extension
                final String finalFileName;
                
                // First try to get filename from header
                if (fileNameFromHeader != null && !fileNameFromHeader.isEmpty()) {
                    Log.d(TAG, "Using filename from Content-Disposition: " + fileNameFromHeader);
                    
                    // Special handling for JPG files in Content-Disposition
                    if (contentDisposition.toLowerCase().contains(".jpg") || contentDisposition.toLowerCase().contains(".jpeg")) {
                        String baseName = fileNameFromHeader;
                        int dotIndex = baseName.lastIndexOf(".");
                        if (dotIndex > 0) {
                            baseName = baseName.substring(0, dotIndex);
                        }
                        finalFileName = baseName + ".jpg";
                        Log.d(TAG, "Fixed JPG filename from Content-Disposition: " + finalFileName);
                    } else {
                        finalFileName = ensureProperFileExtension(fileNameFromHeader, contentType, url);
                    }
                }
                // For binary content, use provided filename with extension correction
                else if (contentType != null && contentType.contains("application/octet-stream")) {
                    // Try to determine a better MIME type based on URL and filename
                    String betterMimeType = determineBetterMimeType(url, fileName);
                    String finalMimeType = (betterMimeType != null && !betterMimeType.equals("application/octet-stream")) 
                                          ? betterMimeType : contentType;
                    
                    Log.d(TAG, "For octet-stream: detected better MIME type: " + finalMimeType);
                    finalFileName = ensureProperFileExtension(fileName, finalMimeType, url);
                }
                // Special handling for PDFs (often misidentified)
                else if ((contentType != null && contentType.contains("application/pdf")) || 
                         url.toLowerCase().contains(".pdf")) {
                    finalFileName = removeExtension(fileName) + ".pdf";
                    Log.d(TAG, "Using PDF filename: " + finalFileName);
                } 
                // For images, ensure proper extension
                else if (contentType != null && contentType.contains("image/")) {
                    if (contentType.contains("jpeg") || contentType.contains("jpg")) {
                        finalFileName = removeExtension(fileName) + ".jpg";
                        Log.d(TAG, "Using JPG filename: " + finalFileName);
                    } else if (contentType.contains("png")) {
                        finalFileName = removeExtension(fileName) + ".png";
                        Log.d(TAG, "Using PNG filename: " + finalFileName);
                    } else if (contentType.contains("gif")) {
                        finalFileName = removeExtension(fileName) + ".gif";
                        Log.d(TAG, "Using GIF filename: " + finalFileName);
                    } else {
                        // Other image types
                        String extension = MimeTypeMap.getSingleton()
                                .getExtensionFromMimeType(contentType);
                        if (extension != null) {
                            finalFileName = removeExtension(fileName) + "." + extension;
                        } else {
                            finalFileName = fileName;
                        }
                    }
                } 
                // For other content types, try to get extension from MIME type
                else if (contentType != null) {
                    String extension = MimeTypeMap.getSingleton()
                            .getExtensionFromMimeType(contentType);
                    if (extension != null) {
                        finalFileName = removeExtension(fileName) + "." + extension;
                    } else {
                        finalFileName = fileName;
                    }
                } 
                // If all else fails, use the provided filename
                else {
                    finalFileName = fileName;
                }

                // If the determined MIME type is for an image file but the extension doesn't match, fix it
                String effectiveFileName = finalFileName;
                String mimeType = getMimeTypeFromFileName(effectiveFileName);
                boolean isFileNameModified = false;
                
                if (mimeType != null && mimeType.startsWith("image/") && 
                    !(effectiveFileName.toLowerCase().endsWith(".jpg") || 
                      effectiveFileName.toLowerCase().endsWith(".jpeg") || 
                      effectiveFileName.toLowerCase().endsWith(".png") || 
                      effectiveFileName.toLowerCase().endsWith(".gif"))) {
                    
                    if (mimeType.equals("image/jpeg")) {
                        effectiveFileName = removeExtension(effectiveFileName) + ".jpg";
                        isFileNameModified = true;
                    } else if (mimeType.equals("image/png")) {
                        effectiveFileName = removeExtension(effectiveFileName) + ".png";
                        isFileNameModified = true;
                    } else if (mimeType.equals("image/gif")) {
                        effectiveFileName = removeExtension(effectiveFileName) + ".gif";
                        isFileNameModified = true;
                    }
                    
                    if (isFileNameModified) {
                        Log.d(TAG, "Fixed image filename extension: " + effectiveFileName);
                    }
                }
                
                Log.d(TAG, "Final file name with extension: " + finalFileName);

                // Handle different storage access methods based on Android version
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    // For Android 10 and above, use MediaStore
                    String finalEffectiveFileName = effectiveFileName;
                    try {
                        android.content.ContentValues values = new android.content.ContentValues();
                        values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, finalEffectiveFileName);
                        
                        // Get precise mime type from filename
                        String usedMimeType = getMimeTypeFromFileName(finalEffectiveFileName);
                        Log.d(TAG, "MIME type for MediaStore: " + usedMimeType);
                        values.put(android.provider.MediaStore.Downloads.MIME_TYPE, usedMimeType);
                        
                        // Choose the right collection based on mime type
                        Uri collectionUri;
                        if (usedMimeType != null && usedMimeType.startsWith("image/")) {
                            Log.d(TAG, "Using MediaStore Images collection");
                            values.put(MediaStore.Images.Media.RELATIVE_PATH, 
                                    Environment.DIRECTORY_PICTURES + "/" + DOWNLOAD_DIRECTORY);
                            collectionUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                        } else {
                            Log.d(TAG, "Using MediaStore Downloads collection");
                            values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, 
                                    Environment.DIRECTORY_DOWNLOADS + "/" + DOWNLOAD_DIRECTORY);
                            collectionUri = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                        }
                        
                        Uri uri = applicationContext.getContentResolver().insert(
                                collectionUri, values);
                        
                        if (uri != null) {
                            Log.d(TAG, "MediaStore URI created: " + uri);
                            OutputStream mediaStoreOutput = applicationContext.getContentResolver().openOutputStream(uri);
                            
                            if (mediaStoreOutput != null) {
                                byte[] buffer = new byte[8192];
                                int bytesRead;
                                long totalBytesRead = 0;
    
                                while ((bytesRead = input.read(buffer)) != -1) {
                                    mediaStoreOutput.write(buffer, 0, bytesRead);
                                    totalBytesRead += bytesRead;
                                }
                                
                                // Ensure output is properly flushed and closed
                                mediaStoreOutput.flush();
                                mediaStoreOutput.close();
                                
                                Log.d(TAG, "Download completed using MediaStore. Total bytes: " + totalBytesRead);
                                
                                // Offer to open the file
                                Handler mainHandler = new Handler(Looper.getMainLooper());
                                final Uri finalUri = uri;
                                final String finalMimeType = mimeType;
                                mainHandler.post(() -> {
                                    offerToOpenFile(finalUri.toString(), finalMimeType);
                                });
                                
                                return;
                            } else {
                                Log.e(TAG, "Failed to open MediaStore output stream");
                            }
                        } else {
                            Log.e(TAG, "Failed to create MediaStore URI");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error using MediaStore for download: " + e.getMessage(), e);
                        // Fall back to legacy method if MediaStore fails
                    }
                }
                
                // Legacy method for older Android versions or as fallback
                File downloadsDir;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    // For Android 10+ use app-specific directory as fallback
                    downloadsDir = new File(applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), 
                            DOWNLOAD_DIRECTORY);
                } else {
                    // For older versions, use public directory
                    downloadsDir = new File(Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_DIRECTORY);
                }
                
                if (!downloadsDir.exists()) {
                    boolean dirCreated = downloadsDir.mkdirs();
                    if (!dirCreated) {
                        Log.e(TAG, "Failed to create download directory: " + downloadsDir.getAbsolutePath());
                    }
                }

                // Use the potentially modified filename
                File outputFile = new File(downloadsDir, effectiveFileName);
                Log.d(TAG, "Output file path: " + outputFile.getAbsolutePath());

                input = connection.getInputStream();
                output = new FileOutputStream(outputFile);

                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalBytesRead = 0;

                while ((bytesRead = input.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                }

                Log.d(TAG, "Download completed. Total bytes: " + totalBytesRead);

                // Process the downloaded file
                // Check for the right MIME type and file extension based on content type
                String finalMimeType;
                
                // Use a conditional check for JPEG files instead of redeclaring variables
                if (finalFileName.toLowerCase().endsWith(".jpg") ||
                    finalFileName.toLowerCase().endsWith(".jpeg")) {
                    finalMimeType = "image/jpeg";
                    // Fix bin extension if needed
                    if (effectiveFileName.toLowerCase().endsWith(".bin")) {
                        effectiveFileName = removeExtension(effectiveFileName) + ".jpg";
                        isFileNameModified = true;
                        Log.d(TAG, "Fixed .bin extension for JPEG file: " + effectiveFileName);
                    }
                } else if (finalFileName.toLowerCase().endsWith(".png")) {
                    finalMimeType = "image/png";
                } else if (finalFileName.toLowerCase().endsWith(".gif")) {
                    finalMimeType = "image/gif";
                } else if (finalFileName.toLowerCase().endsWith(".pdf")) {
                    finalMimeType = "application/pdf";
                } else if (contentType != null &&
                    !contentType.equals("application/octet-stream")) {
                    finalMimeType = contentType;
                
                    // If content type is image/jpeg but filename doesn't end with .jpg or .jpeg
                    if (contentType.equals("image/jpeg") &&
                        !(effectiveFileName.toLowerCase().endsWith(".jpg") || 
                          effectiveFileName.toLowerCase().endsWith(".jpeg"))) {
                        effectiveFileName = removeExtension(effectiveFileName) + ".jpg";
                        isFileNameModified = true;
                        Log.d(TAG, "Fixed extension for JPEG content type: " + effectiveFileName);
                    }
                } else {
                    finalMimeType = getMimeTypeFromFileName(effectiveFileName);
                }

                // Now prepare the actual file for output, using our processed name if it was modified
                final File finalOutputFile;
                if (isFileNameModified) {
                    finalOutputFile = new File(downloadsDir, effectiveFileName);
                    Log.d(TAG, "Using modified filename: " + effectiveFileName);
                } else {
                    finalOutputFile = new File(downloadsDir, finalFileName);
                    Log.d(TAG, "Using original filename: " + finalFileName);
                }

                notifyMediaScanner(effectiveFileName, finalOutputFile, finalMimeType);

                showToastOnUiThread(effectiveFileName + " başarıyla indirildi");
                showSuccessNotification(effectiveFileName, finalOutputFile);

            } catch (Exception e) {
                Log.e(TAG, "Download error: " + e.getMessage(), e);
                showToastOnUiThread("İndirme hatası: " + e.getMessage());
            } finally {
                try {
                    if (output != null) output.close();
                    if (input != null) input.close();
                    if (connection != null) connection.disconnect();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing resources", e);
                }
            }
        }).start();
    }
    
    /**
     * MediaScanner'a yeni dosyayı bildirir
     */
    public void notifyMediaScanner(String fileName, File file, String mimeType) {
        try {
            android.media.MediaScannerConnection.scanFile(applicationContext,
                    new String[]{file.getAbsolutePath()},
                    new String[]{mimeType},
                    (path, uri) -> Log.d(TAG, "Media scan completed: " + uri));
        } catch (Exception e) {
            Log.e(TAG, "Error notifying media scanner", e);
        }
    }
    
    /**
     * İndirme tamamlandığında bildirim göstermek için alıcı kaydeder
     * ve dosyayı açma seçeneği sunar
     */
    public void registerDownloadCompleteReceiver(Context context, long downloadId, String fileName, String mimeType) {
        BroadcastReceiver downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long receivedDownloadId = intent.getLongExtra(android.app.DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadId == receivedDownloadId) {
                    try {
                        // Get the downloaded file's URI
                        android.app.DownloadManager dm = (android.app.DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
                        Uri fileUri = dm.getUriForDownloadedFile(downloadId);
                        
                        if (fileUri != null) {
                            // Log the successful download
                            Log.d(TAG, "Download completed, URI: " + fileUri + ", MIME: " + mimeType);
                            
                            // Offer to open the file on the UI thread
                            new Handler(Looper.getMainLooper()).post(() -> {
                                offerToOpenFile(fileUri.toString(), mimeType);
                            });
                        } else {
                            // If URI is null, just show a toast
                            Toast.makeText(context, 
                                context.getString(R.string.download_completed, fileName), 
                                Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error handling download completion", e);
                        // Show a simple toast on error
                        Toast.makeText(context, 
                            context.getString(R.string.download_completed, fileName), 
                            Toast.LENGTH_SHORT).show();
                    } finally {
                        // Always unregister the receiver
                        try {
                            context.unregisterReceiver(this);
                        } catch (Exception e) {
                            Log.e(TAG, "Error unregistering receiver", e);
                        }
                    }
                }
            }
        };
        
        // İndirme tamamlandığında bildirimi almak için filtre
        IntentFilter filter = new IntentFilter(android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        context.registerReceiver(downloadReceiver, filter);
    }

    /**
     * Başarılı indirme bildirimi gösterir
     */
    private void showSuccessNotification(String fileName, File file) {
        try {
            android.app.NotificationManager notificationManager =
                    (android.app.NotificationManager)
                            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                android.app.NotificationChannel channel =
                        new android.app.NotificationChannel(
                                "download_channel",
                                "İndirme Bildirimleri",
                                android.app.NotificationManager.IMPORTANCE_DEFAULT);
                notificationManager.createNotificationChannel(channel);
            }

            Intent openFileIntent = new Intent(Intent.ACTION_VIEW);
            Uri fileUri;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                fileUri = FileProvider.getUriForFile(
                        applicationContext,
                        applicationContext.getPackageName() + ".fileprovider",
                        file
                );
                openFileIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                fileUri = Uri.fromFile(file);
            }

            String mimeType = getMimeTypeFromFileName(fileName);
            openFileIntent.setDataAndType(fileUri, mimeType);

            android.app.PendingIntent pendingIntent;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                pendingIntent = android.app.PendingIntent.getActivity(applicationContext,
                        0,
                        openFileIntent,
                        android.app.PendingIntent.FLAG_IMMUTABLE);
            } else {
                pendingIntent = android.app.PendingIntent.getActivity(applicationContext,
                        0,
                        openFileIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            }

            NotificationCompat.Builder builder =
                    new NotificationCompat.Builder(applicationContext, "download_channel")
                            .setSmallIcon(android.R.drawable.stat_sys_download_done)
                            .setContentTitle("İndirme Tamamlandı")
                            .setContentText(fileName)
                            .setAutoCancel(true)
                            .setContentIntent(pendingIntent);

            notificationManager.notify((int) System.currentTimeMillis(), builder.build());

        } catch (Exception e) {
            Log.e(TAG, "Error showing notification", e);
        }
    }
    
    /**
     * Ensures that a filename has the proper extension based on content type and URL
     * 
     * @param fileName The original filename
     * @param contentType The content type from the HTTP response
     * @param url The URL being downloaded
     * @return A filename with the proper extension
     */
    private String ensureProperFileExtension(String fileName, String contentType, String url) {
        if (fileName == null || fileName.isEmpty()) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            fileName = "download_" + timeStamp;
        }
        
        // Helper to remove existing extension
        String baseName = fileName;
        int dotIndex = fileName.lastIndexOf(".");
        if (dotIndex > 0) {
            baseName = fileName.substring(0, dotIndex);
        }
        
        // Handle .bin extensions - always replace them
        if (fileName.toLowerCase().endsWith(".bin")) {
            fileName = baseName; // remove .bin extension
            Log.d(TAG, "Removed .bin extension: " + fileName);
        }
        
        // Check for PDF files (often misidentified)
        if ((contentType != null && contentType.contains("application/pdf")) || 
            url.toLowerCase().contains(".pdf")) {
            return baseName + ".pdf";
        }
        
        // Check for binary content but with clues in the URL
        if (contentType != null && contentType.contains("application/octet-stream")) {
            // Look for extension clues in the URL
            if (url.toLowerCase().contains(".jpg") || url.toLowerCase().contains(".jpeg")) {
                return baseName + ".jpg";
            } else if (url.toLowerCase().contains(".png")) {
                return baseName + ".png";
            } else if (url.toLowerCase().contains(".pdf")) {
                return baseName + ".pdf";
            }
        }
        
        // Handle image types specifically
        if (contentType != null) {
            if (contentType.contains("image/jpeg") || contentType.contains("image/jpg")) {
                return baseName + ".jpg";
            } else if (contentType.contains("image/png")) {
                return baseName + ".png";
            } else if (contentType.contains("image/gif")) {
                return baseName + ".gif";
            } else if (contentType.contains("application/pdf")) {
                return baseName + ".pdf";
            } else {
                // Try to get extension from MIME type
                String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(contentType);
                if (extension != null && !extension.isEmpty()) {
                    return baseName + "." + extension;
                }
            }
        }
        
        // If we couldn't determine a better extension, keep the original filename
        // but make sure it's not .bin
        if (fileName.toLowerCase().endsWith(".bin")) {
            return baseName;
        }
        return fileName;
    }

    /**
     * Dosya adından MIME türünü tahmin eder
     */
    private String getMimeTypeFromFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        
        // Normalize to lowercase for comparison
        String lowerFileName = fileName.toLowerCase();
        
        // Image formats
        if (lowerFileName.endsWith(".jpg") || lowerFileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (lowerFileName.endsWith(".png")) {
            return "image/png";
        } else if (lowerFileName.endsWith(".gif")) {
            return "image/gif";
        } else if (lowerFileName.endsWith(".webp")) {
            return "image/webp";
        } else if (lowerFileName.endsWith(".bmp")) {
            return "image/bmp";
        }
        
        // Document formats
        else if (lowerFileName.endsWith(".pdf")) {
            return "application/pdf";
        } else if (lowerFileName.endsWith(".doc")) {
            return "application/msword";
        } else if (lowerFileName.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (lowerFileName.endsWith(".xls")) {
            return "application/vnd.ms-excel";
        } else if (lowerFileName.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (lowerFileName.endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
        } else if (lowerFileName.endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        }
        
        // Text and other common formats
        else if (lowerFileName.endsWith(".txt")) {
            return "text/plain";
        } else if (lowerFileName.endsWith(".html") || lowerFileName.endsWith(".htm")) {
            return "text/html";
        } else if (lowerFileName.endsWith(".css")) {
            return "text/css";
        } else if (lowerFileName.endsWith(".js")) {
            return "application/javascript";
        }
        
        // Archive formats
        else if (lowerFileName.endsWith(".zip")) {
            return "application/zip";
        } else if (lowerFileName.endsWith(".rar")) {
            return "application/x-rar-compressed";
        } else if (lowerFileName.endsWith(".7z")) {
            return "application/x-7z-compressed";
        }
        
        // Try to use the extension lookup as fallback
        else {
            try {
                // First try to get extension directly from the filename
                int lastDot = lowerFileName.lastIndexOf(".");
                if (lastDot > 0) {
                    String extension = lowerFileName.substring(lastDot + 1);
                    String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                    if (mimeType != null && !mimeType.isEmpty()) {
                        Log.d(TAG, "Found MIME type from extension: " + mimeType);
                        return mimeType;
                    }
                }
                
                // Then try using the URL method
                String extension = MimeTypeMap.getFileExtensionFromUrl(fileName);
                if (extension != null) {
                    String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
                    if (mimeType != null) {
                        return mimeType;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error determining MIME type from filename: " + fileName, e);
            }
            
            // Default fallback
            return "application/octet-stream";
        }
    }
    
    /**
     * İndirme tamamlandığında dosyayı açmayı teklif eder
     */
    private void offerToOpenFile(String uriString, String mimeType) {
        if (uriString == null) {
            return;
        }
        
        Log.d(TAG, "Offering to open file - URI: " + uriString);
        Log.d(TAG, "File MIME type: " + mimeType);
        
        try {
            Uri uri = Uri.parse(uriString);
            
            // Get file name from URI
            String fileName = uri.getLastPathSegment();
            if (fileName == null) {
                fileName = "İndirilen dosya";
            }
            
            // Get valid activity context
            Context dialogContext = getValidActivityContext();
            if (dialogContext != null) {
                // Show dialog asking if user wants to open the file
                new AlertDialog.Builder(dialogContext)
                    .setTitle(R.string.download_completed_title)
                    .setMessage(dialogContext.getString(R.string.download_open_prompt, fileName))
                    .setPositiveButton(R.string.download_open, (dialog, which) -> {
                        openFile(uri, mimeType);
                    })
                    .setNegativeButton(R.string.download_cancel, null)
                    .show();
            } else {
                // If no valid context, just show toast
                Toast.makeText(applicationContext, 
                    applicationContext.getString(R.string.download_completed, fileName), 
                    Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error offering to open downloaded file", e);
            // Show toast on error
            Toast.makeText(applicationContext, 
                applicationContext.getString(R.string.download_completed_generic), 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Actually open the file with the proper intent
     */
    private void openFile(Uri uri, String mimeType) {
        Log.d(TAG, "Opening file - URI: " + uri);
        Log.d(TAG, "Opening with MIME type: " + mimeType);
        
        // Fix MIME type if needed
        if (mimeType == null || mimeType.isEmpty() || mimeType.equals("application/octet-stream")) {
            // Try to determine better mime type from URI
            String path = uri.getPath();
            if (path != null) {
                String betterMime = getMimeTypeFromFileName(path);
                if (!betterMime.equals("application/octet-stream")) {
                    mimeType = betterMime;
                    Log.d(TAG, "Improved MIME type for opening: " + mimeType);
                }
            }
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, mimeType);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            if (intent.resolveActivity(applicationContext.getPackageManager()) != null) {
                applicationContext.startActivity(intent);
            } else {
                // No app found to open this file type
                Toast.makeText(applicationContext, 
                    R.string.download_no_app_to_open, 
                    Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening file: " + e.getMessage());
            Toast.makeText(applicationContext, 
                R.string.download_open_error, 
                Toast.LENGTH_SHORT).show();
        }
    }
    
    // Yardımcı metotlar
    private void showToast(String message) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show();
    }
    
    private void showToastOnUiThread(String message) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(() -> Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show());
    }
    
    /**
     * Check storage permissions and request if needed
     * @return true if permissions are already granted
     */
    public boolean checkStoragePermission() {
        if (currentActivityContext == null || !(currentActivityContext instanceof Activity)) {
            Log.e(TAG, "No valid activity context to request permissions");
            return false;
        }
        
        // Android 13+ (API 33+) uses more granular permissions
        if (android.os.Build.VERSION.SDK_INT >= 33) { // Android.os.Build.VERSION_CODES.TIRAMISU
            if (androidx.core.content.ContextCompat.checkSelfPermission(currentActivityContext, 
                    android.Manifest.permission.READ_MEDIA_IMAGES) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return true;
            } else {
                androidx.core.app.ActivityCompat.requestPermissions((Activity) currentActivityContext,
                        new String[]{android.Manifest.permission.READ_MEDIA_IMAGES}, 100);
                return false;
            }
        } 
        // Android 10+ (API 29+) with scoped storage
        else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // With scoped storage, we can use MediaStore or app-specific directories without permission
            return true;
        } 
        // Android 6.0-9.0 (API 23-28) need runtime permissions
        else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(currentActivityContext, 
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == 
                    android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return true;
            } else {
                androidx.core.app.ActivityCompat.requestPermissions((Activity) currentActivityContext,
                        new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 100);
                return false;
            }
        } 
        // Android 5.1 and below (API 22-) - permissions granted at install time
        else {
            return true;
        }
    }

    /**
     * İndirilen dosyaları yönetici uygulamasını açar
     */
    public void showDownloadsManager(Context context) {
        try {
            Intent intent = new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                Uri uri = Uri.parse(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS).toString());
                intent.setDataAndType(uri, "*/*");
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(Intent.createChooser(intent,
                        "İndirilenler Klasörünü Aç"));
            } catch (Exception ex) {
                Toast.makeText(context, "İndirilenler klasörü açılamadı", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error opening downloads folder", ex);
            }
        }
    }

    /**
     * Create MediaStore content URI for inserting a file into shared storage
     * Used for Android 10+ (API 29+)
     * 
     * @param context Context for ContentResolver
     * @param collection The MediaStore collection URI (Images, Video, etc.)
     * @param fileName The desired filename
     * @param mimeType The MIME type of the file
     * @return The content URI or null if creation fails
     */
    private Uri insertMediaFile(Context context, Uri collection, String fileName, String mimeType) {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, mimeType);
            
            // For Android 10+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Store in the Downloads directory
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + 
                        File.separator + DOWNLOAD_DIRECTORY);
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            }
            
            // Insert the URI
            ContentResolver resolver = context.getContentResolver();
            Uri uri = resolver.insert(collection, values);
            
            // Mark as not pending if on Android 10+
            if (uri != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                ContentValues updateValues = new ContentValues();
                updateValues.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(uri, updateValues, null, null);
            }
            
            return uri;
        } catch (Exception e) {
            Log.e(TAG, "Error creating MediaStore file", e);
            return null;
        }
    }
}