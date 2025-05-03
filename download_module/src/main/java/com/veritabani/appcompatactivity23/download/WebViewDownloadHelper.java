package com.veritabani.appcompatactivity23.download;

import android.content.Context;
import android.util.Log;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.JavascriptInterface;

/**
 * Helper class to set up download functionality for WebViews.
 */
public class WebViewDownloadHelper {
    private static final String TAG = "WebViewDownloadHelper";
    private final DownloadManager downloadManager;
    private Context context;
    
    public WebViewDownloadHelper(Context context) {
        this.context = context.getApplicationContext();
        this.downloadManager = DownloadManager.getInstance(context);
    }
    
    /**
     * Sets up WebView downloads.
     *
     * @param webView The WebView to set up downloads for
     */
    public void setupWebViewDownloads(WebView webView) {
        // Standart indirme dinleyicisi
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            // Better filename extraction
            String fileName = null;
            
            // 1. First try to extract from Content-Disposition
            if (contentDisposition != null && !contentDisposition.isEmpty()) {
                fileName = downloadManager.extractFilenameFromContentDisposition(contentDisposition);
                Log.d(TAG, "Filename from Content-Disposition: " + fileName);
            }
            
            // 2. If that fails, try URL-based extraction
            if (fileName == null || fileName.isEmpty()) {
                fileName = downloadManager.extractFilenameFromUrl(url);
                Log.d(TAG, "Filename extracted from URL: " + fileName);
            }
            
            // 3. As a last resort, use URLUtil
            if (fileName == null || fileName.isEmpty()) {
                fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);
                Log.d(TAG, "Filename from URLUtil: " + fileName);
            }
            
            // Fix MIME type for PDF files, JPGs and other binary files
            if (mimeType == null || mimeType.isEmpty() || mimeType.equals("application/octet-stream")) {
                // Check URL for PDF indicators
                if (url.toLowerCase().contains(".pdf")) {
                    mimeType = "application/pdf";
                    if (!fileName.toLowerCase().endsWith(".pdf")) {
                        // Make sure filename has .pdf extension
                        String name = fileName;
                        int lastDot = name.lastIndexOf(".");
                        if (lastDot > 0) {
                            name = name.substring(0, lastDot);
                        }
                        fileName = name + ".pdf";
                    }
                    Log.d(TAG, "Fixed PDF MIME type and filename: " + fileName);
                }
                // Check for JPEG files
                else if (url.toLowerCase().contains(".jpg") || url.toLowerCase().contains(".jpeg")) {
                    mimeType = "image/jpeg";
                    // Make sure filename has .jpg extension
                    if (!fileName.toLowerCase().endsWith(".jpg") && !fileName.toLowerCase().endsWith(".jpeg")) {
                        String name = fileName;
                        int lastDot = name.lastIndexOf(".");
                        if (lastDot > 0) {
                            name = name.substring(0, lastDot);
                        }
                        fileName = name + ".jpg";
                    }
                    Log.d(TAG, "Fixed JPG MIME type and filename: " + fileName);
                }
                // Check for PNG files
                else if (url.toLowerCase().contains(".png")) {
                    mimeType = "image/png";
                    // Ensure extension
                    if (!fileName.toLowerCase().endsWith(".png")) {
                        String name = fileName;
                        int lastDot = name.lastIndexOf(".");
                        if (lastDot > 0) {
                            name = name.substring(0, lastDot);
                        }
                        fileName = name + ".png";
                    }
                    Log.d(TAG, "Fixed PNG MIME type and filename: " + fileName);
                }
            }
            
            // Log download parameters
            Log.d(TAG, "Download - URL: " + url);
            Log.d(TAG, "Filename: " + fileName);
            Log.d(TAG, "MIME type: " + mimeType);
            Log.d(TAG, "Content length: " + contentLength);
            
            // Hız optimizasyonu: Büyük dosyaları doğrudan indirmeye başla
            if (contentLength > 10 * 1024 * 1024) { // 10MB'dan büyük dosyalar
                // Büyük dosyalar için direkt indirme başlat
                downloadManager.downloadFile(url, fileName, mimeType, userAgent, contentDisposition);
                return;
            }
            
