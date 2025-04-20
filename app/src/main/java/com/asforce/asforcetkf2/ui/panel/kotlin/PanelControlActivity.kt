package com.asforce.asforcetkf2.ui.panel.kotlin

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.asforce.asforcetkf2.R
import com.asforce.asforcetkf2.ui.panel.kotlin.FormDialogFragment
import com.asforce.asforcetkf2.util.DataHolder
import com.asforce.asforcetkf2.util.SimpleTextWatcher
import com.asforce.asforcetkf2.util.WebViewPool
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.HashMap
import java.util.regex.Pattern

/**
 * Pano Fonksiyon Kontrolü ekranı
 * WebView kullanarak harici bir form sayfasını yükler ve manipüle eder
 */
class PanelControlActivity : AppCompatActivity(), FormDialogFragment.FormDialogListener {
    
    companion object {
        private const val TAG = "PanelControlActivity"
        private const val PREFS_NAME = "panel_control_settings"
        private const val KEY_URL = "saved_url"
        private const val BASE_URL = "https://app.szutest.com.tr/EXT/PKControl/EditPanoutFunction//"
        private const val FETCH_DELAY = 1000L
    }
    
    // UI Bileşenleri
    private lateinit var urlInputLayout: TextInputLayout
    private lateinit var editTextUrl: TextInputEditText
    private lateinit var buttonLoadPage: ImageView
    private lateinit var buttonFillForm: ImageView
    private lateinit var buttonSaveItems: ImageView
    private lateinit var settingsButton: ImageView
    private lateinit var backButton: ImageView
    
    // WebView
    private var webView: WebView? = null
    private lateinit var webViewPool: WebViewPool
    
    // Sütun genişlikleri ve ayarlar
    private val columnWidths = HashMap<String, String>()
    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    
    // Sütun isimleri ve etiketleri
    private val columnNames = arrayOf(
        "MeasurementName", "NumberOfPhases", "ShortCircuit", "Category",
        "Multiplier", "InA", "PhaseSection", "NeutralSection",
        "ProtectionSection", "Continuity", "CycleImpedance",
        "ExtremeIncomeProtection", "Voltage", "Findings", "Result"
    )
    
    private val columnLabels = arrayOf(
        "Ölçüm Adı", "Faz Sayısı", "Kısa Devre", "Kategori",
        "Çarpan", "In(A)", "Faz Kesiti", "Nötr Kesiti",
        "Koruma Kesiti", "Devamlılık", "Çevrim Emperdansı",
        "Aşırı Gerilim Koruma", "Voltaj", "Bulgular", "Sonuç"
    )
    
    private val defaultColumnWidths = arrayOf(
        "150px", "100px", "100px", "100px",
        "80px", "80px", "100px", "100px",
        "100px", "100px", "120px",
        "130px", "80px", "120px", "100px"
    )
    
    // Sütun genişliği uygulamak için yeniden deneme mekanizması
    private val MAX_RETRY_COUNT = 5
    private val RETRY_DELAY_MS = 500L
    private var retryCount = 0

