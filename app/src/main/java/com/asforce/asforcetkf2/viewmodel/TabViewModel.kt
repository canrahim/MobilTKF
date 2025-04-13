package com.asforce.asforcetkf2.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.asforce.asforcetkf2.data.BrowserDatabase
import com.asforce.asforcetkf2.data.BrowserRepository
import com.asforce.asforcetkf2.model.Tab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Collections

/**
 * ViewModel for managing browser tabs
 * Performans optimizasyonları eklenmiştir
 */
class TabViewModel(application: Application) : AndroidViewModel(application) {
    
    private val repository: BrowserRepository
    val allTabs: LiveData<List<Tab>>
    
    private val _activeTab = MutableLiveData<Tab>()
    val activeTab: LiveData<Tab> = _activeTab
    
    private val _resourceMonitoringEnabled = MutableStateFlow(true)
    val resourceMonitoringEnabled: StateFlow<Boolean> = _resourceMonitoringEnabled
    
    // Son tab güncelleme zamanı ve son işlem zamanı - aşırı işlemlerden kaçınmak için
    private var lastTabUpdateTime = 0L
    private var lastOperationTime = 0L
    private val UPDATE_THROTTLE_MS = 250L // Güncellemeler arası minimum süre
    
    init {
        val tabDao = BrowserDatabase.getDatabase(application).tabDao()
        repository = BrowserRepository(tabDao)
        
        // Performans optimizasyonu: tab listesini ve active tab'i verimli şekilde takip et
        allTabs = repository.allTabs.map { tabs ->
            val currentTime = System.currentTimeMillis()
            // Throttle - çok sık güncellemelerden kaçın
            if (currentTime - lastTabUpdateTime < UPDATE_THROTTLE_MS && tabs.size > 3) {
                // Sadece active tab değişmesi durumunda güncelle
                val activeTabChanged = tabs.find { it.isActive }?.id != _activeTab.value?.id
                if (!activeTabChanged) {
                    return@map tabs
                }
            }
            
            lastTabUpdateTime = currentTime
            
            // Sort tabs by position - optimizasyon: sadece gerekli olduğunda sırala
            if (tabs.size > 1) {
                Collections.sort(tabs) { tab1, tab2 ->
                    // indexOf performans sorunu - doğrudan position değerlerini karşılaştır
                    val position1 = tab1.position
                    val position2 = tab2.position
                    position1.compareTo(position2)
                }
            }
            
            // Find active tab
            val activeTab = tabs.find { it.isActive }
            // Mevcut active tab'den farklıysa değiştir
            if (activeTab != null && _activeTab.value?.id != activeTab.id) {
                _activeTab.postValue(activeTab)
            }
            
            tabs
        }.asLiveData(Dispatchers.Default) // IO thread'de işle
    }
    
