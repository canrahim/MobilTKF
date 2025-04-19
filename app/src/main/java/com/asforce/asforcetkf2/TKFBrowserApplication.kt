package com.asforce.asforcetkf2

import android.app.Application
import android.content.Context
import android.os.Process
import android.webkit.WebView
import com.asforce.asforcetkf2.util.TKFPerformanceManager
import timber.log.Timber
import java.io.File

/**
 * TKF Tarayıcı Uygulama sınıfı
 * Tüm uygulama için gerekli optimizasyonları ve yapılandırmaları içerir
 */
class TKFBrowserApplication : Application() {
    
    // Browser Optimizer - uygulama geneli optimizasyon yöneticisi
    lateinit var browserOptimizer: TKFBrowserOptimizer
        private set
    
    companion object {
        // Instead of using BuildConfig.DEBUG, hardcode this value for now
        // This would be replaced by the actual BuildConfig in a complete project
        private const val DEBUG_MODE = true
        
        /**
         * Uygulama örneğine statik erişim
         */
        @Volatile
        private var instance: TKFBrowserApplication? = null
        
        fun getInstance(): TKFBrowserApplication = 
            instance ?: throw IllegalStateException("Application instance not initialized")
    }
    
    override fun onCreate() {
        // Performans ölçümü başlat
        val startTime = System.currentTimeMillis()
        
        // Instance referansını ayarla
        instance = this
        
        // Bellek optimizasyonu için öncelik ayarla - daha öncelikli başlangıç
        Thread.currentThread().priority = Thread.MAX_PRIORITY
        
        // Uygulama başlangıcı
        super.onCreate()
        
        // Initialize Timber for logging
        if (DEBUG_MODE) {
            Timber.plant(Timber.DebugTree())
        }
        
        // Uygulama başlatma mesajı - PID ve bellek bilgisi ile
        Timber.i("TKF Browser Application starting - PID: ${Process.myPid()}")
        logMemoryInfo()
        
        // Browser Optimizer'ı başlat
        initializeBrowserOptimizer()
        
        // Enable WebView debugging if in debug build
        if (DEBUG_MODE) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        
        // Çerezlerin kalıcı olarak saklanması için ayarla
        setupCookieManager()
        
        // WebView ön-ısınma - uygulama başlangıcında WebView başlatma gecikmesini önler
        preWarmWebView()
        
        // Uzun süren işlemleri arka planda başlat
        startBackgroundTasks()
        
        // Başlangıç süresini ölç
        val duration = System.currentTimeMillis() - startTime
        Timber.i("TKF Browser Application started in $duration ms")
    }
    
    /**
     * Tarayıcı optimizer'ı başlat
     */
    private fun initializeBrowserOptimizer() {
        browserOptimizer = TKFBrowserOptimizer(this)
        browserOptimizer.optimizeAppStart()
    }
    
    /**
     * Çerezlerin kalıcı olarak saklanması için CookieManager'ı yapılandırır
     * Geliştirilmiş oturum yönetimi ve çerez kalıcılığı ile
     */
    private fun setupCookieManager() {
        val cookieManager = android.webkit.CookieManager.getInstance()
        
        // Çerezlerin kabul edilmesi ve kalıcı olması için ayarlar
        cookieManager.setAcceptCookie(true)
        
        // Üçüncü taraf çerezleri de kabul et - iframe veya CDN içeriği için
        try {
            // API 21+ için yöntem
            cookieManager.setAcceptThirdPartyCookies(WebView(this), true)
        } catch (e: Exception) {
            Timber.w("Third party cookies may not be supported: ${e.message}")
        }
        
        // Gizli mod yerine normal mod kullanarak oturum bilgilerinin saklanmasını sağla
        WebView.setWebContentsDebuggingEnabled(DEBUG_MODE)
        
        try {
            // CookieSyncManager kullanımı - geriye dönük uyumluluk
            val cookieSyncMgr = Class.forName("android.webkit.CookieSyncManager")
            val createInstance = cookieSyncMgr.getMethod("createInstance", Context::class.java)
            createInstance.invoke(null, this)
        } catch (e: Exception) {
            Timber.d("Modern cookie manager in use, sync manager not needed: ${e.message}")
        }
        
        // Çerezlerin kalıcı olarak depolanması için flush işlemi
        cookieManager.flush()
        
        Timber.d("Cookie Manager initialized with enhanced persistence")
        
        // Kalıcı depolama modunu kontrol et
        val acceptCookie = cookieManager.acceptCookie()
        Timber.d("Cookie acceptance status: $acceptCookie")
    }
    
