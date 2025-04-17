package com.asforce.asforcetkf2

import android.content.Context
import android.webkit.WebView
import com.asforce.asforcetkf2.model.Tab
import com.asforce.asforcetkf2.util.TKFImageOptimizer
import com.asforce.asforcetkf2.util.TKFPerformanceManager
import com.asforce.asforcetkf2.webview.TKFFormManager
import com.asforce.asforcetkf2.webview.TKFSessionManager
import com.asforce.asforcetkf2.webview.TKFWebViewOptimizer
import com.asforce.asforcetkf2.webview.WebViewPool
import timber.log.Timber

/**
 * TKF Tarayıcı bileşenleri için tüm optimizasyon sınıflarını bir araya getiren ana optimizatör
 * Uygulama genelinde performans ve bellek iyileştirmelerini koordine eder
 */
class TKFBrowserOptimizer(private val context: Context) {
    
    // WebView havuzu - daha verimli bellek kullanımı
    private val webViewPool = WebViewPool(context)
    
    // Performans yöneticisi
    private val performanceManager = TKFPerformanceManager.getInstance(context)
    
    // WebView optimizasyon bileşenleri
    private val webViewOptimizers = mutableMapOf<String, TKFWebViewOptimizer>()
    private val formManagers = mutableMapOf<String, TKFFormManager>()
    private val sessionManagers = mutableMapOf<String, TKFSessionManager>()
    private val imageOptimizers = mutableMapOf<String, TKFImageOptimizer>()
    
    /**
     * Uygulama başlangıç optimizasyonu
     */
    fun optimizeAppStart() {
        // Performans ölçümleri başlat
        performanceManager.startAppMeasurement()
        
        // DNS önbelleği ısıtması
        performanceManager.startDnsWarmup()
        
        // Bellek durumunu kontrol et ve optimizasyon uygula
        performanceManager.optimizeMemory()
        
        Timber.d("App startup optimization applied")
    }
    
    /**
     * Sekme için WebView optimizasyonu 
     * Geliştirilmiş versiyon - WebView havuzu kullanır ve bellek sızıntılarını önler
     */
    fun optimizeTabWebView(webView: WebView, tab: Tab) {
        val tabId = tab.id
        
        // Düşük bellek modu kontrolü
        val isLowMemoryMode = performanceManager.isInLowMemoryMode()
        
        // Optimizasyon öncesi performans ölçümü başlat
        performanceManager.startMeasurement("tab_optimize_$tabId")
        
        // WebView havuzundan optimize edilmiş WebView al veya mevcut olanı optimize et
        val optimizedWebView = if (tab.isNew) {
            // Yeni sekme için WebView havuzundan al
            webViewPool.getWebView(tabId)
        } else {
            // Mevcut WebView'i kullan
            webView
        }
        
        // WebView optimizasyonu
        val optimizer = TKFWebViewOptimizer.getInstance(optimizedWebView)
        webViewOptimizers[tabId] = optimizer
        
        // Ana optimizasyonları uygula
        optimizer.optimizeForTabSwitch(optimizedWebView, tab, tab.isActive)
        
        // Szutest.com.tr kontrolü ve özel optimizasyon
        if (tab.url.contains("szutest.com.tr")) {
            optimizer.optimizeForSzutest(optimizedWebView)
        }
        
        // Oturum yönetimi
        val sessionManager = TKFSessionManager(optimizedWebView)
        sessionManagers[tabId] = sessionManager
        sessionManager.startSessionMonitoring()
        
        // Form yönetimi
        val formManager = TKFFormManager(optimizedWebView)
        formManagers[tabId] = formManager
        formManager.enableFormEnhancements()
        
        // Görüntü optimizasyonu
        val imageOptimizer = TKFImageOptimizer(context, optimizedWebView)
        imageOptimizers[tabId] = imageOptimizer
        
        // Düşük bellek durumunda daha agresif optimizasyon
        if (isLowMemoryMode) {
            imageOptimizer.applyLowMemoryImageOptimization()
            
            // Düşük bellek modunda DOM ağacı derinlemesine temizlenir
            optimizer.enhancedDomOptimization(optimizedWebView)
        } else {
            imageOptimizer.optimizeImages()
        }
        
        // SVG ve ikon optimizasyonu (genellikle yüksek bellek kullanır)
        imageOptimizer.optimizeSvgAndIcons()
        
        // Genel WebView performans optimizasyonu
        performanceManager.optimizeWebView(optimizedWebView)
        
        // Optimizasyon süresini ölç
        val duration = performanceManager.endMeasurement("tab_optimize_$tabId")
        Timber.d("Tab $tabId optimization completed in $duration ms")
    }
    
    /**
     * Sayfa yükleme performansını raporla
     */
    fun reportPageLoadPerformance(tabId: String, url: String, loadTime: Long, resourceCount: Int) {
        performanceManager.reportPageLoadPerformance(url, loadTime, resourceCount)
        
        // Sayfa yükleme sonrası form ve görüntü optimizasyonlarını tekrar kontrol et
        val formManager = formManagers[tabId]
        formManager?.enableFormEnhancements()
        
        val imageOptimizer = imageOptimizers[tabId]
        imageOptimizer?.optimizeImages()
    }
    