    /**
     * Add a new tab
     * @return The ID of the newly created tab
     */
    fun addTab(url: String = "https://www.google.com"): String {
        // Yeni tab ID'si oluştur - main thread'den çağrılabilir
        val newTabId = java.util.UUID.randomUUID().toString()
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tabs = allTabs.value ?: emptyList()
                val position = tabs.size
                
                // Önce tüm sekmeleri pasif yap
                repository.deactivateAllTabs()
                
                val newTab = Tab(
                    id = newTabId,
                    url = url,
                    isActive = true,
                    position = position,
                    lastAccessTime = System.currentTimeMillis()
                )
                
                // Yeni sekmeyi veritabanına ekle
                repository.addTab(newTab, position)
                
                // Active tab olarak güncelle
                _activeTab.postValue(newTab)
                
                Timber.d("Added new tab: ${newTab.id} at position $position")
            } catch (e: Exception) {
                Timber.e(e, "Error adding new tab for URL: $url")
            }
        }
        
        return newTabId
    }
    
    /**
     * Close a tab
     */
    fun closeTab(tab: Tab) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Delete the tab
                repository.deleteTab(tab)
                
                // If it was the active tab, activate the next available tab
                if (tab.isActive) {
                    val tabs = allTabs.value ?: emptyList()
                    if (tabs.isNotEmpty()) {
                        // Find next tab to activate
                        val nextTab = tabs.firstOrNull { it.id != tab.id }
                        nextTab?.let {
                            setActiveTab(it)
                        }
                    }
                }
                
                Timber.d("Closed tab: ${tab.id}")
            } catch (e: Exception) {
                Timber.e(e, "Error closing tab")
            }
        }
    }
    
    /**
     * Set a tab as active
     */
    fun setActiveTab(tab: Tab) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Update tab state
                val updatedTab = tab.copy(
                    isActive = true,
                    lastAccessTime = System.currentTimeMillis()
                )
                
                // If hibernated, wake it up
                if (tab.isHibernated) {
                    repository.wakeUpTab(tab.id)
                }
                
                // Set as active
                repository.setActiveTab(tab.id)
                
                // Update active tab in UI
                _activeTab.postValue(updatedTab)
                
                Timber.d("Set active tab: ${tab.id}")
            } catch (e: Exception) {
                Timber.e(e, "Error setting active tab")
            }
        }
    }
    
    /**
     * Update tab information - Performans optimizasyonu eklenmiştir
     */
    fun updateTab(tab: Tab, position: Int) {
        // Aşırı güncellemeleri önlemek için darboğazlama (throttling) ekle
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastOperationTime < UPDATE_THROTTLE_MS) {
            // Kısa süre içinde çok sık güncelleme isteği
            // Aktif tab veya acil güncelleme değilse ertele
            if (!tab.isActive) {
                // 100ms sonra tekrar kontrol et
                viewModelScope.launch {
                    delay(UPDATE_THROTTLE_MS)
                    // Tab güncelleme isteği hala geçerliyse tekrar dene
                    updateTab(tab, position)
                }
                return
            }
        }
        
        lastOperationTime = currentTime
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateTab(tab, position)
                
                // If this is the active tab, update UI
                if (tab.isActive) {
                    _activeTab.postValue(tab)
                }
                
                Timber.d("Updated tab: ${tab.id}")
            } catch (e: Exception) {
                Timber.e(e, "Error updating tab")
            }
        }
    }
    
    /**
     * Hibernate a tab to save resources
     */
    fun hibernateTab(tab: Tab) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Can't hibernate active tab
                if (tab.isActive) {
                    return@launch
                }
                
                repository.hibernateTab(tab.id)
                
                Timber.d("Hibernated tab: ${tab.id}")
            } catch (e: Exception) {
                Timber.e(e, "Error hibernating tab")
            }
        }
    }
    
    /**
     * Wake up a hibernated tab
     */
    fun wakeUpTab(tab: Tab) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!tab.isHibernated) {
                    return@launch
                }
                
                repository.wakeUpTab(tab.id)
                
                Timber.d("Woke up tab: ${tab.id}")
            } catch (e: Exception) {
                Timber.e(e, "Error waking up tab")
            }
        }
    }
    
    /**
     * Update tab positions after drag and drop reordering
     */
    fun updateTabPositions(tabs: List<Tab>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                tabs.forEachIndexed { index, tab ->
                    repository.updateTabPosition(tab.id, index)
                }
                
                Timber.d("Updated tab positions")
            } catch (e: Exception) {
                Timber.e(e, "Error updating tab positions")
            }
        }
    }
    
    /**
     * Toggle resource monitoring
     */
    fun toggleResourceMonitoring(enabled: Boolean) {
        _resourceMonitoringEnabled.value = enabled
    }
    
    /**
     * Update resource metrics for a tab
     */
    fun updateTabResources(tabId: String, cpuUsage: Float, memoryUsage: Long) {
        val tabs = allTabs.value ?: return
        val tab = tabs.find { it.id == tabId } ?: return
        
        val updatedTab = tab.copy(
            cpuUsage = cpuUsage,
            memoryUsage = memoryUsage
        )
        
        // Update in background to avoid excessive writes
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val position = tabs.indexOf(tab)
                repository.updateTabInBackground(updatedTab, position)
                
                if (tab.isActive) {
                    _activeTab.postValue(updatedTab)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating tab resources")
            }
        }
    }
}
