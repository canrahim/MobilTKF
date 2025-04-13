package com.asforce.asforcetkf2.webview

import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import com.asforce.asforcetkf2.model.Tab
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * WebView performans optimizasyonu için yardımcı sınıf
 * Bu sınıf WebView'in bellek yönetimini ve performansını iyileştirir
 */
class WebViewOptimizer(webView: WebView) {
    
    private val webViewRef = WeakReference(webView)
    private val handler = Handler(Looper.getMainLooper())
    
    private var isOptimizationEnabled = true
    private var memoryUsage = 0L
    
    companion object {
        private const val DEFAULT_MEMORY_THRESHOLD = 50 * 1024 * 1024 // 50MB
        private const val TRIM_MEMORY_INTERVAL = 60000L // 60 saniye
    }
    
    /**
     * WebView için otomatik bellek optimizasyonunu başlat
     */
    fun startMemoryOptimization() {
        if (!isOptimizationEnabled) return
        
        // Başlangıçta ani bir bellek optimizasyonu yap
        val webView = webViewRef.get() ?: return
        webView.freeMemory()
        
        // Düzenli temizlik için planla
        scheduleMemoryTrim()
    }
    
    /**
     * Düzenli aralıklarla bellek temizliği planla
     */
    private fun scheduleMemoryTrim() {
        handler.postDelayed({
            val webView = webViewRef.get() ?: return@postDelayed
            
            // WebView'in kullandığı belleği tahmin et
            memoryUsage = estimateMemoryUsage(webView)
            
            // Bellek eşiğini aşıyorsa belleği temizle
            if (memoryUsage > DEFAULT_MEMORY_THRESHOLD) {
                trimMemory(webView)
            }
            
            // Bir sonraki temizlik için planla
            scheduleMemoryTrim()
        }, TRIM_MEMORY_INTERVAL)
    }
    
    /**
     * Bellek kullanımını tahmin et
     */
    private fun estimateMemoryUsage(webView: WebView): Long {
        // WebView için bellek kullanımını tahmin etmek için basit bir formül
        // Gerçek hayatta daha karmaşık olabilir
        val width = webView.width
        val height = webView.height
        
        // Piksel başına 4 byte (ARGB_8888)
        return (width * height * 4).toLong()
    }
    
    /**
     * WebView belleğini temizle
     */
    private fun trimMemory(webView: WebView) {
        Timber.d("Trimming WebView memory, estimated usage: ${memoryUsage / 1024 / 1024}MB")
        
        // WebView'in belleğini serbest bırak
        webView.freeMemory()
        
        // Önbellek boyutunu azalt
        webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        
        // DOM depolama alanını temizle (önemli)
        webView.clearCache(false)
    }
    
    /**
     * Tab geçişlerinde performansı iyileştir
     * Aktif olmayan WebView'leri uyku moduna alarak kaynak kullanımını azaltır
     */
    fun optimizeForTabSwitch(webView: WebView, tab: Tab, isActive: Boolean) {
        if (!isOptimizationEnabled) return
        
        if (isActive) {
            // Tab aktifleştirildiğinde
            // WebView yükleme performansını iyileştir
            webView.settings.apply {
                // Performans için önce metinleri, sonra resimleri yükle
                blockNetworkImage = false
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            }
            
            // JavaScript'i varsayılan haline geri döndür
            webView.settings.javaScriptEnabled = true
            
            // Süreölçerleri devam ettir
            webView.resumeTimers()
        } else {
            // Tab arka plana alındığında
            // Ağ görüntü yüklemesini engelle
            webView.settings.blockNetworkImage = true
            
            // Arka plandaki tablar için önbellek önceliği
            webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
            
            // Yüklemeyi durdur ve süreölçerleri duraklat
            if (webView.isShown && !tab.isHibernated) {
                webView.stopLoading()
                webView.pauseTimers()
            }
            
            // JavaScript'i devre dışı bırakarak işlemciyi rahatlatabilir, 
            // ancak bu bazı sayfaların durumunu bozabilir, bu yüzden dikkatli kullanın
            // webView.settings.javaScriptEnabled = false
        }
    }
    
    /**
     * WebView'in özelleştirilmiş freeMemory implementasyonu
     */
    private fun WebView.freeMemory() {
        // WebView'in kendisini temizlemesi için JavaScript GC tetikle
        this.evaluateJavascript("if (window.gc) { window.gc(); }", null)
        
        // Lokal depoları sınırla
        this.evaluateJavascript("""
            try {
                // LocalStorage temizleme (sadece inaktif sayfalar için)
                var keysToRemove = [];
                for (var i = 0; i < localStorage.length; i++) {
                    var key = localStorage.key(i);
                    if (key.indexOf('temp_') === 0 || key.indexOf('cache_') === 0) {
                        keysToRemove.push(key);
                    }
                }
                for (var j = 0; j < keysToRemove.length; j++) {
                    localStorage.removeItem(keysToRemove[j]);
                }
                
                // SessionStorage temizleme
                sessionStorage.clear();
            } catch(e) {
                console.error('Storage cleanup error: ' + e);
            }
        """.trimIndent(), null)
    }
    
    /**
     * Sayfa yükleme sonrası optimizasyon
     */
    fun optimizeAfterPageLoad(webView: WebView) {
        if (!isOptimizationEnabled) return
        
        // Sayfa yüklendikten sonra önbellek ayarlarını güncelle
        webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        
        // Resimlerin gösterilmesine izin ver
        webView.settings.blockNetworkImage = false
        
        // Bellek ve CPU optimizasyonu için GC tetikle
        handler.postDelayed({
            webView.freeMemory()
        }, 1000)
    }
    
    /**
     * Optimizasyonu etkinleştir/devre dışı bırak
     */
    fun setOptimizationEnabled(enabled: Boolean) {
        isOptimizationEnabled = enabled
        
        if (enabled) {
            startMemoryOptimization()
        } else {
            handler.removeCallbacksAndMessages(null)
        }
    }
}