    /**
     * Sekme aktivasyon durumunu kontrol et ve optimizasyon uygula
     */
    fun onTabActivationChanged(webView: WebView, tab: Tab, isActive: Boolean) {
        val tabId = tab.id
        val optimizer = webViewOptimizers[tabId] ?: TKFWebViewOptimizer.getInstance(webView)
        
        // Sekme aktiflik durumuna göre optimizasyon
        optimizer.optimizeForTabSwitch(webView, tab, isActive)
        
        if (isActive) {
            // Sekme aktifleştirildiğinde oturum kontrolü
            val sessionManager = sessionManagers[tabId]
            sessionManager?.startSessionMonitoring()
            
            // Görüntü optimizasyonu
            val imageOptimizer = imageOptimizers[tabId]
            imageOptimizer?.optimizeImages()
            
            Timber.d("Tab $tabId activated and optimized")
        } else {
            // Sekme arka plana alındığında bellek tasarrufu
            if (performanceManager.isInLowMemoryMode()) {
                val imageOptimizer = imageOptimizers[tabId]
                imageOptimizer?.applyLowMemoryImageOptimization()
            }
            
            Timber.d("Tab $tabId deactivated and hibernated")
        }
    }
    
    /**
     * Bellek optimizasyonu yap - düşük bellek durumlarında çağrılır
     */
    fun performMemoryOptimization() {
        // Uygulama belleğini optimize et
        performanceManager.optimizeMemory()
        
        // Her sekme için ayrı bellek optimizasyonu
        webViewOptimizers.forEach { (tabId, optimizer) ->
            // WebView'ın kendisini bul
            val webView = getWebViewForTab(tabId) ?: return@forEach
            
            // Görsel optimizasyon
            val imageOptimizer = imageOptimizers[tabId]
            imageOptimizer?.applyLowMemoryImageOptimization()
            
            Timber.d("Memory optimization applied for tab $tabId")
        }
        
        // Ekstra GC tetikle
        System.gc()
    }
    
    /**
     * Tabid'ye karşılık gelen WebView'ı döndür (MainActivity'den enjekte edilmeli)
     */
    private var webViewProvider: ((String) -> WebView?)? = null
    
    fun setWebViewProvider(provider: (String) -> WebView?) {
        webViewProvider = provider
    }
    
    private fun getWebViewForTab(tabId: String): WebView? {
        return webViewProvider?.invoke(tabId)
    }
    
    /**
     * Sekme temizliği - sekme kapatıldığında kaynak temizliği
     * Geliştirilmiş versiyon - bellek sızıntılarını önler ve WebView havuzuna iade eder
     */
    fun cleanupTab(tabId: String) {
        // Optimize edici referansları temizle
        webViewOptimizers.remove(tabId)
        formManagers.remove(tabId)
        sessionManagers.remove(tabId)
        imageOptimizers.remove(tabId)
        
        // WebView havuzuna iade et
        webViewPool.releaseWebView(tabId)
        
        // Potansiyel bellek sızıntılarını kontrol et ve raporla
        reportMemoryLeaks()
        
        Timber.d("Tab $tabId resources cleaned up")
    }
    
    /**
     * Bellek sızıntılarını kontrol et
     */
    private fun reportMemoryLeaks() {
        // Uygulama için LeakCanary gibi araçlar kullanılmalı
        // Burada basit bir bellek kullanım kontrolü yapabiliriz
        val runtime = Runtime.getRuntime()
        val usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L
        val maxHeapSizeInMB = runtime.maxMemory() / 1048576L
        val availHeapSizeInMB = maxHeapSizeInMB - usedMemInMB
        
        Timber.d("Memory usage: $usedMemInMB MB used, $availHeapSizeInMB MB available of $maxHeapSizeInMB MB")
        
        // Bellek kullanımı kritik seviyede mi?
        if (availHeapSizeInMB < maxHeapSizeInMB * 0.2) { // %20'den az boş bellek kaldığında
            Timber.w("Critical memory usage detected! Triggering emergency cleanup")
            System.gc()
            performanceManager.optimizeMemory()
        }
    }
    
    /**
     * Performans istatistiklerini al
     */
    fun getPerformanceStats(): Map<String, Any> {
        val stats = mutableMapOf<String, Any>()
        
        // Performans metrikleri
        stats["metrics"] = performanceManager.getPerformanceStats()
        
        // En yavaş sayfalar
        stats["slowest_pages"] = performanceManager.getSlowestPages()
        
        // Bellek durumu
        stats["low_memory_mode"] = performanceManager.isInLowMemoryMode()
        
        // Aktif sekme sayısı
        stats["active_tabs"] = webViewOptimizers.size
        
        return stats
    }
}