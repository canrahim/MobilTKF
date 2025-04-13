package com.asforce.asforcetkf2.data

import com.asforce.asforcetkf2.model.Tab
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Repository that handles tab data operations
 */
class BrowserRepository(private val tabDao: TabDao) {
    
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    
    // Get all tabs as a Flow
    val allTabs: Flow<List<Tab>> = tabDao.getAllTabs().map { entities ->
        entities.map { entity ->
            Tab(
                id = entity.id,
                url = entity.url,
                title = entity.title,
                isActive = entity.isActive,
                isHibernated = entity.isHibernated,
                lastAccessTime = entity.lastAccessTime
            )
        }
    }
    
    // Add a new tab
    suspend fun addTab(tab: Tab, position: Int) {
        tabDao.insertTab(
            TabEntity(
                id = tab.id,
                url = tab.url,
                title = tab.title,
                position = position,
                isActive = tab.isActive,
                isHibernated = tab.isHibernated,
                lastAccessTime = tab.lastAccessTime
            )
        )
    }
    
    // Update an existing tab
    suspend fun updateTab(tab: Tab, position: Int) {
        tabDao.updateTab(
            TabEntity(
                id = tab.id,
                url = tab.url,
                title = tab.title,
                position = position,
                isActive = tab.isActive,
                isHibernated = tab.isHibernated,
                lastAccessTime = tab.lastAccessTime
            )
        )
    }
    
    // Delete a tab
    suspend fun deleteTab(tab: Tab) {
        tabDao.deleteTabById(tab.id)
    }
    
    // Set active tab
    suspend fun setActiveTab(tabId: String) {
        tabDao.deactivateAllTabs()
        tabDao.activateTab(tabId)
    }
    
    // Tüm sekmeleri pasif yap
    suspend fun deactivateAllTabs() {
        tabDao.deactivateAllTabs()
    }
    
    // Hibernate a tab
    suspend fun hibernateTab(tabId: String) {
        tabDao.hibernateTab(tabId)
    }
    
    // Wake up a hibernated tab
    suspend fun wakeUpTab(tabId: String) {
        tabDao.wakeUpTab(tabId)
    }
    
    // Update tab positions when reordering
    suspend fun updateTabPosition(tabId: String, newPosition: Int) {
        tabDao.updateTabPosition(tabId, newPosition)
    }
    
    // Update tab data in the background
    fun updateTabInBackground(tab: Tab, position: Int) {
        coroutineScope.launch {
            updateTab(tab, position)
        }
    }
}