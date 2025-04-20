package com.asforce.asforcetkf2.ui.ground.kotlin

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
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.asforce.asforcetkf2.R

import com.asforce.asforcetkf2.util.DataHolder
import com.asforce.asforcetkf2.util.SimpleTextWatcher
import com.asforce.asforcetkf2.util.WebViewPool
import java.util.HashMap
import java.util.Random
import java.util.regex.Pattern

/**
 * Topraklama ekranı
 * WebView kullanarak harici bir form sayfasını yükler ve manipüle eder
 */
class TopraklamaActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "TopraklamaActivity"
        private const val PREFS_NAME = "topraklama_settings"
        private const val KEY_URL = "saved_url"
        private const val BASE_URL = "https://app.szutest.com.tr/EXT/PKControl/EditGrounding/"
        private const val FETCH_DELAY = 1000L
    }

    // UI Bileşenleri
    private lateinit var webView: WebView
    private lateinit var editTextUrl: EditText
    private lateinit var buttonLoadPage: ImageView
    private lateinit var buttonFillForm: ImageView
    private lateinit var buttonSaveItems: ImageView
    private lateinit var settingsButton: ImageView
    private lateinit var backButton: ImageView
    private lateinit var webViewPool: WebViewPool

    // Sütun genişlikleri ve ayarlar
    private val columnWidths = HashMap<String, String>()
    private lateinit var sharedPreferences: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    // Topraklama form alanları için gerekli değişkenler
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_topraklama)

        // WebView havuzunu başlat
        webViewPool = WebViewPool.getInstance(this)

        initializeViews()
        setupSharedPreferences()
        setupWebView()
        setupClickListeners()
        restoreState(savedInstanceState)

        // Sayfa otomatik olarak yüklensin
        loadWebPage()
    }

    private fun initializeViews() {
        webView = findViewById(R.id.webView)
        editTextUrl = findViewById(R.id.editTextUrl)
        buttonLoadPage = findViewById(R.id.buttonLoadPage)
        buttonFillForm = findViewById(R.id.buttonFillForm)
        buttonSaveItems = findViewById(R.id.buttonSaveItems)
        settingsButton = findViewById(R.id.settingsButton)
        backButton = findViewById(R.id.backButton)
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

    private fun restoreState(savedInstanceState: Bundle?) {
        savedInstanceState?.let { bundle ->
            val savedUrl = bundle.getString(KEY_URL)
            savedUrl?.let {
                editTextUrl.setText(it)
            }
        }

        // Eğer DataHolder'da URL varsa onu kullan
        if (DataHolder.topraklama.isNotEmpty()) {
            editTextUrl.setText(DataHolder.topraklama)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    private fun setupWebView() {
        // WebView ayarları
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }

        // WebView client'i ayarla
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                applyColumnWidths()
            }
        }

        // Dokunma işleyicisini ayarla - Kaydırma iyileştirmesi için
        webView.setOnTouchListener { v, event ->
            v.parent.requestDisallowInterceptTouchEvent(true)
            v.requestFocus()
            false // Olayları işlemeye devam et
        }
    }

    private fun setupClickListeners() {
        buttonLoadPage.setOnClickListener { loadWebPage() }
        buttonFillForm.setOnClickListener { showFormFillDialog() }
        buttonSaveItems.setOnClickListener { saveItems() }
        settingsButton.setOnClickListener { showSettingsDialog() }
        
        // Geri butonu işlevi
        backButton.setOnClickListener {
            finish() // Aktiviteyi sonlandır
        }
        
        setupTextWatchers()
    }

    private fun showFormFillDialog() {
        // Modal pencere oluşturma
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Form Doldurma Ayarları")

        // Modal içeriğini inflate etme
        val inflater = layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_form_fill, null)
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
        // Boş satırları bul
        val findEmptyRowsScript = """
            var emptyRows = [];
            var inputs = document.querySelectorAll('input[name^="TagName"]');
            for(var i = 0; i < inputs.length; i++) {
                var input = inputs[i];
                if(!input.value || input.value === '' || input.value === '---') {
                    var indexMatch = input.name.match(/\\d+$/);
                    if(indexMatch) {
                        emptyRows.push(indexMatch[0]);
                    }
                }
            }
            JSON.stringify(emptyRows);
        """.trimIndent()

        webView.evaluateJavascript(findEmptyRowsScript) { result ->
            if (result != null && result != "null" && result != "\"\"") {
                try {
                    // JSON string'den tırnak işaretlerini temizle
                    val cleanResult = result.replace("^\"|\"\$".toRegex(), "")

                    // Boş satırları doldur
                    val fillTagNamesScript = """
                        var rowIndexes = JSON.parse('$cleanResult');
                        for(var i = 0; i < rowIndexes.length; i++) {
                            var rowIndex = rowIndexes[i];
                            var tagInput = document.querySelector('input[name="TagName' + rowIndex + '"]');
                            if(tagInput) {
                                tagInput.value = '$tagName';
                                tagInput.dispatchEvent(new Event('input', { bubbles: true }));
                                tagInput.dispatchEvent(new Event('change', { bubbles: true }));
                            }
                        }
                    """.trimIndent()

                    webView.evaluateJavascript(fillTagNamesScript) { _ ->
                        // Ölçülen değerleri doldur
                        val fillMeasuredValuesScript = """
                            var rowIndexes = JSON.parse('$cleanResult');
                            for(var i = 0; i < rowIndexes.length; i++) {
                                var rowIndex = rowIndexes[i];
                                var valueInput = document.querySelector('input[name="MeasuredValue' + rowIndex + '"]');
                                if(valueInput) {
                                    var randomValue = Math.random() * ($maxValue - $minValue) + $minValue;
                                    randomValue = Math.round(randomValue * 100) / 100;
                                    valueInput.value = randomValue.toFixed(2);
                                    valueInput.dispatchEvent(new Event('input', { bubbles: true }));
                                    valueInput.dispatchEvent(new Event('change', { bubbles: true }));
                                }
                            }
                        """.trimIndent()

                        webView.evaluateJavascript(fillMeasuredValuesScript, null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Form doldurma hatası: ${e.message}")
                    Toast.makeText(this, "Form doldurma hatası", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Boş satır bulunamadı
                Toast.makeText(this, "Doldurulacak boş satır bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveItems() {
        val saveScript = """
            var saveButton = document.querySelector('.actionB .btn-group .btn.btn-default.waves-effect.m-r-5');
            if (saveButton) { 
                saveButton.click();
                return 'saved';
            } else {
                return 'save-button-not-found';
            }
        """.trimIndent()
        
        webView.evaluateJavascript(saveScript) { result ->
            val message = if (result.contains("saved")) {
                "Değişiklikler kaydedildi"
            } else {
                "Kaydetme butonu bulunamadı"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
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

    private fun applyColumnWidths() {
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

        webView.evaluateJavascript(checkElementsScript) { result ->
            if ("true" == result) {
                applyWidthsToColumns()
                retryCount = 0 // Başarılı olursa retryCount'u sıfırla
            } else if (retryCount < MAX_RETRY_COUNT) {
                retryCount++
                Log.d(TAG, "Sütun genişliklerini uygulama tekrar deneniyor. Deneme: $retryCount")
                handler.postDelayed({ applyColumnWidths() }, RETRY_DELAY_MS)
            } else {
                Log.e(TAG, "$MAX_RETRY_COUNT denemeden sonra sütun genişlikleri uygulanamadı")
                retryCount = 0 // Bir sonraki deneme için sıfırla
            }
        }
    }

    private fun applyWidthsToColumns() {
        val jsBuilder = StringBuilder()
        jsBuilder.append("(function() {")
        
        // Sütun genişlik ayarlamaları
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

        // Panel padding ayarı ve MutationObserver
        jsBuilder.append("var style = document.createElement('style');")
                .append("style.type = 'text/css';")
                .append("style.innerHTML = '.panel-body { padding: 1px !important; }';")
                .append("document.head.appendChild(style);")

                // MutationObserver ekleyerek sütun genişliklerini koru
                .append("var observer = new MutationObserver(function(mutations) {")
                .append("    mutations.forEach(function(mutation) {")
                .append("        if (mutation.type === 'attributes' || mutation.type === 'childList') {")

        // Observer'da genişlikleri yeniden uygula
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

                // Gözlemlemeyi başlat
                .append("observer.observe(document.body, {")
                .append("    childList: true,")
                .append("    subtree: true,")
                .append("    attributes: true")
                .append("});")

                .append("} catch(e) { console.error('Sütun genişliklerini uygularken hata:', e); }")
                .append("})();")

        webView.evaluateJavascript(jsBuilder.toString(), null)
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

            editText.setOnEditorActionListener { v, actionId, _ ->
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
        builder.setPositiveButton("Kaydet") { _, _ ->
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
                .setPositiveButton(android.R.string.yes) { _, _ -> webView.reload() }
                .setNegativeButton(android.R.string.no, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show()
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
        
        webView.loadUrl(finalUrl)
        
        // URL'yi DataHolder'a kaydet
        DataHolder.topraklama = url
        
        // URL'yi SharedPreferences'a kaydet
        val prefs = getSharedPreferences("TopraklamaPrefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        editor.putString("lastUrl", url)
        editor.apply()
        
        handler.postDelayed({ buttonLoadPage.isEnabled = true }, 1000)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_URL, editTextUrl.text.toString())
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
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
} 