package com.asforce.asforcetkf2.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the tabs table
 */
@Dao
interface TabDao {
    @Query("SELECT * FROM tabs ORDER BY position ASC")
    fun getAllTabs(): Flow<List<TabEntity>>
    
    @Query("SELECT * FROM tabs WHERE id = :tabId")
    suspend fun getTabById(tabId: String): TabEntity?
    
    @Query("SELECT * FROM tabs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTab(): TabEntity?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: TabEntity)
    
    @Update
    suspend fun updateTab(tab: TabEntity)
    
    @Delete
    suspend fun deleteTab(tab: TabEntity)
    
    @Query("DELETE FROM tabs WHERE id = :tabId")
    suspend fun deleteTabById(tabId: String)
    
    @Query("UPDATE tabs SET isActive = 0")
    suspend fun deactivateAllTabs()
    
    @Query("UPDATE tabs SET isActive = 1 WHERE id = :tabId")
    suspend fun activateTab(tabId: String)
    
    @Query("UPDATE tabs SET isHibernated = 1 WHERE id = :tabId")
    suspend fun hibernateTab(tabId: String)
    
    @Query("UPDATE tabs SET isHibernated = 0 WHERE id = :tabId")
    suspend fun wakeUpTab(tabId: String)
    
    @Query("UPDATE tabs SET position = :newPosition WHERE id = :tabId")
    suspend fun updateTabPosition(tabId: String, newPosition: Int)
}
