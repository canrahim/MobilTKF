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
import com.asforce.asforcetkf2.suggestion.SuggestionManager
import com.asforce.asforcetkf2.suggestion.WebViewSuggestionInterface
import com.asforce.asforcetkf2.model.Tab
// import timber.log.Timber - removed for performance

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
            // Error configuring WebView privacy mode: ${e.message}
        }
        
        // Çerez ayarlarının gücellendiğinden emin olmak için tekrar flush yap
        cookieManager.flush()
        
        // WebView configuration applied with enhanced cookie management
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
     * Geliştirilmiş klavye kontrolü ve imlec yönetimi - Sorun giderme için güçlendirilmiş versiyon
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
            // ÖNEMLİ KLAVYE SORUNU ÇÖZÜMÜ:
            // Mevcut bağlantıdan inputType'ı al, ancak değiştirme
            val currentInputType = outAttrs.inputType
            
            // Numerik klavye tespiti yap ama mevcut tipe müdahale etme 
            // Bu, klavyenin kapanıp açılmasının ana nedenlerinden birini giderir
            if ((currentInputType and EditorInfo.TYPE_CLASS_NUMBER) == 0) {
                // İşlem yapmadan mevcut klavyeyi koru
                // outAttrs.inputType değiştirme - bu sorun yaratıyor
            }
            
            // IME seçeneklerinin tutarlı olmasını sağla
            // Bu bayraklar klavyenin kapanmadan açılmasına yardımcı olur
            outAttrs.imeOptions = outAttrs.imeOptions or 
                                  EditorInfo.IME_FLAG_NO_EXTRACT_UI or 
                                  EditorInfo.IME_FLAG_NO_FULLSCREEN or
                                  EditorInfo.IME_FLAG_NO_ENTER_ACTION // Enter tuşu için özel davranışı engelle
            
            // Bunu ekleyerek klavyenin yeniden oluşturulmasını engelliyoruz
            outAttrs.imeOptions = outAttrs.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING.inv()
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
        
        // Input alanına odaklandığında önceki odaklanmış elemanı kaydet
        var lastFocusedInput = null;
        
        // Form verileri yönetimi için özel fonksiyonlar
        window.TKF_FORM_DATA = window.TKF_FORM_DATA || {
            // Bellek içi form verilerini tutacak obje
            formStorage: {},
            
            // Form verilerini kaydet - hem bellekte hem de localStorage'da
            saveFormData: function(formId, data) {
                try {
                    // Bellek içi depolamaya kaydet
                    this.formStorage[formId] = data;
                    
                    // localStorage'a kaydet (kalıcı depolama)
                    localStorage.setItem('TKF_FORM_' + formId, JSON.stringify(data));
                    
                    // Form verisinin sayfalar arası geçişte korunması için
                    sessionStorage.setItem('TKF_FORM_' + formId, JSON.stringify(data));
                    
                    // Özel bir çerez ekleyerek form verilerinin varlığını işaretle
                    // Bu, sayfalar arası geçişlerde ve geri butonuyla gezinmede kritiktir
                    document.cookie = 'TKF_FORM_SAVED_' + formId + '=1; path=/; max-age=3600';
                    
                    console.log('TKF Browser: Form data saved for: ' + formId);
                    return true;
                } catch(e) {
                    console.error('TKF Browser: Error saving form data', e);
                    return false;
                }
            },
            
            // Form verilerini yükle - önce bellekten, yoksa localStorage'dan
            loadFormData: function(formId) {
                try {
                    // Önce bellekteki veriyi kontrol et
                    if (this.formStorage[formId]) {
                        return this.formStorage[formId];
                    }
                    
                    // Bellekte yoksa localStorage'a bak
                    var savedData = localStorage.getItem('TKF_FORM_' + formId);
                    if (!savedData) {
                        // localStorage'da yoksa sessionStorage'a bak (sayfa geçişleri için)
                        savedData = sessionStorage.getItem('TKF_FORM_' + formId);
                    }
                    
                    if (savedData) {
                        var data = JSON.parse(savedData);
                        // Belleğe de yükle
                        this.formStorage[formId] = data;
                        return data;
                    }
                    
                    // Veri bulunamadı
                    return null;
                } catch(e) {
                    console.error('TKF Browser: Error loading form data', e);
                    return null;
                }
            },
            
            // Bir formu verilerle doldur
            fillForm: function(form, data) {
                if (!form || !data) return false;
                
                var inputs = form.querySelectorAll('input, textarea, select');
                for (var i = 0; i < inputs.length; i++) {
                    var input = inputs[i];
                    
                    // Şifre alanlarını atla
                    if (input.type === 'password') continue;
                    
                    // Input'un name veya id'sine göre veri eşleştir
                    var fieldName = input.name || input.id;
                    if (fieldName && data[fieldName] !== undefined) {
                        // Değer ata
                        if (input.type === 'checkbox' || input.type === 'radio') {
                            input.checked = (input.value === data[fieldName]);
                        } else {
                            input.value = data[fieldName];
                            
                            // Input ve change olaylarını tetikleyerek değişikliği bildirme
                            try {
                                // Modern tarayıcılar için
                                input.dispatchEvent(new Event('input', {bubbles: true}));
                                input.dispatchEvent(new Event('change', {bubbles: true}));
                            } catch(e) {
                                // Eski tarayıcılar için
                                var inputEvent = document.createEvent('HTMLEvents');
                                inputEvent.initEvent('input', true, true);
                                input.dispatchEvent(inputEvent);
                                
                                var changeEvent = document.createEvent('HTMLEvents');
                                changeEvent.initEvent('change', true, true);
                                input.dispatchEvent(changeEvent);
                            }
                        }
                    }
                }
                
                return true;
            },
            
            // Form verilerini topla
            collectFormData: function(form) {
                var data = {};
                var inputs = form.querySelectorAll('input, textarea, select');
                
                for (var i = 0; i < inputs.length; i++) {
                    var input = inputs[i];
                    
                    // Sadece name veya id'si olan ve şifre olmayan alanları topla
                    var fieldName = input.name || input.id;
                    if (fieldName && input.type !== 'password' && input.type !== 'file') {
                        if (input.type === 'checkbox' || input.type === 'radio') {
                            if (input.checked) {
                                data[fieldName] = input.value;
                            }
                        } else {
                            data[fieldName] = input.value;
                        }
                    }
                }
                
                return data;
            },
            
            // Benzersiz form ID'si oluştur
            getFormId: function(form) {
                // Form ID belirleme önceliği: id > name > action > konum
                var id = form.id || form.name || '';
                
                if (!id) {
                    // Form action URL'inden ID oluştur
                    var action = form.action || '';
                    if (action) {
                        id = action.split('?')[0]; // Query parametrelerini kaldır
                        id = id.split('/').pop(); // Son path segmentini al
                    }
                }
                
                if (!id) {
                    // Sayfa URL'inden ID oluştur
                    id = window.location.pathname.split('/').pop() || 'form';
                }
                
                // Belirleyici olması için form içindeki ilk birkaç alan adını ekle
                var inputs = form.querySelectorAll('input[name]');
                if (inputs.length > 0) {
                    var firstFieldName = inputs[0].name;
                    id += '_' + firstFieldName;
                }
                
                // ID'yi temizle ve döndür
                return id.replace(/[^a-zA-Z0-9_]/g, '_').toLowerCase();
            }
        };
        
        // Input odaklanma yönetimi - klavyeyi korumaya çalışan versiyon
        var focusHandler = function(e) {
        var target = e.target;
          if (target && (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA')) {
            // Bu yeni bir odaklanma mı yoksa aynı input mu kontrol et
            if (lastFocusedInput !== target) {
              // Yeni bir input alanı - önce eski elemanı kaydet
              var previousFocused = lastFocusedInput;
              // Yeni elemanı son odaklı olarak ayarla
              lastFocusedInput = target;
              
            // Eğer bu ilk odaklanma değilse ve önceki input da varsa
            // Klavyeyi kapatıp açma sorununu önle
            if (previousFocused) {
                // Klavye kapanmasını önleyen önemli değişiklik
                e.preventDefault();
                e.stopPropagation();
                window.native_input_switching = true;
                
                // Önceki input ile şu anki input aynı tipte mi kontrol et 
                var prevType = previousFocused.type || 'text';
                var currentType = target.type || 'text';
                
                // Input tiplerini ve ID'leri log et
                console.log('TKF Browser: Input switch from', prevType, 'to', currentType);
                
                // İmleç hareketlerini kontrol etmek için kullanılabilir:
                var prevId = previousFocused.id || previousFocused.name || 'unknown';
                var currentId = target.id || target.name || 'unknown';
                console.log('TKF Browser: Input switch from', prevId, 'to', currentId);
                
                // Aynı tip klavyede otomatik odaklanma için anahtar belirle
                var key = target.getAttribute('data-tkf-key');
                if (!key) {
                  key = (target.name || target.id || target.placeholder || 'input_' + Math.random().toString(36).substr(2, 9));
                  key = key.replace(/[^a-zA-Z0-9_]/g, '_');
                  target.setAttribute('data-tkf-key', key);
                }
                
                // SuggestionHandler'a bildir - ama klavyeyi kapatmadan (önemli)
                if (window.SuggestionHandler) {
                  window.SuggestionHandler.onSmoothInputFocusChanged(key);
                }
                
                // Klavyeyi açık tutmak için input odağını koru
                setTimeout(function() {
                  target.focus();
                  if (typeof target.select === 'function') {
                    try { target.select(); } catch(e) {}
                  }
                }, 10);
                
                return false; // Olayı durdur
            }
            }
            
            // Her durumda odaklanmayı garanti et
            setTimeout(function() {
              target.focus();
            }, 0);
          }
        };
        document.addEventListener('touchstart', focusHandler, {passive: true});
        document.addEventListener('click', focusHandler, {passive: true});
        
        // Sayfadaki tüm formları işle ve iyileştir
        var enhanceForms = function() {
            var forms = document.querySelectorAll('form');
            console.log('TKF-DEBUG: Found ' + forms.length + ' forms on page');
            
            for (var i = 0; i < forms.length; i++) {
                var form = forms[i];
                
                // Daha önce işlenmiş formu atla
                if (form.hasAttribute('data-tkf-enhanced')) continue;
                
                // Form'u işaretleme
                form.setAttribute('data-tkf-enhanced', 'true');
                
                // Form için benzersiz ID oluştur ve işaretle
                var formId = window.TKF_FORM_DATA.getFormId(form);
                form.setAttribute('data-tkf-id', formId);
                
                console.log('TKF-DEBUG: Processing form:', formId);
                
                // Form gönderim olayını yönetme
                form.addEventListener('submit', function(e) {
                    var thisForm = this;
                    var formId = thisForm.getAttribute('data-tkf-id');
                    
                    // Form verilerini topla
                    var formData = window.TKF_FORM_DATA.collectFormData(thisForm);
                    
                    // Form verilerini kaydet
                    window.TKF_FORM_DATA.saveFormData(formId, formData);
                    
                    console.log('TKF-DEBUG: Form submitted:', formId);
                    return true; // Formu normal gönder
                });
                
                // Form input alanlarındaki değişiklikleri takip et
                var inputs = form.querySelectorAll('input, textarea, select');
                for (var j = 0; j < inputs.length; j++) {
                    var input = inputs[j];
                    
                    // Değişiklik olayını dinle
                    input.addEventListener('change', function() {
                        var thisInput = this;
                        var thisForm = thisInput.form;
                        
                        if (thisForm) {
                            var formId = thisForm.getAttribute('data-tkf-id');
                            if (formId) {
                                // Güncel form verilerini topla
                                var formData = window.TKF_FORM_DATA.collectFormData(thisForm);
                                
                                // Form verilerini kaydet
                                window.TKF_FORM_DATA.saveFormData(formId, formData);
                                console.log('TKF-DEBUG: Form data updated on change:', formId);
                            }
                        }
                    });
                }
                
                // Kaydedilmiş form verilerini yükle ve doldur
                var savedData = window.TKF_FORM_DATA.loadFormData(formId);
                if (savedData) {
                    window.TKF_FORM_DATA.fillForm(form, savedData);
                    console.log('TKF-DEBUG: Loaded saved data for form:', formId);
                }
            }
        };
        
        // Sayfa tam yüklendiğinde formları iyileştir
        if (document.readyState === 'complete') {
            enhanceForms();
        } else {
            window.addEventListener('load', enhanceForms);
        }
        
        // DOM değişikliklerini izle (dinamik form eklemeleri için)
        if (window.MutationObserver) {
            var observer = new MutationObserver(function(mutations) {
                // Değişiklik olduğunda formları tarayıp işle
                enhanceForms();
            });
            
            // Gözlemciyi başlat - tüm DOM'u izle
            if (document.body) {
                observer.observe(document.body, {
                    childList: true,
                    subtree: true
                });
            }
        }
        
        // Sayfa geçişleri için popstate olayını dinle (geri butonu)
        window.addEventListener('popstate', function(e) {
            // Sayfa geçişi olduğunda kısa bir süre bekle ve formları doldur
            setTimeout(enhanceForms, 300);
            console.log('TKF-DEBUG: Page navigation detected (popstate)');
        });
        
        // Hashchange olayı (tek sayfa uygulamaları için)
        window.addEventListener('hashchange', function() {
            // Hash değiştiğinde formları yeniden işle
            setTimeout(enhanceForms, 300);
            console.log('TKF-DEBUG: Page hash changed');
        });
        
        return 'Enhanced form handlers injected with improved data persistence';
        })();
        """

        // Script'i asenkron olarak çalıştır ve sonucu logla
        post {
            this.evaluateJavascript(script) { result ->
                // Form handlers injection result: $result
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
                    
                    // Otomatik odaklama için aktif element kontrolünü kaldırıyoruz
                    
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
        
        evaluateJavascript(script) { _ -> }
    }

    /**
     * Set suggestion manager for this WebView
     */
    fun setSuggestionManager(suggestionManager: SuggestionManager) {
        // Add JavaScript interface for suggestions
        val suggestionInterface = WebViewSuggestionInterface(suggestionManager, this)
        addJavascriptInterface(suggestionInterface, "SuggestionHandler")
        
        // If tab is already initialized, inject the form handlers immediately
        if (tab != null) {
            // Immediate injection for faster response
            injectFormHandlers()
            injectInputTracking()
            injectManualFocusHandling()
            enhanceInputFocusDetection()
            
            // Input alanlarını hazırla ama otomatik odaklama yapma
            post {
                val findInputScript = """
                (function() {
                    var inputs = document.querySelectorAll('input[type="text"], input[type="search"], input[type="url"], input[type="number"], input[type="email"], input[type="password"], textarea');
                    console.log('TKF Browser: Found ' + inputs.length + ' input fields');
                    
                    if (inputs.length > 0) {
                        // Form alanlarının sadece key'lerini etiketle, otomatik odaklama yapma
                        for (var i = 0; i < inputs.length; i++) {
                            if (inputs[i].offsetParent !== null && !inputs[i].disabled && !inputs[i].readOnly) {
                                console.log('TKF Browser: Found visible input: ' + inputs[i].tagName);
                                
                                // Get a key for this input
                                var key = (inputs[i].name || inputs[i].id || inputs[i].placeholder || 'input_' + Math.random().toString(36).substr(2, 9));
                                key = key.replace(/[^a-zA-Z0-9_]/g, '_');
                                inputs[i].setAttribute('data-tkf-key', key);
                                
                                // Artık otomatik odaklama yapmıyoruz, kullanıcı manuel olarak dokunacak
                            }
                        }
                        
                        return 'INPUTS_PREPARED_NO_AUTO_FOCUS';
                    }
                    
                    return 'NO_INPUTS_FOUND';
                })();
                """.trimIndent()
                
                evaluateJavascript(findInputScript) { _ -> }
            }
        }
    }
    
    /**
     * Enhanced keyboard input detection and handling (normal version)
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
            
            // Otomatik odaklama özelliği kaldırıldı - kullanıcılar artık manuel olarak dokunmaları gerekecek
            
            return 'Normal input focus detection added';
        })();
        """
        
        evaluateJavascript(script) { _ -> }
    }
    
    /**
     * Initialize this WebView with a tab
     */
    fun initialize(tab: Tab) {
        this.tab = tab
        
        // Çerez/oturum ayarlarını tekrar uygula
        applyWebViewConfig()
        
        // Klavye durumunu takip et ve sayfa kaydırma işlemini optimize et
        setupKeyboardObserver()
        
        // Set up WebViewClient
        webViewClient = TKFWebViewClient(
            tab = tab,
            onPageStarted = { tabId, url ->
                // Mevcut zaman aşımı varsa iptal et
                loadTimeoutHandler?.removeCallbacksAndMessages(null)
                
                // Şu anki yüklenen URL'i takip et
                currentLoadingUrl = url
                
                // Yeni bir zaman aşımı başlat
                loadTimeoutHandler = Handler(Looper.getMainLooper())
                loadTimeoutHandler?.postDelayed({
                    if (url == currentLoadingUrl) {
                        // Sayfa yükleme zaman aşımı oluştu: $url
                        
                        // WebView hala geçerli mi kontrol et
                        if (isAttachedToWindow && !isDestroyed()) {
                            try {
                                // Acil durum tamamlama scripti çalıştır
                                evaluateJavascript("""
                                    (function() {
                                        console.log('TKF Browser: Zaman aşımı kurtarma başlatılıyor');
                                        // Yükleme olaylarını manuel olarak tetikle
                                        if (document.readyState !== 'complete') {
                                            try {
                                                window.dispatchEvent(new Event('DOMContentLoaded'));
                                                window.dispatchEvent(new Event('load'));
                                                document.dispatchEvent(new Event('readystatechange'));
                                                console.log('TKF Browser: Zorunlu yükleme olayları tetiklendi');
                                            } catch(e) {
                                                console.error('Yükleme olayları hatası:', e);
                                            }
                                        }
                                        return 'timeout_recovery_triggered';
                                    })();
                                """.trimIndent()) { _ ->
                                    // Sayfa yükleme olayını manuel olarak çağır - önce WebView durumunu kontrol et
                                    if (isAttachedToWindow && !isDestroyed()) {
                                        onPageFinished?.invoke(tabId, url, null)
                                    }
                                }
                            } catch (e: Exception) {
                                // Error during timeout recovery
                                loadTimeoutHandler?.removeCallbacksAndMessages(null)
                                currentLoadingUrl = null
                            }
                        } else {
                            // WebView has been destroyed or detached, skipping timeout recovery
                            loadTimeoutHandler?.removeCallbacksAndMessages(null)
                            currentLoadingUrl = null
                        }
                    }
                }, LOAD_TIMEOUT_MS)
                
                onPageStarted?.invoke(tabId, url)
            },
            onPageFinished = { tabId, url, favicon ->
                // Zaman aşımını iptal et, sayfa yüklendi
                loadTimeoutHandler?.removeCallbacksAndMessages(null)
                currentLoadingUrl = null
                
                onPageFinished?.invoke(tabId, url, favicon)
                
                // DOM hazır olduğundan emin olmak için kısa bir gecikme ekle
                if (isAttachedToWindow && !isDestroyed()) {
                    postDelayed({
                        // WebView hala geçerli mi kontrol et
                        if (isAttachedToWindow && !isDestroyed()) {
                            try {
                                // Sayfa yüklendiğinde form işleyicilerini enjekte et
                                injectFormHandlers()
                                // Girdi alanı izleme scriptini enjekte et
                                injectInputTracking()
                                // Manual focus handling
                                injectManualFocusHandling()
                                // Enhance input detection
                                enhanceInputFocusDetection()
                                // Fix toast message visibility issues
                                fixToastVisibility()
                                
                                // Sayfa geçişi kontrol et ve form verilerini geri yükle
                                if (isAttachedToWindow && !isDestroyed()) {
                                    // Özellikle geri butonuyla gezinme durumlarında form verilerinin korunması için
                                    evaluateJavascript("""
                                        (function() {
                                            // Form verilerini sayfa geçişlerinde yeniden yükleme kontrolü
                                            if (window.TKF_IS_BACK_NAVIGATION || localStorage.getItem('TKF_BACK_NAV_URL') === window.location.href) {
                                                console.log('TKF Browser: Checking for form data to restore after navigation');
                                                
                                                // Sayfa geçişi sonrası form verilerini yeniden yükle
                                                if (window.TKF_FORM_DATA) {
                                                    // Formları ara ve işle
                                                    var forms = document.querySelectorAll('form');
                                                    console.log('TKF Browser: Found ' + forms.length + ' forms to check for data restoration');
                                                    
                                                    var formsProcessed = 0;
                                                    for (var i = 0; i < forms.length; i++) {
                                                        // Her form için benzersiz ID oluştur ve veri arama
                                                        var formId = window.TKF_FORM_DATA.getFormId(forms[i]);
                                                        forms[i].setAttribute('data-tkf-id', formId);
                                                        
                                                        // Kaydedilmiş verileri yükle
                                                        var savedData = window.TKF_FORM_DATA.loadFormData(formId);
                                                        if (savedData) {
                                                            // Form verileriyle doldur
                                                            window.TKF_FORM_DATA.fillForm(forms[i], savedData);
                                                            formsProcessed++;
                                                            console.log('TKF Browser: Restored data for form: ' + formId);
                                                        }
                                                    }
                                                    
                                                    return 'TKF_FORMS_RESTORED: ' + formsProcessed + ' of ' + forms.length;
                                                }
                                            }
                                            return 'TKF_NO_NAVIGATION_DETECTED';
                                        })();
                                    """.trimIndent()) { result ->
                                        // Form veri restorasyonu sonucunu logla
                                        // Form data restoration result: $result
                                    }
                                }
                                
                                // Yükleme sonrası performans optimizasyonu yap
                                optimizer.optimizeAfterPageLoad(this@TabWebView)
                                // Çerezleri kalıcı hale getir
                                CookieManager.getInstance().flush()
                            } catch (e: Exception) {
                                // Sayfa yüklendikten sonra script enjeksiyonu sırasında hata
                            }
                        }
                    }, 100) // DOM işlemleri için kısa gecikme
                }
            },
            onReceivedError = { errorCode, description, failingUrl ->
                // Hata durumunda zaman aşımını iptal et
                if (failingUrl == currentLoadingUrl) {
                    loadTimeoutHandler?.removeCallbacksAndMessages(null)
                    currentLoadingUrl = null
                }
                
                onReceivedError?.invoke(errorCode, description, failingUrl)
                
                // Kritik hata - sayfa yüklenemedi, acil durum kurtarma dene
                if (errorCode < 0) { // Negatif hata kodları genellikle ağ hatalarıdır
                    // Kritik sayfa yükleme hatası: $errorCode, $description
                    
                    // Hata mesajını göster ve kullanıcıya bilgi ver
                    post {
                        evaluateJavascript("""
                            (function() {
                                document.body.innerHTML = '<div style="padding:20px;text-align:center;">' +
                                    '<h2>Sayfa yüklenirken hata oluştu</h2>' +
                                    '<p>Hata: $description</p>' +
                                    '<p>URL: $failingUrl</p>' +
                                    '<button onclick="window.location.reload()">Yeniden Dene</button>' +
                                    '</div>';
                                return 'error_page_created';
                            })();
                        """.trimIndent(), null)
                    }
                }
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
     * Verilen URL'nin bilinen bir form URL'si olup olmadığını kontrol et
     */
    private fun isKnownFormUrl(url: String): Boolean {
        return url.contains("login", ignoreCase = true) ||
               url.contains("signup", ignoreCase = true) ||
               url.contains("register", ignoreCase = true) ||
               url.contains("account", ignoreCase = true) ||
               url.contains("contact", ignoreCase = true) ||
               url.contains("checkout", ignoreCase = true) ||
               url.contains("szutest.com.tr", ignoreCase = true)
    }
    
    /**
     * Verilen URL'ye geri buton navigasyonu mu yapıldığını kontrol et
     */
    private fun isBackNavigation(url: String): Boolean {
        // Son ziyaret edilen URL ve mevcut URL kontrolü
        return lastVisitedUrl != null && url == lastVisitedUrl
    }

    /**
     * Load a URL in this WebView - Ultra hızlı yükleme optimizasyonu v2.2
     * Form verilerinin korunması için geliştirilmiş önbellek stratejisi ve bellek yönetimi
     */
    // Sayfa yükleme zaman aşımı değişkenleri
    private var loadTimeoutHandler: Handler? = null
    private var currentLoadingUrl: String? = null
    private val LOAD_TIMEOUT_MS = 20000L // 20 saniye zaman aşımı
    private var lastVisitedUrl: String? = null // Son ziyaret edilen URL'yi takip etmek için
    
    override fun loadUrl(url: String) {
        // Ensure URL has schema
        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
        
        // Eğer aynı sayfanın yenilenmesi değilse, son URL'yi kaydet
        if (lastVisitedUrl != formattedUrl && this.url != formattedUrl) {
            lastVisitedUrl = this.url
        }
        
        // Mevcut zaman aşımı varsa iptal et
        loadTimeoutHandler?.removeCallbacksAndMessages(null)
        
        // Şu anki yüklenen URL'i takip et
        currentLoadingUrl = formattedUrl
        
        // GERİ BUTONUYLA GEÇİŞLERİ İŞARETLE - Form verileri için çok önemli
        val isBackNavigation = isBackNavigation(formattedUrl)
        
        // Sayfa geçişini işaretle (form verilerinin korunması için JavaScript'e bildirilecek)
        if (isBackNavigation) {
            // Geri dönüşlerde form verilerinin korunması için sayfa geçiş bilgisini ekleyelim
            evaluateJavascript("""
                (function() {
                    // Geri navigasyon bilgisini kaydet
                    window.TKF_IS_BACK_NAVIGATION = true;
                    window.TKF_PREVIOUS_URL = '$lastVisitedUrl';
                    window.TKF_CURRENT_URL = '$formattedUrl';
                    console.log('TKF Browser: Back navigation detected to ' + '$formattedUrl');
                    
                    // Geri navigasyon durumunu localStorage'a da kaydet (sayfa yenilenirse kullanmak için)
                    try {
                        localStorage.setItem('TKF_BACK_NAV_URL', '$formattedUrl');
                        localStorage.setItem('TKF_PREVIOUS_URL', '$lastVisitedUrl');
                        localStorage.setItem('TKF_BACK_NAV_TIME', Date.now().toString());
                    } catch(e) {}
                    
                    return 'BACK_NAV_SET';
                })();
            """.trimIndent(), null)
        }
        
        // Hızlı yükleme için görüntüleri engelle
        settings.blockNetworkImage = true
        
        // GELİŞTİRİLMİŞ ÖNBELLEK STRATEJİSİ - Form verileri için kritik
        val isFormUrl = formattedUrl.contains("form", ignoreCase = true) || 
                        isKnownFormUrl(formattedUrl)
        
        // Form sayfaları için önbellek modunu ayarla
        settings.cacheMode = if (isFormUrl || isBackNavigation) {
            // Form sayfaları ve geri dönüşler için önbelleği daha agresif kullan
            WebSettings.LOAD_CACHE_ELSE_NETWORK
        } else if (isNetworkAvailable(context)) {
            // Normal sayfalar için standart önbellek stratejisi
            WebSettings.LOAD_DEFAULT
        } else {
            // Ağ yoksa sadece önbellekten yükle
            WebSettings.LOAD_CACHE_ONLY
        }
        
        // Kritik JavaScript ve depolama ayarları
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.saveFormData = true // Form verilerinin kaydedilmesini etkinleştir
        
        // Form sayfaları için form verilerinin korunmasını sağla
        if (isFormUrl || isBackNavigation) {
            // Çerez yönetimi ve oturum verilerinin korunması
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)
            cookieManager.flush() // Çerezleri kaydet
        }
        
        // URL'i yükle
        // Yüksek performans modunda yükleniyor: $formattedUrl
        super.loadUrl(formattedUrl)
        
        // Sayfa yüklendikten 300ms sonra resimlere izin ver
        postDelayed({
            settings.blockNetworkImage = false
        }, 300)
        
        // Sayfa yüklenmesi için zaman aşımı ayarla
        loadTimeoutHandler = Handler(Looper.getMainLooper())
        loadTimeoutHandler?.postDelayed({
            if (formattedUrl == currentLoadingUrl) {
                // Sayfa yükleme zaman aşımı: $formattedUrl
                
                // Takılmış sayfayı kurtarmak için acil durum scripti çalıştır
                try {
                    // WebView'in hala aktif olup olmadığını kontrol et
                    if (!isDestroyed() && isAttachedToWindow) {
                        evaluateJavascript("""
                            (function() {
                                // Eğer sayfa takılmışsa document readyState'i force complete yap
                                if (document.readyState !== 'complete') {
                                    // Sentetik yükleme olaylarını tetikle
                                    try {
                                        window.dispatchEvent(new Event('DOMContentLoaded'));
                                        window.dispatchEvent(new Event('load'));
                                        document.dispatchEvent(new Event('readystatechange'));
                                        console.log('TKF Browser: Zaman aşımı sonrası zorunlu yükleme olayları tetiklendi');
                                        
                                        // WebView'e sayfa yükleme bitti sinyali gönder
                                        setTimeout(function() {
                                            document.dispatchEvent(new Event('webviewready'));
                                        }, 100);
                                    } catch(e) {
                                        console.error('Yükleme olaylarını zorlama hatası:', e);
                                    }
                                }
                                
                                // Bekleyen kaynakları engellemeyi kaldır
                                try {
                                    // Bekleyen görüntü yüklemelerini bul ve iptal et
                                    var images = document.querySelectorAll('img');
                                    for (var i = 0; i < images.length; i++) {
                                        if (!images[i].complete && images[i].src) {
                                            // Yeniden denemek için orijinal src'yi kaydet
                                            var originalSrc = images[i].src;
                                            images[i].setAttribute('data-original-src', originalSrc);
                                            // Yüklemeyi iptal et
                                            images[i].src = '';
                                            // Yedek görüntü göster
                                            images[i].alt = 'Görüntü yüklenemedi';
                                        }
                                    }
                                    
                                    // Mümkünse bekleyen XHR isteklerini iptal et
                                    if (window.XMLHttpRequest) {
                                        console.log('TKF Browser: XHR istekleri zaman aşımı nedeniyle durduruldu');
                                    }
                                    
                                    // Form alanlarını aktif et (en azından kullanıcı veri girebilsin)
                                    var forms = document.querySelectorAll('form');
                                    for (var j = 0; j < forms.length; j++) {
                                        forms[j].setAttribute('data-tkf-timeouted', 'true');
                                    }
                                    
                                    return 'TKF_TIMEOUT_RECOVERY_COMPLETED';
                                } catch(e) {
                                    return 'TKF_TIMEOUT_RECOVERY_ERROR: ' + e.message;
                                }
                            })();
                        """.trimIndent()) { result ->
                            // Zaman aşımı kurtarma sonucu: $result
                            
                            // Sayfa yükleme bitti olayını manuel olarak tetikle
                            if (tab != null && isAttachedToWindow && !isDestroyed()) {
                                onPageFinished?.invoke(tab?.id ?: "", formattedUrl, null)
                            }
                        }
                    } else {
                        // WebView zaman aşımı sırasında yok edilmiş veya ayrılmış, JavaScript çalıştırma atlanıyor
                        // Temizlik yapılıyor
                        loadTimeoutHandler?.removeCallbacksAndMessages(null)
                        currentLoadingUrl = null
                    }
                } catch (e: Exception) {
                    // JavaScript hata durumunu yakala ve logla
                    // Zaman aşımı sırasında JavaScript çalıştırma hatası
                    loadTimeoutHandler?.removeCallbacksAndMessages(null)
                    currentLoadingUrl = null
                }
            }
        }, LOAD_TIMEOUT_MS)
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
                
                // Hibernated WebView for tab ${it.id}
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
                
                // Woke up WebView for tab ${it.id}
            }
        }
    }
    
    /**
     * Manual focus handling for input fields
     */
    fun injectManualFocusHandling() {
        val script = """
        (function() {
            // Add touch handling script for input fields
            if (window._tkfTouchHandlingInjected) return 'Already injected';
            window._tkfTouchHandlingInjected = true;
            
            // Fix for MutationObserver error
            if (typeof MutationObserver !== 'undefined') {
                console.log('TKF Browser: Fixing MutationObserver implementation');
                try {
                    // Save original MutationObserver
                    var originalMutationObserver = window.MutationObserver;
                    // Create a wrapped version that validates arguments
                    window.MutationObserver = function(callback) {
                        var observer = new originalMutationObserver(callback);
                        var originalObserve = observer.observe;
                        
                        // Override observe method with argument validation
                        observer.observe = function(target, options) {
                            if (!target || typeof target !== 'object' || !(target instanceof Node)) {
                                console.warn('TKF Browser: Invalid MutationObserver target, using document.body instead');
                                // Use document.body as fallback target
                                if (document.body) {
                                    return originalObserve.call(observer, document.body, options || {});
                                } else if (document.documentElement) {
                                    return originalObserve.call(observer, document.documentElement, options || {});
                                } else {
                                    console.error('TKF Browser: No valid DOM target available for MutationObserver');
                                    return false;
                                }
                            }
                            return originalObserve.call(observer, target, options);
                        };
                        return observer;
                    };
                    window.MutationObserver.prototype = originalMutationObserver.prototype;
                    console.log('TKF Browser: MutationObserver fixed successfully');
                } catch(e) {
                    console.error('TKF Browser: Error fixing MutationObserver:', e);
                }
            }
            
            console.log('TKF Browser: Injecting manual touch handling');
            
            // Check if we're on Google search page
            const isGoogleSearch = function() {
                return window.location.hostname.indexOf('google') !== -1;
            };
            
            // Otomatik odaklanma burada kaldırıldı - kullanıcının kendisi tıklaması gerekecek
            
            // Special handling for Google search - GELİŞTİRİLMİŞ VERSİYON
            if (isGoogleSearch()) {
                console.log('TKF Browser: Adding Google search specific handlers - IMPROVED VERSION');
                
                // Enhance Google search functionality
                setTimeout(function() {
                    // Find Google search input
                    var searchInput = document.querySelector('input[name="q"], input[title="Ara"], input[title="Search"]');
                    if (searchInput) {
                        console.log('TKF Browser: Found Google search input');
                        // Make sure its ready for input
                        searchInput.focus();
                        
                        // Track form submits
                        var searchForm = searchInput.closest('form');
                        if (searchForm) {
                            searchForm.addEventListener('submit', function(e) {
                                console.log('TKF Browser: Google search form submitted');
                            });
                        }
                    }
                    
                    // Make search suggestions clickable
                    document.addEventListener('click', function(event) {
                        // Only process real user clicks
                        if (!event.isTrusted) return;
                        
                        var el = event.target;
                        // If user explicitly clicks on a suggestion, ensure it works
                        if (el && (el.closest('[role="option"], .sbct, .suggestions-inner-container') ||
                                  el.matches('.suggestions-inner-container, .sbct'))) {
                            console.log('TKF Browser: User clicked on Google search suggestion - handling');
                        }
                    }, true);
                }, 500); // Allow page to fully load
            }
            
            return 'Enhanced touch handling injected with improved Google search fix';
        })();
        """
        
        evaluateJavascript(script) { _ -> }
    }
    
    /**
     * WebView'e doğrudan klavye girdisi simüle eden bir metod
     */
    fun simulateKeyboardInput(text: String) {
        // Hemen odakla
        requestFocus()
        post { requestFocus() } // İkinci bir odaklama garantisi

        // Çok yöntemli, yüksek başarı oranına sahip geliştirilmiş yaklaşım
        val enhancedScript = """
        (function() {
            try {
                // =========== AŞAMA 1: ADAY ELEMENTİ BUL ===========
                var activeElement = document.activeElement;
                var targetsFound = [];
                var debugInfo = [];
                
                // 1. Aktif elementi kontrol et
                if (activeElement && (activeElement.tagName === 'INPUT' || activeElement.tagName === 'TEXTAREA')) {
                    targetsFound.push({element: activeElement, source: "active_element"});
                    debugInfo.push("Found active element: " + activeElement.tagName + 
                        (activeElement.id ? "#" + activeElement.id : "") + 
                        (activeElement.name ? "[name=" + activeElement.name + "]" : ""));
                }
                
                // 2. Veri-özniteliği ile etiketlenmiş elementleri ara
                var keyElements = document.querySelectorAll('[data-tkf-key]');
                for (var i = 0; i < keyElements.length; i++) {
                    if (keyElements[i].tagName === 'INPUT' || keyElements[i].tagName === 'TEXTAREA') {
                        targetsFound.push({element: keyElements[i], source: "data_key"});
                        debugInfo.push("Found element with data-tkf-key: " + keyElements[i].tagName + 
                            (keyElements[i].id ? "#" + keyElements[i].id : ""));
                    }
                }
                
                // 3. Tüm görünür girdi alanlarını ara
                var allInputs = document.querySelectorAll('input, textarea');
                for (var i = 0; i < allInputs.length; i++) {
                    // Zaten bulunmuş mu kontrol et 
                    var alreadyFound = false;
                    for (var j = 0; j < targetsFound.length; j++) {
                        if (targetsFound[j].element === allInputs[i]) {
                            alreadyFound = true;
                            break;
                        }
                    }
                    
                    if (!alreadyFound && allInputs[i].offsetParent !== null && 
                        !allInputs[i].disabled && !allInputs[i].readOnly &&
                        allInputs[i].type !== 'hidden') {
                            
                        var input = allInputs[i];
                        targetsFound.push({element: input, source: "visible_input"});
                        debugInfo.push("Found visible input: " + input.tagName + 
                            (input.id ? "#" + input.id : "") + 
                            (input.name ? "[name=" + input.name + "]" : ""));
                    }
                }
                
                // Eğer hedef bulunamadıysa, basit bir metin girişi oluşturmayı dene
                if (targetsFound.length === 0) {
                    debugInfo.push("No input elements found, trying to create one");
                    try {
                        // Bir iframe oluştur ve içine bir giriş alanı yerleştir
                        // Bu, bazı JavaScriptle korunan sitelerdeki form kısıtlamalarını aşmaya yardımcı olabilir
                        var tempFrame = document.createElement('iframe');
                        tempFrame.style.position = 'fixed';
                        tempFrame.style.top = '-1000px';
                        tempFrame.style.left = '-1000px';
                        document.body.appendChild(tempFrame);
                        
                        var frameDoc = tempFrame.contentDocument || tempFrame.contentWindow.document;
                        frameDoc.body.innerHTML = '<input type="text" id="tkf_temp_input" />';
                        var tempInput = frameDoc.getElementById('tkf_temp_input');
                        targetsFound.push({element: tempInput, source: "created_input"});
                        debugInfo.push("Created temporary input element in iframe");
                    } catch(e) {
                        debugInfo.push("Failed to create temp input: " + e.message);
                    }
                }
                
                // Hala hiçbir hedef bulunamadıysa, başarısızlık durumunu bildir
                if (targetsFound.length === 0) {
                    return JSON.stringify({
                        success: false,
                        message: "No suitable input elements found",
                        debug: debugInfo
                    });
                }
                
                // =========== AŞAMA 2: DEĞER ATAMA STRATEJİLERİNİ UYGULA ===========
                // Her hedefte değer atama stratejilerini dene
                var results = [];
                var overallSuccess = false;
                
                for (var i = 0; i < targetsFound.length; i++) {
                    var target = targetsFound[i].element;
                    var source = targetsFound[i].source;
                    
                    // Odakla ve seç
                    try {
                        target.focus();
                        target.select();
                    } catch(e) {
                        debugInfo.push("Focus/select error on " + source + ": " + e.message);
                    }
                    
                    // STRATEJİ 1: Doğrudan değer atama
                    var directValue = false;
                    try {
                        var origValue = target.value || "";
                        target.value = '$text';
                        directValue = (target.value === '$text');
                    } catch(e) {
                        debugInfo.push("Direct value error on " + source + ": " + e.message);
                    }
                    
                    // STRATEJİ 2: Property descriptor kullanımı
                    var descriptorValue = false;
                    if (!directValue) {
                        try {
                            var inputProto = Object.getPrototypeOf(target);
                            if (inputProto) {
                                var descriptor = Object.getOwnPropertyDescriptor(inputProto, 'value');
                                if (descriptor && descriptor.set) {
                                    descriptor.set.call(target, '$text');
                                    descriptorValue = (target.value === '$text');
                                }
                            }
                        } catch(e) {
                            debugInfo.push("Descriptor error on " + source + ": " + e.message);
                        }
                    }
                    
                    // STRATEJİ 3: execCommand kullanımı
                    var execCommandValue = false;
                    if (!directValue && !descriptorValue) {
                        try {
                            target.select();
                            execCommandValue = document.execCommand('insertText', false, '$text');
                        } catch(e) {
                            debugInfo.push("execCommand error on " + source + ": " + e.message);
                        }
                    }
                    
                    // STRATEJİ 4: Karakter karakter girme
                    var charByCharValue = false;
                    if (!directValue && !descriptorValue && !execCommandValue) {
                        try {
                            target.value = '';
                            var chars = '$text'.split('');
                            for (var j = 0; j < chars.length; j++) {
                                target.value += chars[j];
                            }
                            charByCharValue = (target.value === '$text');
                        } catch(e) {
                            debugInfo.push("Char-by-char error on " + source + ": " + e.message);
                        }
                    }
                    
                    // STRATEJİ 5: Kompozisyon olayları kullanımı (IME benzeri giriş)
                    var compositionValue = false;
                    if (!directValue && !descriptorValue && !execCommandValue && !charByCharValue) {
                        try {
                            // Giriş temizle
                            target.value = '';
                            // Kompozisyon başlatma olayı
                            var compStartEvent = new Event('compositionstart', {bubbles: true});
                            target.dispatchEvent(compStartEvent);
                            // Kompozisyon güncelleme
                            var compUpdateEvent = new Event('compositionupdate', {bubbles: true});
                            compUpdateEvent.data = '$text';
                            target.dispatchEvent(compUpdateEvent);
                            // Değeri ayarla
                            target.value = '$text';
                            // Kompozisyon bitirme
                            var compEndEvent = new Event('compositionend', {bubbles: true});
                            compEndEvent.data = '$text';
                            target.dispatchEvent(compEndEvent);
                            compositionValue = (target.value === '$text');
                        } catch(e) {
                            debugInfo.push("Composition error on " + source + ": " + e.message);
                        }
                    }
                    
                    // =========== AŞAMA 3: OLAY TETİKLEME ===========
                    // Input ve change olaylarını tetikle - her türlü tetikle, başarılı veya değil
                    var eventsDispatched = false;
                    try {
                        // input olayı
                        var inputEvent = new Event('input', {bubbles: true});
                        target.dispatchEvent(inputEvent);
                        // change olayı
                        var changeEvent = new Event('change', {bubbles: true});
                        target.dispatchEvent(changeEvent);
                        // form olayları - hemen form kontrolü
                        var form = target.form;
                        if (form) {
                            try {
                                var formInputEvent = new Event('input', {bubbles: true});
                                form.dispatchEvent(formInputEvent);
                            } catch(e) { /* Form olaylarında hata olabilir, yoksay */ }
                        }
                        eventsDispatched = true;
                    } catch(e) {
                        // Fallback: eski tarayıcılar için createEvent
                        try {
                            var fallbackInput = document.createEvent('HTMLEvents');
                            fallbackInput.initEvent('input', true, true);
                            target.dispatchEvent(fallbackInput);
                            var fallbackChange = document.createEvent('HTMLEvents');
                            fallbackChange.initEvent('change', true, true);
                            target.dispatchEvent(fallbackChange);
                            eventsDispatched = true;
                        } catch(e2) {
                            debugInfo.push("Event error on " + source + ": " + e.message + ", fallback: " + e2.message);
                        }
                    }
                    
                    // Son bir kez odakla
                    try {
                        target.focus();
                    } catch(e) { /* yoksay */ }
                    
                    // Sonucu kaydet
                    var methodsSuccess = directValue || descriptorValue || execCommandValue || charByCharValue || compositionValue;
                    var finalValue = target.value || "";
                    var successful = (finalValue === '$text');
                    
                    results.push({
                        source: source,
                        element: target.tagName + (target.id ? "#" + target.id : ""),
                        successful: successful,
                        finalValue: finalValue,
                        eventsDispatched: eventsDispatched,
                        methods: {
                            directValue: directValue,
                            descriptorValue: descriptorValue,
                            execCommandValue: execCommandValue,
                            charByCharValue: charByCharValue,
                            compositionValue: compositionValue
                        }
                    });
                    
                    // Genel başarı durumunu güncelle
                    if (successful) {
                        overallSuccess = true;
                    }
                    
                    // Başarılı olduysa, diğer hedefleri denemeyi kes
                    if (successful) {
                        break;
                    }
                }
                
                // Değer atama sonucunu döndür 
                return JSON.stringify({
                    success: overallSuccess,
                    message: overallSuccess ? "Value set successfully" : "Failed to set value",
                    targetsCount: targetsFound.length,
                    results: results,
                    finalValue: overallSuccess ? '$text' : "",
                    debug: debugInfo
                });
                
            } catch(e) {
                return JSON.stringify({
                    success: false,
                    message: "Error: " + e.message,
                    stack: e.stack,
                    debug: ["Fatal error: " + e.message]
                });
            }
        })();
        """.trimIndent()
        
        // Ana stratejiyi uygula ve başarı olup olmadığını kontrol et
        evaluateJavascript(enhancedScript) { result ->
            try {
                // Sonuç stringini temizle (tırnak işaretlerini ve kaçış karakterlerini kaldır)
                val cleanResult = result.trim().removeSurrounding("\"").replace("\\\"(", "\"(").replace("\\\"", "\"").replace("\\\\", "\\")
                
                try {
                    // JSON sonucunu ayrıştırmaya çalış
                    val jsonResult = org.json.JSONObject(cleanResult)
                    val success = jsonResult.optBoolean("success", false)
                    
                    // Başarısız olduysa, son çare olarak tekrar dene
                    if (!success) {
                        // Bir süre bekleyip son çare yaklaşımını dene
                        Handler(Looper.getMainLooper()).postDelayed({
                            // Super güçlü son çare yaklaşımı - en basit ve kaba yöntem
                            val finalResortScript = """
                            (function() {
                                try {
                                    // Mevcut bağlama JavaScript kodu enjekte et
                                    var js = "var allInputs = document.querySelectorAll('input, textarea');" +
                                            "for (var i = 0; i < allInputs.length; i++) {" +
                                            "  if (allInputs[i].offsetParent !== null) {" +
                                            "    allInputs[i].value = '$text';" +
                                            "    var inputEvent = new Event('input', {bubbles: true});" +
                                            "    allInputs[i].dispatchEvent(inputEvent);" +
                                            "    var changeEvent = new Event('change', {bubbles: true});" +
                                            "    allInputs[i].dispatchEvent(changeEvent);" +
                                            "  }" +
                                            "}";
                                    
                                    // Siteye doğrudan bir script etiketi olarak enjekte et
                                    var scriptElement = document.createElement('script');
                                    scriptElement.textContent = js;
                                    document.head.appendChild(scriptElement);
                                    document.head.removeChild(scriptElement);
                                    
                                    // Aktif form varsa, enter tuşu göndermeyi dene
                                    setTimeout(function() {
                                        var activeElement = document.activeElement;
                                        if (activeElement && activeElement.form) {
                                            var enterEvent = new KeyboardEvent('keydown', {
                                                bubbles: true,
                                                cancelable: true,
                                                key: 'Enter',
                                                code: 'Enter',
                                                keyCode: 13,
                                                which: 13
                                            });
                                            activeElement.dispatchEvent(enterEvent);
                                        }
                                    }, 200);
                                    
                                    return "EMERGENCY_ATTEMPT_COMPLETED";
                                } catch(e) {
                                    return "EMERGENCY_ATTEMPT_FAILED: " + e.message;
                                }
                            })();
                            """.trimIndent()
                            
                            evaluateJavascript(finalResortScript) { _ -> }
                        }, 200) // Son çare denemesi için kısa bir gecikme
                    }
                } catch (e: Exception) {
                    // Error parsing JSON result
                }
            } catch (e: Exception) {
                // Error processing result
            }
        }
    }
    
    /**
     * Clean up resources when this WebView is no longer needed
     * Geliştirilmiş bellek temizleme v2.0
     */
    fun cleanup() {
        // JavaScript ile önce bellek temizliği yap
        try {
            evaluateJavascript("""
                (function() {
                    try {
                        // Tüm zamanlayıcıları ve aralıkları temizle
                        const highestId = setTimeout(() => {}, 0);
                        for (let i = 0; i < highestId; i++) {
                            clearTimeout(i);
                            clearInterval(i);
                        }
                        
                        // DOM içeriğini temizle
                        if (document && document.body) {
                            document.body.innerHTML = '';
                        }
                        
                        // Büyük global nesneleri temizle
                        for (let prop in window) {
                            if (window.hasOwnProperty(prop) && 
                                typeof window[prop] === 'object' && 
                                window[prop] !== null) {
                                try { 
                                    window[prop] = null; 
                                } catch(e) {}
                            }
                        }
                        
                        // Web Workers'ları sonlandır
                        if (window._tkfWorkers && Array.isArray(window._tkfWorkers)) {
                            window._tkfWorkers.forEach(worker => {
                                try { worker.terminate(); } catch(e) {}
                            });
                        }
                        
                        // Depolama yönetimi
                        try { localStorage.clear(); } catch(e) {}
                        try { sessionStorage.clear(); } catch(e) {}
                        try { if (window.indexedDB && window.indexedDB.deleteDatabase) {
                            window.indexedDB.deleteDatabase('TKFBrowser');
                        }} catch(e) {}
                        
                        return "CLEANUP_COMPLETE";
                    } catch(e) {
                        return "CLEANUP_ERROR: " + e.message;
                    }
                })();
            """.trimIndent()) { result ->
                // Temizlik sonucunu logla
                // WebView JS cleanup result: $result
                
                // JavaScript tamamlandıktan sonra ana temizlik işlemlerini yap
                completeCleanup()
            }
            
            // JavaScript sonucunun gelmemesi durumunda 300ms sonra mecburen temizlik yap
            postDelayed({ completeCleanup() }, 300)
        } catch (e: Exception) {
            // JavaScript çalıştırma hatası - doğrudan temizliğe geç
            // Error during JS cleanup
            completeCleanup()
        }
    }
    
    /**
     * JavaScript temizlikten sonra ana kaynak temizleme
     */
    private fun completeCleanup() {
        try {
            // Önce WebView'in hala geçerli olup olmadığını kontrol et
            if (!isAttachedToWindow || windowVisibility == View.GONE || windowToken == null) {
                // WebView zaten destroy edilmiş veya görünümden kaldırılmış, işlemleri atlayalım
                return
            }
            
            // Tüm yükleme ve işlemleri durdur
            try { stopLoading() } catch (e: Exception) {}
            
            // JavaScript'i devre dışı bırak 
            try { settings.javaScriptEnabled = false } catch (e: Exception) {}
            
            // Önbellekleri temizle - her biri için ayrı try-catch
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
            onDownloadRequested = null
            onLongPress = null
            
            // WebView'i yok et - önce isValid kontrolü yap
            if (isAttachedToWindow && windowToken != null) {
                try { destroy() } catch (e: Exception) {}
            }
            
            // Completed WebView cleanup process
        } catch (e: Exception) {
            // Error during WebView cleanup
        }
    }

    /**
     * Fix for toast message visibility issues
     * This ensures toast-container elements and notifications remain visible
     */
    fun fixToastVisibility() {
        val script = """
        (function() {
            // Check if already injected
            if (window._tkfToastFixInjected) return 'Already injected';
            window._tkfToastFixInjected = true;
            
            console.log('TKF Browser: Injecting toast message visibility fix');
            
            // 1. Preserve toast container during DOM manipulations
            const preserveToastElements = function() {
                // Find toast container elements
                const toastContainers = document.querySelectorAll('.toast-container, #toast-container, [aria-live="polite"], [role="alert"]');
                
                if (toastContainers.length > 0) {
                    console.log('TKF Browser: Found ' + toastContainers.length + ' toast containers to preserve');
                    
                    // Make sure toast containers are always visible
                    toastContainers.forEach(function(container) {
                        // Ensure toast container stays visible
                        container.style.setProperty('display', 'block', 'important');
                        container.style.setProperty('visibility', 'visible', 'important');
                        container.style.setProperty('opacity', '1', 'important');
                        container.style.setProperty('pointer-events', 'auto', 'important');
                        
                        // Ensure any toast messages inside are visible
                        const toasts = container.querySelectorAll('.toast, .toast-info, .toast-success, .toast-error, .toast-warning');
                        toasts.forEach(function(toast) {
                            toast.style.setProperty('display', 'block', 'important');
                            toast.style.setProperty('visibility', 'visible', 'important');
                            toast.style.setProperty('opacity', '1', 'important');
                        });
                        
                        console.log('TKF Browser: Preserved toast container visibility');
                    });
                    
                    return true;
                }
                return false;
            };
            
            // 2. Monitor DOM for dynamic toast elements
            const observeToasts = function() {
                // Create a mutation observer to watch for toast elements
                const toastObserver = new MutationObserver(function(mutations) {
                    // Check for toast elements on DOM changes
                    setTimeout(preserveToastElements, 50);  // Small delay to allow toast to render
                });
                
                // Start observing the body for toast-related changes
                if (document.body) {
                    toastObserver.observe(document.body, { 
                        childList: true, 
                        subtree: true,
                        attributes: true,
                        attributeFilter: ['style', 'class']
                    });
                    console.log('TKF Browser: Toast observer attached to document body');
                }
            };
            
            // 3. Fix any existing toast elements immediately
            preserveToastElements();
            
            // 4. Start observing for future toast elements
            observeToasts();
            
            // 5. Override any cleanup functions that might hide toasts
            if (window.TKF_cleanupEvents) {
                const originalCleanup = window.TKF_cleanupEvents;
                window.TKF_cleanupEvents = function() {
                    // Run the original cleanup
                    originalCleanup();
                    // Restore toast visibility after cleanup
                    preserveToastElements();
                };
                console.log('TKF Browser: Cleanup function patched to preserve toasts');
            }
            
            return 'Toast visibility fix injected';
        })();
        """
        
        evaluateJavascript(script) { _ -> }
    }

    /**
     * Klavye açılıp kapandığında WebView içeriğini uygun şekilde ayarlamak için
     * klavye durumunu izleyen bir observer kurar - GELİŞTİRİLMİŞ VERSİYON
     */
    private fun setupKeyboardObserver() {
        // Ana pencerenin kök görünümü
        val rootView = this.rootView
        // Son klavye durumunu izle
        var lastKeyboardVisible = false
        var lastKeyboardHeight = 0
        
        // Görünüm değişikliklerini dinleyici - performans için optimize edildi
        val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            try {
                // Dikdörtgen oluştur
                val r = Rect()
                // Görünür ekran alanı bilgilerini al
                rootView.getWindowVisibleDisplayFrame(r)
                
                // Ekran yüksekliğini al
                val screenHeight = rootView.height
                
                // Klavye yüksekliğini hesapla
                val keyboardHeight = screenHeight - r.bottom
                
                // Klavyenin görünür olup olmadığını kontrol et
                // Genellikle klavye ekranın %15'inden büyükse görünür kabul edilir
                val isKeyboardVisible = keyboardHeight > screenHeight * 0.15
                
                // Sadece klavye durumu değiştiğinde işlem yap
                if (isKeyboardVisible != lastKeyboardVisible || 
                    (isKeyboardVisible && Math.abs(keyboardHeight - lastKeyboardHeight) > 100)) {
                    
                    // Durum değişikliğini sakla
                    lastKeyboardVisible = isKeyboardVisible
                    lastKeyboardHeight = keyboardHeight
                    
                    if (isKeyboardVisible) {
                        // ÖNEMLİ: Klavye artık açık olduğundan, önce odağı koruyarak gerekli JavaScript kodunu çalıştır
                        post {
                            // Aktif elemanı kontrol et ve görünür olduğundan emin ol
                            evaluateJavascript("""
                            (function() {
                                try {
                                    // Aktif elemanı bul
                                    var activeElement = document.activeElement;
                                    
                                    // Eğer bir input odaklanmışsa
                                    if (activeElement && (activeElement.tagName === 'INPUT' || activeElement.tagName === 'TEXTAREA')) {
                                        console.log('TKF Browser: Klavye açıldı, odak düzenleniyor');
                                        
                                        // Odak kaybını ve klavye kapanmasını önlemek için kritik
                                        var saved = activeElement;
                                        
                                        // Elemanı görünür yap
                                        if (activeElement.scrollIntoViewIfNeeded) {
                                            activeElement.scrollIntoViewIfNeeded(true);
                                        } else if (activeElement.scrollIntoView) {
                                            activeElement.scrollIntoView({behavior: 'smooth', block: 'center'});
                                        }
                                        
                                        // Scroll sonrası elemanın odaklanması korunmayabilir, tekrar odakla
                                        setTimeout(function() {
                                            if (document.activeElement !== saved) {
                                                saved.focus();
                                                if (typeof saved.select === 'function') saved.select();
                                            }
                                        }, 100);
                                        
                                        // Önce odağı bildirdikten sonra key'i işle
                                        var key = activeElement.getAttribute('data-tkf-key');
                                        if (!key) {
                                            key = (activeElement.name || activeElement.id || activeElement.placeholder || 'input_' + Math.random().toString(36).substr(2, 9));
                                            key = key.replace(/[^a-zA-Z0-9_]/g, '_');
                                            activeElement.setAttribute('data-tkf-key', key);
                                        }
                                        
                                        // Öneri işleyicisini bilgilendir (ama klavyeyi kapatma)
                                        if (window.SuggestionHandler) {
                                            window.SuggestionHandler.onSmoothInputFocusChanged(key);
                                        }
                                        
                                        return true;
                                    }
                                    return false;
                                } catch(e) {
                                    console.error('TKF Browser: Klavye düzenleme hatası', e);
                                    return false;
                                }
                            })();
                            """.trimIndent()) { result ->
                                // Form odaklanması başarılı oldu mu kontrol et
                                val focusSuccess = result.contains("true")
                                
                                // İlave olarak, odaklanma işlemi tamamlandıktan sonra biraz daha kaydır
                                if (focusSuccess) {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        // Daha hassas bir kaydırma miktarı kullan
                                        scrollBy(0, keyboardHeight / 5)
                                    }, 200) // Biraz daha uzun bir bekleme ile daha iyi sonuç al
                                }
                            }
                        }
                        
                        // WebView'e klavye için padding ekle
                        setPadding(paddingLeft, paddingTop, paddingRight, keyboardHeight)
                    } else {
                        // Klavye kapandığında padding'i sıfırla
                        setPadding(paddingLeft, paddingTop, paddingRight, 0)
                        
                        // İlave olarak, mevcut odaklı elemanı log et (debug için)
                        post {
                            evaluateJavascript("""
                            (function() {
                                var activeElement = document.activeElement;
                                if (activeElement) {
                                    return 'Klavye kapandı, aktif eleman: ' + activeElement.tagName + 
                                           (activeElement.id ? '#' + activeElement.id : '');
                                }
                                return 'Klavye kapandı, aktif eleman yok';
                            })();
                            """.trimIndent()) { _ -> }
                        }
                    }
                }
            } catch (e: Exception) {
                // Hata durumlarını görmezden gel
            }
        }
        
        // Observer'i attach et
        rootView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
    }

    /**
     * Check if WebView is destroyed or not usable
     */
    private fun isDestroyed(): Boolean {
        return !isAttachedToWindow || windowVisibility == View.GONE || windowToken == null
    }
}