package com.asforce.asforcetkf2.ui.leakage

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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.asforce.asforcetkf2.R
import com.asforce.asforcetkf2.util.DataHolder
import com.asforce.asforcetkf2.util.SimpleTextWatcher
import com.asforce.asforcetkf2.util.WebViewPool
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Kaçak Akım Kontrolü ekranı
 * WebView kullanarak harici bir form sayfasını yükler ve manipüle eder
 */
class LeakageControlActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "LeakageControlActivity"
        private const val PREFS_NAME = "leakage_control_settings"
        private const val KEY_URL = "saved_url"
        private const val BASE_URL = "https://app.szutest.com.tr/EXT/PKControl/EditLeakage/"
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
    
    private val columnNames = arrayOf(
        "MeasuredLocation", "LeakageRole", "TestCoefficient", "OpenningCurrent",
        "OpenningValue", "Otomet", "TestPoint", "RCDNo", "Result"
    )
    
    private val columnLabels = arrayOf(
        "Ölçüm yapılan Yer", "Kaç. Ak. Rolesi", "Test Katsayısı", "Açma Akımı(mA)",
        "Açma Zamanı(mS)", "Otomat", "Test Noktası", "RCD No", "Sonuç"
    )
    
    private val defaultColumnWidths = arrayOf(
        "140px", "100px", "60px", "70px", "70px", "80px", "130px", "80px", "130px"
    )
    
    // Sütun genişliği uygulamak için yeniden deneme mekanizması
    private val MAX_RETRY_COUNT = 5
    private val RETRY_DELAY_MS = 500L
    private var retryCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leakage_control)

        // WebView havuzunu başlat
        webViewPool = WebViewPool.getInstance(this)
        
        initializeViews()
        setupToolbar()
        setupSharedPreferences()
        setupWebView()
        setupClickListeners()
        setupTouchHandling()
        restoreState(savedInstanceState)

        // URL'yi Shared Preferences'tan yükle
        val prefs = getSharedPreferences("LeakageControlPrefs", Context.MODE_PRIVATE)
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
        backButton = findViewById(R.id.backButton)
        
        // WebView havuzundan al
        webView = webViewPool.acquireWebView()
        
        // WebView'in mevcut bir parent'i varsa önce ondan kaldır
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
    
    private fun setupToolbar() {
        // Toolbar kaldırıldığı için bu metod artık kullanılmıyor
        // Ancak geriye dönük uyumluluk için boş olarak bırakıldı
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
                        val prefs = getSharedPreferences("LeakageControlPrefs", Context.MODE_PRIVATE)
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
        }
    }

    private fun setupClickListeners() {
        buttonLoadPage.setOnClickListener { loadWebPage() }
        buttonFillForm.setOnClickListener { fillForm() }
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
        handler.postDelayed({ fetchAndSaveMeasuredLocation0() }, FETCH_DELAY)
        handler.postDelayed({ buttonLoadPage.isEnabled = true }, 1000)
    }

    private fun fillForm() {
        // WebView null kontrolü ekleyelim
        if (webView == null) {
            Log.e(TAG, "WebView is null in fillForm")
            Toast.makeText(this, "Form doldurulamadı, lütfen sayfayı yeniden yükleyin", Toast.LENGTH_SHORT).show()
            return
        }
        
        val jsCode = buildFormFillJavaScript()
        webView?.evaluateJavascript(jsCode) { result ->
            Toast.makeText(this, "Form dolduruldu", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildFormFillJavaScript(): String {
        return """
            function fillForm() {
                var rows = document.querySelectorAll('[name^="LeakageRole"]');
                for (var i = 0; i < rows.length; i++) {
                    var leakageRole = document.getElementsByName('LeakageRole' + i)[0];
                    var result = document.getElementsByName('Result' + i)[0];
                    var openingCurrent = document.getElementsByName('OpenningCurrent' + i)[0];
                    var openingValue = document.getElementsByName('OpenningValue' + i)[0];
                    var testCoefficient = document.getElementsByName('TestCoefficient' + i)[0];

                    if (testCoefficient) {
                        Array.from(testCoefficient.options).forEach(option => {
                            if (option.label.trim() === '1') {
                                option.selected = true;
                            }
                        });
                        testCoefficient.dispatchEvent(new Event('change', { bubbles: true }));
                    }

                    if (leakageRole && result && openingCurrent && openingValue) {
                        if (result.value === 'string:Uygun Değil' || 
                            result.value === 'string:Test Edilemedi' || 
                            result.value === 'string:Test Edilemedi (NOT 1)') {
                            openingCurrent.value = '---';
                            openingValue.value = '---';
                        } else {
                            if (leakageRole.value === 'string:30ma') {
                                openingCurrent.value = (15.0 + Math.random() * (27.9 - 15.0)).toFixed(1);
                            } else if (leakageRole.value === 'string:300ma') {
                                openingCurrent.value = (150.0 + Math.random() * (279.9 - 150.0)).toFixed(1);
                            }

                            let randomValue = Math.random() * 100;
                            if (randomValue < 80) {
                                openingValue.value = (20.0 + Math.random() * (30.9 - 20.0)).toFixed(1);
                            } else {
                                openingValue.value = (30.9 + Math.random() * (55.9 - 30.9)).toFixed(1);
                            }
                        }

                        openingCurrent.dispatchEvent(new Event('input', { bubbles: true }));
                        openingValue.dispatchEvent(new Event('input', { bubbles: true }));
                    }
                }
                return 'completed';
            }
            fillForm();
        """.trimIndent()
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
                // Önce doğrudan ng-click özniteliğiyle butonu bulmayı dene
                var saveButton = document.querySelector('button[ng-click="submitLeakageItems()"]');
                
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

                jsBuilder.append("var elements = document.getElementsByName('")
                        .append(columnName)
                        .append("0');")
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

                jsBuilder.append("var elements = document.getElementsByName('")
                        .append(columnName)
                        .append("0');")
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

    private fun fetchAndSaveMeasuredLocation0() {
        // WebView null kontrolü ekleyelim
        if (webView == null) {
            Log.e(TAG, "WebView is null in fetchAndSaveMeasuredLocation0")
            return
        }
        
        val script = """
            (function() {
                var element = document.querySelector('[name="MeasuredLocation0"]');
                return element ? element.value : '';
            })()
        """.trimIndent()

        webView?.evaluateJavascript(script) { value ->
            if (value != null && value != "null" && value != "\"\"") {
                DataHolder.measuredLocation0 = value.replace("^\"|\"$".toRegex(), "")
                Log.d(TAG, "MeasuredLocation0: ${DataHolder.measuredLocation0}")
            } else {
                Log.d(TAG, "MeasuredLocation0 değeri alınamadı")
            }
        }
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