package com.asforce.asforcetkf2.webview

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import timber.log.Timber
import com.asforce.asforcetkf2.suggestion.SuggestionManager
import com.asforce.asforcetkf2.suggestion.WebViewSuggestionInterface
import com.asforce.asforcetkf2.model.Tab

/**
 * Custom WebView component that represents a browser tab with all necessary functionality
 */
class TabWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {
    
    var tab: Tab? = null
        
    // WebView bellek ve performans optimizasyonu için
    private lateinit var optimizer: WebViewOptimizer
    
    // Listeners
    var onPageStarted: ((String, String) -> Unit)? = null
    var onPageFinished: ((String, String, Bitmap?) -> Unit)? = null
    var onProgressChanged: ((String, Int) -> Unit)? = null
    var onReceivedTitle: ((String, String) -> Unit)? = null
    var onReceivedError: ((Int, String, String) -> Unit)? = null
    var onReceivedSslError: ((SslError) -> Boolean)? = null
    var onJsAlert: ((String, String, JsResult) -> Boolean)? = null
    var onJsConfirm: ((String, String, JsResult) -> Boolean)? = null
    var onFileChooser: ((ValueCallback<Array<Uri>>) -> Boolean)? = null
    var onLongPress: ((String, String) -> Unit)? = null
    
    init {
        setupWebView()
        // Optimizer'i başlat
        optimizer = WebViewOptimizer(this)
        optimizer.startMemoryOptimization()
    }
    
    /**
     * WebView konfigurasyonunu uygula - çerez ayarları ve gizlilik modu
     */
    private fun applyWebViewConfig() {
        // WebView DOM depolama izinlerini etkinleştir - oturum verileri için kritik
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        
        // Form verilerinin saklanmasını etkinleştir
        settings.saveFormData = true
        settings.savePassword = true
        
        // Çerez izinlerini etkinleştir ve güçlendir
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, true)
        
        // Oturum çerezlerinin kalıcı olmasını sağla
        cookieManager.flush()
        
