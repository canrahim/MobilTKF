package com.asforce.asforcetkf2.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import timber.log.Timber
import java.io.File

/**
 * Utility class to handle file downloads
 */
class TKFDownloadManager(private val context: Context) {
    
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    
    /**
     * Start downloading a file
     */
    fun downloadFile(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    ): Long {
        try {
            // Extract filename from URL or content disposition
            val fileName = extractFilename(url, contentDisposition)
            
            // Create download request
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("Downloading $fileName")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType(mimeType)
                .addRequestHeader("User-Agent", userAgent)
            
            // Start download
            val downloadId = downloadManager.enqueue(request)
            Timber.d("Download started: $fileName, ID: $downloadId")
            
            return downloadId
        } catch (e: Exception) {
            Timber.e(e, "Error starting download for URL: $url")
            return -1
        }
    }
    
    /**
     * Extract filename from content disposition or URL
     */
    private fun extractFilename(url: String, contentDisposition: String): String {
        // Try to extract from content disposition first
        val filenameFromContentDisposition = contentDisposition
            .takeIf { it.isNotEmpty() }
            ?.let { disposition ->
                val pattern = "filename=[\"']?([^\"']+)[\"']?".toRegex()
                pattern.find(disposition)?.groupValues?.getOrNull(1)
            }
        
        // If that fails, extract from URL
        val filenameFromUrl = url.takeIf { it.isNotEmpty() }
            ?.let { Uri.parse(it).lastPathSegment }
        
        // Use one of the extracted filenames or a default
        return filenameFromContentDisposition
            ?: filenameFromUrl
            ?: "download_${System.currentTimeMillis()}"
    }
    
    /**
     * Get information about a download
     */
    fun getDownloadInfo(downloadId: Long): DownloadInfo? {
        try {
            val query = DownloadManager.Query().setFilterById(downloadId)
            val cursor = downloadManager.query(query)
            
            cursor.use { c ->
                if (c.moveToFirst()) {
                    val statusIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val reasonIndex = c.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val totalSizeIndex = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val bytesDownloadedIndex = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val localUriIndex = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    
                    val status = c.getInt(statusIndex)
                    val reason = c.getInt(reasonIndex)
                    val totalSize = c.getLong(totalSizeIndex)
                    val bytesDownloaded = c.getLong(bytesDownloadedIndex)
                    val localUri = c.getString(localUriIndex)
                    
                    return DownloadInfo(
                        id = downloadId,
                        status = status,
                        reason = reason,
                        totalSize = totalSize,
                        bytesDownloaded = bytesDownloaded,
                        progress = if (totalSize > 0) (bytesDownloaded * 100 / totalSize).toInt() else 0,
                        localUri = localUri
                    )
                }
            }
            
            return null
        } catch (e: Exception) {
            Timber.e(e, "Error getting download info for ID: $downloadId")
            return null
        }
    }
    
    /**
     * Cancel a download
     */
    fun cancelDownload(downloadId: Long): Boolean {
        return try {
            val removedRows = downloadManager.remove(downloadId)
            removedRows > 0
        } catch (e: Exception) {
            Timber.e(e, "Error canceling download ID: $downloadId")
            false
        }
    }
    
    /**
     * Data class representing download information
     */
    data class DownloadInfo(
        val id: Long,
        val status: Int,
        val reason: Int,
        val totalSize: Long,
        val bytesDownloaded: Long,
        val progress: Int,
        val localUri: String?
    ) {
        val isCompleted: Boolean
            get() = status == DownloadManager.STATUS_SUCCESSFUL
        
        val isFailed: Boolean
            get() = status == DownloadManager.STATUS_FAILED
        
        val isRunning: Boolean
            get() = status == DownloadManager.STATUS_RUNNING
        
        val isPaused: Boolean
            get() = status == DownloadManager.STATUS_PAUSED
    }
}
