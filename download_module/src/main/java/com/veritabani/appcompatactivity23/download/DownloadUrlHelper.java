package com.veritabani.appcompatactivity23.download;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Utility class for identifying and handling special download URLs.
 */
public class DownloadUrlHelper {
    private static final String TAG = "DownloadUrlHelper";
    
    /**
     * Checks if a URL is a download URL.
     *
     * @param url The URL to check
     * @return True if the URL is a download URL, false otherwise
     */
    public static boolean isDownloadUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        // Common download path patterns
        return url.contains("/EXT/PKControl/DownloadFile") ||
               url.contains("/DownloadFile") ||
               url.contains("/download.php") ||
               url.contains("/filedownload") ||
               url.contains("/file_download") ||
               url.contains("/getfile") ||
               url.contains("/get_file") ||
               (url.contains("download") && url.contains("id=")) ||
               Pattern.compile(".*\\.(pdf|doc|docx|xls|xlsx|ppt|pptx|zip|rar|7z|txt|csv)($|\\?.*)").matcher(url).matches();
    }
    
    /**
     * Gets the file name from a URL.
     *
     * @param url The URL to get the file name from
     * @return The file name
     */
    public static String getFileNameFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        // Try to extract file name from URL
        String fileName = URLUtil.guessFileName(url, null, null);
        
        // If URL has query parameters, try to extract file name from them
        Uri uri = Uri.parse(url);
        String fileParam = uri.getQueryParameter("file");
        if (fileParam != null && !fileParam.isEmpty()) {
            fileName = fileParam;
        }
        
        String nameParam = uri.getQueryParameter("name");
        if (nameParam != null && !nameParam.isEmpty()) {
            fileName = nameParam;
        }
        
        String fnParam = uri.getQueryParameter("fn");
        if (fnParam != null && !fnParam.isEmpty()) {
            fileName = fnParam;
        }
        
        // Special handling for specific URLs
        if (url.contains("/EXT/PKControl/DownloadFile") || url.contains("/DownloadFile")) {
            String type = uri.getQueryParameter("type");
            String id = uri.getQueryParameter("id");
            
            if (type != null && type.startsWith("F") && type.length() > 1) {
                fileName = type.substring(1);
            } else if (id != null && !id.isEmpty()) {
                fileName = "download_" + id;
            }
        }
        
        // Ensure file name has extension
        if (fileName != null && !fileName.contains(".")) {
            String mimeType = getMimeTypeFromUrl(url);
            String extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (extension != null) {
                fileName += "." + extension;
            } else {
                // Default to .bin if can't determine extension
                fileName += ".bin";
            }
        }
        
        return fileName;
    }
    
    /**
     * Gets the MIME type from a URL.
     *
     * @param url The URL to get the MIME type from
     * @return The MIME type
     */
    public static String getMimeTypeFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        
        // Check for common file extensions
        if (url.toLowerCase().endsWith(".pdf")) {
            return "application/pdf";
        } else if (url.toLowerCase().endsWith(".doc")) {
            return "application/msword";
        } else if (url.toLowerCase().endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (url.toLowerCase().endsWith(".xls")) {
            return "application/vnd.ms-excel";
        } else if (url.toLowerCase().endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (url.toLowerCase().endsWith(".ppt")) {
            return "application/vnd.ms-powerpoint";
        } else if (url.toLowerCase().endsWith(".pptx")) {
            return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        } else if (url.toLowerCase().endsWith(".zip")) {
            return "application/zip";
        } else if (url.toLowerCase().endsWith(".rar")) {
            return "application/x-rar-compressed";
        } else if (url.toLowerCase().endsWith(".7z")) {
            return "application/x-7z-compressed";
        } else if (url.toLowerCase().endsWith(".txt")) {
            return "text/plain";
        } else if (url.toLowerCase().endsWith(".csv")) {
            return "text/csv";
        } else if (url.toLowerCase().endsWith(".jpg") || url.toLowerCase().endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (url.toLowerCase().endsWith(".png")) {
            return "image/png";
        } else if (url.toLowerCase().endsWith(".gif")) {
            return "image/gif";
        } else if (url.toLowerCase().endsWith(".mp3")) {
            return "audio/mpeg";
        } else if (url.toLowerCase().endsWith(".mp4")) {
            return "video/mp4";
        } else if (url.toLowerCase().endsWith(".webm")) {
            return "video/webm";
        }
        
        // Try to extract extension from URL
        String extension = MimeTypeMap.getFileExtensionFromUrl(url);
        if (extension != null && !extension.isEmpty()) {
            String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mimeType != null) {
                return mimeType;
            }
        }
        
        // Default to octet-stream
        return "application/octet-stream";
    }
    
    /**
     * Extracts file name from content disposition header.
     *
     * @param contentDisposition The content disposition header
     * @return The file name
     */
    public static String extractFileNameFromContentDisposition(String contentDisposition) {
        if (contentDisposition == null || contentDisposition.isEmpty()) {
            return null;
        }
        
        try {
            Pattern pattern = Pattern.compile("filename\\*?=['\"]?(?:UTF-\\d['\"]*)?([^;\\r\\n\"']*)['\"]?;?", 
                    Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(contentDisposition);
            
            if (matcher.find()) {
                String fileName = matcher.group(1);
                
                // Decode URL-encoded parts
                fileName = fileName.replaceAll("%20", " ")
                        .replaceAll("%[0-9a-fA-F]{2}", "")
                        .trim();
                
                // Remove quotes if present
                if (fileName.startsWith("\"") && fileName.endsWith("\"")) {
                    fileName = fileName.substring(1, fileName.length() - 1);
                }
                
                // Replace invalid file name characters
                fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
                
                return fileName;
            }
            
            // Try another pattern as fallback
            Pattern fallbackPattern = Pattern.compile("filename=['\"]?([^;\\r\\n\"']*)['\"]?", 
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
}