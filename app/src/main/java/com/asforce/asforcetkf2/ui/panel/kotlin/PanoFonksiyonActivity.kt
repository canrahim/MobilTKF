package com.asforce.asforcetkf2.ui.panel.kotlin

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
 * Pano Fonksiyon ekranı
 * WebView kullanarak harici bir form sayfasını yükler ve manipüle eder
 */
class PanoFonksiyonActivity : AppCompatActivity(), FormDialogFragment.FormDialogListener {
    
    companion object {
        private const val TAG = "PanoFonksiyonActivity"
        private const val PREFS_NAME = "column_settings2"
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
    private lateinit var menuIcon1: ImageButton
    private lateinit var menuContent1: LinearLayout
    private lateinit var backButton: ImageView
    
    // WebView
    private var webView: WebView? = null
    private lateinit var webViewPool: WebViewPool
    
    // Sütun genişlikleri ve ayarlar
    private val columnWidths = HashMap<String, String>()
    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    
    // Form Dialog
    private lateinit var formDialog: FormDialogFragment
    
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu3)

        // WebView havuzunu başlat
        webViewPool = WebViewPool.getInstance(this)
        
        initializeViews()
        setupSharedPreferences()
        setupWebView()
        setupClickListeners()
        setupTouchHandling()
        restoreState(savedInstanceState)

        // URL'yi Shared Preferences'tan yükle
        val prefs = getSharedPreferences("PanoFonksiyonPrefs", Context.MODE_PRIVATE)
        val lastUrl = prefs.getString("lastUrl", "") ?: "" // Varsayılan olarak boş dize
        
        // DataHolder'dan URL'yi al eğer varsa, yoksa Shared Preferences'tan al
        if (DataHolder.url.isNotEmpty()) {
            editTextUrl.setText(DataHolder.url)
        } else {
            editTextUrl.setText(lastUrl)
        }

