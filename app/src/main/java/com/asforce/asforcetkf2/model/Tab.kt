package com.asforce.asforcetkf2.model

import android.graphics.Bitmap
import java.util.UUID

/**
 * Represents a browser tab with all its properties
 */
data class Tab(
    val id: String = UUID.randomUUID().toString(),
    var url: String = "https://www.google.com",
    var title: String = "",
    var favicon: Bitmap? = null,
    var isActive: Boolean = false,
    var isLoading: Boolean = false,
    var isHibernated: Boolean = false,
    var cpuUsage: Float = 0f,
    var memoryUsage: Long = 0L,
    var lastAccessTime: Long = System.currentTimeMillis(),
    var position: Int = 0
) {
    // Computed property for display title (use URL if title is empty)
    val displayTitle: String
        get() = if (title.isBlank()) url else title
}