            // Boyut bilgisini göster
            String sizeInfo = null;
            if (contentLength > 0) {
                float sizeMB = contentLength / (1024f * 1024f);
                if (sizeMB >= 1) {
                    sizeInfo = String.format("%.1f MB", sizeMB);
                } else {
                    float sizeKB = contentLength / 1024f;
                    sizeInfo = String.format("%.0f KB", sizeKB);
                }
            }
            
            // Resim ise veya içerik türü resim ise ona göre bildirim göster
            boolean isImage = false;
            if (mimeType != null && mimeType.startsWith("image/")) {
                isImage = true;
            }
            
            // İndirme onayı göster
            downloadManager.showDownloadConfirmationDialog(
                    url, fileName, mimeType, userAgent, contentDisposition, sizeInfo, isImage);
        });
        
        // JavaScript indirme arayüzünü ekle
        setupJavaScriptInterface(webView);
        
        // Custom WebViewClient ile indirilebilir linkleri yakala 
        setupCustomWebViewClient(webView);
    }
    
    /**
     * JavaScript arayüzünü WebView'a ekler
     */
    private void setupJavaScriptInterface(WebView webView) {
        webView.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void downloadImage(String imageUrl) {
                Log.d(TAG, "JS image download request: " + imageUrl);
                ImageDownloader imageDownloader = new ImageDownloader(context);
                imageDownloader.downloadImage(imageUrl, webView);
            }
            
            @JavascriptInterface
            public void handleDownloadUrl(String url) {
                Log.d(TAG, "JS download URL: " + url);
                if (isDownloadUrl(url)) {
                    handleSpecialDownloadUrl(url);
                } else {
                    // Normal indirme
                    String fileName = downloadManager.extractFilenameFromUrl(url);
                    String userAgent = webView.getSettings().getUserAgentString();
                    downloadManager.downloadFile(url, fileName, null, userAgent, null);
                }
            }
        }, "NativeDownloader");
        
        // İndirme butonlarını yakala
        injectDownloadButtonHandler(webView);
    }
    
    /**
     * İndirme butonlarını yakalamak için JavaScript enjekte eder
     */
    private void injectDownloadButtonHandler(WebView webView) {
        String js = "javascript:(function() {\n" +
                "    console.log('Injecting download button handler');\n" +
                "    var downloadLinks = document.querySelectorAll('a[title=\"İndir\"], a.btn-success, a:contains(\"İndir\"), button:contains(\"İndir\")');\n" +
                "    console.log('Found download buttons: ' + downloadLinks.length);\n" +
                "    for (var i = 0; i < downloadLinks.length; i++) {\n" +
                "        var link = downloadLinks[i];\n" +
                "        if (!link.hasAttribute('data-download-handled')) {\n" +
                "            link.setAttribute('data-download-handled', 'true');\n" +
                "            var originalOnClick = link.onclick;\n" +
                "            link.onclick = function(e) {\n" +
                "                e.preventDefault();\n" +
                "                var url = this.href || this.getAttribute('data-url') || this.getAttribute('href');\n" +
                "                if (url) {\n" +
                "                    window.NativeDownloader.handleDownloadUrl(url);\n" +
                "                    return false;\n" +
                "                }\n" +
                "                if (originalOnClick) {\n" +
                "                    return originalOnClick.call(this, e);\n" +
                "                }\n" +
                "            };\n" +
                "        }\n" +
                "    }\n" +
                "})();";

        webView.evaluateJavascript(js, null);
    }
    
    /**
     * URL'nin indirilebilir bir bağlantı olup olmadığını kontrol eder
     */
    private boolean isDownloadUrl(String url) {
        if (url == null) return false;
        return url.contains("/EXT/PKControl/DownloadFile") ||
                url.contains("/DownloadFile") ||
                (url.contains("download") && url.contains("id="));
    }
    
    /**
     * Özel indirme URL'lerini işler
     */
    private void handleSpecialDownloadUrl(String url) {
        Log.d(TAG, "Handling special download URL: " + url);
        try {
            android.net.Uri uri = android.net.Uri.parse(url);
            String type = uri.getQueryParameter("type");
            String id = uri.getQueryParameter("id");
            String format = uri.getQueryParameter("format");
            String fileName = "download_" + System.currentTimeMillis();
            String mimeType = null;
            boolean isPdf = false;
            boolean isImage = false;
            
            // Special handling for URLs containing SoilContinuity which we know should be JPG
            if (url.contains("SoilContinuity")) {
                isImage = true;
                mimeType = "image/jpeg";
                Log.d(TAG, "Detected SoilContinuity JPG image from URL");
            }
            
            // Check content disposition from URL if available
            String contentDisposition = null;
            try {
                java.net.URL urlObj = new java.net.URL(url);
                java.net.HttpURLConnection connection = (java.net.HttpURLConnection) urlObj.openConnection();
                connection.setRequestMethod("HEAD");
                connection.connect();
                contentDisposition = connection.getHeaderField("Content-Disposition");
                connection.disconnect();
                if (contentDisposition != null) {
                    Log.d(TAG, "Found Content-Disposition: " + contentDisposition);
                }
            } catch (Exception e) {
                Log.d(TAG, "Could not perform HEAD request: " + e.getMessage());
            }
            
            // Determine file type from URL and parameters
            if (url.toLowerCase().contains(".pdf") || 
                url.toLowerCase().contains("pdf=true") ||
                (format != null && format.toLowerCase().equals("pdf"))) {
                isPdf = true;
                mimeType = "application/pdf";
            } else if (url.toLowerCase().contains(".jpg") || 
                       url.toLowerCase().contains(".jpeg") ||
                       (format != null && (format.toLowerCase().equals("jpg") || format.toLowerCase().equals("jpeg")))) {
                isImage = true;
                mimeType = "image/jpeg";
            } else if (url.toLowerCase().contains(".png") ||
                       (format != null && format.toLowerCase().equals("png"))) {
                isImage = true;
                mimeType = "image/png";
            }
            
            // Try to extract filename from Content-Disposition if available
            if (contentDisposition != null) {
                String extractedName = downloadManager.extractFilenameFromContentDisposition(contentDisposition);
                if (extractedName != null && !extractedName.isEmpty()) {
                    fileName = extractedName;
                    Log.d(TAG, "Using filename from Content-Disposition: " + fileName);
                    
                    // Try to determine MIME type from the filename extension
                    if (fileName.toLowerCase().endsWith(".jpg") || fileName.toLowerCase().endsWith(".jpeg")) {
                        isImage = true;
                        mimeType = "image/jpeg";
                    } else if (fileName.toLowerCase().endsWith(".png")) {
                        isImage = true;
                        mimeType = "image/png";
                    } else if (fileName.toLowerCase().endsWith(".pdf")) {
                        isPdf = true;
                        mimeType = "application/pdf";
                    }
                }
            }
            
            // Extract filename from type parameter if not found in Content-Disposition
            if (type != null && type.startsWith("F") && type.length() > 1) {
                String fileNameBase = type.substring(1);
                fileName = fileNameBase;
                Log.d(TAG, "Using filename from type parameter: " + fileName);
                
                // Special handling for SoilContinuity - make sure it's saved as JPG
                if (fileName.equals("SoilContinuity")) {
                    isImage = true;
                    mimeType = "image/jpeg";
                    if (id != null && !id.isEmpty()) {
                        fileName = id + fileName + ".jpg";
                    } else {
                        fileName = fileName + ".jpg";
                    }
                    Log.d(TAG, "Set SoilContinuity as JPG: " + fileName);
                }
            } else if (id != null && !id.isEmpty() && (contentDisposition == null || fileName.equals("download_" + System.currentTimeMillis()))) {
                fileName = "download_" + id;
                Log.d(TAG, "Using filename from id parameter: " + fileName);
            }
            
            // Ensure proper extension based on type
            if (isPdf) {
                if (!fileName.toLowerCase().endsWith(".pdf")) {
                    fileName = fileName.replaceAll("\\.[^.]*$", "") + ".pdf";
                }
            } else if (isImage) {
                // Use appropriate image extension
                if (mimeType != null && mimeType.equals("image/jpeg")) {
                    if (!fileName.toLowerCase().endsWith(".jpg") && !fileName.toLowerCase().endsWith(".jpeg")) {
                        // Remove existing extension if any, but preserve SoilContinuity special case with .jpg
                        if (!fileName.contains("SoilContinuity.jpg")) {
                            fileName = fileName.replaceAll("\\.[^.]*$", "") + ".jpg";
                        }
                    }
                } else if (mimeType != null && mimeType.equals("image/png")) {
                    if (!fileName.toLowerCase().endsWith(".png")) {
                        fileName = fileName.replaceAll("\\.[^.]*$", "") + ".png";
                    }
                } else {
                    // Default to jpg for other image types
                    if (!fileName.toLowerCase().endsWith(".jpg") && !fileName.toLowerCase().endsWith(".jpeg")) {
                        fileName = fileName.replaceAll("\\.[^.]*$", "") + ".jpg";
                    }
                    if (mimeType == null) {
                        mimeType = "image/jpeg";
                    }
                }
            }
            
            Log.d(TAG, "Final filename: " + fileName);
            Log.d(TAG, "MIME type: " + mimeType);
            
            // Use custom download with proper MIME type
            // Special handling for SoilContinuity since we know it's a JPG image
            if (fileName.contains("SoilContinuity") && !fileName.toLowerCase().endsWith(".jpg")) {
                fileName = fileName + ".jpg";
                mimeType = "image/jpeg";
                isImage = true;
                Log.d(TAG, "Enforced JPG for SoilContinuity: " + fileName);
            }
            
            if (mimeType != null) {
                // Daha güvenilir indirme için ImageDownloader kullan (görsel dosyalar için)
                if (isImage && mimeType.startsWith("image/")) {
                    // For SoilContinuity, use direct download for more control
                    if (fileName.contains("SoilContinuity")) {
                        downloadManager.downloadFile(url, fileName, "image/jpeg", "Mozilla/5.0", contentDisposition);
                        Log.d(TAG, "Using direct download for SoilContinuity with image/jpeg MIME type");
                    } else {
                        ImageDownloader imageDownloader = new ImageDownloader(context);
                        imageDownloader.downloadImage(url, null);
                    }
                } else {
                    // Diğer dosya türleri için standart indirme yöntemini kullan
                    downloadManager.downloadFile(url, fileName, mimeType, "Mozilla/5.0", contentDisposition);
                }
            } else {
                // Use custom download for other cases
                downloadManager.startCustomDownload(url, fileName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing download URL", e);
            // Fallback to simpler download
            String fileName = downloadManager.extractFilenameFromUrl(url);
            downloadManager.downloadFile(url, fileName, null, "Mozilla/5.0", null);
        }
    }
    
    /**
     * WebView için özel istemci kurar
     */
    private void setupCustomWebViewClient(WebView webView) {
        WebViewClient originalClient = webView.getWebViewClient();
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (originalClient != null) {
                    originalClient.onPageFinished(view, url);
                }
                // Sayfa yüklendiğinde JavaScript enjekte et
                injectDownloadButtonHandler(view);
            }
            
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Log.d(TAG, "shouldOverrideUrlLoading: " + url);
                if (url != null && isDownloadUrl(url)) {
                    handleSpecialDownloadUrl(url);
                    return true;
                }
                
                if (originalClient != null) {
                    return originalClient.shouldOverrideUrlLoading(view, url);
                }
                return false;
            }
            
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (isDownloadUrl(url)) {
                    // Ana thread'de çalıştır
                    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                        handleSpecialDownloadUrl(url);
                    });
                }
                
                if (originalClient != null) {
                    return originalClient.shouldInterceptRequest(view, request);
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
    }
    
    /**
     * Returns the DownloadManager instance.
     *
     * @return The DownloadManager instance
     */
    public DownloadManager getDownloadManager() {
        return downloadManager;
    }
    
    /**
     * Clean up resources when the helper is no longer needed.
     */
    public void cleanup() {
        downloadManager.unregisterDownloadReceiver();
    }
}