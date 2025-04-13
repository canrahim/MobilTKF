package com.asforce.asforcetkf2.webview

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.util.AttributeSet
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import com.asforce.asforcetkf2.model.Tab
import timber.log.Timber

/**
 * Custom WebView component that represents a browser tab with all necessary functionality
 */
class TabWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {
    
    var tab: Tab? = null
        private set
        
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
    var onDownloadRequested: ((String, String, String, String, Long) -> Unit)? = null
    
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
        
        // Gizli modu kapat, oturum bilgilerinin saklanmasını sağla
        try {
            val privateMode = WebView::class.java.getMethod("setPrivateBrowsingEnabled", Boolean::class.javaPrimitiveType)
            privateMode.invoke(this, false)
        } catch (e: Exception) {
            Timber.e("Error configuring WebView privacy mode: ${e.message}")
        }
        
        // Çerez ayarlarının gücellendiğinden emin olmak için tekrar flush yap
        cookieManager.flush()
        
        Timber.d("WebView configuration applied with enhanced cookie management")
    }
    
    /**
     * WebView'i performans için optimize et
     */
    private fun optimizeForPerformance() {
        // Donanım hızlandırma
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        // Yükleme performansını artır
        settings.blockNetworkImage = true // Önce sayfa yüklensin, sonra resimler
        settings.loadsImagesAutomatically = true
        
        // JavaScript yürütme optimizasyonu
        settings.setGeolocationEnabled(false) // Konum için izin istemesini önler
        settings.mediaPlaybackRequiresUserGesture = true // Otomatik video oynatmaları engeller
        
        // Önbellek ayarları
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        // settings.setAppCacheEnabled(true) // API level 33'te kaldırıldı
        
        // Kaydırma performansını iyileştir
        overScrollMode = OVER_SCROLL_NEVER
        scrollBarStyle = SCROLLBARS_INSIDE_OVERLAY
        isVerticalScrollBarEnabled = false
        
        // DOM Storage yönetimi
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        
        // Sayfa içeriği performansı
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        
        // Kayan ve animasyonlu öğeleri optimize et
        // settings.enableSmoothTransition = false // API level 33'te kaldırıldı
    }
    
    /**
     * Configure the WebView with optimal settings
     */
    private fun setupWebView() {
        // WebView performans ayarları
        setWillNotDraw(false)
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        
        with(settings) {
            // Hızlandırma
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            cacheMode = WebSettings.LOAD_DEFAULT
            
            // JavaScript ve öbekleme davranışı (önemli performans ayarları)
            blockNetworkImage = true // Önce sayfa yüklensin, sonra resimler
            allowContentAccess = true
            
            // Önbellek kullanımı - Performans için kritik
            // setAppCacheEnabled(true) // API level 33'te kaldırıldı
            databaseEnabled = true
            domStorageEnabled = true
            
            // Görüntüleme ayarları
            useWideViewPort = true
            loadWithOverviewMode = true
            
            // JavaScript
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = true // Form gönderimi için true olarak ayarlandı
            mediaPlaybackRequiresUserGesture = true // Otomatik medya oynatma engeli
            
            // Form ve kimlik doğrulama işlemleri için önemli ayarlar
            domStorageEnabled = true      // DOM storage enabled for forms/auth
            databaseEnabled = true        // Database storage enabled for forms/auth
            saveFormData = true           // Form verilerini kaydetme etkin
            savePassword = true           // Şifre kaydetme etkin
            
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
            // enableSmoothTransition = false // API level 33'te kaldırıldı
            setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL) // Daha hızlı layout
            setNeedInitialFocus(false) // Gereksiz fokus işlemlerini azalt
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
        
        // Klavye ve odaklanma ayarları
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        
        // Performansı artırmak için asenkron yerine senkron işleme modu
        settings.blockNetworkLoads = false
        
        // WebView içinde klavye işlevselliğini etkinleştir (dokunma performansı arttırıldı)
        setOnTouchListener { v, event ->
            // Dokunan alanı belirle ve odaklan
            if (isFocused) {
                // Zaten odaklandıysa işlem yapma
                v.performClick()
                false
            } else {
                requestFocusFromTouch()
                v.requestFocus()
                v.performClick()
                false
            }
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
        
        // Eğer super metodu null döndüyse kendi connection'umuzı oluştur
        if (connection == null) {
            // EditorInfo ayarları
            outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_DONE
            outAttrs.inputType = EditorInfo.TYPE_CLASS_TEXT or EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE or EditorInfo.TYPE_TEXT_FLAG_AUTO_CORRECT
            
            // Base input connection oluştur
            return BaseInputConnection(this, true)
        }
        
        return connection
    }
    
    /**
     * Form alanları için JavaScript enjeksiyonu - Geliştirilmiş ve güçlendirilmiş versiyon
     */
    fun injectFormHandlers() {
        // Form giriş, tıklama ve gönderim olaylarını yönetmek için optimize edilmiş script
        val script = """
        (function() {
          // Zaten enjekte edilmişse tekrar yapmayalım
          if (window._tkfInjected) return 'Already injected';
          window._tkfInjected = true;
          
          // Form odaklama yönetimi - minimal versiyon
          var focusHandler = function(e) {
            var target = e.target;
            if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA')) {
              target.focus();
            }
          };
          document.addEventListener('touchstart', focusHandler, {passive: true});
          document.addEventListener('click', focusHandler, {passive: true});
          
          // Sadece login formlarını arayalım
          var loginForm = document.querySelector('form[action*="login"], form[action*="Login"], form[action*="account"], form[id*="login"], form[class*="login"]');
          
          if (loginForm) {
            console.log('TKF-DEBUG: Login form found');
            
            // Form gönderim olayını yönetme - basitleştirilmiş
            loginForm.addEventListener('submit', function(e) {
              var formData = {};
              var inputs = this.querySelectorAll('input[type="text"], input[type="password"], input[type="email"]');
              for (var j = 0; j < inputs.length; j++) {
                var input = inputs[j];
                if (input.name) {
                  formData[input.name] = input.value;
                }
              }
              
              // Oturum verilerini lokalda sakla - minimal versiyon
              try {
                localStorage.setItem('_tkf_login_data', JSON.stringify(formData));
                document.cookie = 'TKF_LOGIN_SUBMIT=1; path=/; max-age=300';
              } catch(err) {}
              
              return true; // Formu normal gönder
            });
            
            // Formdaki ilk submit butonunu bul
            var submitButton = loginForm.querySelector('input[type="submit"], button[type="submit"]');
            if (submitButton) {
              submitButton.addEventListener('click', function() {
                document.cookie = 'TKF_LOGIN_CLICK=1; path=/; max-age=300';
              });
            }
          }
          
          // Sayfa yükleme sonrası form doldurma kodu - basitleştirilmiş
          try {
            if (document.cookie.indexOf('TKF_LOGIN_SUBMIT=1') !== -1) {
              var savedData = localStorage.getItem('_tkf_login_data');
              if (savedData) {
                var formData = JSON.parse(savedData);
                var form = document.querySelector('form');
                if (form) {
                  var inputs = form.querySelectorAll('input[type="text"], input[type="password"], input[type="email"]');
                  for (var i = 0; i < inputs.length; i++) {
                    var input = inputs[i];
                    if (input.name && formData[input.name]) {
                      input.value = formData[input.name];
                    }
                  }
                }
              }
            }
          } catch(e) {}
          
          return 'Enhanced form handlers injected';
        })();
        """

        // Script'i asenkron olarak çalıştır ve sonucu logla
        post {
            this.evaluateJavascript(script) { result ->
                Timber.d("Form handlers injection result: $result")
            }
        }
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
                // Sayfa yüklendiğinde form işleyicilerini enjekte et
                injectFormHandlers()
                // Yükleme sonrası performans optimizasyonu yap
                optimizer.optimizeAfterPageLoad(this@TabWebView)
                // Çerezleri kalıcı hale getir
                CookieManager.getInstance().flush()
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
        
        // Set up DownloadListener
        setDownloadListener(TKFDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            onDownloadRequested?.invoke(url, userAgent, contentDisposition, mimeType, contentLength)
        })
        
        // Load the URL
        if (tab.url.isNotEmpty()) {
            loadUrl(tab.url)
        }
    }
    
    /**
     * Login sayfası olup olmadığını kontrol et
     */
    private fun isLoginPage(url: String): Boolean {
        return url.contains("login", ignoreCase = true) ||
               url.contains("signin", ignoreCase = true) ||
               url.contains("auth", ignoreCase = true) ||
               url.contains("account", ignoreCase = true) ||
               url.contains("szutest.com.tr", ignoreCase = true)
    }

    /**
     * Load a URL in this WebView - Hızlandırılmış performans optimizasyonu
     */
    override fun loadUrl(url: String) {
        // Ensure URL has schema
        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
        
        // Sayfa yüklenmeden önce temel performans ayarları
        settings.blockNetworkImage = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT // Varsayılan önbellek stratejisini kullan
        
        // Temel çerez yönetimi - gerekli minimum ayarlar
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(this, true)
        
        // JavaScripti aktifleştir ve depolama izinlerini etkinleştir
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        
        // Login sayfaları için özel ayarlar - kısa ve özlü
        if (isLoginPage(formattedUrl)) {
            // Özel durum için haber ver
            Timber.d("Detected login/auth page: $formattedUrl")
            
            // Form kaydı - minimal gereksinimler
            settings.saveFormData = true
        }
        
        // URL bilgisini logla ve çerezleri güncelleyip yükle
        tab?.let {
            Timber.d("Loading URL: $formattedUrl in tab ${it.id}")
        }

        // Direkt yükleme yap - asenkron post kaldırıldı
        super.loadUrl(formattedUrl)
        
        // Oturum sayfaları için form handlerı enjekte et - diğer sayfalar için yapma
        if (isLoginPage(formattedUrl) && formattedUrl.contains("szutest.com.tr", ignoreCase = true)) {
            // Gecikme süresini kısalttık
            postDelayed({
                injectFormHandlers()
            }, 200)
        }
    }
    
    /**
     * Update tab state - Performans optimizasyonu eklendi
     */
    fun updateTabState(updatedTab: Tab) {
        // Tab durumu değişmiş mi kontrol et, gereksiz güncellemeleri önlemek için
        if (tab?.hashCode() != updatedTab.hashCode()) {
            tab = updatedTab
        }
    }
    
    /**
     * Hibernate this WebView to save resources - Performans iyileştirmesi eklendi
     */
    fun hibernate() {
        tab?.let {
            if (!it.isHibernated) {
                // Stop loading, clear focus, pause timers
                stopLoading()
                clearFocus()
                pauseTimers()
                
                // Make it invisible to reduce rendering
                visibility = View.INVISIBLE
                
                // Optimize for hibernation
                optimizer.optimizeForTabSwitch(this, it, false)
                
                // Resim yüklemeyi engelle
                settings.blockNetworkImage = true
                
                // Görüntüleme katmanını sistemle aynı hizaya getir
                setLayerType(View.LAYER_TYPE_NONE, null)
                
                Timber.d("Hibernated WebView for tab ${it.id}")
            }
        }
    }
    
    /**
     * Wake up a hibernated WebView - Performans iyileştirmesi eklendi
     */
    fun wakeUp() {
        tab?.let {
            if (it.isHibernated) {
                // Resume timers, restore visibility
                resumeTimers()
                visibility = View.VISIBLE
                
                // Optimize for active tab
                optimizer.optimizeForTabSwitch(this, it, true)
                
                // Donanım hızlandırma etkinleştir
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                
                // Resimlerin yüklenmesine izin ver
                post {
                    settings.blockNetworkImage = false
                }
                
                // Çerez ayarlarını tekrar kontrol et
                applyWebViewConfig()
                
                Timber.d("Woke up WebView for tab ${it.id}")
            }
        }
    }
    
    /**
     * Clean up resources when this WebView is no longer needed
     * Geliştirilmiş bellek temizleme
     */
    fun cleanup() {
        // Tüm yükleme ve işlemleri durdur
        stopLoading()
        
        // JavaScript'i devre dışı bırak 
        settings.javaScriptEnabled = false
        
        // Önbellekleri temizle
        clearCache(true)
        clearFormData()
        clearHistory()
        clearSslPreferences()
        clearMatches()
        
        // Cookie'leri temizleme - oturum bilgilerini korumak için kaldırıldı
        // CookieManager.getInstance().removeAllCookies(null)
        
        // Tüm görüntüleme işlemlerini durdur
        onPause()
        pauseTimers()
        
        // WebView'i yok et
        destroy()
        
        Timber.d("Cleaned up WebView for tab ${tab?.id}")
    }
}