        // Sayfa otomatik olarak yüklensin
        loadWebPage()
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
        webView?.let { view ->
            // Kaydırma performansını artırmak için
            view.overScrollMode = View.OVER_SCROLL_ALWAYS
            
            // Size parametrelerini güncelle
            view.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Dokunma ve kaydırma iyileştirmesi
            view.isFocusable = true
            view.isFocusableInTouchMode = true
            
            // Direkt WebView'e geçerli onTouch olayları için
            view.setOnTouchListener { v, event ->
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
        menuIcon1 = findViewById(R.id.menuIcon1)
        menuContent1 = findViewById(R.id.menuContent1)
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
        webView?.let { view ->
            webViewContainer.addView(view)
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
        
        webView?.apply {
            // WebView dokunma hassasiyeti ve sürükleme ayarlarını iyileştir
            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            
            // Kaydırma davranışını iyileştir
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = true
            isScrollContainer = true
            isScrollbarFadingEnabled = false // Kaydırma çubuğunu her zaman göster
            
            // WebView kaydırma davranışını iyileştir
            setOnTouchListener { v, event ->
                // Her türlü dokunma olayında WebView'in olayları işlemesine izin ver
                // Yönü ne olursa olsun (yatay/dikey) kaydırmaya izin ver
                // WebView kendi içinde kaydırmayı yönetmesi için parent'tan dokunma olaylarını engelle
                v.parent.requestDisallowInterceptTouchEvent(true)
                
                // WebView için odak ayarla (klavye girişi vb. için)
                v.requestFocus()
                
                // false döndür ki WebView olayları işlemeye devam etsin
                false
            }
            
            // Progress bar'ın durumunu kontrol et ve WebView'in yükleme yüzdesi %100 olduğunda gizle
            setWebChromeClient(object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    if (newProgress == 100) {
                        hideProgressBar()
                    }
                }
            })
            
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    
                    // Web sayfası yüklenme işlemi tamamlandığında, WebView kontrolü
                    // Aktivite kapanırken WebView null olabilir, bu durumda işlemi atlayarak çöküşü önleyelim
                    if (webView == null) {
                        Log.w(TAG, "WebView is null in onPageFinished, skipping operations")
                        return
                    }
                    
                    try {
                        applyColumnWidths()
                        
                        // URL'yi kaydet
                        val prefs = getSharedPreferences("PanoFonksiyonPrefs", Context.MODE_PRIVATE)
                        val editor = prefs.edit()
                        editor.putString("lastUrl", url)
                        editor.apply()
                        
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
                        
                        // Kopyalama butonları için olay dinleyicisi ekle
                        val copyButtonListenerJS = """
                            (function() {
                                // Kopyalama butonuna tıklandığında çalışacak fonksiyon
                                function handleCopyClick() {
                                    console.log('Kopyalama işlemi sonrası genişlik düzenleme planlandı');
                                    // 400ms sonra sütun genişliklerini uygula
                                    setTimeout(function() {
                                        window.androidColumnWidthCallback.reapplyColumnWidths();
                                        // 200ms sonra ikinci kez dene
                                        setTimeout(function() {
                                            window.androidColumnWidthCallback.reapplyColumnWidths();
                                        }, 200);
                                    }, 400);
                                }
                                
                                // DOM'a bir global olay dinleyicisi ekle (capture phase)
                                document.addEventListener('click', function(event) {
                                    // Kopyalama butonuna tıklanıp tıklanmadığını kontrol et
                                    var target = event.target;
                                    
                                    // Buton elementine kadar yukarı çık
                                    while (target != null) {
                                        if (target.tagName === 'BUTTON' || 
                                            (target.getAttribute && target.getAttribute('ng-click') && 
                                             target.getAttribute('ng-click').indexOf('copyPanoutFunctionItem') > -1)) {
                                            // Kopyalama butonu bulundu
                                            handleCopyClick();
                                            // Olayı engelleme (orijinal işlev çalışsın)
                                            return;
                                        } else if (target.tagName === 'I' && target.className.indexOf('fa-copy') > -1) {
                                            // Kopyalama iconu bulundu
                                            handleCopyClick();
                                            return;
                                        }
                                        target = target.parentNode;
                                    }
                                }, true);
                                
                                // Sayfadaki tüm kopyalama butonları/ikonları için de kontrol et
                                function checkForCopyIcons() {
                                    // fa-copy sınıfına sahip tüm ikonları bul
                                    var copyIcons = document.querySelectorAll('.fa-copy');
                                    copyIcons.forEach(function(icon) {
                                        // Parent butonunu bul
                                        var button = icon.closest('button') || icon.parentNode;
                                        if (button) {
                                            // Orijinal tıklama işlevini bozmadan önce bir olay dinleyicisi ekle
                                            button.addEventListener('click', function(e) {
                                                // Olayı engelleme, sadece genişlik düzenleme işlevini ekle
                                                handleCopyClick();
                                            });
                                        }
                                    });
                                }
                                
                                // Sayfa yüklendiğinde kontrol et
                                if (document.readyState === 'complete') {
                                    checkForCopyIcons();
                                } else {
                                    // Sayfa tam olarak yüklendiğinde
                                    window.addEventListener('load', checkForCopyIcons);
                                }
                                
                                // 1 saniye sonra tekrar kontrol et (Angular bazen gecikmeli yükler)
                                setTimeout(checkForCopyIcons, 1000);
                                
                                return true;
                            })();
                        """.trimIndent()
                        
                        view.evaluateJavascript(copyButtonListenerJS) { result ->
                            Log.d(TAG, "Copy button listener setup result: $result")
                        }
                        
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
            }
            
            // JavaScript'ten Android'e köprü oluştur
            addJavascriptInterface(object : Any() {
                @android.webkit.JavascriptInterface
                fun reapplyColumnWidths() {
                    Log.d(TAG, "JavaScript'ten sütun genişlikleri yeniden uygulama isteği alındı")
                    handler.post { applyColumnWidths() }
                }
            }, "androidColumnWidthCallback")
        }
    }

    private fun setupClickListeners() {
        buttonLoadPage.setOnClickListener { loadWebPage() }
        buttonFillForm.setOnClickListener { fillForm() }
        buttonSaveItems.setOnClickListener { saveItems() }
        settingsButton.setOnClickListener { showSettingsDialog() }
        
        // Geri butonuna tıklama işlevini ekle
        backButton.setOnClickListener {
            finish() // Aktiviteyi sonlandır
        }
        
        // Menü iconu tıklanınca menüyü göster/gizle
        menuIcon1.setOnClickListener { toggleMenu() }
        
        // Menü seçenekleri - şimdilik sadece Toast mesajları gösterelim
        findViewById<View>(R.id.menu1Option1).setOnClickListener {
            Toast.makeText(this, "Kaçak Akım seçildi", Toast.LENGTH_SHORT).show()
            menuContent1.visibility = View.GONE
        }

        findViewById<View>(R.id.menu1Option2).setOnClickListener {
            Toast.makeText(this, "Pano Fonksiyon seçildi", Toast.LENGTH_SHORT).show()
            menuContent1.visibility = View.GONE
        }
        
        findViewById<View>(R.id.menu1Option3).setOnClickListener {
            Toast.makeText(this, "Topraklama seçildi", Toast.LENGTH_SHORT).show()
            menuContent1.visibility = View.GONE
        }

        findViewById<View>(R.id.menu1Option4).setOnClickListener {
            Toast.makeText(this, "Termal Kamera seçildi", Toast.LENGTH_SHORT).show()
            menuContent1.visibility = View.GONE
        }

        setupTextWatchers()
    }