        // HTML5 yerel depolama etkinleştir
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        
        // Karma içerik yönetimi - güvenlik için önemli
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        
        // JavaScript etkinleştirme
        settings.javaScriptEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
    }
    
    /**
     * WebView'i performans için optimize et
     */
    private fun optimizeForPerformance() {
        // Donanım hızlandırma - maksimum performans için
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        // Sayfa önce yapı olarak yüklensin, resimler daha sonra gelsin
        settings.blockNetworkImage = true
        settings.loadsImagesAutomatically = true
        
        // JavaScript yürütme optimizasyonu
        settings.setGeolocationEnabled(false) // Konum izinlerini devre dışı bırak
        settings.mediaPlaybackRequiresUserGesture = true // Kullanıcı etkileşimi olmadan medya oynatma
        
        // Gelişmiş önbellek stratejisi
        settings.cacheMode = WebSettings.LOAD_CACHE_ELSE_NETWORK
        
        // Kaydırma performansını iyileştir
        overScrollMode = OVER_SCROLL_NEVER
        scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        
        // DOM Storage ve veritabanı desteği
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        
        // Hızlı sayfa görüntüleme
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        
        // Görüntü ölçekleme optimizasyonu
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        
        // Maksimum performans için ek ayarlar
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH)
        
        // Text rendering optimizasyonu
        settings.textZoom = 100
    }
    
    /**
     * Configure the WebView with optimal settings
     */
    private fun setupWebView() {
        // Klavye sorununu çözmek için - bunu öncelikle tanımla
        isFocusableInTouchMode = true
        isFocusable = true
        isClickable = true
        isScrollContainer = true
        // Uzun basma olayını tanımla
        setOnLongClickListener {
            // Basılan link'i al
            val result = hitTestResult
            
            // Link URL'sini kontrol et
            if (result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                result.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                val url = result.extra
                if (url != null) {
                    // Link URL'sini ve sekme ID'sini callback'e gönder
                    onLongPress?.invoke(tab?.id ?: "", url)
                    return@setOnLongClickListener true
                }
            }
            false
        }
        
        // Klavye açıldığında içeriğin otomatik olarak kaydırılmasını sağla
        // Optimizasyon: Bu kısım performans problemi yaratabilir, iyileştirilmiş bir yaklaşımla değiştiriyoruz
        setOnApplyWindowInsetsListener { v, insets ->
            // Sadece klavye yüksekliği değiştiğinde padding'i güncelle
            // Bu, sürekli padding değişikliklerini önler
            if (v.paddingBottom != insets.systemWindowInsetBottom && insets.systemWindowInsetBottom > 0) {
                v.setPadding(
                    v.paddingLeft,
                    v.paddingTop,
                    v.paddingRight,
                    insets.systemWindowInsetBottom
                )
            } else if (insets.systemWindowInsetBottom == 0 && v.paddingBottom > 0) {
                // Klavye kapandığında padding'i kaldır
                v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, 0)
            }
            insets
        }
        
        // WebView performans ayarları
        setWillNotDraw(false)
        
        with(settings) {
            // Hızlandırma
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            cacheMode = WebSettings.LOAD_DEFAULT
            
            // JavaScript ve öbekleme davranışı (önemli performans ayarları)
            blockNetworkImage = true // Önce sayfa yüklensin, sonra resimler
            allowContentAccess = true
            
            // Önbellek kullanımı - Performans için kritik
            databaseEnabled = true
            domStorageEnabled = true
            
            // Görüntüleme ayarları
            useWideViewPort = true
            loadWithOverviewMode = true
            
            // JavaScript
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = true
            
            // Form ve kimlik doğrulama işlemleri için önemli ayarlar
            domStorageEnabled = true
            databaseEnabled = true
            saveFormData = true
            savePassword = true
            
            // Güvenlik
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            
            // HTTP desteği
            userAgentString = userAgentString.replace("; wv", "") + " TKFBrowser/1.0"

            // Harici içerikler
            allowFileAccess = true
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            
            // Metin/Zum
            textZoom = 100
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            // Kodlama
            defaultTextEncodingName = "UTF-8"
            
            // Ek performans optimizasyonları
            setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL)
            setNeedInitialFocus(false)
        }
        
        // WebView konfigurasyonunu uygula
        applyWebViewConfig()
        
        // Set initial scale
        setInitialScale(0)
        
        // Enable hardware acceleration
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        // Set scrollbar style
        scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
        
        // Reduce memory usage when not visible
        onVisibilityChanged(this, View.GONE)
        
        // WebView JavaScript ayarları
        settings.userAgentString += " AndroidWebKit/TKFBrowser"
        
        // Klavye ve odaklanma ayarları - artık yukarıda tanımlandı
        // İlave ayarlar: İçerik kayması sorunlarını önlemek için
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isHorizontalFadingEdgeEnabled = false
        isVerticalFadingEdgeEnabled = false
        
        // Performansı artırmak için asenkron yerine senkron işleme modu
        settings.blockNetworkLoads = false
        
        // WebView içinde klavye ve odaklanma işlevselliğini etkinleştir - GELİŞTİRİLMİŞ VERSİYON
        setOnTouchListener { v, event ->
            // İlk olarak tıklama olayını işle
            v.performClick()
            
            // Odaklanma sorunlarını önlemek için dokunma olaylarını kontrol et
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // WebView'e dokunulduğunda sadece odaklanmaya izin ver
                    // Klavye davranışını etkileme
                    if (!isFocused) {
                        requestFocusFromTouch()
                        v.requestFocus()
                    }
                }
                android.view.MotionEvent.ACTION_UP -> {
                    // Input alanına tıklandığında JavaScript ile kontrol et
                    // Tıklanan alan bir form alanı ise, klavyeyi nazikçe göster
                    evaluateJavascript("""
                    (function() {
                        try {
                            // Dokunma noktasındaki elementi bul
                            var x = ${event.x};
                            var y = ${event.y};
                            var element = document.elementFromPoint(x, y);
                            
                            // Eğer dokunulan yer bir giriş alanı ise
                            if (element && (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA')) {
                                // Olay işlendi sinyali gönder
                                return 'INPUT_TOUCHED';
                            }
                            return 'NOT_INPUT';
                        } catch(e) {
                            return 'ERROR: ' + e.message;
                        }
                    })();
                    """.trimIndent()) { result ->
                        // İşlem gerekmiyor - JavaScript zaten TKF fonksiyonlarını çağıracak
                    }
                }
            }
            
            // Olayı normal işle (WebView'in standart dokunma işlemlerini engelleme)
            false
        }
        
        // Optimize for performance
        optimizeForPerformance()
    }
    
    /**
     * Klavye girişini yönetmek için input connection oluştur
     */
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // Önce default davranışı al
        val connection = super.onCreateInputConnection(outAttrs)
        
        // Eğer super metodu null döndüyse veya aktif bir form elemanı varsa kendi connection'umızı oluştur
        if (connection == null) {
            // EditorInfo ayarları - klavye tipini değiştirme ve extract modu sorununu önle
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or 
                                  EditorInfo.IME_FLAG_NO_FULLSCREEN or 
                                  EditorInfo.IME_ACTION_DONE

            // İnput tipini sabit tut - karışık klavye sorununu önle
            outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT
            
            // Base input connection oluştur - true olması önemli! (input bağlantısı açık kalır)
            return BaseInputConnection(this, true)
        } else {
            // Mevcut bağlantıdan inputType'ı al, ancak değiştirme
            val currentInputType = outAttrs.inputType
            
            // IME seçeneklerinin tutarlı olmasını sağla
            // Bu bayraklar klavyenin kapanmadan açılmasına yardımcı olur
            outAttrs.imeOptions = outAttrs.imeOptions or 
                                  EditorInfo.IME_FLAG_NO_EXTRACT_UI or 
                                  EditorInfo.IME_FLAG_NO_FULLSCREEN or
                                  EditorInfo.IME_FLAG_NO_ENTER_ACTION
            
            outAttrs.imeOptions = outAttrs.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING.inv()
        }
        
        return connection
    }
    
    /**
     * Form alanları için JavaScript enjeksiyonu - Geliştirilmiş ve güçlendirilmiş versiyon
     */
    fun injectFormHandlers() {
        // Form işleme scriptleri...
    }
    
    /**
     * Inject input tracking script for suggestions
     */
    fun injectInputTracking() {
        // Input takip scriptleri...
    }

    /**
     * Enhanced keyboard input detection and handling
     */
    fun enhanceInputFocusDetection() {
        // Gelişmiş klavye algılama...
    }
    
    /**
     * Manual focus handling for input fields
     */
    fun injectManualFocusHandling() {
        // Manuel odak kontrolü için script...
    }
    
    /**
     * WebView'e doğrudan klavye girdisi simüle eden bir metod
     */
    fun simulateKeyboardInput(text: String) {
        // Klavye girişi simülasyonu...
    }

    /**
     * Set suggestion manager for this WebView
     */
    fun setSuggestionManager(suggestionManager: SuggestionManager) {
        // Add JavaScript interface for suggestions
        val suggestionInterface = WebViewSuggestionInterface(suggestionManager, this)
        addJavascriptInterface(suggestionInterface, "SuggestionHandler")
        
        // Immediate injection for faster response
        injectFormHandlers()
        injectInputTracking()
    }
    
    /**
     * Initialize this WebView with a tab
     */
    fun initialize(tab: Tab) {
        this.tab = tab
        
        // Çerez/oturum ayarlarını tekrar uygula
        applyWebViewConfig()
        
        // Set up WebViewClient
        webViewClient = TKFWebViewClient(
            tab = tab,
            onPageStarted = { tabId, url ->
                onPageStarted?.invoke(tabId, url)
            },
            onPageFinished = { tabId, url, favicon ->
                onPageFinished?.invoke(tabId, url, favicon)
            },
            onReceivedError = { errorCode, description, failingUrl ->
                onReceivedError?.invoke(errorCode, description, failingUrl)
            },
            onReceivedSslError = { error ->
                onReceivedSslError?.invoke(error) ?: false
            }
        )
        
        // Set up WebChromeClient
        webChromeClient = TKFWebChromeClient(
            tab = tab,
            onProgressChanged = { tabId, progress ->
                onProgressChanged?.invoke(tabId, progress)
            },
            onReceivedTitle = { tabId, title ->
                onReceivedTitle?.invoke(tabId, title)
            },
            onJsAlert = { url, message, result ->
                onJsAlert?.invoke(url, message, result) ?: false
            },
            onJsConfirm = { url, message, result ->
                onJsConfirm?.invoke(url, message, result) ?: false
            },
            onFileChooser = { callback ->
                onFileChooser?.invoke(callback) ?: false
            }
        )
        
        // Load the URL
        if (tab.url.isNotEmpty()) {
            loadUrl(tab.url)
        }
    }
    
    /**
     * Fix toast message visibility issues
     */
    fun fixToastVisibility() {
        // Toast mesajlarının görünürlüğünü düzeltme...
    }

    /**
     * Keyboard observer setup
     */
    private fun setupKeyboardObserver() {
        // Klavye gözlemcisi ayarları...
    }

    /**
     * Hibernate this WebView to save resources
     */
    fun hibernate() {
        // Stop loading, clear focus, pause timers
        stopLoading()
        clearFocus()
        pauseTimers()
        
        // Make it invisible to reduce rendering
        visibility = View.INVISIBLE
        
        // Resim yüklemeyi engelle
        settings.blockNetworkImage = true
        
        // Görüntüleme katmanını sistemle aynı hizaya getir
        setLayerType(View.LAYER_TYPE_NONE, null)
    }
    
    /**
     * Wake up a hibernated WebView
     */
    fun wakeUp() {
        // Resume timers, restore visibility
        resumeTimers()
        visibility = View.VISIBLE
        
        // Donanım hızlandırma etkinleştir
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        // Resimlerin yüklenmesine izin ver
        post {
            settings.blockNetworkImage = false
        }
        
        // Çerez ayarlarını tekrar kontrol et
        applyWebViewConfig()
    }
    
    /**
     * Clean up resources when this WebView is no longer needed
     */
    fun cleanup() {
        // Tüm yükleme ve işlemleri durdur
        try { stopLoading() } catch (e: Exception) {}
        
        try { settings.javaScriptEnabled = false } catch (e: Exception) {}
        
        // Önbellekleri temizle
        try { clearCache(true) } catch (e: Exception) {}
        try { clearFormData() } catch (e: Exception) {}
        try { clearHistory() } catch (e: Exception) {}
        try { clearSslPreferences() } catch (e: Exception) {}
        try { clearMatches() } catch (e: Exception) {}
        
        // Tüm görüntüleme işlemlerini durdur
        try { onPause() } catch (e: Exception) {}
        try { pauseTimers() } catch (e: Exception) {}
        
        // Bellek sızıntılarını önlemek için referansları temizle
        tag = null
        tab = null
        onPageStarted = null
        onPageFinished = null
        onProgressChanged = null
        onReceivedTitle = null
        onReceivedError = null
        onReceivedSslError = null
        onJsAlert = null
        onJsConfirm = null
        onFileChooser = null
        onLongPress = null
    }
} 