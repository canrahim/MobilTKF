package com.asforce.asforcetkf2.webview

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.view.View
import java.util.concurrent.ConcurrentHashMap
import java.util.Queue
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicInteger
import timber.log.Timber

/**
 * WebView nesneleri için havuz yönetimi - ültre optimize edilmiş versiyon 2.0
 * Bellek kullanımını optimize etmek için WebView'leri yeniden kullanır,
 * gereksiz WebView oluşturmayı önler ve adaptif havuz boyutlama sağlar.
 * Thread-safe implementasyon ile bellek sızıntılarını önler.
 */
class WebViewPool(private val context: Context) {
    
    // Thread-safe veri yapıları kullan
    private val idleWebViews: Queue<WebView> = LinkedList<WebView>()
    private val activeWebViews = ConcurrentHashMap<String, WebView>()
    private val webViewReferences = ConcurrentHashMap<String, WebView>()
    
    // CPU ve bellek durumuna göre adaptif havuz boyutlama
    private var maxPoolSize = calculateOptimalPoolSize() 
    
    // WebView oluşturma ve yeniden kullanım istatistikleri
    private val creationCount = AtomicInteger(0)
    private val reuseCount = AtomicInteger(0)
    private val destroyCount = AtomicInteger(0)
    
    // Performans için ek veriler
    private var lastMemoryTrimTime = 0L
    private val MEMORY_TRIM_INTERVAL = 60000L // 1 dakika
    
    /**
     * Belirtilen sekme ID'si için WebView al - Thread-safe ve geliştirilmiş versiyon
     * Havuzdan akıllı bir şekilde WebView seçer veya yeni oluşturur
     */
    @Synchronized
    fun getWebView(tabId: String): WebView {
        // İstatistik için zamanı kaydet
        val startTime = System.currentTimeMillis()
        
        try {
            // 1. Önce zaten aktif olanı kontrol et
            activeWebViews[tabId]?.let { existingView ->
                Timber.d("Returning existing active WebView for tab $tabId")
                return existingView
            }
            
            // 2. Referansları kontrol et - düzenli olarak temizlenmemiş
            webViewReferences[tabId]?.let { referencedView ->
                Timber.d("Reusing referenced WebView for tab $tabId")
                activeWebViews[tabId] = referencedView
                return referencedView
            }
            
            // 3. Atıl WebView havuzundan al
            synchronized(idleWebViews) {
                if (idleWebViews.isNotEmpty()) {
                    val webView = idleWebViews.poll()
                    if (webView != null) {
                        Timber.d("Reusing WebView from pool for tab $tabId")
                        
                        // WebView'ı yeniden kullanım için hazırla
                        prepareWebViewForReuse(webView)
                        
                        // Aktif havuzlara ekle ve referans tut
                        activeWebViews[tabId] = webView
                        webViewReferences[tabId] = webView
                        
                        // İstatistik güncelle
                        reuseCount.incrementAndGet()
                        
                        return webView
                    }
                }
            }
            
            // 4. Havuzda uygun WebView yoksa yeni oluştur
            Timber.d("Creating new WebView for tab $tabId")
            val webView = createOptimizedWebView()
            
            // Her iki havuza da ekle
            activeWebViews[tabId] = webView
            webViewReferences[tabId] = webView
            
            // İstatistik güncelle
            creationCount.incrementAndGet()
            
            // Bellek trim kontrolü - 1 dakikada bir yap
            checkAndTrimMemory()
            
            return webView
        } finally {
            // Performans logging
            val duration = System.currentTimeMillis() - startTime
            if (duration > 50) { // 50ms üzerindeki süreleri log'la
                Timber.d("WebView acquisition for tab $tabId took $duration ms")
            }
        }
    }
    
