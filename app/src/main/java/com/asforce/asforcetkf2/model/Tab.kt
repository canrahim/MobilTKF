package com.asforce.asforcetkf2.model

import android.graphics.Bitmap
import java.util.UUID

/**
 * Represents a browser tab with all its properties
 * Geliştirilmiş sürüm: optimizasyon durum bilgileri eklendi
 */
data class Tab(
    val id: String = UUID.randomUUID().toString(),
    var url: String = "https://www.google.com",
    var title: String = "",
    var favicon: Bitmap? = null,
    var isActive: Boolean = false,
    var isLoading: Boolean = false,
    var isHibernated: Boolean = false,
    var isNew: Boolean = true,
    var isOptimizationApplied: Boolean = false,
    var cpuUsage: Float = 0f,
    var memoryUsage: Long = 0L,
    var lastAccessTime: Long = System.currentTimeMillis(),
    var lastOptimizationTime: Long = 0L,
    var position: Int = 0,
    var resourceCount: Int = 0
) {
    // Computed property for display title (use URL if title is empty)
    val displayTitle: String
        get() = if (title.isBlank()) url else title
        
    // Computed property to determine if tab needs optimization
    val needsOptimization: Boolean
        get() {
            val currentTime = System.currentTimeMillis()
            val timeSinceLastOptimization = currentTime - lastOptimizationTime
            
            // Optimize if:
            // 1. Never optimized before OR
            // 2. It's been more than 5 minutes since last optimization OR
            // 3. Resource count is high (more than 50 resources loaded)
            return !isOptimizationApplied || 
                   timeSinceLastOptimization > 5 * 60 * 1000 || 
                   resourceCount > 50
        }
        
    // Computed property to determine if tab is inactive and should be hibernated
    val shouldHibernate: Boolean
        get() {
            val currentTime = System.currentTimeMillis()
            val timeSinceLastAccess = currentTime - lastAccessTime
            
            // Hibernate if:
            // 1. Not active AND
            // 2. Not already hibernated AND
            // 3. Not been accessed for more than 10 minutes
            return !isActive && 
                   !isHibernated && 
                   timeSinceLastAccess > 10 * 60 * 1000
        }
        
    // Mark optimization as applied
    fun markOptimized() {
        isOptimizationApplied = true
        lastOptimizationTime = System.currentTimeMillis()
    }
    
    // Update access time when tab is interacted with
    fun updateAccessTime() {
        lastAccessTime = System.currentTimeMillis()
    }
}
