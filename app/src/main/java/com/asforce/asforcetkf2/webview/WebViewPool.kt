package com.asforce.asforcetkf2.webview

import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import timber.log.Timber

/**
 * WebView nesneleri için havuz yönetimi
 * Bellek kullanımını optimize etmek için WebView'leri yeniden kullanır
 * ve gereksiz WebView oluşturmayı önler
 */
class WebViewPool(private val context: Context) {
    
    private val idleWebViews = mutableListOf<WebView>()
    private val activeWebViews = mutableMapOf<String, WebView>()
    private val maxPoolSize = 3 // Maksimum atıl WebView sayısı
    
    /**
     * Belirtilen sekme ID'si için WebView al
     * Önce aktif sekmelerden, sonra atıl havuzundan, yoksa yeni oluştur
     */
    @Synchronized
    fun getWebView(tabId: String): WebView {
        // Aktif sekmelere ait WebView varsa onu kullan
        activeWebViews[tabId]?.let { return it }
        
        // Atıl WebView varsa onu kullan
        if (idleWebViews.isNotEmpty()) {
            val webView = idleWebViews.removeAt(0)
            Timber.d("Reusing WebView from pool for tab $tabId")
            
            // WebView'ı hazırla
            prepareWebViewForReuse(webView)
            
            // Aktif havuza kaydet
            activeWebViews[tabId] = webView
            return webView
        }
        
        // Yoksa yeni WebView oluştur
        Timber.d("Creating new WebView for tab $tabId")
        val webView = createOptimizedWebView()
        activeWebViews[tabId] = webView
        return webView
    }
    
    /**
     * Yeniden kullanım için WebView temizliği
     */
    private fun prepareWebViewForReuse(webView: WebView) {
        try {
            // WebView'ı temizle
            webView.stopLoading()
            webView.clearHistory()
            webView.clearCache(true)
            webView.clearFormData()
            webView.clearSslPreferences()
            webView.clearDisappearingChildren()
            
            // Ayarları varsayılana döndür
            webView.settings.javaScriptEnabled = true
            webView.settings.useWideViewPort = true
            webView.settings.loadWithOverviewMode = true
            webView.settings.domStorageEnabled = true
            webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
            
            // Boş sayfa yükle
            webView.loadUrl("about:blank")
        } catch (e: Exception) {
            Timber.e("Error preparing WebView for reuse: ${e.message}")
        }
    }
    
    /**
     * Sekme kapandığında WebView'ı havuza iade et
     */
    @Synchronized
    fun releaseWebView(tabId: String) {
        val webView = activeWebViews.remove(tabId) ?: return
        
        // WebView'ı temizleyip havuza ekle veya yok et
        try {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            
            if (idleWebViews.size < maxPoolSize) {
                Timber.d("Returning WebView to pool for tab $tabId")
                idleWebViews.add(webView)
            } else {
                Timber.d("Destroying WebView for tab $tabId (pool full)")
                webView.destroy()
            }
        } catch (e: Exception) {
            Timber.e("Error releasing WebView: ${e.message}")
            webView.destroy()
        }
    }
    
    /**
     * Optimize edilmiş WebView oluştur
     */
    private fun createOptimizedWebView(): WebView {
        return WebView(context).apply {
            settings.apply {
                // Optimize edilmiş WebView ayarları
                javaScriptEnabled = true
                domStorageEnabled = true
                loadsImagesAutomatically = true
                useWideViewPort = true
                loadWithOverviewMode = true
                cacheMode = WebSettings.LOAD_DEFAULT
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                
                // Güvenlik optimizasyonları
                allowFileAccess = false
                allowContentAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                
                // Performans optimizasyonları
                blockNetworkImage = false
                mediaPlaybackRequiresUserGesture = true
            }
            
            // Hafıza optimizasyonu için yazılım render modunda başlat
            setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
        }
    }
    
    /**
     * Havuzdaki tüm WebView'ları temizle
     * Uygulama kapatılırken veya düşük bellek durumlarında çağrılmalı
     */
    @Synchronized
    fun clearPool() {
        Timber.d("Clearing WebView pool")
        
        idleWebViews.forEach { 
            try {
                it.destroy() 
            } catch (e: Exception) {
                Timber.e("Error destroying WebView: ${e.message}")
            }
        }
        idleWebViews.clear()
        
        // GC tetikle
        System.gc()
    }
    
    /**
     * Aktif WebView sayısını al
     */
    @Synchronized
    fun getActiveWebViewCount(): Int {
        return activeWebViews.size
    }
    
    /**
     * Atıl WebView sayısını al
     */
    @Synchronized
    fun getIdleWebViewCount(): Int {
        return idleWebViews.size
    }
}
