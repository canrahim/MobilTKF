package com.asforce.asforcetkf2.webview

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Handler
import android.os.Looper
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
import com.asforce.asforcetkf2.suggestion.SuggestionManager
import com.asforce.asforcetkf2.suggestion.WebViewSuggestionInterface
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
     * Inject input tracking script for suggestions
     */
    fun injectInputTracking() {
        val script = """
        (function() {
            // Check if already injected
            if (window._tkfInputTrackingInjected) return 'Already injected';
            window._tkfInputTrackingInjected = true;
            
            console.log('TKF Browser: Input tracking script injected');
            
            // Add event listeners to all input fields
            function setupInputTracking() {
                console.log('TKF Browser: Setting up input tracking for ' + 
                    document.querySelectorAll('input[type="text"], input[type="number"], input[type="email"], input[type="password"], textarea').length + 
                    ' elements');
                    
                document.querySelectorAll('input[type="text"], input[type="number"], input[type="email"], input[type="password"], textarea').forEach(function(input) {
                    // Skip if already tracked
                    if (input.hasAttribute('data-tkf-tracked')) return;
                    
                    // Get a key for the input field
                    var key = (input.name || input.id || input.placeholder || 'input_' + Math.random().toString(36).substr(2, 9));
                    key = key.replace(/[^a-zA-Z0-9_]/g, '_');
                    input.setAttribute('data-tkf-key', key);
                    console.log('TKF Browser: Tracking input field with key: ' + key);
                    
                    // Add focus event listener
                    input.addEventListener('focus', function() {
                        console.log('TKF Browser: Input focused: ' + key);
                        if (window.SuggestionHandler) {
                            window.SuggestionHandler.onInputFocused(key);
                        } else {
                            console.warn('TKF Browser: SuggestionHandler not available');
                        }
                    });
                    
                    // Add immediate focus notification for currently focused element
                    if (document.activeElement === input) {
                        console.log('TKF Browser: Input is already focused: ' + key);
                        setTimeout(function() {
                            if (window.SuggestionHandler) {
                                window.SuggestionHandler.onInputFocused(key);
                            }
                        }, 100);
                    }
                    
                    // Add input event listener
                    input.addEventListener('input', function() {
                        console.log('TKF Browser: Input changed: ' + key + ' = ' + input.value);
                        if (window.SuggestionHandler) {
                            window.SuggestionHandler.onInputChanged(key, input.value);
                        }
                    });
                    
                    // Add change event listener to save suggestions
                    input.addEventListener('change', function() {
                        console.log('TKF Browser: Input change event: ' + key + ' = ' + input.value);
                        if (input.value && window.SuggestionHandler) {
                            window.SuggestionHandler.saveInputSuggestion(key, input.value);
                        }
                    });
                    
                    // Mark as tracked
                    input.setAttribute('data-tkf-tracked', 'true');
                });
            }
            
            // Run immediately
            setupInputTracking();
            
            // Run again when DOM changes
            var observer = new MutationObserver(function(mutations) {
                console.log('TKF Browser: DOM changed, re-setting up input tracking');
                setupInputTracking();
            });
            
            observer.observe(document.body, { childList: true, subtree: true });
            
            // Run on form submission
            document.addEventListener('submit', function(e) {
                var form = e.target;
                var inputs = form.querySelectorAll('input[type="text"], input[type="email"], input[type="number"], textarea');
                
                console.log('TKF Browser: Form submitted with ' + inputs.length + ' input fields');
                inputs.forEach(function(input) {
                    var key = input.getAttribute('data-tkf-key');
                    if (key && input.value && window.SuggestionHandler) {
                        console.log('TKF Browser: Saving form input: ' + key + ' = ' + input.value);
                        window.SuggestionHandler.saveInputSuggestion(key, input.value);
                    }
                });
            });
            
            // Check for the active element on page load
            if (document.activeElement && 
                (document.activeElement.tagName === 'INPUT' || document.activeElement.tagName === 'TEXTAREA')) {
                var activeElement = document.activeElement;
                var key = activeElement.getAttribute('data-tkf-key');
                if (key && window.SuggestionHandler) {
                    console.log('TKF Browser: Active element on load: ' + key);
                    setTimeout(function() {
                        window.SuggestionHandler.onInputFocused(key);
                    }, 500);
                }
            }
            
            return 'Input tracking injected with enhanced logging';
        })();
        """
        
        evaluateJavascript(script) { result ->
            Timber.d("Input tracking injection result: $result")
        }
    }

    /**
     * Set suggestion manager for this WebView
     */
    fun setSuggestionManager(suggestionManager: SuggestionManager) {
        Timber.d("[SUGGESTION] Setting suggestion manager on WebView")
        // Add JavaScript interface for suggestions
        val suggestionInterface = WebViewSuggestionInterface(suggestionManager, this)
        addJavascriptInterface(suggestionInterface, "SuggestionHandler")
        
        // If tab is already initialized, inject the form handlers immediately
        if (tab != null) {
            Timber.d("[SUGGESTION] Tab already initialized, injecting handlers")
            // Immediate injection for faster response
            injectFormHandlers()
            injectInputTracking()
            injectManualFocusHandling()
            enhanceInputFocusDetection()
            
            // Also try to immediately find and focus input fields
            post {
                val findInputScript = """
                (function() {
                    var inputs = document.querySelectorAll('input[type="text"], input[type="search"], input[type="url"], input[type="number"], input[type="email"], input[type="password"], textarea');
                    console.log('TKF Browser: Found ' + inputs.length + ' input fields');
                    
                    if (inputs.length > 0) {
                        var focusedAny = false;
                        
                        // Check if any input is currently visible and near the viewport center
                        for (var i = 0; i < inputs.length; i++) {
                            if (inputs[i].offsetParent !== null && !inputs[i].disabled && !inputs[i].readOnly) {
                                console.log('TKF Browser: Found visible input: ' + inputs[i].tagName);
                                
                                // Get a key for this input
                                var key = (inputs[i].name || inputs[i].id || inputs[i].placeholder || 'input_' + Math.random().toString(36).substr(2, 9));
                                key = key.replace(/[^a-zA-Z0-9_]/g, '_');
                                inputs[i].setAttribute('data-tkf-key', key);
                                
                                // Notify SuggestionHandler if available
                                if (window.SuggestionHandler) {
                                    window.SuggestionHandler.onInputFocused(key);
                                    focusedAny = true;
                                    break;
                                }
                            }
                        }
                        
                        return focusedAny ? 'NOTIFIED_SUGGESTION_HANDLER' : 'NO_SUGGESTION_HANDLER';
                    }
                    
                    return 'NO_INPUTS_FOUND';
                })();
                """.trimIndent()
                
                evaluateJavascript(findInputScript) { result ->
                    Timber.d("[SUGGESTION] Initial input field search result: $result")
                }
            }
        }
    }
    
    /**
     * Enhanced keyboard input detection and handling
     */
    fun enhanceInputFocusDetection() {
        val script = """
        (function() {
            // Make sure we only inject once
            if (window._tkfEnhancedInputDetection) return 'Already enhanced';
            window._tkfEnhancedInputDetection = true;
            
            console.log('TKF Browser: Enhancing input focus detection');
            
            // Override HTMLElement.focus to catch all focus events
            var originalFocus = HTMLElement.prototype.focus;
            HTMLElement.prototype.focus = function() {
                var result = originalFocus.apply(this, arguments);
                
                // If this is an input or textarea, do extra handling
                if (this.tagName === 'INPUT' || this.tagName === 'TEXTAREA') {
                    console.log('TKF Browser: Element focused through focus() method: ' + this.tagName);
                    // Try to get or create a key for this element
                    var key = this.getAttribute('data-tkf-key');
                    if (!key) {
                        key = (this.name || this.id || this.placeholder || 'input_' + Math.random().toString(36).substr(2, 9));
                        key = key.replace(/[^a-zA-Z0-9_]/g, '_');
                        this.setAttribute('data-tkf-key', key);
                    }
                    
                    // Notify suggestion handler if available
                    if (window.SuggestionHandler) {
                        window.SuggestionHandler.onInputFocused(key);
                    }
                }
                
                return result;
            };
            
            // Enhanced touch handling with better suggestion detection
            document.addEventListener('touchstart', function(event) {
                setTimeout(function() {
                    if (document.activeElement && 
                        (document.activeElement.tagName === 'INPUT' || 
                         document.activeElement.tagName === 'TEXTAREA')) {
                        
                        var el = document.activeElement;
                        console.log('TKF Browser: Element focused after touch: ' + el.tagName);
                        
                        // Try to get or create a key
                        var key = el.getAttribute('data-tkf-key');
                        if (!key) {
                            key = (el.name || el.id || el.placeholder || 'input_' + Math.random().toString(36).substr(2, 9));
                            key = key.replace(/[^a-zA-Z0-9_]/g, '_');
                            el.setAttribute('data-tkf-key', key);
                        }
                        
                        // Notify suggestion handler
                        if (window.SuggestionHandler) {
                            console.log('TKF Browser: Notifying SuggestionHandler of focused input: ' + key);
                            window.SuggestionHandler.onInputFocused(key);
                        }
                    }
                }, 100);
            }, {passive: true});
            
            return 'Enhanced input focus detection added';
        })();
        """
        
        evaluateJavascript(script) { result ->
            Timber.d("[SUGGESTION] Enhanced input focus detection result: $result")
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
                // Girdi alanı izleme scriptini enjekte et
                injectInputTracking()
                // Manual focus handling
                injectManualFocusHandling()
                // Enhance input detection
                enhanceInputFocusDetection()
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
     * Load a URL in this WebView - Ultra hızlı yükleme optimizasyonu
     */
    override fun loadUrl(url: String) {
        // Ensure URL has schema
        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
        
        // Hızlı yükleme için görüntüleri engelle
        settings.blockNetworkImage = true
        
        // Hızlı önbellek için optimum strateji
        settings.cacheMode = if (isNetworkAvailable(context)) {
            WebSettings.LOAD_CACHE_ELSE_NETWORK
        } else {
            WebSettings.LOAD_CACHE_ONLY
        }
        
        // Kritik JavaScript ve depolama ayarları
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        
        // URL'i yükle
        Timber.d("Yüksek performans modunda yükleniyor: $formattedUrl")
        super.loadUrl(formattedUrl)
        
        // Sayfa yüklendikten 300ms sonra resimlere izin ver
        postDelayed({
            settings.blockNetworkImage = false
        }, 300)
    }
    
    /**
     * Ağ bağlantısının mevcut olup olmadığını kontrol et
     */
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        return capabilities != null && (
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
        )
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
     * Manual focus handling for input fields
     */
    private fun injectManualFocusHandling() {
        val script = """
        (function() {
            // Add touch handling script for input fields
            if (window._tkfTouchHandlingInjected) return 'Already injected';
            window._tkfTouchHandlingInjected = true;
            
            console.log('TKF Browser: Injecting manual touch handling');
            
            // For all touch events on the document
            document.addEventListener('touchstart', function(event) {
                // Get the touched element
                var el = document.elementFromPoint(event.touches[0].clientX, event.touches[0].clientY);
                console.log('TKF Browser: Touch detected on element: ' + (el ? el.tagName : 'unknown'));
                
                // Check if it's an input field
                if (el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA')) {
                    console.log('TKF Browser: Touch on input field: ' + el.tagName + '#' + el.id);
                    
                    // Focus the element
                    el.focus();
                    
                    // Get the input key
                    var key = el.getAttribute('data-tkf-key');
                    if (!key) {
                        // Create a key if not exists
                        key = (el.name || el.id || el.placeholder || 'input_' + Math.random().toString(36).substr(2, 9));
                        key = key.replace(/[^a-zA-Z0-9_]/g, '_');
                        el.setAttribute('data-tkf-key', key);
                    }
                    
                    // Notify SuggestionHandler
                    if (window.SuggestionHandler) {
                        console.log('TKF Browser: Manually notifying SuggestionHandler for key: ' + key);
                        window.SuggestionHandler.onInputFocused(key);
                    } else {
                        console.warn('TKF Browser: SuggestionHandler not available for manual focus');
                    }
                }
            }, { passive: true });
            
            return 'Manual touch handling injected';
        })();
        """
        
        evaluateJavascript(script) { result ->
            Timber.d("[SUGGESTION] Manual focus handling injection result: $result")
        }
    }
    
    /**
     * WebView'e doğrudan klavye girdisi simüle eden bir metod
     */
    fun simulateKeyboardInput(text: String) {
        Timber.d("[DIRECT INPUT] Attempting direct keyboard simulation for: '$text'")
        
        // WebView'in odakta olmasını sağla
        requestFocus()

        // YÖNTEM 1: Doğrudan değer atama ve DOM değiştirme - en güvenilir yöntem
        val enhancedScript = """
        (function() {
            try {
                // First find active element
                var activeElement = document.activeElement;
                var elementToUpdate = null;
                var debugInfo = "Initial state: ";
                
                // Check if we have a focused input element
                if (activeElement && (activeElement.tagName === 'INPUT' || activeElement.tagName === 'TEXTAREA')) {
                    elementToUpdate = activeElement;
                    debugInfo += "Found active element: " + elementToUpdate.tagName;
                } else {
                    // If no active element, search for visible inputs
                    debugInfo += "No active element found, searching for visible inputs. ";
                    var inputs = document.querySelectorAll('input[type="text"], input[type="search"], input[type="url"], input[type="number"], input[type="email"], input[type="password"], textarea');
                    
                    for (var i = 0; i < inputs.length; i++) {
                        var input = inputs[i];
                        // Check if the input is visible and not disabled
                        if (input.offsetParent !== null && !input.disabled && !input.readOnly) {
                            elementToUpdate = input;
                            // Force focus 
                            input.focus();
                            debugInfo += "Found visible input: " + input.tagName;
                            break;
                        }
                    }
                }
                
                // If we found an element to update
                if (elementToUpdate) {
                    // Remember original state
                    var originalValue = elementToUpdate.value;
                    
                    // Check if it's an input or textarea element
                    debugInfo += ". Attempting to update element.";
                    
                    // APPROACH 1: Directly set the value
                    elementToUpdate.value = '$text';
                    debugInfo += " Value set directly.";
                    
                    // APPROACH 2: Use Object.getOwnPropertyDescriptor if direct setting didn't work
                    if (elementToUpdate.value !== '$text') {
                        debugInfo += " Direct setting failed, trying descriptor.";
                        var descriptor = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(elementToUpdate), 'value');
                        if (descriptor && descriptor.set) {
                            descriptor.set.call(elementToUpdate, '$text');
                            debugInfo += " Used descriptor.";
                        }
                    }
                    
                    // APPROACH 3: Use execCommand if available
                    if (elementToUpdate.value !== '$text') {
                        debugInfo += " Trying execCommand.";
                        elementToUpdate.select();
                        if (document.execCommand) {
                            document.execCommand('insertText', false, '$text');
                            debugInfo += " Used execCommand.";
                        }
                    }
                    
                    // APPROACH 4: Character-by-character simulation
                    if (elementToUpdate.value !== '$text') {
                        debugInfo += " Trying character-by-character.";
                        // Clear input
                        elementToUpdate.value = '';
                        // Add character by character
                        for (var i = 0; i < '$text'.length; i++) {
                            elementToUpdate.value += '$text'.charAt(i);
                        }
                        debugInfo += " Used character-by-character.";
                    }
                    
                    // Dispatch events
                    function triggerEvent(element, eventName) {
                        try {
                            var event = new Event(eventName, { bubbles: true });
                            element.dispatchEvent(event);
                            debugInfo += " " + eventName + " event dispatched.";
                        } catch (e) {
                            debugInfo += " Error dispatching " + eventName + ": " + e.message + ".";
                            try {
                                // Fallback for older browsers
                                var fallbackEvent = document.createEvent('HTMLEvents');
                                fallbackEvent.initEvent(eventName, true, true);
                                element.dispatchEvent(fallbackEvent);
                                debugInfo += " " + eventName + " event dispatched with fallback.";
                            } catch (e2) {
                                debugInfo += " Even fallback failed: " + e2.message + ".";
                            }
                        }
                    }
                    
                    // Trigger events after updating value
                    triggerEvent(elementToUpdate, 'input');
                    triggerEvent(elementToUpdate, 'change');
                    
                    // APPROACH 5: Mock keyboard events as a last resort
                    if (elementToUpdate.value !== '$text') {
                        debugInfo += " Trying keyboard events.";
                        // Clear again
                        elementToUpdate.value = '';
                        elementToUpdate.focus();
                        
                        // Send keyboard events for each character
                        var chars = '$text'.split('');
                        for (var i = 0; i < chars.length; i++) {
                            var char = chars[i];
                            var keyCode = char.charCodeAt(0);
                            
                            try {
                                // Key down
                                var keydownEvent = new KeyboardEvent('keydown', {
                                    key: char,
                                    code: 'Key' + char.toUpperCase(),
                                    keyCode: keyCode,
                                    which: keyCode,
                                    bubbles: true
                                });
                                elementToUpdate.dispatchEvent(keydownEvent);
                                
                                // Key press
                                var keypressEvent = new KeyboardEvent('keypress', {
                                    key: char,
                                    code: 'Key' + char.toUpperCase(),
                                    keyCode: keyCode,
                                    which: keyCode,
                                    bubbles: true
                                });
                                elementToUpdate.dispatchEvent(keypressEvent);
                                
                                // Add character
                                elementToUpdate.value += char;
                                
                                // Key up
                                var keyupEvent = new KeyboardEvent('keyup', {
                                    key: char,
                                    code: 'Key' + char.toUpperCase(),
                                    keyCode: keyCode,
                                    which: keyCode,
                                    bubbles: true
                                });
                                elementToUpdate.dispatchEvent(keyupEvent);
                                
                                debugInfo += " Keyboard event for '" + char + "'.";
                            } catch (e) {
                                debugInfo += " Error with keyboard event: " + e.message + ".";
                            }
                        }
                        
                        // Ensure final value and trigger events again
                        elementToUpdate.value = '$text';
                        triggerEvent(elementToUpdate, 'input');
                        triggerEvent(elementToUpdate, 'change');
                    }
                    
                    // Focus element at the end to ensure it's selected
                    elementToUpdate.focus();
                    
                    // Check if we were successful
                    if (elementToUpdate.value === '$text') {
                        return "SUCCESS: Value set to '" + elementToUpdate.value + "'. " + debugInfo;
                    } else {
                        return "PARTIAL_SUCCESS: Value is '" + elementToUpdate.value + "' should be '$text'. " + debugInfo;
                    }
                } else {
                    return "NO_INPUT_FOUND: " + debugInfo;
                }
            } catch(e) {
                return "ERROR: " + e.message;
            }
        })();
        """.trimIndent()
        
        // Execute the enhanced script
        evaluateJavascript(enhancedScript) { result ->
            Timber.d("[DIRECT INPUT] Enhanced input result: $result")
            
            // If the first attempt fails, try a more aggressive approach with a slight delay
            if (!result.contains("SUCCESS")) {
                Handler(Looper.getMainLooper()).postDelayed({
                    // Force another attempt with a different strategy focused on active element
                    val lastResortScript = """
                    (function() {
                        try {
                            // Force focus on any input we can find
                            var allInputs = document.querySelectorAll('input, textarea');
                            var debugInfo = "Last resort: ";
                            var foundAny = false;
                            
                            for (var i = 0; i < allInputs.length; i++) {
                                try {
                                    var input = allInputs[i];
                                    if (input.offsetParent !== null) {
                                        input.focus();
                                        debugInfo += "Found visible input " + input.tagName + "#" + input.id + ". ";
                                        foundAny = true;
                                        
                                        // Set value
                                        input.value = '$text';
                                        
                                        // Trigger events
                                        var inputEvent = new Event('input', { bubbles: true });
                                        input.dispatchEvent(inputEvent);
                                        var changeEvent = new Event('change', { bubbles: true });
                                        input.dispatchEvent(changeEvent);
                                        
                                        debugInfo += "Set value and triggered events.";
                                        break;
                                    }
                                } catch(e) {
                                    debugInfo += "Error with input " + i + ": " + e.message + ". ";
                                }
                            }
                            
                            return foundAny ? "LAST_RESORT_SUCCESS: " + debugInfo : "LAST_RESORT_FAILED: " + debugInfo;
                        } catch(e) {
                            return "LAST_RESORT_ERROR: " + e.message;
                        }
                    })();
                    """.trimIndent()
                    
                    evaluateJavascript(lastResortScript) { fallbackResult ->
                        Timber.d("[DIRECT INPUT] Last resort result: $fallbackResult")
                    }
                }, 100) // Small delay before last resort
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