    private var copyButtonTimerHandler: Handler? = null
    private var copyButtonTimerRunnable: Runnable? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_panel_control)

        // Render sınırlarını kaldır
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        // WebView havuzunu başlat
        webViewPool = WebViewPool.getInstance(this)
        
        initializeViews()
        setupSharedPreferences()
        setupWebView()
        setupClickListeners()
        setupTouchHandling()
        restoreState(savedInstanceState)

        // URL'yi Shared Preferences'tan yükle
        val prefs = getSharedPreferences("PanelControlPrefs", Context.MODE_PRIVATE)
        val lastUrl = prefs.getString("lastUrl", "") ?: "" 
        
        // DataHolder'dan URL'yi al eğer varsa, yoksa Shared Preferences'tan al
        if (DataHolder.url.isNotEmpty()) {
            editTextUrl.setText(DataHolder.url)
        } else {
            editTextUrl.setText(lastUrl)
        }

        // Sayfa otomatik olarak yüklensin
        loadWebPage()
        
        // Kopyalama butonlarını periyodik olarak optimize et
        startCopyButtonMonitor()
    }
    
    /**
     * WebView yüklemesi tamamlandığında progress bar'ı gizler
     */
    private fun hideProgressBar() {
        runOnUiThread {
            val progressBar = findViewById<View>(R.id.progressBar)
            progressBar?.visibility = View.GONE
            Log.d(TAG, "Progress bar gizlendi")
        }
    }

    /**
     * WebView yüklenmeye başladığında progress bar'ı gösterir
     */
    private fun showProgressBar() {
        runOnUiThread {
            val progressBar = findViewById<View>(R.id.progressBar)
            progressBar?.visibility = View.VISIBLE
            Log.d(TAG, "Progress bar gösterildi")
        }
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchHandling() {
        // WebView için direkt dokunma olayı iyileştirmesi
        val webViewInstance = webView
        if (webViewInstance != null) {
            // Kaydırma performansını artırmak için
            webViewInstance.overScrollMode = View.OVER_SCROLL_ALWAYS
            
            // Size parametrelerini güncelle
            webViewInstance.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Dokunma ve kaydırma iyileştirmesi
            webViewInstance.isFocusable = true
            webViewInstance.isFocusableInTouchMode = true
            
            // Direkt WebView'e geçerli onTouch olayları için
            webViewInstance.setOnTouchListener { v, event ->
                v.requestFocus()
                v.performClick()
                false // Olayları işlemeye devam et
            }
        }
        
        // Kaydırmayı düzenli aralıklarla optimize et (animasyon frame'leriyle senkronize et)
        handler.post(object : Runnable {
            override fun run() {
                // Performans için webview'i yenile
                webView?.invalidate()
                
                // Kendimizi sıradaki frame'e zamanla
                handler.postDelayed(this, 16) // ~60fps
            }
        })
    }

    private fun initializeViews() {
        urlInputLayout = findViewById(R.id.urlInputLayout)
        editTextUrl = findViewById(R.id.editTextUrl)
        buttonLoadPage = findViewById(R.id.buttonLoadPage)
        buttonFillForm = findViewById(R.id.buttonFillForm)
        buttonSaveItems = findViewById(R.id.buttonSaveItems)
        settingsButton = findViewById(R.id.settingsButton)
        backButton = findViewById(R.id.backButton)
        
        // WebView havuzundan al
        webView = webViewPool.acquireWebView()
        
        // WebView'in mevcut bir parent'i varsa önce kaldır
        val parent = webView?.parent
        if (parent is ViewGroup) {
            Log.d(TAG, "WebView already has a parent, removing from parent first")
            parent.removeView(webView)
        }
        
        // WebView'i container'a ekle
        val webViewContainer = findViewById<LinearLayout>(R.id.webViewContainer)
        val webViewInstance = webView
        if (webViewInstance != null) {
            webViewContainer.addView(webViewInstance)
        }
    }

    private fun setupSharedPreferences() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadColumnWidths()
    }

    private fun loadColumnWidths() {
        for (i in columnNames.indices) {
            val width = sharedPreferences.getString(columnNames[i], defaultColumnWidths[i]) ?: defaultColumnWidths[i]
            columnWidths[columnNames[i]] = width
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupWebView() {
        // WebView null kontrolü ekleyelim
        if (webView == null) {
            Log.e(TAG, "WebView is null in setupWebView")
            // Tekrar webview havuzundan almayı deneyelim
            webView = webViewPool.acquireWebView()
            
            // WebView'in mevcut bir parent'i varsa önce ondan kaldır
            val parent = webView?.parent
            if (parent is ViewGroup) {
                Log.d(TAG, "WebView already has a parent in setupWebView, removing it first")
                parent.removeView(webView)
            }
            
            val webViewContainer = findViewById<LinearLayout>(R.id.webViewContainer)
            if (webViewContainer != null && webView != null) {
                webViewContainer.addView(webView)
            } else {
                Log.e(TAG, "Could not recover WebView in setupWebView")
                return
            }
        }
        
        // WebView'i yapılandır
        val webViewInstance = webView
        if (webViewInstance != null) {
            // WebView dokunma hassasiyeti ve sürükleme ayarlarını iyileştir
            webViewInstance.settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            webViewInstance.settings.javaScriptEnabled = true
            webViewInstance.settings.domStorageEnabled = true
            webViewInstance.settings.builtInZoomControls = true
            webViewInstance.settings.displayZoomControls = false
            webViewInstance.settings.useWideViewPort = true
            webViewInstance.settings.loadWithOverviewMode = true
            
            // JavaScript performansını artırmak için
            webViewInstance.settings.setRenderPriority(WebSettings.RenderPriority.HIGH)
            webViewInstance.settings.cacheMode = WebSettings.LOAD_NO_CACHE // Daha hızlı yükleme için 
            
            // İlk yüklemeyi kolaylaştır (resimler sonra yüklensin başlangıçta)
            webViewInstance.settings.blockNetworkImage = true
            
            // Donanım hızlandırmasını etkinleştir - maksimum performans
            webViewInstance.setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            // DOM Storage sınırlamasını kaldır
            webViewInstance.settings.domStorageEnabled = true
            webViewInstance.settings.databaseEnabled = true
            webViewInstance.settings.setGeolocationEnabled(false) // Gerekli değilse kapat
            
            // JIT JavaScript performans iyileştirmesi (Nougat ve sonrası)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    val setJSEnabled = WebSettings::class.java.getDeclaredMethod("setJavaScriptEnabled", Boolean::class.java)
                    setJSEnabled.invoke(webViewInstance.settings, true)
                    
                    val setDisabledActionModeMenuItems = WebSettings::class.java.getDeclaredMethod("setDisabledActionModeMenuItems", Int::class.java)
                    setDisabledActionModeMenuItems.invoke(webViewInstance.settings, 1)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set advanced JS settings", e)
            }
            
            // Render thread'lerin ayrılması - UI freezing azaltır
            try {
                // Android 7.0+ için
                val field = WebView::class.java.getDeclaredMethod("setDataDirectorySuffix", String::class.java)
                field.isAccessible = true
                field.invoke(null, "pano_webview_data")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set data directory suffix", e)
            }
            
            // Dokunma ve kaydırma iyileştirmesi - tam sayfa için
            webViewInstance.isScrollbarFadingEnabled = true
            webViewInstance.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            
            // Ek performans iyileştirmeleri
            webViewInstance.settings.allowFileAccess = true
            webViewInstance.settings.allowContentAccess = true
            
            // WebView kaydırma davranışını iyileştir
            webViewInstance.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Touch olayları için UI thread'ini serbest bırak
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                        v.requestFocus()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // Touch iptal edildiğinde veya bittiğinde
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                
                // false döndür ki WebView olayları işlemeye devam etsin
                false
            }
            
            // Progress bar'ın durumunu kontrol et ve WebView'in yükleme yüzdesi %100 olduğunda gizle
            webViewInstance.setWebChromeClient(object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    if (newProgress >= 95) { // 100 yerine 95'te gizle daha hızlı geri bildirim için
                        hideProgressBar()
                        
                        // Resimleri şimdi yükle
                        view?.settings?.blockNetworkImage = false
                    }
                }
            })
            
            webViewInstance.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    
                    // Web sayfası yüklenme işlemi tamamlandığında, WebView kontrolü
                    // Aktivite kapanırken WebView null olabilir, bu durumda işlemi atlayarak çöküşü önleyelim
                    if (webView == null) {
                        Log.w(TAG, "WebView is null in onPageFinished, skipping operations")
                        return
                    }
                    
                    try {
                        // İlk olarak CSS iyileştirmeleri ekle - daha iyi render için
                        val cssOptimizationJS = """
                            (function() {
                                // Rendering ve repaint iyileştirmeleri
                                document.body.style.willChange = 'transform';
                                document.body.style.transformStyle = 'preserve-3d';
                                document.body.style.backfaceVisibility = 'hidden';
                                
                                // Animasyonları ve geçişleri düzgünleştir
                                var style = document.createElement('style');
                                style.textContent = `
                                    * {
                                        -webkit-transition: none !important;
                                        -moz-transition: none !important;
                                        -o-transition: none !important;
                                        transition: none !important;
                                        animation-duration: 0.01s !important;
                                        scroll-behavior: auto !important;
                                        text-rendering: optimizeSpeed !important;
                                    }
                                    button, a, [role="button"] {
                                        cursor: pointer !important;
                                        -webkit-tap-highlight-color: transparent !important;
                                    }
                                    .btn, button { 
                                        will-change: transform !important;
                                        transform: translateZ(0) !important;
                                    }
                                    i.fa.fa-copy {
                                        pointer-events: none !important;
                                    }
                                `;
                                document.head.appendChild(style);
                                
                                return "CSS optimizations applied";
                            })();
                        """.trimIndent()
                        
                        webView?.evaluateJavascript(cssOptimizationJS) { result ->
                            Log.d(TAG, "CSS optimization result: $result")
                        }
                        
                        applyColumnWidths()
                        
                        // URL'yi kaydet
                        val prefs = getSharedPreferences("PanelControlPrefs", Context.MODE_PRIVATE)
                        val editor = prefs.edit()
                        editor.putString("lastUrl", url)
                        editor.apply {}
                        
                        // Optimize copyPanoutFunctionItem function to prevent freezing
                        injectCopyButtonOptimization()
                        
                        // Doğrudan kopyalama butonlarını optimize et
                        injectDirectCopyButtonFix()
                        
                        // Özel kaydırma optimizasyonu - daha agresif bir yaklaşım
                        val scrollOptimizationJS = """
                            (function() {
                              document.body.style.overflow = 'scroll';
                              document.body.style.height = 'auto';
                              document.documentElement.style.overflow = 'scroll';
                              document.documentElement.style.height = 'auto';
                              document.body.style.webkitOverflowScrolling = 'touch';
                              document.documentElement.style.webkitOverflowScrolling = 'touch';
                              var style = document.createElement('style');
                              style.textContent = '* { -webkit-overflow-scrolling: touch !important; } ' +
                                'body, html { overflow: scroll !important; height: auto !important; } ' +
                                'div, table, tr, td { overflow: visible !important; } ' +
                                '.scroll-wrapper { overflow: scroll !important; -webkit-overflow-scrolling: touch !important; }';
                              document.head.appendChild(style);
                              
                              // Form elementlerini kaydırılabilir yap
                              var forms = document.querySelectorAll('form');
                              for (var i = 0; i < forms.length; i++) {
                                forms[i].style.overflow = 'auto';
                                forms[i].style.webkitOverflowScrolling = 'touch';
                              }
                              
                              // Tüm container'ları kaydırılabilir yap
                              var containers = document.querySelectorAll('.container, .container-fluid, .panel, .panel-body, .row, .col');
                              for (var i = 0; i < containers.length; i++) {
                                containers[i].style.overflow = 'visible';
                              }
                            })();
                        """.trimIndent()
                        
                        view.evaluateJavascript(scrollOptimizationJS, null)
                        
                        // Kesinlikle progress bar'ı gizle
                        hideProgressBar()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error in onPageFinished: ${e.message}")
                    }
                }
                
                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    // WebView kontrolü
                    if (webView == null) {
                        Log.w(TAG, "WebView is null in onPageStarted, skipping operations")
                        return
                    }
                    
                    // Sayfa yüklenmeye başladığında progress bar'ı göster
                    showProgressBar()
                }
                
                // Sayfanın daha hızlı yüklenmesi için
                override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                    view.loadUrl(url)
                    return true
                }
            }
        }
    }

    private fun setupClickListeners() {
        buttonLoadPage.setOnClickListener { loadWebPage() }
        buttonFillForm.setOnClickListener { showFormDialog() }
        buttonSaveItems.setOnClickListener { saveItems() }
        settingsButton.setOnClickListener { showSettingsDialog() }
        backButton.setOnClickListener {
            finish() // Ana ekrana geri dön
        }

        setupTextWatchers()
    }

    private fun setupTextWatchers() {
        editTextUrl.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable) {
                val input = s.toString()
                DataHolder.url = input

                if (input.matches(".*\\d+$".toRegex())) {
                    // Find the trailing digits (any number of digits)
                    val pattern = "\\d+$"
                    val r = Pattern.compile(pattern)
                    val m = r.matcher(input)
                    if (m.find()) {
                        val digits = m.group()
                        editTextUrl.removeTextChangedListener(this)
                        editTextUrl.setText(digits)
                        editTextUrl.setSelection(digits.length)
                        editTextUrl.addTextChangedListener(this)
                        DataHolder.url = digits
                    }
                }
            }
        })
    }

    private fun loadWebPage() {
        val url = editTextUrl.text.toString().trim()

        if (url.isEmpty()) {
            Toast.makeText(this, "Lütfen bir URL veya kod girin", Toast.LENGTH_SHORT).show()
            return
        }

        val finalUrl = when {
            url.matches("\\d+".toRegex()) -> BASE_URL + url
            !url.startsWith("http") -> "https://$url"
            else -> url
        }

        buttonLoadPage.isEnabled = false
        
        // WebView null kontrolü ekleyelim
        if (webView == null) {
            Log.e(TAG, "WebView is null before loadUrl, trying to recover...")
            webView = webViewPool.acquireWebView()
            val webViewContainer = findViewById<LinearLayout>(R.id.webViewContainer)
            if (webViewContainer != null && webView != null) {
                webViewContainer.addView(webView)
                setupWebView() // WebView client'i yeniden ayarlayalım
            } else {
                Log.e(TAG, "Could not recover WebView, cannot load URL")
                Toast.makeText(this, "Sayfa yüklenemedi, lütfen tekrar deneyin", Toast.LENGTH_SHORT).show()
                buttonLoadPage.isEnabled = true
                return
            }
        }
        
        webView?.loadUrl(finalUrl)
        handler.postDelayed({ buttonLoadPage.isEnabled = true }, 1000)
    }

    private fun showFormDialog() {
        // Form verilerini SharedPreferences'tan yükle
        val prefs = getSharedPreferences("PanelFormData", MODE_PRIVATE)
        DataHolder.url = editTextUrl.text.toString()
        
        // Form diyaloğunu oluştur ve göster
        val formDialog = FormDialogFragment(webView)
        formDialog.show(supportFragmentManager, "PanelFormDialog")
    }
    
    /**
     * Kopyalama butonlarının donma sorununu çözmek için optimizasyon
     * ng-click fonksiyonlarını iyileştirir
     */
    private fun injectCopyButtonOptimization() {
        // WebView null kontrolü
        if (webView == null) {
            Log.e(TAG, "WebView is null in injectCopyButtonOptimization")
            return
        }

        val optimizationScript = """
            (function() {
                try {
                    // Önceki uygulama varsa temizle
                    if (window.originalCopyPanoutFunctionItem) {
                        return "Already optimized";
                    }
                    
                    // Button click tepkisi iyileştirme - tüm butonlar için
                    document.addEventListener('click', function(e) {
                        if (e.target && (e.target.tagName === 'BUTTON' || e.target.closest('button'))) {
                            var button = e.target.tagName === 'BUTTON' ? e.target : e.target.closest('button');
                            button.style.opacity = '0.7';
                            setTimeout(function() { button.style.opacity = '1'; }, 100);
                        }
                    }, true);
                    
                    // Angular scope'u bul
                    var waitForAngular = function(callback, maxAttempts) {
                        var attempts = 0;
                        var interval = setInterval(function() {
                            attempts++;
                            
                            // Eğer Angular bulunduysa
                            if (window.angular && document.querySelector('[ng-controller]')) {
                                clearInterval(interval);
                                var el = document.querySelector('[ng-controller]');
                                var scope = window.angular.element(el).scope();
                                callback(scope);
                                return;
                            }
                            
                            // Maksimum deneme sayısı aşılırsa
                            if (attempts >= maxAttempts) {
                                clearInterval(interval);
                                console.error('Angular scope bulunamadı');
                            }
                        }, 100);
                    };
                    
                    // Angular scope bulunduğunda
                    waitForAngular(function(scope) {
                        // Orijinal fonksiyonu sakla
                        window.originalCopyPanoutFunctionItem = scope.copyPanoutFunctionItem;
                        
                        // Optimize edilmiş fonksiyonla değiştir
                        scope.copyPanoutFunctionItem = function(index) {
                            // Visual feedback için - hemen tepki ver
                            var buttons = document.querySelectorAll('button[ng-click*="copyPanoutFunctionItem"]');
                            if (buttons && buttons.length > index) {
                                var button = buttons[index];
                                var originalColor = button.style.backgroundColor;
                                
                                // Renk değiştirerek geri bildirim - hemen göster
                                button.style.backgroundColor = '#c8e6c9';
                                
                                // Orijinal fonksiyonu direkt çağır
                                try {
                                    window.originalCopyPanoutFunctionItem.call(scope, index);
                                    
                                    // Hemen UI güncellemesi için
                                    if (scope["${"$"}root"] && scope["${"$"}root"]["${"$"}${"$"}phase"] !== "${"$"}apply" && scope["${"$"}root"]["${"$"}${"$"}phase"] !== "${"$"}digest") {
                                        scope["${"$"}apply"]();
                                    }
                                } catch (e) {
                                    console.error('Kopyalama hatası:', e);
                                }
                                
                                // Rengi geri getir
                                setTimeout(function() {
                                    button.style.backgroundColor = originalColor;
                                }, 150); // Daha kısa sürede rengi değiştir
                            } else {
                                // Buton yoksa yine de orijinal fonksiyonu çağır
                                try {
                                    window.originalCopyPanoutFunctionItem.call(scope, index);
                                    if (scope["${"$"}root"] && scope["${"$"}root"]["${"$"}${"$"}phase"] !== "${"$"}apply" && scope["${"$"}root"]["${"$"}${"$"}phase"] !== "${"$"}digest") {
                                        scope["${"$"}apply"]();
                                    }
                                } catch (e) {
                                    console.error('Kopyalama hatası:', e);
                                }
                            }
                            
                            // UI bloğunu engellemek için her zaman başarılı olduğunu belirt
                            return true;
                        };
                        
                        // Kopyalama butonlarına ek listenerlar ekle
                        var copyButtons = document.querySelectorAll('button[ng-click*="copyPanoutFunctionItem"]');
                        for (var i = 0; i < copyButtons.length; i++) {
                            (function(idx) {
                                var button = copyButtons[idx];
                                button.addEventListener('click', function(e) {
                                    // Original event'i durdurmak için değil, performans için
                                    // e.stopPropagation(); 
                                    // Orijinal ng-click olayı çalışmaya devam edecek
                                    
                                    // Görsel geri bildirim
                                    button.style.opacity = '0.7';
                                    setTimeout(function() {
                                        button.style.opacity = '1';
                                    }, 150);
                                }, true); // Use capturing to run before Angular's click handler
                            })(i);
                        }
                        
                        console.log('Kopyalama butonu optimizasyonu tamamlandı, ' + 
                                  copyButtons.length + ' buton iyileştirildi');
                    }, 20); // 20 deneme, yaklaşık 2 saniye
                    
                    return "Optimization script injected";
                } catch (e) {
                    console.error('Kopyalama butonu optimizasyonu hatası:', e);
                    return "Error: " + e.message;
                }
            })();
        """.trimIndent()

        webView?.evaluateJavascript(optimizationScript) { result ->
            Log.d(TAG, "Copy button optimization result: $result")
        }

        // Ayrıca genel performans iyileştirmeleri ekle
        val performanceScript = """
            (function() {
                // Event handler'ları optimize et
                if (typeof jQuery !== 'undefined') {
                    // jQuery kullanılıyorsa delegated event'leri kullan
                    jQuery(document).off('click.tkfOptimized').on('click.tkfOptimized', 'button,a,input[type="button"]', function() {
                        // Bu element tıklandığında
                        var el = this;
                        // Görsel geri bildirim
                        var wasOutlined = el.style.outline;
                        el.style.outline = '1px solid rgba(0,0,0,0.1)';
                        setTimeout(function() {
                            el.style.outline = wasOutlined;
                        }, 150);
                    });
                }
                
                // Sayfa render performansını artır
                document.documentElement.style.contain = 'content';
                var styleSheet = document.createElement('style');
                styleSheet.textContent = 'button, a { contain: layout style; backface-visibility: hidden; }' +
                                         'table { contain: layout; }' +
                                         '.btn, .btn-default { transform: translateZ(0); }'; // GPU hızlandırma
                document.head.appendChild(styleSheet);
                
                // 60fps animasyon için requestAnimationFrame kullan
                var raf = window.requestAnimationFrame || window.webkitRequestAnimationFrame;
                if (raf) {
                    // Add a class to indicate the optimization is active
                    document.body.classList.add('tkf-optimized');
                }
                
                return "Performance optimizations applied";
            })();
        """.trimIndent()

        webView?.evaluateJavascript(performanceScript) { result ->
            Log.d(TAG, "Performance optimization result: $result")
        }
    }
    
    /**
     * Kopyalama butonlarına doğrudan event listener ekleyerek hızlı tepki vermelerini sağlar
     */
    private fun injectDirectCopyButtonFix() {
        // WebView null kontrolü
        if (webView == null) {
            Log.e(TAG, "WebView is null in injectDirectCopyButtonFix")
            return
        }

        val directFixScript = """
            (function() {
                try {
                    // Web Worker kullanarak arka planda işlem yapma
                    if (!window.copyWorker) {
                        var workerScript = `
                            self.addEventListener('message', function(e) {
                                self.postMessage({
                                    type: 'log',
                                    message: 'Worker received message: ' + e.data.action
                                });
                                if (e.data.action === 'copy') {
                                    self.postMessage({
                                        type: 'copied',
                                        index: e.data.index,
                                        elementId: e.data.elementId
                                    });
                                }
                            });
                        `;
                        
                        // Inline worker oluştur
                        var blob = new Blob([workerScript], {type: 'application/javascript'});
                        var workerUrl = URL.createObjectURL(blob);
                        window.copyWorker = new Worker(workerUrl);
                        
                        window.copyWorker.addEventListener('message', function(e) {
                            if (e.data.type === 'log') {
                                console.log(e.data.message);
                            } else if (e.data.type === 'copied') {
                                var button = document.querySelector('[data-optimized-id="' + e.data.elementId + '"]');
                                if (button) {
                                    button.style.backgroundColor = '';
                                    button.removeAttribute('data-processing');
                                }
                            }
                        });
                    }
                
                    // Eğer zaten optimize edilmiş butonları işaretlediysen tekrar yapma
                    if (window.optimizedCopyButtons) {
                        var existingOptimizedCount = Object.keys(window.optimizedCopyButtons).length;
                        
                        // Butonları doğrudan seç - ngClick yerine i class'ı kullanarak daha hızlı bul
                        var copyButtons = document.querySelectorAll('button i.fa.fa-copy');
                        var copyButtonElements = [];
                        
                        // Butonları bul ve listele
                        for (var i = 0; i < copyButtons.length; i++) {
                            var btn = copyButtons[i].closest('button');
                            if (btn && btn.getAttribute('ng-click') && btn.getAttribute('ng-click').indexOf('copyPanoutFunctionItem') !== -1) {
                                copyButtonElements.push(btn);
                            }
                        }
                        
                        // Yeni buton yok ise işleme gerek yok
                        if (!copyButtonElements || copyButtonElements.length === 0 || copyButtonElements.length <= existingOptimizedCount) {
                            return "No new buttons to optimize";
                        }
                        
                        // Sadece yeni butonları optimize et
                        var newButtonsCount = 0;
                        for (var i = 0; i < copyButtonElements.length; i++) {
                            var button = copyButtonElements[i];
                            var buttonId = button.getAttribute('data-optimized-id');
                            
                            // Bu buton daha önce optimize edilmemiş
                            if (!buttonId) {
                                buttonId = 'copy-btn-' + i;
                                optimizeButton(button, buttonId, i);
                                newButtonsCount++;
                            }
                        }
                        
                        return "Optimized " + newButtonsCount + " new buttons";
                    } else {
                        // İlk kez çalıştırılıyor, tüm butonları optimize et
                        window.optimizedCopyButtons = {};
                        
                        // Butonları doğrudan seç - daha güvenilir yöntem
                        var copyButtons = document.querySelectorAll('button i.fa.fa-copy');
                        if (!copyButtons || copyButtons.length === 0) {
                            // Alternatif yöntem
                            copyButtons = document.querySelectorAll('button[ng-click*="copyPanoutFunctionItem"]');
                            if (!copyButtons || copyButtons.length === 0) {
                                return "Copy buttons not found yet";
                            }
                            
                            // Her butonu optimize et
                            console.log("Found " + copyButtons.length + " copy buttons to optimize (method 2)");
                            for (var i = 0; i < copyButtons.length; i++) {
                                var button = copyButtons[i];
                                var buttonId = 'copy-btn-' + i;
                                optimizeButton(button, buttonId, i);
                            }
                            
                            return "Direct copy button fix applied to " + copyButtons.length + " buttons";
                        }
                        
                        // Butonları bul ve optimize et
                        var copyButtonElements = [];
                        for (var i = 0; i < copyButtons.length; i++) {
                            var btn = copyButtons[i].closest('button');
                            if (btn && btn.getAttribute('ng-click') && btn.getAttribute('ng-click').indexOf('copyPanoutFunctionItem') !== -1) {
                                copyButtonElements.push(btn);
                            }
                        }
                        
                        console.log("Found " + copyButtonElements.length + " copy buttons to optimize (method 1)");
                        
                        // Her butonu optimize et
                        for (var i = 0; i < copyButtonElements.length; i++) {
                            var button = copyButtonElements[i];
                            var buttonId = 'copy-btn-' + i;
                            optimizeButton(button, buttonId, i);
                        }
                        
                        return "Direct copy button fix applied to " + copyButtonElements.length + " buttons";
                    }
                    
                    // Buton optimizasyon fonksiyonu
                    function optimizeButton(button, buttonId, index) {
                        // Butona ID ekle
                        button.setAttribute('data-optimized-id', buttonId);
                        button.setAttribute('data-button-index', index);
                        
                        // Optimize edildiğini işaretle
                        window.optimizedCopyButtons[buttonId] = true;
                        
                        // Mevcut tüm listenerleri temizle
                        var newButton = button.cloneNode(true);
                        if (button.parentNode) {
                            button.parentNode.replaceChild(newButton, button);
                            button = newButton;
                            
                            // ID'leri tekrar ayarla
                            button.setAttribute('data-optimized-id', buttonId);
                            button.setAttribute('data-button-index', index);
                        }
                        
                        // Ana clickleri engelle
                        button.setAttribute('onclick', 'event.preventDefault(); event.stopPropagation(); return false;');
                        
                        // Dokunmatik olaylar için ayrı listener ekle (mobile)
                        button.addEventListener('touchstart', function(e) {
                            handleButtonClick(e, this);
                        }, {passive: false, capture: true});
                        
                        // Sadece bir kez click listener ekle
                        button.addEventListener('click', function(e) {
                            handleButtonClick(e, this);
                        }, {passive: false, capture: true});
                        
                        // Ortak tıklama işleyicisi
                        function handleButtonClick(e, buttonElement) {
                            // Tıklama olayını işlemek için kısa bir zaman aralığı (debounce)
                            if (buttonElement.getAttribute('data-processing') === 'true') {
                                e.preventDefault();
                                e.stopPropagation();
                                return false;
                            }
                            
                            // İşlem başladı olarak işaretle
                            buttonElement.setAttribute('data-processing', 'true');
                            
                            // Görsel geri bildirim - anında
                            buttonElement.style.backgroundColor = '#c8e6c9';
                            
                            // Kopyalama işlemini worker'a gönder
                            if (window.copyWorker) {
                                window.copyWorker.postMessage({
                                    action: 'copy',
                                    index: parseInt(buttonElement.getAttribute('data-button-index')),
                                    elementId: buttonElement.getAttribute('data-optimized-id')
                                });
                            }
                            
                            // Kopyalama işlemini çağır
                            try {
                                var scope = window.angular.element(buttonElement).scope();
                                var indexAttr = buttonElement.getAttribute('data-button-index');
                                var index = parseInt(indexAttr);
                                
                                // Doğrudan orijinal fonksiyonu çağır - timeout olmadan
                                if (window.originalCopyPanoutFunctionItem) {
                                    window.originalCopyPanoutFunctionItem.call(scope, index);
                                } else {
                                    // Orijinal angular ng-click parse et ve çalıştır
                                    var ngClick = buttonElement.getAttribute('ng-click');
                                    if (ngClick && scope) {
                                        var matches = ngClick.match(/copyPanoutFunctionItem\\(([^)]+)\\)/);
                                        if (matches && matches[1]) {
                                            var param = matches[1].trim();
                                            if (param === "${"$"}index") {
                                                // Use the index value we already defined above
                                                param = indexAttr;
                                            }
                                            if (scope.copyPanoutFunctionItem) {
                                                scope.copyPanoutFunctionItem(parseInt(param));
                                            }
                                        }
                                    }
                                }
                                
                                // Sayfayı güncelle
                                if (scope && scope["${"$"}apply"]) {
                                    try {
                                        if (scope["${"$"}root"] && 
                                            scope["${"$"}root"]["${"$"}${"$"}phase"] !== "${"$"}apply" && 
                                            scope["${"$"}root"]["${"$"}${"$"}phase"] !== "${"$"}digest") {
                                            scope["${"$"}apply"]();
                                        }
                                    } catch(err) {
                                        console.error("Scope apply error:", err);
                                    }
                                }
                            } catch (err) {
                                console.error("Kopyalama hatası:", err);
                            }
                            
                            // İşlemi tamamla - daha kısa sürede
                            setTimeout(function() {
                                buttonElement.style.backgroundColor = '';
                                buttonElement.removeAttribute('data-processing');
                            }, 100);
                            
                            // Kesinlikle durduralım
                            e.preventDefault();
                            e.stopPropagation();
                            e.stopImmediatePropagation();
                            return false;
                        }
                    }
                } catch (e) {
                    console.error('Direct copy button fix error:', e);
                    return "Error: " + e.message;
                }
            })();
        """.trimIndent()

        webView?.evaluateJavascript(directFixScript) { result ->
            Log.d(TAG, "Direct copy button fix result: $result")
            
            // Kaydırma olaylarını iyileştir
            webView?.evaluateJavascript("""
                (function() {
                    // Mevcut kaydırma olaylarını kaldır
                    var html = document.documentElement;
                    html.style.overscrollBehavior = 'none';
                    html.style.touchAction = 'manipulation';
                    
                    // Tıklama olaylarını hızlandır
                    var style = document.createElement('style');
                    style.textContent = `
                        * { -webkit-tap-highlight-color: transparent !important; }
                        button, a { touch-action: manipulation !important; cursor: pointer !important; }
                        button, a, input, [role="button"] { user-select: none !important; }
                        .btn, button { pointer-events: auto !important; }
                    `;
                    document.head.appendChild(style);
                    
                    // Dokunma olaylarını iyileştir (300ms gecikmesini kaldır)
                    document.addEventListener('touchstart', function(){}, {passive: true});
                    
                    return "Touch optimization applied";
                })();
            """) { touchResult ->
                Log.d(TAG, "Touch optimization result: $touchResult")
            }
        }
    }
    
    override fun onFormSubmitted(
        continuity: String,
        extremeProtection: String,
        voltage: String,
        findings: String,
        cycleImpedance: String
    ) {
        // Form verilerini kaydet
        val prefs = getSharedPreferences("PanelFormData", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("continuity", continuity)
        editor.putString("extremeIncomeProtection", extremeProtection)
        editor.putString("voltage", voltage)
        editor.putString("findings", findings)
        editor.putString("cycleImpedance", cycleImpedance)
        editor.apply {}
        
        // JavaScript ile form alanlarını doldur
        val jsCode = """
            (function() {
                // CycleImpedance için
                var cycleImpedances = document.querySelectorAll('[name^="CycleImpedance"]');
                cycleImpedances.forEach(function(input) {
                    input.value = '$cycleImpedance';
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                });

                // Continuity için
                var continuities = document.querySelectorAll('[name^="Continuity"]');
                continuities.forEach(function(input) {
                    input.value = '$continuity';
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                });

                // ExtremeIncomeProtection için
                var extremeProtections = document.querySelectorAll('[name^="ExtremeIncomeProtection"]');
                extremeProtections.forEach(function(input) {
                    input.value = '$extremeProtection';
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                });

                // Voltage için
                var voltages = document.querySelectorAll('[name^="Voltage"]');
                voltages.forEach(function(input) {
                    input.value = '$voltage';
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                });

                // Findings için
                var findings = document.querySelectorAll('[name^="Findings"]');
                findings.forEach(function(input) {
                    input.value = '$findings';
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                });

                return 'completed';
            })();
        """.trimIndent()
        
        // WebView null kontrolü
        if (webView == null) {
            Log.e(TAG, "WebView is null in onFormSubmitted")
            Toast.makeText(this, "Form doldurulamadı, lütfen sayfayı yeniden yükleyin", Toast.LENGTH_SHORT).show()
            return
        }
        
        webView?.evaluateJavascript(jsCode) { result ->
            if (result.contains("completed")) {
                Toast.makeText(this, "Form dolduruldu", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Form doldurulurken bir hata oluştu", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveItems() {
        // WebView null kontrolü ekleyelim
        if (webView == null) {
            Log.e(TAG, "WebView is null in saveItems")
            Toast.makeText(this, "Değişiklikler kaydedilemedi, lütfen sayfayı yeniden yükleyin", Toast.LENGTH_SHORT).show()
            return
        }
        
        val saveScript = """
            (function() {
                // Önce doğrudan kaydet butonunu bulmayı dene
                var saveButton = document.querySelector('.actionB .btn-group .btn.btn-default.waves-effect.m-r-5');
                
                // Eğer bulunamazsa, daha esnek bir seçici kullanarak class'a göre bul
                if (!saveButton) {
                    saveButton = document.querySelector('.btn.btn-default.waves-effect.m-r-5');
                }
                
                // Hala bulunamadıysa, metin içeriğine göre bul
                if (!saveButton) {
                    var buttons = document.querySelectorAll('button');
                    for (var i = 0; i < buttons.length; i++) {
                        if (buttons[i].textContent.toLowerCase().indexOf('kaydet') !== -1) {
                            saveButton = buttons[i];
                            break;
                        }
                    }
                }
                
                if (saveButton) { 
                    saveButton.click();
                    console.log('Kaydet butonu bulundu ve tıklandı');
                    return 'saved';
                } else {
                    console.error('Kaydet butonu bulunamadı');
                    return 'save-button-not-found';
                }
            })();
        """.trimIndent()
                
        webView?.evaluateJavascript(saveScript) { result ->
            if (result.contains("saved")) {
                Toast.makeText(this, "Değişiklikler kaydedildi", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Kaydetme butonu bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun applyColumnWidths() {
        // WebView null kontrolü ekleyelim
        if (webView == null) {
            Log.e(TAG, "WebView is null in applyColumnWidths")
            return
        }

        try {
            val checkElementsScript = """
                (function() {
                    var allFound = true;
                    for (var i = 0; i < ${columnNames.size}; i++) {
                        var elements = document.getElementsByName('${columnNames[0]}0');
                        if (!elements || elements.length === 0) {
                            allFound = false;
                            break;
                        }
                    }
                    return allFound;
                })()
            """.trimIndent()

            webView?.evaluateJavascript(checkElementsScript) { result ->
                when {
                    "true" == result -> {
                        applyWidthsToColumns()
                        retryCount = 0 // Reset retry count on success
                    }
                    retryCount < MAX_RETRY_COUNT -> {
                        retryCount++
                        Log.d(TAG, "Retrying column width application. Attempt: $retryCount")
                        handler.postDelayed({ applyColumnWidths() }, RETRY_DELAY_MS)
                    }
                    else -> {
                        Log.e(TAG, "Failed to apply column widths after $MAX_RETRY_COUNT attempts")
                        retryCount = 0 // Reset for next time
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in applyColumnWidths: ${e.message}")
            retryCount = 0 // Reset for next time
        }
    }

    private fun applyWidthsToColumns() {
        // WebView null kontrolü ekleyelim
        if (webView == null) {
            Log.e(TAG, "WebView is null in applyWidthsToColumns")
            return
        }
        
        try {
            val jsBuilder = StringBuilder()
            jsBuilder.append("(function() {")

            // Column width adjustments with verification
            jsBuilder.append("try {")
            for (columnName in columnNames) {
                val columnWidth = columnWidths[columnName]

                jsBuilder.append("var elements = document.querySelectorAll('input[name^=\"")
                        .append(columnName)
                        .append("\"]');") // Tüm indeksli input'ları seçiyoruz
                        .append("if(elements && elements.length > 0) {")
                        .append("    for(var i=0; i<elements.length; i++) {")
                        .append("        elements[i].style.width = '")
                        .append(columnWidth)
                        .append("';")
                        .append("        elements[i].style.minWidth = '")
                        .append(columnWidth)
                        .append("';")
                        .append("    }")
                        .append("}")
            }

            // Panel padding adjustment with mutation observer
            jsBuilder.append("var style = document.createElement('style');")
                    .append("style.type = 'text/css';")
                    .append("style.innerHTML = '.panel-body { padding: 1px !important; }';")
                    .append("document.head.appendChild(style);")

                    // Add MutationObserver to maintain column widths
                    .append("var observer = new MutationObserver(function(mutations) {")
                    .append("    mutations.forEach(function(mutation) {")
                    .append("        if (mutation.type === 'attributes' || mutation.type === 'childList') {")

            // Re-apply widths in observer
            for (columnName in columnNames) {
                val columnWidth = columnWidths[columnName]

                jsBuilder.append("var elements = document.querySelectorAll('input[name^=\"")
                        .append(columnName)
                        .append("\"]');") 
                        .append("if(elements && elements.length > 0) {")
                        .append("    for(var i=0; i<elements.length; i++) {")
                        .append("        elements[i].style.width = '")
                        .append(columnWidth)
                        .append("';")
                        .append("        elements[i].style.minWidth = '")
                        .append(columnWidth)
                        .append("';")
                        .append("    }")
                        .append("}")
            }

            jsBuilder.append("        }")
                    .append("    });")
                    .append("});")

                    // Start observing
                    .append("observer.observe(document.body, {")
                    .append("    childList: true,")
                    .append("    subtree: true,")
                    .append("    attributes: true")
                    .append("});")

                    .append("} catch(e) { console.error('Error applying column widths:', e); }")
                    .append("return 'applied';")
                    .append("})()")

            webView?.evaluateJavascript(jsBuilder.toString()) { result ->
                if ("\"applied\"" == result) {
                    Log.d(TAG, "Column widths applied successfully")
                } else {
                    Log.e(TAG, "Error applying column widths: $result")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in applyWidthsToColumns: ${e.message}")
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showSettingsDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Sütun Genişliklerini Ayarla")

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        val padding = resources.getDimensionPixelSize(android.R.dimen.app_icon_size) / 4
        layout.setPadding(padding, padding, padding, padding)

        val editTexts = arrayOfNulls<EditText>(columnNames.size)

        for (i in columnNames.indices) {
            val columnName = columnNames[i]
            val columnLabel = columnLabels[i]

            val label = android.widget.TextView(this)
            label.text = "$columnLabel:"
            layout.addView(label)

            val editText = EditText(this)
            editText.hint = "Genişlik ($columnLabel)"
            editText.setText(columnWidths[columnName])
            editText.imeOptions = EditorInfo.IME_ACTION_DONE
            editText.inputType = InputType.TYPE_CLASS_TEXT

            editText.setOnEditorActionListener { v, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard(v)
                    return@setOnEditorActionListener true
                }
                false
            }

            editTexts[i] = editText
            layout.addView(editText)
        }

        builder.setView(layout)
        builder.setPositiveButton("Kaydet") { dialog, which ->
            saveColumnSettings(editTexts)
            showPageRefreshConfirmation()
        }

        builder.setNegativeButton("İptal", null)
        builder.show()
    }

    private fun saveColumnSettings(editTexts: Array<EditText?>) {
        val editor = sharedPreferences.edit()
        for (i in columnNames.indices) {
            val newWidth = editTexts[i]?.text.toString()
            columnWidths[columnNames[i]] = newWidth
            editor.putString(columnNames[i], newWidth)
        }
        editor.apply {}
    }

    private fun showPageRefreshConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Sayfa Yenileme")
            .setMessage("Ayarları uygulamak için sayfa yenilenecektir. Onaylıyor musunuz?")
            .setPositiveButton("Evet") { dialog, which -> webView?.reload() }
            .setNegativeButton("Hayır", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_URL, editTextUrl.text.toString())
    }

    private fun restoreState(savedInstanceState: Bundle?) {
        savedInstanceState?.let { bundle ->
            val savedUrl = bundle.getString(KEY_URL)
            savedUrl?.let {
                editTextUrl.setText(it)
            }
        }

        if (DataHolder.url.isNotEmpty()) {
            editTextUrl.setText(DataHolder.url)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            handleTouchEvent(event)
        }
        return super.dispatchTouchEvent(event)
    }

    private fun handleTouchEvent(event: MotionEvent) {
        val focusedView = currentFocus
        if (focusedView !is EditText) {
            return
        }

        val outRect = Rect()
        focusedView.getGlobalVisibleRect(outRect)
        if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
            focusedView.clearFocus()
            hideKeyboard(focusedView)
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onBackPressed() {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    // Aktivite yaşam döngüsü yönetimi
    override fun onPause() {
        super.onPause()
        // WebView durdurma işlemlerini yap
        webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        // WebView yeniden başlatma işlemlerini yap
        webView?.onResume() ?: run {
            // WebView yoksa yeniden oluşturmayı dene
            Log.w(TAG, "WebView is null in onResume, trying to recover")
            initializeViews()
            setupWebView()
        }
    }
    
    override fun onDestroy() {
        try {
            // Timer'ı durdur
            stopCopyButtonMonitor()
            
            // WebView'i havuza geri ver
            val webViewInstance = webView
            if (webViewInstance != null) {
                // WebView içeriğini temizleyelim
                webViewInstance.stopLoading()
                webViewInstance.loadUrl("about:blank")
                webViewInstance.clearHistory()
                webViewInstance.clearCache(true)
                
                // WebView'in parent'i varsa önce kaldır
                val parent = webViewInstance.parent
                if (parent is ViewGroup) {
                    parent.removeView(webViewInstance)
                }
                
                // Havuza geri verelim
                webViewPool.releaseWebView(webViewInstance)
                webView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up WebView in onDestroy", e)
        }
        super.onDestroy()
    }

    /**
     * Kopyalama butonlarını belirli aralıklarla optimize eden bir timer başlatır 
     */
    private fun startCopyButtonMonitor() {
        copyButtonTimerHandler = Handler(Looper.getMainLooper())
        
        copyButtonTimerRunnable = object : Runnable {
            override fun run() {
                // WebView aktif ve görünür mü kontrol et
                if (webView != null && webView?.visibility == View.VISIBLE) {
                    injectDirectCopyButtonFix()
                }
                
                // Daha seyrek çalıştır - 8 saniyede bir yerine
                copyButtonTimerHandler?.postDelayed(this, 8000)
            }
        }
        
        // Timer'ı başlat - ilk çalıştırmayı geciktir
        copyButtonTimerHandler?.postDelayed(copyButtonTimerRunnable!!, 3000)
    }
    
    /**
     * Timer'ı durdur ve temizle
     */
    private fun stopCopyButtonMonitor() {
        copyButtonTimerRunnable?.let {
            copyButtonTimerHandler?.removeCallbacks(it)
        }
        copyButtonTimerHandler = null
        copyButtonTimerRunnable = null
    }
}