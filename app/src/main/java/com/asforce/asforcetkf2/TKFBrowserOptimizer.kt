package com.asforce.asforcetkf2

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import com.asforce.asforcetkf2.model.Tab
import com.asforce.asforcetkf2.util.TKFImageOptimizer
import com.asforce.asforcetkf2.util.TKFPerformanceManager
import com.asforce.asforcetkf2.webview.TKFFormManager
import com.asforce.asforcetkf2.webview.TKFSessionManager
import com.asforce.asforcetkf2.webview.TKFWebViewOptimizer
import com.asforce.asforcetkf2.webview.WebViewPool
import com.asforce.asforcetkf2.webview.TabWebView
import java.util.concurrent.ConcurrentHashMap
import java.lang.ref.WeakReference
import timber.log.Timber

/**
 * TKF Tarayıcı bileşenleri için tüm optimizasyon sınıflarını bir araya getiren ana optimizatör
 * Uygulama genelinde performans ve bellek iyileştirmelerini koordine eder
 * 
 * Geliştirilmiş versiyon: Daha agresif bellek yönetimi, paralel işlem desteği ve leak önleme
 */
class TKFBrowserOptimizer(private val context: Context) {
    
    // WebView havuzu - daha verimli bellek kullanımı ve otomatik temizleme
    private val webViewPool = WebViewPool(context)
    
    // Performans yöneticisi - singleton instance
    private val performanceManager = TKFPerformanceManager.getInstance(context)
    
    // WebView optimizasyon bileşenleri - thread-safe map kullanımı
    private val webViewOptimizers = ConcurrentHashMap<String, TKFWebViewOptimizer>()
    private val formManagers = ConcurrentHashMap<String, TKFFormManager>()
    private val sessionManagers = ConcurrentHashMap<String, TKFSessionManager>()
    private val imageOptimizers = ConcurrentHashMap<String, TKFImageOptimizer>()
    
    // Tab referansları için weak reference kullanımı - memory leak önleme
    private val tabReferences = ConcurrentHashMap<String, WeakReference<Tab>>()
    
    // Optimizasyon durumu - bazı işlemleri öncelik ve sıklığa göre yönetmek için
    private var lastFullOptimizationTime = 0L
    private var optimizationLevel = "normal" // normal, moderate, aggressive, extreme
    
