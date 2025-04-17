package com.asforce.asforcetkf2

import android.app.Application
import android.content.Context
import android.os.Process
import android.webkit.WebView
import com.asforce.asforcetkf2.util.TKFPerformanceManager
import timber.log.Timber

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
     */
    private fun prepareFolders() {
        try {
            // İndirilenler klasörü
            val downloadsDir = getExternalFilesDir(null)?.also {
                if (!it.exists()) it.mkdirs()
            }
            
            // Önbellek temizleme - 7 günden eski önbellek dosyalarını temizle
            val cacheDir = cacheDir
            if (cacheDir.exists() && cacheDir.isDirectory) {
                val cutoffTime = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000) // 7 gün öncesi
                cacheDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoffTime) {
                        try {
                            if (file.isDirectory) file.deleteRecursively() else file.delete()
                        } catch (e: Exception) {
                            Timber.w("Could not delete cache file: ${file.name}")
                        }
                    }
                }
            }
            
            Timber.d("Application folders prepared")
        } catch (e: Exception) {
            Timber.e("Error preparing application folders: ${e.message}")
        }
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
     */
    override fun onLowMemory() {
        super.onLowMemory()
        Timber.w("System reports low memory")
        
        // Agresif bellek optimizasyonu uygula
        browserOptimizer.performMemoryOptimization()
        
        // Bellek durumunu logla
        logMemoryInfo()
    }
    
    /**
     * Kritik olmayan bileşenlerin belleğini serbestleştirir
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        
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
        
        // Kritik seviyeler için bellek optimizasyonu
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            browserOptimizer.performMemoryOptimization()
            
            // WebView önbelleklerini temizle
            if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
                try {
                    WebView(this).clearCache(true)
                } catch (e: Exception) {
                    Timber.w("Could not clear WebView cache: ${e.message}")
                }
                
                // GC tetikle
                System.gc()
            }
        }
    }
}