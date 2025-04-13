package com.asforce.asforcetkf2.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a Tab in the database
 */
@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey
    val id: String,
    val url: String,
    val title: String,
    val position: Int,
    val isActive: Boolean,
    val isHibernated: Boolean,
    val lastAccessTime: Long
)