    private fun toggleMenu() {
        // Menüyü aç/kapat
        menuContent1.visibility = if (menuContent1.visibility == View.VISIBLE) View.GONE else View.VISIBLE
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

    private fun fillForm() {
        // Form verilerini SharedPreferences'tan yükle
        val prefs = getSharedPreferences("MyFormData", MODE_PRIVATE)
        DataHolder.continuity = prefs.getString("continuity", "0.09") ?: "0.09"
        DataHolder.extremeIncomeProtection = prefs.getString("extremeIncomeProtection", "---") ?: "---"
        DataHolder.voltage = prefs.getString("voltage", "230.1") ?: "230.1"
        DataHolder.findings = prefs.getString("findings", "---") ?: "---"
        DataHolder.cycleImpedance = prefs.getString("cycleImpedance", "EK-TP") ?: "EK-TP"

        // Diyalogu oluştur ve göster
        formDialog = FormDialogFragment(webView)
        formDialog.show(supportFragmentManager, "FormDialog")
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
                var saveButton = document.querySelector('.actionB .btn-group .btn.btn-default.waves-effect.m-r-5');
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

    override fun onFormSubmitted(continuity: String, extremeProtection: String, 
                               voltage: String, findings: String, cycleImpedance: String) {
        // DataHolder'ı güncelle
        DataHolder.continuity = continuity
        DataHolder.extremeIncomeProtection = extremeProtection
        DataHolder.voltage = voltage
        DataHolder.findings = findings
        DataHolder.cycleImpedance = cycleImpedance
        
        // Save the current values to SharedPreferences for future use
        val prefs = getSharedPreferences("MyFormData", MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("continuity", continuity)
        editor.putString("extremeIncomeProtection", extremeProtection)
        editor.putString("voltage", voltage)
        editor.putString("findings", findings)
        editor.putString("cycleImpedance", cycleImpedance)
        editor.apply()
        
        // JavaScript ile form alanlarını doldur
        val jsCode = """
            var cycleImpedances = document.querySelectorAll('[name^="CycleImpedance"]');
            cycleImpedances.forEach(function(input) {
                input.value = '$cycleImpedance';
                input.dispatchEvent(new Event('input', { bubbles: true }));
            });

            var continuities = document.querySelectorAll('[name^="Continuity"]');
            continuities.forEach(function(input) {
                input.value = '$continuity';
                input.dispatchEvent(new Event('input', { bubbles: true }));
            });

            var extremeProtections = document.querySelectorAll('[name^="ExtremeIncomeProtection"]');
            extremeProtections.forEach(function(input) {
                input.value = '$extremeProtection';
                input.dispatchEvent(new Event('input', { bubbles: true }));
            });

            var voltages = document.querySelectorAll('[name^="Voltage"]');
            voltages.forEach(function(input) {
                input.value = '$voltage';
                input.dispatchEvent(new Event('input', { bubbles: true }));
            });

            var findings = document.querySelectorAll('[name^="Findings"]');
            findings.forEach(function(input) {
                input.value = '$findings';
                input.dispatchEvent(new Event('input', { bubbles: true }));
            });
        """.trimIndent()

        // JavaScript kodunu çalıştır
        webView?.evaluateJavascript(jsCode, null)
    }

    private fun applyColumnWidths() {
        // WebView null kontrolü ekleyelim
        if (webView == null) {
            Log.e(TAG, "WebView is null in applyColumnWidths")
            return
        }

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
    }

    private fun applyWidthsToColumns() {
        // WebView null kontrolü ekleyelim
        if (webView == null) {
            Log.e(TAG, "WebView is null in applyWidthsToColumns")
            return
        }
        
        val jsBuilder = StringBuilder()
        jsBuilder.append("(function() {")
        jsBuilder.append("try {")

        for (columnName in columnNames) {
            val columnWidth = columnWidths[columnName]

            // İsimleri dinamik olarak seçmek için "starts-with" (başlangıcı eşleşen) kullanıyoruz
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
                .append(";")
        }

        jsBuilder.append("} catch(e) { console.error('Error applying column widths:', e); }")
            .append("})()");

        webView?.evaluateJavascript(jsBuilder.toString(), null)
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

            val label = TextView(this)
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
        editor.apply()
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
            // WebView'i havuza geri ver
            webView?.let { view ->
                // WebView içeriğini temizleyelim
                view.stopLoading()
                view.loadUrl("about:blank")
                view.clearHistory()
                view.clearCache(true)
                
                // WebView'in parent'i varsa önce kaldır
                val parent = view.parent
                if (parent is ViewGroup) {
                    parent.removeView(view)
                }
                
                // Havuza geri verelim
                webViewPool.releaseWebView(view)
                webView = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up WebView in onDestroy", e)
        }
        super.onDestroy()
    }
} 