    /**
     * WebView ön ısıtma - Android WebView başlatma gecikmesini önler
     */
    private fun preWarmWebView() {
        // Arka planda çalışacak thread
        Thread {
            try {
                val startTime = System.currentTimeMillis()
                Timber.d("Pre-warming WebView...")
                
                // WebView'i oluştur ve temel ayarlarını yap - sadece isınma amaçlı
                val webView = WebView(this)
                
                // WebView'i DOMContentLoaded event'ine kadar başlat ve sonra temizle
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.databaseEnabled = true
                webView.settings.cacheMode = android.webkit.WebSettings.LOAD_CACHE_ELSE_NETWORK
                
                // Boş bir HTML yükle - render motoru aktif olsun
                val html = "<html><body><h1>TKF Browser Warmup</h1></body></html>"
                webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                
                // Kısa bir süre bekle, sonra temizle
                Thread.sleep(500)
                webView.stopLoading()
                webView.destroy()
                
                val duration = System.currentTimeMillis() - startTime
                Timber.d("WebView pre-warmed in $duration ms")
                
            } catch (e: Exception) {
                Timber.e("WebView pre-warming failed: ${e.message}")
            }
        }.start()
    }
    
    /**
     * Arka planda çalışacak işlemleri başlat
     */
    private fun startBackgroundTasks() {
        // Uygulama klasörlerini ve kaynakları hazırla
        prepareFolders()
        
        // Performans optimizasyonları
        val perfManager = TKFPerformanceManager.getInstance(this)
        perfManager.updateSettings(
            enableDnsWarmup = true,
            enableAggressiveOptimization = false
        )
    }
    
