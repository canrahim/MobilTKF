package com.asforce.asforcetkf2.webview

import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import com.asforce.asforcetkf2.model.Tab
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * WebView performans optimizasyonu için temel sınıf
 * Bu sınıf WebView'in bellek yönetimini ve performansını iyileştirir
 * TKFWebViewOptimizer tarafından miras alınır
 */
open class WebViewOptimizer(webView: WebView) {
    
    private val webViewRef = WeakReference(webView)
    private val handler = Handler(Looper.getMainLooper())
    
    private var isOptimizationEnabled = true
    private var memoryUsage = 0L
    
    companion object {
        private const val DEFAULT_MEMORY_THRESHOLD = 50 * 1024 * 1024 // 50MB
        private const val TRIM_MEMORY_INTERVAL = 30000L // 30 saniye - daha sık aralık
        
        // Statik optimizer instance, her bir WebView için ayrı ayrı oluşturmamak için
        @Volatile
        private var sharedOptimizer: WebViewOptimizer? = null
        
        /**
         * Singleton Optimizer alıcı - bellek tasarrufu için
         */
        fun getInstance(webView: WebView): WebViewOptimizer {
            return sharedOptimizer ?: synchronized(this) {
                sharedOptimizer ?: WebViewOptimizer(webView).also { sharedOptimizer = it }
            }
        }
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
     * Geliştirilmiş sürüm - daha hızlı ve daha hafif
     */
    fun optimizeForTabSwitch(webView: WebView, tab: Tab, isActive: Boolean) {
        if (!isOptimizationEnabled) return
        
        if (isActive) {
            // Tab aktifleştirildiğinde - YILDIZ TURBO Hız!
            webView.settings.apply {
                // Kademeli yükleme stratejisi - önce yapı, sonra görseller
                blockNetworkImage = true
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                
                // Hızlı önbellek yükleme stratejisi - ağdan yükleme öncesinde önbelleğe bakar
                cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
                
                // Görüntüleme optimizasyonları - hızlı görüntüleme
                useWideViewPort = true 
                loadWithOverviewMode = true
                
                // Metin ölçekleme optimizasyonu - ayarları korumak için dokunma
                textZoom = 100
                
                // JavaScript performansı
                javaScriptCanOpenWindowsAutomatically = true
                
                // DOM depolama ve veritabanı desteği
                domStorageEnabled = true
                databaseEnabled = true
            }
            
            // Çerez ayarlarını güncelle
            android.webkit.CookieManager.getInstance().setAcceptCookie(true)
            
            // JavaScript'i etkinleştir
            webView.settings.javaScriptEnabled = true
            
            // Süreölçerleri devam ettir
            webView.resumeTimers()
            
            // Görünürlük ayarla
            webView.visibility = View.VISIBLE
            
            // GC tetikle - daha temiz başlangıç
            webView.freeMemory()
            
            // Donanım hızlandırma - GPU'yu aktifleştir
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            // Ekstra performans için ipuçlarını tetikle
            webView.evaluateJavascript("""
                (function() {
                    // Kaydırma davranışı optimizasyonu
                    if (document.body) {
                        document.body.style.overscrollBehavior = 'none';
                        
                        // Sayfa görünürlüğünü geri yükle
                        document.body.style.zoom = '1.0';
                        document.body.style.opacity = '1';
                        
                        // Görüntüleri kademeli olarak yükle
                        var lazyImages = document.querySelectorAll('img[loading="lazy"]');
                        if (lazyImages.length > 0) {
                            for (var i = 0; i < Math.min(lazyImages.length, 5); i++) {
                                lazyImages[i].loading = 'eager';
                            }
                        }
                        
                        // Görünürlük olayını tetikle
                        var event = new Event('visibilitychange');
                        document.dispatchEvent(event);
                        
                        // Sekme aktifleştirildi olayı
                        if (window.TKF_onTabActivated) {
                            window.TKF_onTabActivated();
                        }
                    }
                    
                    // Performans ipuçları kaydet
                    window.TKF_performanceTips = {
                        lastActivation: Date.now()
                    };
                    
                    return 'ACTIVATED';
                })();
            """.trimIndent(), null)
            
            // Görüntü yüklemeyi kısa bir gecikmeyle aktifleştir
            Handler(Looper.getMainLooper()).postDelayed({
                webView.settings.blockNetworkImage = false
            }, 300)
        } else {
            // Tab arka plana alındığında - MAKSIMUM bellek tasarrufu!
            
            // Ağ yükleme işlemlerini durdur
            webView.settings.blockNetworkImage = true
            
            // Minimum önbellek ayarları - sadece lokalden yükle
            webView.settings.cacheMode = WebSettings.LOAD_CACHE_ONLY
            
            // Yüklemeyi durdur ve süreölçerleri duraklat - ancak oturum verilerini koru
            if (webView.isShown && !tab.isHibernated) {
                webView.stopLoading()
                webView.pauseTimers()
            }
            
            // Görünürlük azalt
            webView.visibility = View.INVISIBLE
            
            // Bellek kullanımını azaltmak için yazılım render moduna geç
            webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            
            // JavaScript motoru durdur - ancak oturum korumasıyla
            // False yerine önemli JavaScriptlerin çalışmasına izin ver
            if (!tab.url.contains("login") && !tab.url.contains("auth") && !tab.url.contains("szutest")) {
                webView.settings.javaScriptEnabled = false
            }
            
            // GC tetikle
            webView.freeMemory()
            
            // DOM ağacını küçült ama tamamen silme - oturum verilerini koru
            webView.evaluateJavascript(
                """
                (function() {
                    try {
                        // Oturum durumunu koru ama görsel içeriği azalt
                        if (document.body) {
                            // Görüntüleme özelliklerini azalt
                            document.body.style.zoom = '0.1';
                            document.body.style.opacity = '0.1';
                            
                            // Görüntüleri kaldır ama DOM yapısını koru
                            var images = document.querySelectorAll('img');
                            for (var i = 0; i < images.length; i++) {
                                images[i].style.display = 'none';
                                if (images[i].src) {
                                    images[i].setAttribute('data-src', images[i].src);
                                    images[i].src = '';
                                }
                            }
                            
                            // İframe'leri durdur
                            var iframes = document.querySelectorAll('iframe');
                            for (var j = 0; j < iframes.length; j++) {
                                iframes[j].style.display = 'none';
                                if (iframes[j].src) {
                                    iframes[j].setAttribute('data-src', iframes[j].src);
                                    iframes[j].src = 'about:blank';
                                }
                            }
                            
                            // Video ve ses elementlerini durdur
                            var media = document.querySelectorAll('video, audio');
                            for (var k = 0; k < media.length; k++) {
                                if (media[k].pause) media[k].pause();
                                media[k].style.display = 'none';
                            }
                            
                            // Olay temizleme fonksiyonu oluştur
                            if (!window.TKF_cleanupEvents) {
                                window.TKF_cleanupEvents = function() {
                                    // Sadece en önemli olayları bırak
                                    var elements = document.querySelectorAll('*');
                                    for (var l = 0; l < elements.length; l++) {
                                        var el = elements[l];
                                        // Form elementleri dışındaki tüm işlevleri temizle
                                        if (el.tagName !== 'FORM' && 
                                            el.tagName !== 'INPUT' && 
                                            el.tagName !== 'BUTTON' && 
                                            el.tagName !== 'SELECT' && 
                                            el.tagName !== 'TEXTAREA') {
                                            
                                            // Güvenli temizleme - sadece bazı olayları kaldır
                                            var safeEvents = ['submit', 'change', 'input'];
                                            
                                            // Sadece görsel olayları temizle, formla ilgili olanları tut
                                            var listeners = ['click', 'mousemove', 'mouseover', 'mouseout', 'scroll', 'resize'];
                                            for (var m = 0; m < listeners.length; m++) {
                                                el['on' + listeners[m]] = null;
                                            }
                                        }
                                    }
                                    
                                    console.log('TKF Event Cleanup Complete');
                                };
                            }
                            
                            // Sekme arka planda bildirimini gönder
                            if (window.TKF_onTabDeactivated) {
                                window.TKF_onTabDeactivated();
                            } else {
                                // Oluştur
                                window.TKF_onTabDeactivated = function() {
                                    console.log('TKF Tab deactivated at ' + new Date());
                                    
                                    // Olay temizliği yap
                                    if (window.TKF_cleanupEvents) {
                                        window.TKF_cleanupEvents();
                                    }
                                };
                                
                                // Çağır
                                window.TKF_onTabDeactivated();
                            }
                            
                            return 'DOM hibernated with session preservation';
                        }
                    } catch(e) {
                        return 'Error hibernating DOM: ' + e.message;
                    }
                })();
                """.trimIndent(), null
            )
        }
    }
    
    /**
     * WebView'in özelleştirilmiş freeMemory implementasyonu
     * Geliştirilmiş bellek yönetimi ve DOM temizleme
     */
    private fun WebView.freeMemory() {
        // WebView'in kendisini temizlemesi için JavaScript GC tetikle
        this.evaluateJavascript("if (window.gc) { window.gc(); }", null)
        
        // Lokal depoları sınırla ve DOM ağacını optimize et
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
                
                // SessionStorage temizleme - önemli olanları koru
                var sessionKeysToKeep = ['user', 'auth', 'token', 'login'];
                var sessionKeysToRemove = [];
                
                for (var k = 0; k < sessionStorage.length; k++) {
                    var sessionKey = sessionStorage.key(k);
                    var shouldKeep = false;
                    
                    // Önemli verileri kontrol et
                    for (var l = 0; l < sessionKeysToKeep.length; l++) {
                        if (sessionKey.toLowerCase().indexOf(sessionKeysToKeep[l]) !== -1) {
                            shouldKeep = true;
                            break;
                        }
                    }
                    
                    if (!shouldKeep) {
                        sessionKeysToRemove.push(sessionKey);
                    }
                }
                
                // Önemli olmayan session verilerini temizle
                for (var m = 0; m < sessionKeysToRemove.length; m++) {
                    sessionStorage.removeItem(sessionKeysToRemove[m]);
                }
                
                // DOM ağacını optimize et - görünmeyen öğeleri temizle
                if (document.body) {
                    // Görünür olmayan alanları temizle
                    var elements = document.body.querySelectorAll('*');
                    for (var n = 0; n < elements.length; n++) {
                        var el = elements[n];
                        // Görünmeyen elementleri temizle
                        if (el.offsetParent === null && 
                            el.tagName !== 'SCRIPT' && 
                            el.tagName !== 'STYLE' && 
                            el.tagName !== 'LINK' && 
                            el.tagName !== 'META') {
                                
                            // Görünmeyen büyük DOM elementlerinin içeriğini temizle
                            if (el.childNodes && el.childNodes.length > 10) {
                                el.innerHTML = '';
                            }
                        }
                    }
                    
                    // Gereksiz event listener'ları kaldır
                    if (window.TKF_cleanupEvents) {
                        window.TKF_cleanupEvents();
                    }
                }
            } catch(e) {
                console.error('Storage cleanup error: ' + e);
            }
        """.trimIndent(), null)
        
        // Native bellek temizliği
        System.gc()
    }
    
    /**
     * Sayfa yükleme sonrası optimizasyon - Geliştirilmiş performans sürüm 2.0
     */
    fun optimizeAfterPageLoad(webView: WebView) {
        if (!isOptimizationEnabled) return
        
        // Sayfa yüklendikten sonra önbellek ayarlarını güncelle
        webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        
        // Resimlerin gösterilmesine izin ver
        webView.settings.blockNetworkImage = false
        
        // Performans optimizasyonu scriptini enjekte et
        webView.evaluateJavascript("""
            (function() {
                // Görüntü optimizasyonu
                function optimizeImages() {
                    // Ekranda görünmeyen görselleri lazy-load olarak işaretle
                    var images = document.querySelectorAll('img:not([loading])');
                    if (images.length > 0) {
                        var viewportHeight = window.innerHeight;
                        var viewportWidth = window.innerWidth;
                        
                        for (var i = 0; i < images.length; i++) {
                            var img = images[i];
                            var rect = img.getBoundingClientRect();
                            
                            // Ekranda görünmeyenler lazy-load olsun
                            if (rect.bottom < 0 || rect.top > viewportHeight || 
                                rect.right < 0 || rect.left > viewportWidth) {
                                img.loading = 'lazy';
                            }
                        }
                        console.log('TKF: Optimized ' + images.length + ' images');
                    }
                    
                    // Video otomatik oynatmayı engelle
                    var videos = document.querySelectorAll('video[autoplay]');
                    for (var j = 0; j < videos.length; j++) {
                        videos[j].removeAttribute('autoplay');
                        videos[j].pause();
                    }
                }
                
                // TKF performans objesi
                window.TKF_performance = {
                    startTime: Date.now(),
                    optimizeImages: optimizeImages,
                    // Tab aktifleşme olayı işleyicisi
                    onTabActivated: function() {
                        console.log('TKF Tab activated at ' + new Date());
                        optimizeImages();
                    }
                };
                
                // İlk görüntü optimizasyonunu yap
                optimizeImages();
                
                // Sayfa animasyonlarını gereksiz ise devre dışı bırak
                if (navigator.deviceMemory && navigator.deviceMemory < 4) {
                    document.body.classList.add('reduce-animations');
                    var style = document.createElement('style');
                    style.textContent = '.reduce-animations * { transition-duration: 0.1s !important; animation-duration: 0.1s !important; }';
                    document.head.appendChild(style);
                }
                
                // Kaydırma olayını optimize et
                var scrollTimeout;
                window.addEventListener('scroll', function() {
                    clearTimeout(scrollTimeout);
                    scrollTimeout = setTimeout(optimizeImages, 200);
                }, { passive: true });
                
                return 'TKF Performance Optimization v2.0 Active';
            })();
        """.trimIndent(), null)
        
        // Bellek ve CPU optimizasyonu için GC tetikle - kademeli optimizasyon
        handler.postDelayed({
            webView.freeMemory()
            
            // Tüm görsellerin yüklenmesi bittiğinde son bir optimizasyon yap
            handler.postDelayed({
                webView.evaluateJavascript("""
                    (function() {
                        // Yükleme tamamlandıktan sonra son optimizasyonlar
                        if (window.TKF_performance) {
                            // Yükleme süresini kaydet
                            window.TKF_performance.loadTime = Date.now() - window.TKF_performance.startTime;
                            console.log('TKF: Page loaded in ' + window.TKF_performance.loadTime + 'ms');
                            
                            // Son görüntü optimizasyonu
                            window.TKF_performance.optimizeImages();
                        }
                        
                        // CSS animasyonlarını minimize et
                        document.body.classList.add('tkf-optimized');
                        
                        return 'TKF Final Optimization Complete';
                    })();
                """.trimIndent(), null)
            }, 2000) // Sayfa tamamen yüklendikten 2 saniye sonra
        }, 1000) // Sayfa yüklendikten 1 saniye sonra
    }
    
    /**
     * Optimizasyonu etkinleştir/devre dışı bırak
     */
    /**
     * Optimizasyonu etkinleştir/devre dışı bırak
     */
    open fun setOptimizationEnabled(enabled: Boolean) {
        isOptimizationEnabled = enabled
        
        if (enabled) {
            startMemoryOptimization()
        } else {
            handler.removeCallbacksAndMessages(null)
        }
    }
    
    /**
     * WebView'i ilk optimize etme - ek optimizasyonlar alt sınıflardan gelebilir
     */
    open fun initialOptimize(webView: WebView) {
        // Önbellek modunu optimum değere ayarla
        webView.settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        
        // DOM depolama desteğini aktifleştir - önbellek iyileştirmeleri için
        webView.settings.domStorageEnabled = true
        
        // Bellek kullanımını izlemeye başla
        startMemoryOptimization()
        
        Timber.d("WebView initial optimization applied")
    }
}