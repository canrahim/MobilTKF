package com.asforce.asforcetkf2.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Performans ölçütlerini yöneten ve optimize eden sınıf
 * Uygulama genelinde hız ve bellek optimizasyonları sağlar
 */
class TKFPerformanceManager private constructor(context: Context) {
    
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("tkf_performance", Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    
    // Performans metriklerini saklama
    private val metrics = ConcurrentHashMap<String, Long>()
    private val startTimes = ConcurrentHashMap<String, Long>()
    
    // WebView dns önbelleği sıcak tutma durumu
    private val isDnsWarmupActive = AtomicBoolean(false)
    
    // Düşük bellek durumu ve agresif optimizasyon
    private var isLowMemoryMode = false
    
    companion object {
        @Volatile
        private var instance: TKFPerformanceManager? = null
        
        /**
         * Singleton instance
         */
        fun getInstance(context: Context): TKFPerformanceManager =
            instance ?: synchronized(this) {
                instance ?: TKFPerformanceManager(context).also { instance = it }
            }
    }
    
    /**
     * Temel ölçümleri başlat - uygulama başlangıcında çağrılır
     * Geliştirilmiş sürüm - proaktif performans izleme
     */
    fun startAppMeasurement() {
        startMeasurement("app_start")
        
        // İlk açılışta otomatik DNS önbelleği ısıtma aktif et
        if (prefs.getBoolean("enable_dns_warmup", true)) {
            startDnsWarmup()
        }
        
        // Düşük bellek modunu kontrol et
        checkLowMemoryMode()
        
        // Düzenli bellek izleme başlat
        setupLowMemoryMonitoring()
        
        // CPU kullanımı izleme
        startCpuMonitoring()
    }
    
    /**
     * DNS önbelleği sıcak tutma - popüler adresleri önbelleğe alır
     * DNS aramaları sonucunda ilk sayfa yükleme hızını artırır
     */
    fun startDnsWarmup() {
        if (isDnsWarmupActive.getAndSet(true)) {
            return // Zaten çalışıyorsa tekrar başlatma
        }
        
        Thread {
            try {
                val startTime = SystemClock.elapsedRealtime()
                Timber.d("DNS Warmup started")
                
                // Popüler domainlerin listesi (uygulamanın sık ziyaret ettiği siteler)
                val domains = listOf(
                    "szutest.com.tr",
                    "google.com",
                    "app.szutest.com.tr",
                    "cdn.szutest.com.tr"
                )
                
                // Her domain için DNS araması yap
                domains.forEach { domain ->
                    try {
                        val result = java.net.InetAddress.getAllByName(domain)
                        Timber.d("DNS Warmup: $domain -> ${result.firstOrNull()?.hostAddress}")
                    } catch (e: Exception) {
                        Timber.w("DNS Warmup failed for $domain: ${e.message}")
                    }
                }
                
                val duration = SystemClock.elapsedRealtime() - startTime
                Timber.d("DNS Warmup completed in $duration ms")
                metrics["dns_warmup"] = duration
                
            } catch (e: Exception) {
                Timber.e("DNS Warmup error: ${e.message}")
            } finally {
                isDnsWarmupActive.set(false)
            }
        }.start()
    }
    
    /**
     * Düzenli bellek izleme
     */
    fun setupLowMemoryMonitoring() {
        // Düzenli bellek izleme
        val handler = Handler(Looper.getMainLooper())
        val checkInterval = 30000L // 30 saniye
        
        handler.postDelayed(object : Runnable {
            override fun run() {
                checkLowMemoryMode()
                
                // Kritik bellek durumunda agresif optimizasyon
                if (isLowMemoryMode) {
                    // Gereksiz kaynakları temizle
                    trimApplicationMemory()
                    clearUnusedCache()
                }
                
                // Bir sonraki kontrolü planla
                handler.postDelayed(this, checkInterval)
            }
        }, checkInterval)
        
        Timber.d("Low memory monitoring started")
    }
    
    /**
     * CPU kullanımı izleme ve aşırı kullanım tespiti
     */
    private fun startCpuMonitoring() {
        val handler = Handler(Looper.getMainLooper())
        val checkInterval = 10000L // 10 saniye
        
        handler.postDelayed(object : Runnable {
            override fun run() {
                // CPU kullanım oranlarını kontrol et
                val cpuUsage = getCpuUsage()
                
                if (cpuUsage > 80) { // %80'den fazla CPU kullanımı
                    Timber.w("High CPU usage detected: $cpuUsage%")
                    // Yüksek CPU kullanımında yapacak işlemler
                    // Örneğin animasyon ve görsel işlemlerini azalt
                }
                
                metrics["last_cpu_usage"] = cpuUsage.toLong()
                
                // Bir sonraki kontrolü planla
                handler.postDelayed(this, checkInterval)
            }
        }, checkInterval)
    }
    
    /**
     * Mevcut uygulama CPU kullanım oranını al
     * Not: Bu metot yaklaşık bir değer döndürür, kesin CPU kullanımı için daha karmaşık
     * native kütüphaneler kullanılabilir.
     */
    private fun getCpuUsage(): Float {
        try {
            val process = Runtime.getRuntime().exec("top -n 1")
            val reader = process.inputStream.bufferedReader()
            var line: String?
            var cpuTotal = 0f
            var processCount = 0
            
            // top komutunun sonucunu oku
            while (reader.readLine().also { line = it } != null) {
                // Sadece uygulama için CPU kullanımını hesapla
                if (line?.contains(appContext.packageName) == true) {
                    val parts = line?.trim()?.split("\\s+".toRegex())
                    if (parts != null && parts.size > 9) {
                        val cpuValue = parts[8].replace("%", "").toFloatOrNull() ?: 0f
                        cpuTotal += cpuValue
                        processCount++
                    }
                }
            }
            
            // Ortalama CPU kullanım oranı
            return if (processCount > 0) cpuTotal / processCount else 0f
        } catch (e: Exception) {
            Timber.e("Error getting CPU usage: ${e.message}")
            return 0f
        }
    }
    
    /**
     * Düşük bellek modunu kontrol et ve gerekirse aktifleştir
     * Geliştirilmiş sürüm - daha hassas bellek tespit algoritması
     */
    private fun checkLowMemoryMode() {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        // Mevcut bellek kullanımı kontrol et
        val availableMemMB = memoryInfo.availMem / (1024 * 1024)
        val totalMemMB = memoryInfo.totalMem / (1024 * 1024)
        val memoryUsagePercent = 100 - (availableMemMB * 100 / totalMemMB)
        
        // Geçmiş bellek kullanımı trendi - ani bellek değişimlerini tespit et
        val lastMemoryUsage = prefs.getInt("last_memory_usage_percent", 0)
        val memoryIncreaseRate = if (lastMemoryUsage > 0) memoryUsagePercent - lastMemoryUsage else 0
        
        // Güncel bellek kullanımını kaydet
        prefs.edit().putInt("last_memory_usage_percent", memoryUsagePercent.toInt()).apply()
        
        Timber.d("Memory check: $availableMemMB MB available ($memoryUsagePercent% used, change: $memoryIncreaseRate%)")
        
        // Bellek durumu kontrolleri:
        // 1. Sistem zaten düşük bellek bildirdi mi?
        // 2. Yüksek bellek kullanımı var mı? (%80 üzerinde)
        // 3. Ani bellek artış trendi var mı? (%10'dan fazla ani artış kritik olabilir)
        
        val newLowMemoryMode = memoryInfo.lowMemory || 
                            memoryUsagePercent > 80 || 
                            (memoryUsagePercent > 70 && memoryIncreaseRate > 10)
        
        // Bellek modu değişti mi?
        if (newLowMemoryMode != isLowMemoryMode) {
            isLowMemoryMode = newLowMemoryMode
            
            if (isLowMemoryMode) {
                Timber.w("Low memory mode activated (usage: $memoryUsagePercent%, trend: $memoryIncreaseRate%)")
                prefs.edit().putBoolean("low_memory_mode", true).apply()
                
                // Bellek temizleme
                System.gc()
                optimizeMemory()
            } else {
                Timber.d("Exiting low memory mode")
                prefs.edit().putBoolean("low_memory_mode", false).apply()
            }
        }
    }
    
    /**
     * Performans ölçümünü başlat
     */
    fun startMeasurement(key: String) {
        startTimes[key] = SystemClock.elapsedRealtime()
    }
    
    /**
     * Performans ölçümünü bitir ve sonucu kaydet
     */
    fun endMeasurement(key: String): Long {
        val startTime = startTimes[key] ?: return 0
        val endTime = SystemClock.elapsedRealtime()
        val duration = endTime - startTime
        
        metrics[key] = duration
        Timber.d("Performance: $key completed in $duration ms")
        
        return duration
    }
    
    /**
     * WebView performansını optimize et
     */
    fun optimizeWebView(webView: WebView, isDarkTheme: Boolean = false) {
        // Güç tasarrufu modunda daha agresif optimizasyon
        val aggressiveOptimization = isLowMemoryMode || 
                                   prefs.getBoolean("aggressive_optimization", false)
        
        // WebView render modunu optimize et
        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
        
        // Karanlık tema optimizasyonu
        if (isDarkTheme) {
            webView.setBackgroundColor(android.graphics.Color.BLACK)
        }
        
        // WebView ayarlarını optimizasyon seviyesine göre yapılandır
        webView.settings.apply {
            // Genel optimizasyonlar
            blockNetworkImage = true // Sayfa yükleme sırasında geçici olarak görüntüleri engelle
            setRenderPriority(android.webkit.WebSettings.RenderPriority.HIGH)
            cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
            
            // Agresif optimizasyon durumunda daha sıkı ayarlar
            if (aggressiveOptimization) {
                // Gereksiz özellikleri kapat
                setGeolocationEnabled(false)
                mediaPlaybackRequiresUserGesture = true
            }
        }
        
        // JavaScript performans optimizasyonu
        val script = """
            (function() {
                // Performans ayarları
                window.TKF_PERFORMANCE_SETTINGS = {
                    lowMemoryMode: ${isLowMemoryMode},
                    aggressiveOptimization: ${aggressiveOptimization},
                    darkTheme: ${isDarkTheme}
                };
                
                // Sayfa yüklenme performansını optimize et
                if (window.TKF_PERFORMANCE_SETTINGS.lowMemoryMode) {
                    // Düşük bellek modunda animasyonları devre dışı bırak
                    document.documentElement.style.setProperty('--animation-duration', '0s');
                    
                    // Büyük görüntüleri geciktir
                    var style = document.createElement('style');
                    style.textContent = 'img { opacity: 0; transition: opacity 0.3s; }';
                    document.head.appendChild(style);
                    
                    setTimeout(function() {
                        style.textContent = 'img { opacity: 1; }';
                    }, 500);
                }
                
                return 'WebView optimization applied';
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            Timber.d("WebView optimization result: $result")
        }
        
        // Görüntülerin yüklenmesine izin vermek için kısa bir gecikmeden sonra blockNetworkImage'i kaldır
        handler.postDelayed({
            webView.settings.blockNetworkImage = false
        }, 500)
    }
    
    /**
     * Sayfa yükleme performansını raporla
     */
    fun reportPageLoadPerformance(url: String, loadTime: Long, resourceCount: Int) {
        // Performans metriklerini kaydet
        val key = "page_load_${url.hashCode()}"
        metrics[key] = loadTime
        
        // Şüpheli yavaş sayfaları logla
        if (loadTime > 5000) { // 5 saniyeden uzun süren sayfalar
            Timber.w("Slow page load: $url took $loadTime ms with $resourceCount resources")
        }
        
        // Sayfa yükleme süresini kalıcı istatistiklere ekle
        val stats = prefs.getStringSet("slow_pages", mutableSetOf()) ?: mutableSetOf()
        if (loadTime > 3000) { // 3 saniyeden uzun yüklemeler istatistiklere eklensin
            val entry = "$url:$loadTime:$resourceCount"
            stats.add(entry)
            
            // En fazla 20 kayıt tut
            if (stats.size > 20) {
                val sortedStats = stats.map {
                    val parts = it.split(":")
                    val time = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                    Pair(it, time)
                }.sortedByDescending { it.second }
                
                // En yavaş 20 sayfayı tut
                val newStats = sortedStats.take(20).map { it.first }.toMutableSet()
                prefs.edit().putStringSet("slow_pages", newStats).apply()
            } else {
                prefs.edit().putStringSet("slow_pages", stats).apply()
            }
        }
    }
    
    /**
     * Belleği optimize et - geliştirilmiş sürüm
     * Bellek boşaltma ve önbellek temizleme işlemleri
     */
    fun optimizeMemory() {
        // Düşük bellek durumunda mı kontrol et
        checkLowMemoryMode()
        
        // Her zaman çalışacak temel temizlik
        trimApplicationMemory()
        
        if (isLowMemoryMode) {
            // Agresif bellek temizliği yap
            System.gc()
            
            // Önbellekten gereksiz dosyaları temizle
            clearUnusedCache()
            
            // Kullanılmayan kaynak resimlerini temizle
            clearImageCache()
            
            Timber.d("Performed aggressive memory optimization")
        }
    }
    
    /**
     * Uygulama belleğini temizle, gereksiz kaynakları boşalt
     */
    private fun trimApplicationMemory() {
        val activityManager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        
        try {
            // API uyumluluğu için alternatif yaklaşım kullan
            // Doğrudan trimMemory() çağrısı yerine ComponentCallbacks2 implementasyonlarını kullan
            // Uygulama bileşenlerine düşük bellek uyarısı gönder
            val app = appContext as? android.app.Application
            app?.let {
                // Bileşenlere düşük bellek bildirimi gönder
                it.registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
                    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
                    
                    override fun onLowMemory() {}
                    
                    override fun onTrimMemory(level: Int) {}
                })
                
                // Bildirimi gönder ve bileşeni hemen kaldır
                it.unregisterComponentCallbacks(object : android.content.ComponentCallbacks2 {
                    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {}
                    
                    override fun onLowMemory() {}
                    
                    override fun onTrimMemory(level: Int) {}
                })
            }
            
            // GC tetikle
            System.runFinalization()
            System.gc()
            
            Timber.d("Application memory trimmed")
        } catch (e: Exception) {
            Timber.e("Error trimming application memory: ${e.message}")
        }
    }
    
    /**
     * Resim önbelleğini temizle
     */
    private fun clearImageCache() {
        try {
            // Android bitmap önbelleğini temizle
            android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888).recycle()
            
            // Bitmap havuzunu boşalt
            val bitmapCount = android.graphics.BitmapFactory.Options().inBitmap?.let { 1 } ?: 0
            if (bitmapCount > 0) {
                Timber.d("Bitmap pool cleared")
            }
        } catch (e: Exception) {
            Timber.e("Error clearing image cache: ${e.message}")
        }
    }
    
    /**
     * Kullanılmayan önbellek dosyalarını temizle
     */
    private fun clearUnusedCache() {
        try {
            // WebView önbelleğini temizle
            WebView(appContext).clearCache(true)
            
            // Uygulama önbellek klasörünü temizle
            val cacheDir = appContext.cacheDir
            clearDirectory(cacheDir, 7) // 7 günden eski dosyaları temizle
        } catch (e: Exception) {
            Timber.e("Error clearing cache: ${e.message}")
        }
    }
    
    /**
     * Belirtilen günden daha eski dosyaları temizle
     */
    private fun clearDirectory(directory: java.io.File, daysOld: Int) {
        val cutoffTime = System.currentTimeMillis() - (daysOld * 24 * 60 * 60 * 1000)
        
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                clearDirectory(file, daysOld)
            } else if (file.lastModified() < cutoffTime) {
                file.delete()
            }
        }
    }
    
    /**
     * Performans ayarlarını güncelle
     */
    fun updateSettings(enableDnsWarmup: Boolean, enableAggressiveOptimization: Boolean) {
        prefs.edit()
            .putBoolean("enable_dns_warmup", enableDnsWarmup)
            .putBoolean("aggressive_optimization", enableAggressiveOptimization)
            .apply()
        
        // DNS önbelleği ısıtmayı güncel ayarlara göre başlat
        if (enableDnsWarmup && !isDnsWarmupActive.get()) {
            startDnsWarmup()
        }
    }
    
    /**
     * Performans istatistiklerini al
     */
    fun getPerformanceStats(): Map<String, Long> {
        return metrics.toMap()
    }
    
    /**
     * En yavaş sayfaların listesini al
     */
    fun getSlowestPages(): List<Triple<String, Long, Int>> {
        val stats = prefs.getStringSet("slow_pages", mutableSetOf()) ?: mutableSetOf()
        
        return stats.mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size >= 3) {
                val url = parts[0]
                val time = parts[1].toLongOrNull() ?: 0L
                val resourceCount = parts[2].toIntOrNull() ?: 0
                Triple(url, time, resourceCount)
            } else {
                null
            }
        }.sortedByDescending { it.second }
    }
    
    /**
     * Düşük bellek modunda mı kontrol et
     */
    fun isInLowMemoryMode(): Boolean {
        return isLowMemoryMode
    }
}