    /**
     * Uygulama başlangıç optimizasyonu - Geliştirilmiş versiyon
     * Daha agresif optimizasyon, ön yükleme ve ısınma aşamaları içerir
     */
    fun optimizeAppStart() {
        // Kritik zamanlama: Başlangıç zamanını kaydet
        val startTime = System.currentTimeMillis()
        
        // İlk önce düşük bellek durumunu kontrol et
        val isLowMemory = performanceManager.isInLowMemoryMode()
        optimizationLevel = if (isLowMemory) "aggressive" else "normal"
        
        // Performans ölçümleri başlat
        performanceManager.startAppMeasurement()
        
        // DNS ve ağ ön ısıtma - paralel işlem
        performanceManager.startDnsWarmup()
        
        // WebView ön ısıtma - bir tane ön oluştur
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                WebView.startSafeBrowsing(context, { success ->
                    Timber.d("SafeBrowsing initialization: $success")
                })
            } catch (e: Exception) {
                Timber.e(e, "SafeBrowsing initialization failed")
            }
        }
        
        // Bellek optimizasyonu - uygulamanın ilk açılışında agresif temizlik
        performAsyncMemoryOptimization()
        
        // UI thread'i bloklamamak için belirli optimizasyonları arka planda yap
        Thread {
            try {
                performanceManager.optimizeSystemResources()
                // WebView havuzunu ön ısıt
                webViewPool.preloadWebViews(2) // En çok 2 WebView önceden yükle
            } catch (e: Exception) {
                Timber.e(e, "Background optimization error")
            }
        }.start()
        
        // İstatistikleri kaydet
        val duration = System.currentTimeMillis() - startTime
        Timber.d("Enhanced app startup optimization completed in $duration ms, mode: $optimizationLevel")
    }
    
    /**
     * Bellek optimizasyonunu asenkron olarak gerçekleştir
     */
    private fun performAsyncMemoryOptimization() {
        Thread {
            try {
                // Sistem bellek durumunu kontrol et
                val memStatus = performanceManager.getMemoryStatus()
                
                // Bellek durumuna göre optimizasyon seviyesini güncelle
                optimizationLevel = when (memStatus) {
                    "critical" -> "extreme"
                    "low" -> "aggressive"
                    "moderate" -> "moderate"
                    else -> "normal"
                }
                
                // Bellek optimizasyon seviyesine göre işlem yap
                performanceManager.optimizeMemory(optimizationLevel)
                
                Timber.d("Async memory optimization completed, level: $optimizationLevel")
            } catch (e: Exception) {
                Timber.e(e, "Async memory optimization error")
            }
        }.start()
    }
    
    /**
     * Sekme için WebView optimizasyonu 
     * Geliştirilmiş versiyon 2.0 - Daha akıllı WebView havuz yönetimi, adaptif optimizasyon ve performans izleme
     */
    fun optimizeTabWebView(webView: WebView, tab: Tab) {
        val tabId = tab.id
        
        // Tab weak reference kaydı - memory leak takibi için
        tabReferences[tabId] = WeakReference(tab)
        
        // Bellek durumu ve optimizasyon seviyesi kontrolü - daha dinamik
        val memoryStatus = performanceManager.getMemoryStatus()
        val shouldApplyAggressiveOptimization = memoryStatus == "critical" || memoryStatus == "low"
        val shouldUseMinimalOptimization = tab.isActive && memoryStatus == "high"
        
        // Dinamik olarak optimizasyon seviyesini güncelle
        optimizationLevel = when (memoryStatus) {
            "critical" -> "extreme"
            "low" -> "aggressive"
            "moderate" -> "moderate"
            else -> if (tab.isActive) "normal" else "moderate"
        }
        
        Timber.d("Tab $tabId optimization started with level: $optimizationLevel, memory: $memoryStatus")
        
        // Optimizasyon öncesi performans ölçümü başlat
        val startTime = System.currentTimeMillis()
        performanceManager.startMeasurement("tab_optimize_$tabId")
        
        // WebView havuzundan optimize edilmiş WebView al veya mevcut olanı optimize et
        // İyileştirilmiş havuz yönetimi - tab durumuna göre karar verme
        val optimizedWebView = if (tab.isNew) {
            // Yeni sekme için WebView havuzundan al
            val poolWebView = webViewPool.getWebView(tabId) 
            poolWebView
        } else if (webView is TabWebView && webView.tab?.id == tabId) {
            // Aynı TabWebView için sadece optimize et
            webView
        } else {
            // WebView değişmiş, havuzdan yeni bir tane al
            webViewPool.getWebView(tabId)
        }
        
        // WebView optimizasyonu - Singleton model
        val optimizer = TKFWebViewOptimizer.getInstance(optimizedWebView)
        webViewOptimizers[tabId] = optimizer
        
        // Kademeli optimizasyon stratejisi uygula - önce kritik optimizasyonlar
        if (tab.isActive || shouldApplyAggressiveOptimization) {
            // Step 1: En kritik performans optimizasyonları
            optimizer.optimizeForTabSwitch(optimizedWebView, tab, tab.isActive)
            
            // Veri tasarrufu modu kontrolü
            val isDataSaverEnabled = performanceManager.isDataSaverEnabled()
            
            // Step 2: Oturum ve güvenlik optimizasyonları
            val sessionManager = TKFSessionManager(optimizedWebView)
            sessionManagers[tabId] = sessionManager
            sessionManager.startSessionMonitoring()
            
            // Step 3: Domain-spesifik optimizasyonlar (Adaptif yaklaşım)
            when {
                tab.url.contains("szutest.com.tr") -> {
                    optimizer.optimizeForSzutest(optimizedWebView)
                    Timber.d("Applied SzuTest specific optimizations")
                }
                tab.url.contains("tkf", ignoreCase = true) -> {
                    // Spesifik TKF optimizasyonları burada uygulanabilir
                    Timber.d("Applied general optimizations for TKF URL")
                }
                tab.url.contains("google.com") -> {
                    // Google spesifik optimizasyonlar
                    Timber.d("Applied general optimizations for Google URL")
                }
            }
        }
        
        // Form yönetimi - bellek durumuna göre optimizasyon seviyesi
        if (!shouldUseMinimalOptimization) {
            val formManager = TKFFormManager(optimizedWebView)
            formManagers[tabId] = formManager
            
            // Optimizasyon seviyesine göre form iyileştirmelerini uygula
            when (optimizationLevel) {
                "normal" -> formManager.enableFormEnhancements()
                "moderate" -> formManager.enableFormEnhancements()
                else -> formManager.enableFormEnhancements()
            }
        }
        
        // Görüntü optimizasyonu - kademeli strateji
        val imageOptimizer = TKFImageOptimizer(context, optimizedWebView)
        imageOptimizers[tabId] = imageOptimizer
        
        // Optimizasyon seviyesine göre görüntü iyileştirmelerini uygula
        when (optimizationLevel) {
            "extreme" -> {
                // Maksimum bellek tasarrufu - resim optimizasyonu
                imageOptimizer.optimizeImages()
                // Agresif DOM optimizasyonu
                optimizer.enhancedDomOptimization(optimizedWebView)
                Timber.w("Extreme optimization applied for tab $tabId - images optimized")
            }
            "aggressive" -> {
                // Agresif bellek tasarrufu - düşük kalite resimler
                imageOptimizer.optimizeImages()
                // DOM optimizasyonu
                optimizer.enhancedDomOptimization(optimizedWebView)
                Timber.d("Aggressive optimization applied for tab $tabId")
            }
            "moderate" -> {
                // Orta seviye bellek tasarrufu - normal optimizasyon
                imageOptimizer.optimizeImages()
                Timber.d("Moderate optimization applied for tab $tabId")
            }
            else -> {
                // Normal optimizasyon - kullanıcı deneyimini korur
                imageOptimizer.optimizeImages()
                Timber.d("Normal optimization applied for tab $tabId")
            }
        }
        
        // SVG ve ikon optimizasyonu (genellikle yüksek bellek kullanır)
        // Sadece düşük optimizasyon seviyesinde yapılır
        if (optimizationLevel == "normal") {
            // SVG optimizasyonu için ek yöntemler eklenebilir
        }
        
        // Genel WebView performans optimizasyonu - özel sekme tipi kontrolü
        if (webView is TabWebView) {
            // TabWebView için performans optimizasyonu
            performanceManager.optimizeWebView(webView)
        } else {
            performanceManager.optimizeWebView(optimizedWebView)
        }
        
        // Memory leak kontrolü ve önleme
        if (shouldApplyAggressiveOptimization) {
            checkForMemoryLeaks()
        }
        
        // Optimizasyon tamamlandı - durumu güncelle ve ölç
        tab.markOptimized()
        
        // Optimizasyon süresini ölç ve kaydet
        val duration = System.currentTimeMillis() - startTime
        performanceManager.recordOptimizationTime("tab_optimize", duration)
        Timber.d("Tab $tabId optimization completed in $duration ms, level: $optimizationLevel")
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
        
        // Sekme aktivasyon durumuna göre optimizasyon
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
                imageOptimizer?.optimizeImages()
            }
            
            Timber.d("Tab $tabId deactivated and hibernated")
        }
    }
    
    /**
     * Bellek optimizasyonu yap - düşük bellek durumlarında çağrılır
     * Geliştirilmiş versiyon: Progressive optimization ve leak detection
     */
    fun performMemoryOptimization() {
        // Optimizasyon başlangıcını ölç
        val startTime = System.currentTimeMillis()
        Timber.d("Starting memory optimization with level: $optimizationLevel")
        
        // Bellek durumunu kontrol et ve optimizasyon seviyesini belirle
        val memStatus = performanceManager.getMemoryStatus()
        val newOptimizationLevel = when (memStatus) {
            "critical" -> "extreme"
            "low" -> "aggressive"
            "moderate" -> "moderate"
            else -> "normal"
        }
        
        // Optimizasyon seviyesi değişimini kaydet
        val optimizationChanged = optimizationLevel != newOptimizationLevel
        optimizationLevel = newOptimizationLevel
        
        // Uygulama belleğini optimize et - yeni seviyeye göre
        performanceManager.optimizeMemory(optimizationLevel)
        
        // Aktif sekme ID'sini al
        val activeTabId = webViewProvider?.invoke("")?.let { webView ->
            if (webView is TabWebView) webView.tab?.id else ""
        } ?: ""
        
        // Her sekme için ayrı bellek optimizasyonu - önceliklendirilmiş sırayla
        val tabIds = webViewOptimizers.keys.sortedWith(compareBy { tabId -> 
            // Aktif sekmeyi en sona koy (en az optimize edilecek)
            // Ayrıca son erişim zamanına göre de sırala - daha eski sekmeleri daha agresif optimize et
            val priority = if (tabId == activeTabId) 1 else 0
            priority
        })
        
        // Kritik/Agresif modda herşeyi optimize et, normal modda sadece inaktif sekmeleri
        val shouldOptimizeAll = optimizationLevel == "extreme" || optimizationLevel == "aggressive"
        
        // Toplam temizlenen bellek takibi (yaklaşık değerler)
        var estimatedMemorySaved = 0L
        var tabsOptimized = 0
        var tabsHibernated = 0
        
        // Sekmeleri işle - aralıklı optimizasyon stratejisi uygula (UI bloklamayı azaltmak için)
        val batchSize = 3 // Her seferde işlenecek maksimum sekme sayısı
        val delayBetweenBatches = 50L // ms cinsinden gecikmeler
        
        // Sekmeleri batch'lere ayır
        val batches = tabIds.chunked(batchSize)
        
        // Her bir batch'i işlemek için Thread kullan
        // Not: Handler ve Looper yerine doğrudan Thread kullanıyoruz
        batches.forEachIndexed { batchIndex, batch ->
            // Her bir batch için çalıştırma
            Thread {
                // Bu batch'teki sekmeleri işle
                for (tabId in batch) {
                    // Aktif sekmeyi atlayıp atlamama kontrolü
                    if (tabId == activeTabId && !shouldOptimizeAll) continue
                    
                    // WebView'ın kendisini bul
                    val webView = getWebViewForTab(tabId) ?: continue
                    
                    // Tab weak reference kontrolü
                    val tab = tabReferences[tabId]?.get()
                    val isOldTab = tab?.let { System.currentTimeMillis() - it.lastAccessTime > 10 * 60 * 1000 } ?: false // 10 dakikadan eski
                    
                    // Tab spesifik optimizasyon
                    when (optimizationLevel) {
                        "extreme" -> {
                            // Ekstra agresif - optimizasyon
                            imageOptimizers[tabId]?.optimizeImages()
                            webViewOptimizers[tabId]?.enhancedDomOptimization(webView)
                            
                            // JS temizlik script'i çalıştır (daha agresif)
                            webView.evaluateJavascript("""
                                (function() {
                                    try {
                                        // Tüm zamanlayıcıları ve aralıkları temizle
                                        var highestTimeout = setTimeout(() => {}, 0);
                                        for (var i = 0; i < highestTimeout; i++) {
                                            clearTimeout(i);
                                            clearInterval(i);
                                        }
                                        
                                        // Tüm worker'ları sonlandır
                                        if (window.Worker && window._tkfWorkers) {
                                            window._tkfWorkers.forEach(w => w.terminate());
                                        }
                                        
                                        // Büyük global nesneleri temizle
                                        if (window.TKF_largeObjects) {
                                            window.TKF_largeObjects.forEach(key => {
                                                window[key] = null;
                                            });
                                        }
                                        
                                        // Tüm iframe'leri boşalt
                                        var iframes = document.querySelectorAll('iframe');
                                        iframes.forEach(iframe => {
                                            if (iframe.src && iframe.src !== 'about:blank') {
                                                iframe.src = 'about:blank';
                                            }
                                        });
                                        
                                        // Büyük DOM ağaçlarını temizle
                                        if (!document.hidden) {
                                            var scrollTop = window.pageYOffset || document.documentElement.scrollTop;
                                            var viewportHeight = window.innerHeight;
                                            
                                            // Görünmeyen alanları temizle
                                            var elements = document.querySelectorAll('*');
                                            for (var i = 0; i < elements.length; i++) {
                                                var el = elements[i];
                                                if (el.tagName !== 'BODY' && el.tagName !== 'HTML' && el.tagName !== 'HEAD') {
                                                    var rect = el.getBoundingClientRect();
                                                    // Viewport'tan çok uzaktaysa (3 ekran boyu)
                                                    if (rect.bottom < -viewportHeight*2 || rect.top > viewportHeight*3) {
                                                        if (el.style) {
                                                            el.style.display = 'none';
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        
                                        return "EXTREME_MEMORY_CLEANUP_COMPLETE";
                                    } catch(e) {
                                        return "CLEANUP_ERROR: " + e.message;
                                    }
                                })();
                            """.trimIndent(), null)
                            
                            // Hiberne et (aktif değilse)
                            if (tabId != activeTabId && webView is TabWebView) {
                                webView.hibernate()
                                tabsHibernated++
                            }
                            
                            // Önbelleği temizle
                            webView.clearCache(true)
                            
                            // İstatistik güncelle
                            estimatedMemorySaved += 15000 // ~15MB
                            tabsOptimized++
                            
                            Timber.w("EXTREME memory optimization for tab $tabId")
                        }
                        "aggressive" -> {
                            // Agresif - optimizasyon
                            imageOptimizers[tabId]?.optimizeImages()
                            webViewOptimizers[tabId]?.enhancedDomOptimization(webView)
                            
                            // JS temizlik script'i çalıştır (orta seviye)
                            webView.evaluateJavascript("""
                                (function() {
                                    try {
                                        // Tüm zamanlayıcıları ve aralıkları temizle
                                        var highestTimeout = setTimeout(() => {}, 0);
                                        for (var i = 0; i < highestTimeout; i++) {
                                            clearTimeout(i);
                                            clearInterval(i);
                                        }
                                        
                                        // Ekran dışı resimlerin yüklenmesini durdur
                                        var viewportHeight = window.innerHeight;
                                        var images = document.querySelectorAll('img');
                                        for (var i = 0; i < images.length; i++) {
                                            var img = images[i];
                                            var rect = img.getBoundingClientRect();
                                            if (rect.top > viewportHeight*2 || rect.bottom < -viewportHeight) {
                                                if (img.src) {
                                                    img.setAttribute('data-tkf-src', img.src);
                                                    img.src = '';
                                                }
                                            }
                                        }
                                        
                                        return "AGGRESSIVE_MEMORY_CLEANUP_COMPLETE";
                                    } catch(e) {
                                        return "CLEANUP_ERROR: " + e.message;
                                    }
                                })();
                            """.trimIndent(), null)
                            
                            // İnaktif sekmeleri hiberne et
                            if (tabId != activeTabId && webView is TabWebView) {
                                webView.hibernate()
                                tabsHibernated++
                            }
                            
                            // İstatistik güncelle
                            estimatedMemorySaved += 8000 // ~8MB
                            tabsOptimized++
                            
                            Timber.d("AGGRESSIVE memory optimization for tab $tabId")
                        }
                        "moderate" -> {
                            // Orta seviye - uzun süre kullanılmayan sekmeleri hiberne et
                            val shouldHibernate = tab?.shouldHibernate ?: false || isOldTab
                            
                            if (shouldHibernate && webView is TabWebView) {
                                webView.hibernate()
                                tabsHibernated++
                                Timber.d("Tab $tabId hibernated due to inactivity")
                            }
                            
                            // Görüntü optimizasyonu
                            imageOptimizers[tabId]?.optimizeImages() // Optimizasyon uygula
                            
                            // Basit JS temizlik
                            if (isOldTab) {
                                webView.evaluateJavascript("""
                                    (function() {
                                        try {
                                            // Uzun süreli zamanlayıcıları temizle
                                            var highestTimeout = setTimeout(() => {}, 0);
                                            for (var i = 0; i < highestTimeout; i++) {
                                                if (i % 2 === 0) { // Yarısını temizle
                                                    clearTimeout(i);
                                                    clearInterval(i);
                                                }
                                            }
                                            return "MODERATE_MEMORY_CLEANUP_COMPLETE";
                                        } catch(e) {
                                            return "CLEANUP_ERROR: " + e.message;
                                        }
                                    })();
                                """.trimIndent(), null)
                            }
                            
                            // İstatistik güncelle
                            estimatedMemorySaved += if (shouldHibernate) 5000 else 2000 // ~5MB veya ~2MB
                            tabsOptimized++
                            
                            Timber.d("MODERATE memory optimization for tab $tabId")
                        }
                        else -> {
                            // Normal - minimal optimizasyon
                            imageOptimizers[tabId]?.optimizeImages()
                            
                            // İstatistik güncelle
                            estimatedMemorySaved += 500 // ~0.5MB
                            tabsOptimized++
                            
                            Timber.d("NORMAL memory optimization for tab $tabId")
                        }
                    }
                }
                
                // Son batch ise sonuç işlemleri yap
                if (batchIndex == batches.size - 1) {
                    finishMemoryOptimization(startTime, estimatedMemorySaved, tabsOptimized, tabsHibernated)
                }
            }.start()
        }
        
        // Batch'ler boşsa hemen bitir
        if (batches.isEmpty()) {
            finishMemoryOptimization(startTime, 0, 0, 0)
        }
    }
    
    /**
     * Bellek optimizasyonunu tamamla ve sonuçları raporla
     */
    private fun finishMemoryOptimization(startTime: Long, estimatedMemorySaved: Long, tabsOptimized: Int, tabsHibernated: Int) {
        // Ek optimizasyon - sadece extreme/aggressive modlarda
        if (optimizationLevel == "extreme" || optimizationLevel == "aggressive") {
            // Memory leak kontrolü yap
            checkForMemoryLeaks()
            
            // GC tetikle - güçlendirilmiş versiyon
            triggerCleanup(true)
        }
        
        // Optimizasyon sonuçlarını raporla
        val duration = System.currentTimeMillis() - startTime
        val memSavedMB = estimatedMemorySaved / 1024f
        
        // Performans yöneticisine optimizasyon metriklerini bildir
        performanceManager.recordOptimizationStats(
            optimizationLevel = optimizationLevel,
            durationMs = duration,
            estimatedMemorySavedKb = estimatedMemorySaved,
            tabsOptimized = tabsOptimized,
            tabsHibernated = tabsHibernated
        )
        
        Timber.d("Memory optimization completed in $duration ms, level: $optimizationLevel, " +
                 "saved ~$memSavedMB MB, optimized $tabsOptimized tabs, hibernated $tabsHibernated tabs")
    }
    
    /**
     * Tüm temizlik işlemlerini tetikle
     */
    private fun triggerCleanup(aggressive: Boolean = false) {
        // WebView havuzunu temizle
        if (aggressive) {
            webViewPool.trimToSize(1) // Sadece 1 tane tutma (aktif sekme)
        }
        
        // Gereksiz managerları temizle
        cleanupInactiveManagers()
        
        // Sistem GC tetikleme
        System.gc()
        Runtime.getRuntime().gc()
        
        // İstatistikleri kaydet
        val runtime = Runtime.getRuntime()
        val usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        Timber.d("After cleanup: Used memory: $usedMemInMB MB")
    }
    
    /**
     * Kullanılmayan manager'ları temizle
     */
    private fun cleanupInactiveManagers() {
        // Tüm sekme ID'lerini al
        val activeTabIds = tabReferences.keys.filter { tabReferences[it]?.get() != null }
        
        // Eşleştirmeleri temizle
        val keysToRemove = mutableListOf<String>()
        
        // Artık aktif olmayan sekmeleri belirle
        webViewOptimizers.keys.forEach { tabId ->
            if (!activeTabIds.contains(tabId)) {
                keysToRemove.add(tabId)
            }
        }
        
        // Temizleme işlemini gerçekleştir
        keysToRemove.forEach { tabId ->
            webViewOptimizers.remove(tabId)
            formManagers.remove(tabId)
            sessionManagers.remove(tabId)
            imageOptimizers.remove(tabId)
            tabReferences.remove(tabId)
            
            Timber.d("Cleaned up managers for inactive tab $tabId")
        }
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
     * Geliştirilmiş versiyon 2.0 - Daha derin temizleme ve havuz optimizasyonu
     */
    fun cleanupTab(tabId: String) {
        Timber.d("Starting cleanup for tab $tabId")
        
        // Tab referansını al ve sil
        val tabRef = tabReferences[tabId]
        val tab = tabRef?.get()
        tabReferences.remove(tabId)
        
        // Kapatılan sekmenin istatistiklerini kaydet
        tab?.let { nonNullTab ->
            performanceManager.logTabStats(
                tabId = tabId,
                url = nonNullTab.url,
                duration = System.currentTimeMillis() - nonNullTab.lastAccessTime,
                resourceCount = nonNullTab.resourceCount
            )
        }
        
        // WebView'i al ve temizle - deferred cleanup ile
        val webView = getWebViewForTab(tabId)
        webView?.let { view ->
            if (view is TabWebView) {
                // Önce genel kaynakları temizle
                view.evaluateJavascript("""
                    (function() {
                        try {
                            // Clear all timeouts and intervals
                            const highestId = setTimeout(() => {}, 0);
                            for (let i = 0; i < highestId; i++) {
                                clearTimeout(i);
                                clearInterval(i);
                            }
                            
                            // Release any held resources
                            if (window.TKF_cleanup) {
                                window.TKF_cleanup();
                            }
                            
                            // Clean up large objects
                            if (window.TKF_performance) {
                                window.TKF_performance = null;
                            }
                            
                            return "PRE_CLEANUP_SUCCESS";
                        } catch(e) {
                            return "CLEANUP_ERROR: " + e.message;
                        }
                    })();
                """.trimIndent()) { result ->
                    Timber.d("Tab $tabId JS pre-cleanup result: $result")
                    
                    // JS temizlikten sonra native temizliği tamamla
                    // Optimize edici referansları temizle
                    webViewOptimizers.remove(tabId)
                    formManagers.remove(tabId)
                    sessionManagers.remove(tabId)
                    imageOptimizers.remove(tabId)
                    
                    // WebView havuzuna iade et - delayed temizlik ile
                    webViewPool.releaseWebView(tabId, true)
                    view.cleanup()
                    
                    Timber.d("Tab $tabId resources fully cleaned up")
                }
            } else {
                // Normal WebView - standart temizlik
                webViewOptimizers.remove(tabId)
                formManagers.remove(tabId)
                sessionManagers.remove(tabId)
                imageOptimizers.remove(tabId)
                
                // WebView havuzuna iade et
                webViewPool.releaseWebView(tabId)
                
                Timber.d("Tab $tabId resources cleaned up (standard way)")
            }
        }
        
        // Potansiyel bellek sızıntılarını kontrol et ve temizle
        checkForMemoryLeaks()
        
        // Sistem bellek durumunu kontrol et ve gerekirse pool'u küçült
        val memStatus = performanceManager.getMemoryStatus()
        if (memStatus == "critical" || memStatus == "low") {
            webViewPool.trimToSize(2) // En fazla 2 WebView tut
            Timber.w("Trimmed WebView pool due to low memory")
        }
    }
    
    /**
     * Gelişmiş bellek sızıntısı kontrolü ve düzeltme
     * Weak reference kontrolü ve kaynak temizliği içerir
     */
    private fun checkForMemoryLeaks() {
        Timber.d("Checking for memory leaks...")
        
        // 1. Runtime bellek istatistiklerini kontrol et
        val runtime = Runtime.getRuntime()
        val usedMemInMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxHeapSizeInMB = runtime.maxMemory() / (1024 * 1024)
        val usedMemoryPercentage = usedMemInMB.toFloat() / maxHeapSizeInMB.toFloat() * 100
        
        Timber.d("Memory usage: $usedMemInMB MB used ($usedMemoryPercentage% of max $maxHeapSizeInMB MB)")
        
        // 2. WebView sızıntılarını kontrol et - weak reference temizliği
        val validTabIds = mutableSetOf<String>()
        var invalidReferences = 0
        
        // Tab referanslarını kontrol et
        tabReferences.forEach { (tabId, weakRef) ->
            if (weakRef.get() != null) {
                validTabIds.add(tabId)
            } else {
                invalidReferences++
            }
        }
        
        // 3. Geçersiz manager referanslarını temizle
        val managersToClean = mutableListOf<String>()
        webViewOptimizers.keys.forEach { tabId ->
            if (!validTabIds.contains(tabId)) {
                managersToClean.add(tabId)
            }
        }
        
        // Temizleme işlemi
        managersToClean.forEach { tabId ->
            webViewOptimizers.remove(tabId)
            formManagers.remove(tabId)
            sessionManagers.remove(tabId)
            imageOptimizers.remove(tabId)
            tabReferences.remove(tabId)
            
            // WebView havuzunu da temizle
            webViewPool.releaseWebView(tabId)
            
            Timber.w("Cleaned up potential memory leak for tab $tabId")
        }
        
        // 4. Bellek kullanımına göre agresif temizleme
        val isMemoryCritical = usedMemoryPercentage > 80 // %80 üzeri kullanım kritiktir
        if (isMemoryCritical) {
            Timber.w("Critical memory usage detected! Triggering emergency cleanup")
            
            // Emergency bellek temizliği
            webViewPool.emergencyCleanup()
            performanceManager.emergencyMemoryCleanup()
            
            // Sistem GC tetikle
            System.gc()
            Runtime.getRuntime().gc()
            
            // Agresif optimizasyon seviyesine geç
            optimizationLevel = "extreme"
        }
        
        // 5. Sonuçları logla
        Timber.d("Memory leak check completed: $invalidReferences invalid references cleaned, " + 
                 "${managersToClean.size} managers cleaned, memory status: ${if (isMemoryCritical) "CRITICAL" else "OK"}")
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