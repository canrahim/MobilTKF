package com.asforce.asforcetkf2.webview

import android.webkit.DownloadListener
import timber.log.Timber

/**
 * Custom DownloadListener for handling file downloads
 */
class TKFDownloadListener(
    private val onDownloadRequested: (String, String, String, String, Long) -> Unit
) : DownloadListener {
    
    override fun onDownloadStart(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimetype: String,
        contentLength: Long
    ) {
        Timber.d("Download requested: $url, mimetype: $mimetype, size: $contentLength")
        onDownloadRequested(url, userAgent, contentDisposition, mimetype, contentLength)
    }
}