    /**
     * WebView havuzuna önceden WebView'lar yükle - optimizasyon için
     * İlk açılışta yapılırsa performans artışı sağlar
     */
    @Synchronized
    fun preloadWebViews(count: Int) {
        val targetCount = Math.min(count, maxPoolSize)
        val currentCount = idleWebViews.size
        
        if (currentCount >= targetCount) return
        
        Timber.d("Preloading ${targetCount - currentCount} WebViews into pool")
        
        try {
            for (i in 0 until (targetCount - currentCount)) {
                val webView = createOptimizedWebView(true) // lightweight mode
                synchronized(idleWebViews) {
                    idleWebViews.add(webView)
                }
                creationCount.incrementAndGet()
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during WebView preloading")
        }
    }
    
    /**
     * Havuzu belirtilen boyuta küçült
     * Düşük bellek durumlarında çağrılır
     */
    @Synchronized
    fun trimToSize(size: Int) {
        synchronized(idleWebViews) {
            while (idleWebViews.size > size) {
                val webView = idleWebViews.poll()
                webView?.let {
                    try {
                        it.destroy()
                        destroyCount.incrementAndGet()
                    } catch (e: Exception) {
                        Timber.e(e, "Error destroying WebView during trim")
                    }
                }
            }
        }
        
        // Maksimum havuz boyutunu güncelle
        if (size < maxPoolSize) {
            maxPoolSize = size
            Timber.d("WebView pool size reduced to $maxPoolSize")
        }
        
        // GC öner
        System.gc()
    }
    
    /**
     * Bellek trim işlemini kontrol et ve gerekirse yap
     */
    private fun checkAndTrimMemory() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastMemoryTrimTime > MEMORY_TRIM_INTERVAL) {
            lastMemoryTrimTime = currentTime
            
            // Bellek trim fonksiyonunu çağır
            performMemoryTrim()
        }
    }
    
    /**
     * Düşük bellek durumlarında çağrılan açil bellek temizliği
     */
    fun emergencyCleanup() {
        Timber.w("Performing emergency WebView pool cleanup")
        
        // Tüm boşta WebView'ları temizle
        synchronized(idleWebViews) {
            for (webView in idleWebViews) {
                try {
                    webView.destroy()
                    destroyCount.incrementAndGet()
                } catch (e: Exception) {
                    Timber.e("Failed to destroy WebView in emergency cleanup: ${e.message}")
                }
            }
            idleWebViews.clear()
        }
        
        // Maksimum havuz boyutunu düşür
        maxPoolSize = Math.max(1, maxPoolSize / 2)
        Timber.d("Reduced max pool size to $maxPoolSize due to memory pressure")
        
        // Depolama ve önbellekleri temizle
        try {
            WebStorage.getInstance().deleteAllData()
            // WebView önbelleğini temizle - API'ye uygun şekilde
            WebView(context).clearCache(true)
        } catch (e: Exception) {
            Timber.e("Failed to clear WebView caches: ${e.message}")
        }
        
        // GC'yi zorunlu olarak çağır
        System.gc()
    }
    
    /**
     * Belirtilen sekme ID'si için WebView al
     * Önce aktif sekmelerden, sonra atıl havuzundan, yoksa yeni oluştur
     */
    private fun performMemoryTrim() {
        // Kullanılmayan WebView referanslarını temizle
        val activeIds = activeWebViews.keys.toSet()
        val idsToRemove = mutableListOf<String>()
        
        webViewReferences.keys.forEach { tabId ->
            if (!activeIds.contains(tabId)) {
                idsToRemove.add(tabId)
            }
        }
        
        // Gereksiz referansları kaldır
        idsToRemove.forEach { tabId ->
            webViewReferences.remove(tabId)
        }
        
        if (idsToRemove.isNotEmpty()) {
            Timber.d("Cleaned up ${idsToRemove.size} unused WebView references")
        }
        
        // Atıl havuz boyutunu kontrol et ve gerekirse küçült
        val currentPoolSize = idleWebViews.size
        val optimalSize = calculateOptimalPoolSize()
        
        if (currentPoolSize > optimalSize) {
            trimToSize(optimalSize)
        }
    }
    
    /**
     * Yeniden kullanım için WebView temizliği - Geliştirilmiş versiyon
     * Daha derin temizlik ve optimizasyon sağlar
     */
    private fun prepareWebViewForReuse(webView: WebView) {
        try {
            // 1. Önce JavaScript ile DOM ve bellek temizliği
            webView.evaluateJavascript("""
                (function() {
                    try {
                        // Tüm zamanlayıcıları temizle
                        var highestTimeoutId = setTimeout(function(){}, 0);
                        for (var i = 0; i < highestTimeoutId; i++) {
                            clearTimeout(i);
                            clearInterval(i);
                        }
                        
                        // Tüm global değişkenleri temizle
                        for (var prop in window) {
                            if (window.hasOwnProperty(prop) && prop.indexOf('webkit') === -1 
                                && typeof window[prop] !== 'function') {
                                try {
                                    delete window[prop];
                                } catch(e) {}
                            }
                        }
                        
                        // LocalStorage ve SessionStorage temizleme
                        if (window.localStorage) {
                            try { localStorage.clear(); } catch(e) {}
                        }
                        if (window.sessionStorage) {
                            try { sessionStorage.clear(); } catch(e) {}
                        }
                        
                        // Document içeriğini temizle
                        if (document.body) document.body.innerHTML = '';
                        
                        return "CLEANUP_COMPLETE";
                    } catch(e) {
                        return "CLEANUP_ERROR: " + e.message;
                    }
                })();
            """.trimIndent()) { result ->
                // JavaScript temizlik sonucu
                if (result != "\"CLEANUP_COMPLETE\"") {
                    Timber.d("WebView JS cleanup result: $result")
                }
            }
            
            // 2. WebView'ı temizle - temel işlemler
            webView.stopLoading()
            webView.clearHistory()
            webView.clearCache(true)
            webView.clearFormData()
            webView.clearSslPreferences()
            webView.clearDisappearingChildren()
            
            // 3. Ayarları varsayılana döndür - performans odaklı
            webView.settings.apply {
                javaScriptEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                blockNetworkImage = true // Önce sayfa yüklensin, sonra görseller
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                mediaPlaybackRequiresUserGesture = true
                allowContentAccess = true
                allowFileAccess = false  // Güvenlik için false
            }
            
            // 4. Görünürlük ve katman optimizasyonu
            webView.visibility = View.INVISIBLE // İlk yükleme sırasında görünmez
            webView.setBackgroundColor(Color.TRANSPARENT) // Saydam arka plan
            webView.alpha = 0.99f // Alpha restart trick
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null) // Hızlı render
            
            // 5. Boş sayfa yükle
            webView.loadUrl("about:blank")
            
            // 6. Bir süre sonra görünür yap
            webView.postDelayed({
                webView.visibility = View.VISIBLE
            }, 100)
            
        } catch (e: Exception) {
            Timber.e(e, "Error preparing WebView for reuse")
        }
    }
    
    /**
     * Sekme kapandığında WebView'ı havuza iade et - Geliştirilmiş versiyon
     * @param delayedCleanup Eğer true ise, temizleme işlemi biraz geciktirilir
     */
    @Synchronized
    fun releaseWebView(tabId: String, delayedCleanup: Boolean = false) {
        val webView = activeWebViews.remove(tabId)
        if (webView == null) {
            Timber.d("No active WebView found for tab $tabId")
            return
        }
        
        // Asenkron temizlik - UI thread'ini bloklamamak için
        if (delayedCleanup) {
            webView.post {
                performReleaseCleanup(tabId, webView)
            }
        } else {
            performReleaseCleanup(tabId, webView)
        }
    }
    
    /**
     * WebView temizleme işlemlerini gerçekleştir
     */
    private fun performReleaseCleanup(tabId: String, webView: WebView) {
        try {
            // WebView'ı temizle
            webView.stopLoading()
            
            // DOM ve JavaScript temizliği
            webView.evaluateJavascript("""
                (function() {
                    try {
                        // Kill all timers
                        var highestId = setTimeout(() => {}, 0);
                        for (let i = 0; i < highestId; i++) {
                            clearTimeout(i); 
                            clearInterval(i);
                        }
                        
                        // Clear memory intensive resources
                        if (document.body) document.body.innerHTML = '';
                        
                        // Clear storages
                        try { localStorage.clear(); } catch(e) {}
                        try { sessionStorage.clear(); } catch(e) {}
                        
                        return "RELEASE_CLEANUP_COMPLETE";
                    } catch(e) {
                        return "RELEASE_CLEANUP_ERROR: " + e.message;
                    }
                })();
            """.trimIndent()) { result ->
                Timber.d("Tab $tabId JS cleanup: $result")
                
                // Boş sayfa yükle
                webView.loadUrl("about:blank")
                
                // Referans havuzundan kaldır
                webViewReferences.remove(tabId)
                
                // Havuza ekle veya yok et
                synchronized(idleWebViews) {
                    if (idleWebViews.size < maxPoolSize) {
                        Timber.d("Returning WebView to pool for tab $tabId")
                        idleWebViews.add(webView)
                    } else {
                        Timber.d("Destroying WebView for tab $tabId (pool full)")
                        webView.destroy()
                        destroyCount.incrementAndGet()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during WebView release cleanup")
            try {
                webView.destroy()
                destroyCount.incrementAndGet()
            } catch (e2: Exception) {
                Timber.e(e2, "Failed to destroy WebView during error recovery")
            }
        }
    }
    
    /**
     * Ultra optimize edilmiş WebView oluştur
     * @param lightweight Daha hafif bir WebView oluşturmak için true
     */
    private fun createOptimizedWebView(lightweight: Boolean = false): WebView {
        val startTime = System.currentTimeMillis()
        
        Timber.d("Creating ${if (lightweight) "lightweight" else "standard"} WebView")
        
        // Bellek durumunu kontrol et ve buna göre optimizasyonu ayarla
        val runtime = Runtime.getRuntime()
        val usedMemoryPercent = (runtime.totalMemory() - runtime.freeMemory()) * 100 / runtime.maxMemory()
        val isLowMemory = usedMemoryPercent > 70 // %70 üzerinde kullanım olduğunda düşük bellek modu
        
        // Eğer bellek az ise, lightweight mod zorunlu olmalı
        val forceLightweight = isLowMemory || lightweight
        
        // Çok düşük bellek durumunda temizlik yap
        if (usedMemoryPercent > 85) {
            Timber.w("Memory pressure detected (${usedMemoryPercent.toInt()}%), cleaning WebView caches")
            try {
                WebStorage.getInstance().deleteAllData()
                // WebView önbelleğini temizle - API'ye uygun şekilde
                WebView(context).clearCache(true)
            } catch (e: Exception) {
                Timber.e("Failed to clear WebView caches: ${e.message}")
            }
            // GC tetikle
            System.gc()
        }
        
        return WebView(context).apply {
            // WebView optimize edici ayarlar - Çok düşük bellek kullanımı için
            setLayerType(if (forceLightweight) View.LAYER_TYPE_SOFTWARE else View.LAYER_TYPE_HARDWARE, null)
            setBackgroundColor(Color.TRANSPARENT) // Saydam arka plan, daha az bellek
            isVerticalScrollBarEnabled = false // Kaydırma çubuklarını devre dışı bırak
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER // Fazla kaydırma efektini kapat
            
            // Boyut ve görünürlük optimizasyonu
            minimumHeight = 1
            minimumWidth = 1
            alpha = 0.99f // Alpha optimizasyonu - rendering motorunu yeniler
            
            // Çizim devre dışı - sadece gerektiğinde çizim yap
            setWillNotDraw(forceLightweight) // Hafif modda çizim işlemlerini atla
            
            // Önce boş sayfa yükle - daha hızlı başlangıç
            loadUrl("about:blank")
            
            settings.apply {
                // 1. Temel performans ayarları - bellek durumuna göre adapte et
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                cacheMode = when {
                    usedMemoryPercent > 80 -> WebSettings.LOAD_CACHE_ONLY // Çok düşük bellek
                    forceLightweight -> WebSettings.LOAD_CACHE_ELSE_NETWORK // Düşük bellek
                    else -> WebSettings.LOAD_DEFAULT // Normal bellek
                }
                
                // JavaScript'i önce devre dışı bırak, sadece gerektiğinde etkinleştir
                javaScriptEnabled = !forceLightweight
                
                // 2. Görüntü optimizasyonu - bellek durumuna göre
                blockNetworkImage = true // Önce sayfa yüklensin, sonra görseller
                loadsImagesAutomatically = !forceLightweight // Hafif modda görselleri yükleme
                useWideViewPort = !forceLightweight // Hafif modda geniş viewport yok
                loadWithOverviewMode = !forceLightweight // Hafif modda overview mod yok
                
                // 3. Bellek optimizasyonu - düşük bellek durumunda agresif
                domStorageEnabled = !forceLightweight // Çok düşük bellekte DOM storage yok
                databaseEnabled = !forceLightweight // Çok düşük bellekte database yok
                // AppCache artık kullanımdan kaldırıldı
                setGeolocationEnabled(false) // Konum devre dışı
                
                // 4. Güvenlik ayarları
                allowFileAccess = false
                allowFileAccessFromFileURLs = false
                allowUniversalAccessFromFileURLs = false
                allowContentAccess = !forceLightweight
                
                // 5. Ek optimizasyonlar - minimal özellikler
                mediaPlaybackRequiresUserGesture = true
                saveFormData = false
                savePassword = false
                
                // 6. Kullanıcı deneyimi - minimal
                textZoom = 100
                setSupportZoom(!forceLightweight)
                builtInZoomControls = !forceLightweight
                displayZoomControls = false
                
                // 7. Http/Https güvenliği
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                
                // 8. User Agent - daha kısa
                if (!forceLightweight) {
                    userAgentString = userAgentString.replace("; wv", "") + " AsforceTKF2/2.0"
                }
                
                // 9. Düşük bellek modunda ek optimizasyonlar
                if (forceLightweight && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        setForceDark(WebSettings.FORCE_DARK_OFF)
                    } catch (e: Exception) {
                        Timber.e("Error setting force dark: ${e.message}")
                    }
                }
            }
            
            // Boş sayfa yükle
            loadUrl("about:blank")
            
            // Oluşturma sonrası optimizasyon - bellek durumuna göre geciktirme
            if (!forceLightweight) {
                // 100ms gecikme ile hardware hızlandırma etkinleştir
                postDelayed({
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    
                    // JavaScript etkinleştir
                    settings.javaScriptEnabled = true
                    
                    // Boş DOM oluştur - daha iyi performans
                    evaluateJavascript("""
                        (function() {
                            document.open();
                            document.write('<html><head><meta name="viewport" content="width=device-width, initial-scale=1.0"></head><body></body></html>');
                            document.close();
                            return "DOM_INITIALIZED";
                        })();
                    """.trimIndent(), null)
                }, 100)
            }
        }.also {
            // Oluşturma süresini ölç ve logla
            val duration = System.currentTimeMillis() - startTime
            Timber.d("WebView creation took $duration ms (mode: ${if (forceLightweight) "lightweight" else "standard"})")
        }
    }
    
    /**
     * En uygun havuz boyutunu hesapla
     * Cihazın bellek durumuna göre bir havuz boyutu belirler
     */
    private fun calculateOptimalPoolSize(): Int {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024) // MB cinsinden
        
        // Adaptif havuz boyutu
        return when {
            maxMemory >= 512 -> 4 // 512MB üzerinde 4 WebView
            maxMemory >= 256 -> 3 // 256MB - 512MB arası 3 WebView
            maxMemory >= 128 -> 2 // 128MB - 256MB arası 2 WebView
            else -> 1 // Düşük bellek durumunda sadece 1 WebView
        }
    }
    
    /**
     * Havuzdaki tüm WebView'ları temizle
     * Uygulama kapatılırken veya düşük bellek durumlarında çağrılmalı
     */
    @Synchronized
    fun clearPool() {
        Timber.d("Clearing WebView pool")
        
        // Tüm atıl WebView'ları temizle
        synchronized(idleWebViews) {
            var count = 0
            while (idleWebViews.isNotEmpty()) {
                val webView = idleWebViews.poll()
                try {
                    webView?.destroy()
                    count++
                    destroyCount.incrementAndGet()
                } catch (e: Exception) {
                    Timber.e(e, "Error destroying WebView during pool clear")
                }
            }
            Timber.d("Destroyed $count idle WebViews")
        }
        
        // Referansları temizle
        webViewReferences.clear()
        
        // İstatistikleri güncelle
        val stats = getPoolStats()
        Timber.d("WebView pool stats after clear: $stats")
        
        // WebStorage temizle
        try {
            WebStorage.getInstance().deleteAllData()
        } catch (e: Exception) {
            Timber.e(e, "Error clearing WebStorage")
        }
        
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
        synchronized(idleWebViews) {
            return idleWebViews.size
        }
    }
    
    /**
     * Havuz istatistiklerini al
     */
    @Synchronized
    fun getPoolStats(): Map<String, Any> {
        return mapOf(
            "active_count" to activeWebViews.size,
            "idle_count" to idleWebViews.size,
            "reference_count" to webViewReferences.size,
            "max_pool_size" to maxPoolSize,
            "created" to creationCount.get(),
            "reused" to reuseCount.get(),
            "destroyed" to destroyCount.get(),
            "memory_mb" to (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024)
        )
    }
}
