package com.asforce.asforcetkf2.ui.topraklama.kotlin

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
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.asforce.asforcetkf2.R
import com.asforce.asforcetkf2.util.DataHolder
import com.asforce.asforcetkf2.util.SimpleTextWatcher
import com.asforce.asforcetkf2.util.WebViewPool
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.util.HashMap
import java.util.Random
import java.util.regex.Pattern

/**
 * Topraklama Kontrolü ekranı
 * WebView kullanarak harici bir form sayfasını yükler ve manipüle eder
 */
class TopraklamaControlActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "TopraklamaControlActivity"
        private const val PREFS_NAME = "topraklama_control_settings"
        private const val KEY_URL = "saved_url"
        private const val BASE_URL = "https://app.szutest.com.tr/EXT/PKControl/EditGrounding/"
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
    
    // Topraklama form alanları için değişkenler
    private val columnNames = arrayOf(
        "TPStatus", "TagName", "MeasuredLocation", "ProtectiveConductorSection",
        "LeakageRelayType", "InA", "OpenningType", "OpeningTypeMultiplier", "IaA",
        "MeasuredGroundingShortCircuitCurrent", "MeasuredValue", "LimitValue"
    )
    
    private val columnLabels = arrayOf(
        "TP Durum", "Etiket Adı", "Ölçüm yapılan Yer", "Koruma İletkeni Kesiti",
        "Kaçak Akım Rölesi", "In(A)", "Açma Tipi", "Çarpan(TMŞ)", "Ia(A)",
        "Hesaplanan Toprak Kısa D.Akımı", "Ölçülen Değer", "Sınır Değer"
    )
    
    private val defaultColumnWidths = arrayOf(
        "80px", "100px", "140px", "120px", "120px", "70px", "80px", "80px",
        "70px", "130px", "100px", "100px"
    )
    
    // Sütun genişliği uygulamak için yeniden deneme mekanizması
    private val MAX_RETRY_COUNT = 5
    private val RETRY_DELAY_MS = 500L
    private var retryCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_topraklama_control)

        // WebView havuzunu başlat
        webViewPool = WebViewPool.getInstance(this)
        
        initializeViews()
        setupSharedPreferences()
        setupWebView()
        setupClickListeners()
        setupTouchHandling()
        restoreState(savedInstanceState)

        // URL'yi Shared Preferences'tan yükle
        val prefs = getSharedPreferences("TopraklamaControlPrefs", Context.MODE_PRIVATE)
        val lastUrl = prefs.getString("lastUrl", "") ?: "" // Varsayılan olarak boş dize
        
        // DataHolder'dan URL'yi al eğer varsa, yoksa Shared Preferences'tan al
        if (DataHolder.topraklama.isNotEmpty()) {
            editTextUrl.setText(DataHolder.topraklama)
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
        // UI Bileşenlerini tanımla
        urlInputLayout = findViewById(R.id.urlInputLayout)
        editTextUrl = findViewById(R.id.editTextUrl)
        buttonLoadPage = findViewById(R.id.buttonLoadPage)
        buttonFillForm = findViewById(R.id.buttonFillForm)
        buttonSaveItems = findViewById(R.id.buttonSaveItems)
        settingsButton = findViewById(R.id.settingsButton)
        backButton = findViewById(R.id.backButton)
        
        // WebView'i doğrudan XML'de tanımlı olandan al
        webView = findViewById(R.id.webView)
        
        // WebView varsa görünürlüğünü kontrol et
        webView?.let { view ->
            Log.d(TAG, "Found WebView from XML layout")
            view.visibility = View.VISIBLE
            
            // WebView container görünürlüğünü kontrol et
            val webViewContainer = findViewById<LinearLayout>(R.id.webViewContainer)
            webViewContainer.visibility = View.VISIBLE
        } ?: run {
            // XML'de tanımlı webview bulunamadıysa yeni oluştur
            Log.d(TAG, "WebView not found in XML, creating new one")
            webView = WebView(this)
            
            webView?.let { view ->
                // Görünürlük ayarları
                view.visibility = View.VISIBLE
                
                // WebView layoutu ayarla
                val layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                view.layoutParams = layoutParams
                
                // Container'a ekle
                val webViewContainer = findViewById<LinearLayout>(R.id.webViewContainer)
                webViewContainer.removeAllViews()
                webViewContainer.addView(view)
                webViewContainer.visibility = View.VISIBLE
                
                view.requestLayout()
            }
        }
        
        // Debug bilgisi
        Log.d(TAG, "WebView initialized: ${webView != null}, Visibility: ${webView?.visibility}")
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

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView() {
        // WebView null ise yeni bir tane oluştur
        if (webView == null) {
            Log.d(TAG, "WebView is null in setupWebView, creating new one")
            webView = WebView(this)
            
            // WebView'i container'a ekle
            val webViewContainer = findViewById<LinearLayout>(R.id.webViewContainer)
            if (webViewContainer != null) {
                // Mevcut içeriği temizle
                webViewContainer.removeAllViews()
                webViewContainer.visibility = View.VISIBLE
                
                // WebView'i ekle ve parametrelerini ayarla
                webView?.let { view ->
                    val layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    view.layoutParams = layoutParams
                    view.visibility = View.VISIBLE
                    webViewContainer.addView(view)
                    view.requestLayout()
                }
            } else {
                Log.e(TAG, "WebViewContainer not found")
                return
            }
        }
        
        webView?.apply {
            // Açık bir şekilde JS etkinleştirme ve diğer kritik ayarlar
            settings.apply {
                javaScriptEnabled = true // En önemli ayar!
                domStorageEnabled = true
                javaScriptCanOpenWindowsAutomatically = true
                loadsImagesAutomatically = true
                
                // Hata ayıklama ve konsol mesajlarını etkinleştir
                setGeolocationEnabled(true)
                // setAppCacheEnabled artık desteklenmiyor
                databaseEnabled = true
                
                // Gelişmiş ayarlar
                allowContentAccess = true
                allowFileAccess = true
                setSupportMultipleWindows(true)
                
                // Görüntüleme ayarları
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = true
                displayZoomControls = false
                
                layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            }
            
            // Kaydırma davranışını iyileştir
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = true
            isScrollContainer = true
            isScrollbarFadingEnabled = false // Kaydırma çubuğunu her zaman göster
            
            // WebView kaydırma davranışını iyileştir
            setOnTouchListener { v, event ->
                // Her türlü dokunma olayında WebView'in olayları işlemesine izin ver
                // Yönü ne olursa olsun (yatay/dikey) kaydırmaya izin ver
                v.parent.requestDisallowInterceptTouchEvent(true)
                
                // WebView için odak ayarla
                v.requestFocus()
                
                // false döndür ki WebView olayları işlemeye devam etsin
                false
            }
            
            // Debug mesajları için Chrome client
            setWebChromeClient(object : android.webkit.WebChromeClient() {
                // Konsol mesajlarını yakala
                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                    Log.d(TAG, "JS Console ${consoleMessage.messageLevel()}: ${consoleMessage.message()}" +
                            " -- line ${consoleMessage.lineNumber()} of ${consoleMessage.sourceId()}")
                    return true
                }
                
                // Javascript iletileri gösterme kabiliyeti
                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                    Log.d(TAG, "JS Alert: $message")
                    result?.confirm()
                    return true
                }
                
                // Progress bar durumunu kontrol et
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
                        val prefs = getSharedPreferences("TopraklamaControlPrefs", Context.MODE_PRIVATE)
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
        buttonFillForm.setOnClickListener { showFormFillDialog() }
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
                DataHolder.topraklama = input

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
                        DataHolder.topraklama = digits
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

    private fun showFormFillDialog() {
        // Modal pencere oluşturma
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Form Doldurma Ayarları")

        // Modal içeriğini inflate etme
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_topraklama_form_fill, null)
        builder.setView(dialogView)

        // UI elemanlarını bulma
        val editTextTagName = dialogView.findViewById<EditText>(R.id.editTextTagName)
        val editTextMinValue = dialogView.findViewById<EditText>(R.id.editTextMinValue)
        val editTextMaxValue = dialogView.findViewById<EditText>(R.id.editTextMaxValue)
        val buttonSave = dialogView.findViewById<Button>(R.id.buttonSave)
        val buttonCancel = dialogView.findViewById<Button>(R.id.buttonCancel)

        // Default değer atama
        editTextTagName.setText("---")

        // Dialog oluşturma
        val dialog = builder.create()
        dialog.setCancelable(false)

        // İptal butonuna tıklama
        buttonCancel.setOnClickListener { dialog.dismiss() }

        // Kaydet butonuna tıklama
        buttonSave.setOnClickListener {
            val tagName = editTextTagName.text.toString().trim()
            val minValueStr = editTextMinValue.text.toString().trim()
            val maxValueStr = editTextMaxValue.text.toString().trim()

            if (TextUtils.isEmpty(tagName)) {
                Toast.makeText(this, "Etiket adı boş olamaz", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (TextUtils.isEmpty(minValueStr) || TextUtils.isEmpty(maxValueStr)) {
                Toast.makeText(this, "Lütfen minimum ve maksimum değerleri girin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val minValue = minValueStr.toFloat()
                val maxValue = maxValueStr.toFloat()

                if (minValue >= maxValue) {
                    Toast.makeText(this, "Minimum değer maksimum değerden küçük olmalıdır", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Form doldurma işlemini başlat
                fillForm(tagName, minValue, maxValue)
                dialog.dismiss()
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Geçersiz sayı formatı", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    private fun fillForm(tagName: String, minValue: Float, maxValue: Float) {
        // WebView null kontrolü ekleyelim
        if (webView == null) {
            Log.e(TAG, "WebView is null in fillForm")
            Toast.makeText(this, "Form doldurulamadı, lütfen sayfayı yeniden yükleyin", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Daha önce doldurma işlemi yapılıp yapılmadığını haber ver
        Toast.makeText(this, "Form doldurma işlemi başlatılıyor...", Toast.LENGTH_SHORT).show()

        Log.d(TAG, "Starting form fill operation with parameters: tagName=$tagName, minValue=$minValue, maxValue=$maxValue")
        
        // Boş form alanlarını bul
        val findEmptyRowsScript = """
            (function() {
                try {
                    console.log('Searching for empty TagName fields...');
                    var allInputs = document.querySelectorAll('input[name^="TagName"]');
                    console.log('Total TagName fields found: ' + allInputs.length);
                    
                    var emptyRows = [];
                    for(var i = 0; i < allInputs.length; i++) {
                        var input = allInputs[i];
                        console.log('Checking input: ' + input.name + ', value: "' + input.value + '"');
                        
                        if(!input.value || input.value === '' || input.value === '---') {
                            var indexMatch = input.name.match(/\d+$/);
                            if(indexMatch) {
                                var rowIndex = indexMatch[0];
                                console.log('Found empty row with index: ' + rowIndex);
                                emptyRows.push(rowIndex);
                            }
                        }
                    }
                    
                    console.log('Total empty rows found: ' + emptyRows.length);
                    return JSON.stringify(emptyRows);
                } catch(e) {
                    console.error('Error in findEmptyRowsScript:', e);
                    return JSON.stringify({error: e.toString()});
                }
            })();
        """.trimIndent()

        webView?.evaluateJavascript(findEmptyRowsScript) { result ->
            Log.d(TAG, "FindEmptyRows result: $result")
            
            if (result != null && result != "null" && result != "\"\"" && !result.contains("error")) {
                try {
                    // Remove quotes from the JSON string result
                    val cleanResult = result.replace("^\"|\"$".toRegex(), "")
                    Log.d(TAG, "Clean result: $cleanResult")

                    // Fill the empty rows one by one to avoid complex JavaScript
                    val fillTagNamesScript = """
                        (function() {
                            try {
                                console.log('Starting to fill TagName fields');
                                var rowIndexes = JSON.parse('$cleanResult');
                                console.log('Rows to fill: ' + rowIndexes.length + ', indexes: ' + rowIndexes.join(', '));
                                
                                for(var i = 0; i < rowIndexes.length; i++) {
                                    var rowIndex = rowIndexes[i];
                                    var tagInput = document.querySelector('input[name="TagName' + rowIndex + '"]');
                                    console.log('Processing row: ' + rowIndex + ', found input: ' + (tagInput ? 'yes' : 'no'));
                                    
                                    if(tagInput) {
                                        console.log('Setting TagName' + rowIndex + ' to "$tagName"');
                                        tagInput.value = '$tagName';
                                        tagInput.dispatchEvent(new Event('input', { bubbles: true }));
                                        tagInput.dispatchEvent(new Event('change', { bubbles: true }));
                                    }
                                }
                                return "completed_tags";
                            } catch(e) {
                                console.error('Error in fillTagNamesScript:', e);
                                return JSON.stringify({error: e.toString()});
                            }
                        })();
                    """.trimIndent()

                    webView?.evaluateJavascript(fillTagNamesScript) { tagResult ->
                        Log.d(TAG, "FillTagNames result: $tagResult")
                        
                        // MeasuredValue alanlarını doldur
                        val fillMeasuredValuesScript = """
                            (function() {
                                try {
                                    console.log('Starting to fill MeasuredValue fields');
                                    var rowIndexes = JSON.parse('$cleanResult');
                                    console.log('Rows to fill: ' + rowIndexes.length);
                                    
                                    for(var i = 0; i < rowIndexes.length; i++) {
                                        var rowIndex = rowIndexes[i];
                                        var valueInput = document.querySelector('input[name="MeasuredValue' + rowIndex + '"]');
                                        console.log('Processing MeasuredValue' + rowIndex + ', found input: ' + (valueInput ? 'yes' : 'no'));
                                        
                                        if(valueInput) {
                                            var randomValue = Math.random() * ($maxValue - $minValue) + $minValue;
                                            randomValue = Math.round(randomValue * 100) / 100;
                                            var formattedValue = randomValue.toFixed(2);
                                            console.log('Setting MeasuredValue' + rowIndex + ' to ' + formattedValue);
                                            
                                            valueInput.value = formattedValue;
                                            valueInput.dispatchEvent(new Event('input', { bubbles: true }));
                                            valueInput.dispatchEvent(new Event('change', { bubbles: true }));
                                        }
                                    }
                                    return "completed_values";
                                } catch(e) {
                                    console.error('Error in fillMeasuredValuesScript:', e);
                                    return JSON.stringify({error: e.toString()});
                                }
                            })();
                        """.trimIndent()

                        webView?.evaluateJavascript(fillMeasuredValuesScript) { valueResult ->
                            Log.d(TAG, "FillMeasuredValues result: $valueResult")
                            
                            // Sonucu bildir
                            val successMessage = if (valueResult?.contains("completed_values") == true) {
                                "Form başarıyla dolduruldu"
                            } else {
                                "Form dolduruldu, ancak bazı alanlar işlenemedi"
                            }
                            
                            Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error filling form: ${e.message}")
                    Toast.makeText(this, "Form doldurma hatası: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                // No empty rows found or error occurred
                Log.d(TAG, "No empty rows found or error in script")
                Toast.makeText(this, "Doldurulacak boş satır bulunamadı", Toast.LENGTH_SHORT).show()
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
        
        // Kullanıcıya bilgi ver
        Toast.makeText(this, "Kaydetme işlemi başlatılıyor...", Toast.LENGTH_SHORT).show()
        
        val saveScript = """
            (function() {
                try {
                    console.log('Looking for save button...');
                    
                    // Önce doğrudan ng-click özniteliğiyle butonu bulmayı dene
                    var saveButton = document.querySelector('.actionB .btn-group .btn.btn-default.waves-effect.m-r-5');
                    console.log('Using primary selector: ' + (saveButton ? 'Button found' : 'Button not found'));
                    
                    // Eğer bulunamazsa, daha esnek bir seçici kullanarak class'a göre bul
                    if (!saveButton) {
                        saveButton = document.querySelector('.btn.btn-default.waves-effect.m-r-5');
                        console.log('Using secondary selector: ' + (saveButton ? 'Button found' : 'Button not found'));
                    }
                    
                    // Hala bulunamadıysa, metin içeriğine göre bul
                    if (!saveButton) {
                        var buttons = document.querySelectorAll('button');
                        console.log('Found ' + buttons.length + ' buttons, searching for "kaydet" text');
                        
                        for (var i = 0; i < buttons.length; i++) {
                            var buttonText = buttons[i].textContent.toLowerCase();
                            console.log('Button ' + i + ' text: "' + buttonText + '"');
                            
                            if (buttonText.indexOf('kaydet') !== -1) {
                                saveButton = buttons[i];
                                console.log('Found button with "kaydet" text');
                                break;
                            }
                        }
                    }
                    
                    // Son bir deneme daha - tüm form gönderme butonlarını dene
                    if (!saveButton) {
                        var submitButtons = document.querySelectorAll('button[type="submit"], input[type="submit"]');
                        console.log('Found ' + submitButtons.length + ' submit buttons');
                        
                        if (submitButtons.length > 0) {
                            saveButton = submitButtons[0];
                            console.log('Using first submit button as fallback');
                        }
                    }
                    
                    if (saveButton) { 
                        console.log('Save button found, clicking now');
                        saveButton.click();
                        return JSON.stringify({
                            status: 'success',
                            method: 'button_click',
                            buttonText: saveButton.textContent.trim()
                        });
                    } else {
                        // Son çare - form gönderme işlemini deneyerek ara
                        var forms = document.querySelectorAll('form');
                        console.log('No save button found, trying to submit form directly. Forms found: ' + forms.length);
                        
                        if (forms.length > 0) {
                            console.log('Submitting the first form');
                            forms[0].submit();
                            return JSON.stringify({
                                status: 'success',
                                method: 'form_submit'
                            });
                        }
                        
                        console.error('Could not find any save button or form');
                        return JSON.stringify({
                            status: 'error',
                            message: 'No save button or form found'
                        });
                    }
                } catch(e) {
                    console.error('Error in save script:', e);
                    return JSON.stringify({
                        status: 'error',
                        message: e.toString()
                    });
                }
            })();
        """.trimIndent()
                
        webView?.evaluateJavascript(saveScript) { result ->
            Log.d(TAG, "SaveItems result: $result")
            
            try {
                // Temizlenmiş sonuç al
                val cleanResult = result.trim().replace("^\"|\"$".toRegex(), "").replace("\\\"", "\"").replace("\\\\", "\\")
                
                // JSON olarak işle
                val jsonResult = org.json.JSONObject(cleanResult)
                val status = jsonResult.optString("status", "unknown")
                
                if (status == "success") {
                    val method = jsonResult.optString("method", "unknown")
                    val message = if (method == "button_click") {
                        "Değişiklikler kaydedildi"
                    } else {
                        "Form gönderildi"
                    }
                    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                } else {
                    val errorMessage = jsonResult.optString("message", "Bilinmeyen hata")
                    Toast.makeText(this, "Kaydetme başarısız: $errorMessage", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing save result: ${e.message}")
                
                // Basit kontrol ile geribildirim verelim
                if (result.contains("success")) {
                    Toast.makeText(this, "Değişiklikler kaydedildi", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Kaydetme işlemi sırasında hata oluştu", Toast.LENGTH_SHORT).show()
                }
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

        if (DataHolder.topraklama.isNotEmpty()) {
            editTextUrl.setText(DataHolder.topraklama)
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
            // WebView'i temizle (havuza geri vermek yerine doğrudan temizle)
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
                
                // WebView'i destroy et
                view.destroy()
                webView = null
                
                Log.d(TAG, "WebView successfully cleaned up and destroyed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up WebView in onDestroy", e)
        }
        super.onDestroy()
    }
}