    /**
     * Uygulama klasörlerini ve kaynakları hazırla
     * Geliştirilmiş versiyon: Paralel işleme ve daha akıllı önbellek yönetimi
     */
    private fun prepareFolders() {
        // İşlemi ayrı bir thread'de çalıştır - UI bloklanmasını önler
        Thread {
            try {
                Timber.d("Preparing application folders...")
                val startTime = System.currentTimeMillis()
                
                // Gerekli klasörleri hazırla
                val fileDirectories = mutableListOf<java.io.File?>()
                
                // İndirilenler klasörü
                fileDirectories.add(getExternalFilesDir(null))
                
                // Önbellekler klasörü
                fileDirectories.add(cacheDir)
                
                // WebView önbellek klasörü
                fileDirectories.add(getDir("webview", Context.MODE_PRIVATE))
                
                // Resim önbellek klasörü
                fileDirectories.add(getDir("images", Context.MODE_PRIVATE))
                
                // Tüm klasörlerin oluştuğundan emin ol
                fileDirectories.forEach { dir ->
                    dir?.let {
                        if (!it.exists()) {
                            if (it.mkdirs()) {
                                Timber.d("Created directory: ${it.absolutePath}")
                            } else {
                                Timber.w("Failed to create directory: ${it.absolutePath}")
                            }
                        }
                    }
                }
                
                // Önbellek temizleme stratejisi - daha akıllı yaklaşım
                val totalSpace = cacheDir.totalSpace
                val freeSpace = cacheDir.freeSpace
                val usedPercentage = 100 - (freeSpace * 100 / totalSpace)
                
                // Çok doluysa daha agresif temizle
                val cutoffDays = when {
                    usedPercentage > 90 -> 1 // 1 günden eski dosyaları sil
                    usedPercentage > 70 -> 3 // 3 günden eski dosyaları sil
                    else -> 7 // 7 günden eski dosyaları sil
                }
                
                val cutoffTime = System.currentTimeMillis() - (cutoffDays * 24 * 60 * 60 * 1000L)
                
                // Önbellek temizleme - belirli bir tarihten eski önbellek dosyalarını sil
                var deletedFiles = 0
                var deletedSize = 0L
                var failedDeletes = 0
                
                // Zamana dayanan temizlik
                cacheDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoffTime) {
                        val fileSize = if (file.isDirectory) file.walkTopDown().filter { it.isFile }.map { it.length() }.sum() else file.length()
                        
                        try {
                            val deleted = if (file.isDirectory) file.deleteRecursively() else file.delete()
                            if (deleted) {
                                deletedFiles++
                                deletedSize += fileSize
                            } else {
                                failedDeletes++
                            }
                        } catch (e: Exception) {
                            Timber.w("Could not delete cache file: ${file.name}, error: ${e.message}")
                            failedDeletes++
                        }
                    }
                }
                
                // Önbellek durumunu logla
                val deletedMB = deletedSize / (1024 * 1024)
                Timber.d("Cache cleanup: deleted $deletedFiles files ($deletedMB MB), failed: $failedDeletes")
                
                // WebView-spesifik önbellekleri temizlemeyi dene
                try {
                    val webViewCacheDir = getDir("webview", Context.MODE_PRIVATE)
                    val webViewCacheDirFiles = webViewCacheDir.listFiles()
                    if (webViewCacheDirFiles != null && webViewCacheDirFiles.isNotEmpty()) {
                        webViewCacheDirFiles.forEach { file ->
                            if (file.lastModified() < cutoffTime) {
                                if (file.isDirectory) file.deleteRecursively() else file.delete()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.w("WebView cache cleanup error: ${e.message}")
                }
                
                val duration = System.currentTimeMillis() - startTime
                Timber.d("Application folders prepared in $duration ms")
            } catch (e: Exception) {
                Timber.e("Error preparing application folders: ${e.message}")
            }
        }.start()
    }
    
    /**
     * Bellek bilgisini logla
     */
    private fun logMemoryInfo() {
        val runtime = Runtime.getRuntime()
        val usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L
        val maxMemoryMB = runtime.maxMemory() / 1048576L
        val availableMemoryMB = runtime.freeMemory() / 1048576L
        
        Timber.d("Memory - Used: $usedMemoryMB MB, Max: $maxMemoryMB MB, Available: $availableMemoryMB MB")
    }
    
    /**
     * Düşük bellek uyarısı alındığında çağrılır
     * Geliştirilmiş bellek kurtarma - En kritik durum
     */
    override fun onLowMemory() {
        super.onLowMemory()
        val startTime = System.currentTimeMillis()
        Timber.w("CRITICAL: System reports low memory - emergency cleanup initiated")
        
        // Olay başlamadan önce bellek durumunu logla
        logMemoryInfo()
        
        // En agresif bellek optimizasyonu uygula
        browserOptimizer.performMemoryOptimization()
        
        // Acil durum bellek temizliği
        try {
            // Sistemin WebView önbelleklerini temizle
            WebView(this).clearCache(true)
            
            // Tüm önbellek dosyalarını temizle
            val cacheDir = cacheDir
            if (cacheDir.exists() && cacheDir.isDirectory) {
                cacheDir.listFiles()?.forEach { file ->
                    if (file.name.contains("cache") || file.name.contains("temp") || file.name.contains("tmp")) {
                        try {
                            if (file.isDirectory) file.deleteRecursively() else file.delete()
                        } catch (e: Exception) {
                            // Temizleme hatasını geç - şu anda maksimum bellek kurtarmaya odaklan
                        }
                    }
                }
            }
            
            // WebView depolamayı temizle
            try { android.webkit.WebStorage.getInstance().deleteAllData() } catch (e: Exception) { }
            
            // Tüm HTTP önbelleklerini zorla temizle
            try { android.webkit.CookieManager.getInstance().removeSessionCookies(null) } catch (e: Exception) { }
            
            // GC tetikle
            System.gc()
            Runtime.getRuntime().gc()
            
        } catch (e: Exception) {
            Timber.e("Error during emergency memory cleanup: ${e.message}")
        }

        // Son bellek durumunu logla
        logMemoryInfo()
        
        val duration = System.currentTimeMillis() - startTime
        Timber.d("Emergency memory cleanup completed in $duration ms")
    }
    
    /**
     * Kritik olmayan bileşenlerin belleğini serbestleştirir
     * Geliştirilmiş versiyon: Kademeli bellek optimizasyonu
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        val startTime = System.currentTimeMillis()
        
        // Bellek kısıtlama seviyesine göre farklı eylemler
        val levelStr = when (level) {
            TRIM_MEMORY_RUNNING_MODERATE -> "TRIM_MEMORY_RUNNING_MODERATE"
            TRIM_MEMORY_RUNNING_LOW -> "TRIM_MEMORY_RUNNING_LOW"
            TRIM_MEMORY_RUNNING_CRITICAL -> "TRIM_MEMORY_RUNNING_CRITICAL"
            TRIM_MEMORY_UI_HIDDEN -> "TRIM_MEMORY_UI_HIDDEN"
            TRIM_MEMORY_BACKGROUND -> "TRIM_MEMORY_BACKGROUND"
            TRIM_MEMORY_MODERATE -> "TRIM_MEMORY_MODERATE"
            TRIM_MEMORY_COMPLETE -> "TRIM_MEMORY_COMPLETE"
            else -> "UNKNOWN"
        }
        
        Timber.d("onTrimMemory: $levelStr ($level)")
        
        // Bellek durumunu logla
        logMemoryInfo()
        
        // Optimizasyon seviyesini belirle
        val optimizationLevel = when (level) {
            TRIM_MEMORY_RUNNING_CRITICAL, TRIM_MEMORY_COMPLETE -> "extreme"
            TRIM_MEMORY_RUNNING_LOW, TRIM_MEMORY_BACKGROUND -> "aggressive"
            TRIM_MEMORY_RUNNING_MODERATE, TRIM_MEMORY_MODERATE -> "moderate" 
            else -> "normal"
        }
        
        // Seviyeye göre optimizasyon yöntemini belirle
        // UI Thread'i bloklamadan asenkron çalıştır
        Thread {
            try {
                // Optimize edicini direk çağır
                TKFPerformanceManager.getInstance(this).optimizeMemory(optimizationLevel)
                
                // Kritik seviyeler için ana optimizasyonu da çağır
                if (level >= TRIM_MEMORY_RUNNING_LOW) {
                    browserOptimizer.performMemoryOptimization()
                    
                    // WebView önbelleklerini temizle
                    if (level >= TRIM_MEMORY_RUNNING_CRITICAL || level == TRIM_MEMORY_COMPLETE) {
                        try {
                            WebView(this).clearCache(true)
                            // WebView depolama temizliği
                            android.webkit.WebStorage.getInstance().deleteAllData()
                            // Son çare: GC tetikle
                            System.gc()
                            Runtime.getRuntime().gc()
                        } catch (e: Exception) {
                            Timber.w("Could not clear WebView cache: ${e.message}")
                        }
                    }
                }
                
                // Arka planda çalışıyorsa veya UI gizliyse ek optimizasyonlar
                if (level == TRIM_MEMORY_UI_HIDDEN || level == TRIM_MEMORY_BACKGROUND) {
                    // Geçici önbellek dosyalarını temizle
                    val tempDir = File(cacheDir, "tmp")
                    if (tempDir.exists() && tempDir.isDirectory) {
                        tempDir.listFiles()?.forEach { it.delete() }
                    }
                }
                
                val duration = System.currentTimeMillis() - startTime
                Timber.d("Memory optimization (${optimizationLevel}) completed in $duration ms")
                
            } catch (e: Exception) {
                Timber.e("Error during memory optimization: ${e.message}")
            }
        }.start()
    }
}