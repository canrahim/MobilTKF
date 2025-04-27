package com.asforce.asforcetkf2

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.JsResult
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.widget.EditText
import android.widget.Toast
import com.asforce.asforcetkf2.suggestion.SuggestionManager
import com.asforce.asforcetkf2.util.DeviceManager
import com.asforce.asforcetkf2.util.OutOfScopeModule
import com.asforce.asforcetkf2.ui.leakage.LeakageControlActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcetkf2.adapter.TabsAdapter
import com.asforce.asforcetkf2.databinding.ActivityMainBinding
import com.asforce.asforcetkf2.model.Tab
import com.asforce.asforcetkf2.qrscanner.QRScannerFragment
import com.asforce.asforcetkf2.util.DataHolder
import com.asforce.asforcetkf2.util.TabResourceMonitor
import com.asforce.asforcetkf2.util.TKFDownloadManager
import com.asforce.asforcetkf2.viewmodel.TabViewModel
import com.asforce.asforcetkf2.webview.TabWebView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import android.os.Handler
import android.os.Looper

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: TabViewModel by viewModels()

    // Açıkça değişken türlerini belirtelim
    private lateinit var tabsAdapter: TabsAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    // Dosya seçici ve kamera için gerekli değişkenler
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraPhotoPath: String? = null
    private var currentPhotoUri: Uri? = null
    private val CAMERA_PERMISSION_REQUEST_CODE = 1001

    // Dosya seçici başlatıcı - önceki hali
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val resultCode = result.resultCode

        filePathCallback?.let { callback ->
            if (resultCode == Activity.RESULT_OK && data != null) {
                val results = if (data.clipData != null) {
                    val count = data.clipData?.itemCount ?: 0
                    Array(count) { i ->
                        data.clipData?.getItemAt(i)?.uri
                    }.filterNotNull().toTypedArray()
                } else {
                    arrayOf(data.data).filterNotNull().toTypedArray()
                }
                callback.onReceiveValue(results)
            } else {
                callback.onReceiveValue(null)
            }
            filePathCallback = null
        }
    }

    // Kamera başlatıcı
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            filePathCallback?.let { callback ->
                currentPhotoUri?.let { uri ->
                    callback.onReceiveValue(arrayOf(uri))
                } ?: callback.onReceiveValue(null)
            }
        } else {
            filePathCallback?.onReceiveValue(null)
        }
        filePathCallback = null
        currentPhotoUri = null
    }

    private val downloadManager by lazy { TKFDownloadManager(this) }
    private val resourceMonitor by lazy { TabResourceMonitor() }
    private val activeWebViews = mutableMapOf<String, TabWebView>()

    // Suggestion manager
    private lateinit var suggestionManager: SuggestionManager

    // Device manager
    private lateinit var deviceManager: DeviceManager

    // Topraklama kontrolünden dönüş için
    private val topraklamaActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val hasTopraklamaSorunu = data?.getBooleanExtra("hasTopraklamaSorunu", false) ?: DataHolder.hasTopraklamaSorunu

            if (hasTopraklamaSorunu) {

                // WebView'i al
                val currentTab = viewModel.activeTab.value
                val webView = currentTab?.let { activeWebViews[it.id] }

                if (webView != null) {
                    // Seçeneği "Uygun Değil" olarak ayarla ve tickbox'ı işaretle
                    val setTopraklamaSorunScript = """
                        (function() {
                            try {
                                // Topraklama sorununu forma uygulama
                                // Topraklama sorusunu bul (soru 9)
                                var selectElement = document.querySelector('select[name="Questions[8].Option"]');
                                if (selectElement) {
                                    // Topraklama seçenek elementi bulundu
                                    
                                    // "Uygun Değil" (değer: 2) seçeneğini seç
                                    selectElement.value = "2";
                                    
                                    // Change event'i tetikle
                                    var event = new Event('change', { bubbles: true });
                                    selectElement.dispatchEvent(event);
                                    
                                    // Select elementi güncellemek için kullanılan Bootstrap selectpicker'ı güncelle
                                    if (typeof $ !== 'undefined' && $('.selectpicker').length > 0) {
                                        $('.selectpicker').selectpicker('refresh');
                                    }
                                    
                                    // Switchery elementi bul ve işaretle
                                    var switcheryElements = document.querySelectorAll('.switchery');
                                    for (var i = 0; i < switcheryElements.length; i++) {
                                        var parentLabel = switcheryElements[i].parentElement;
                                        var labelText = parentLabel ? parentLabel.textContent.trim() : '';
                                        
                                        if (labelText.indexOf('Topraklama direnç değeri ölçülemeyen') !== -1) {
                                            // Bu, bizim aranan elementimiz
                                            // Topraklama checkbox elementi bulundu
                                            
                                            // Checkbox'ı kontrol et
                                            var checkbox = parentLabel.querySelector('input[type="checkbox"]');
                                            if (checkbox && !checkbox.checked) {
                                                checkbox.checked = true;
                                                
                                                // Change event'i tetikle
                                                var event = new Event('change', { bubbles: true });
                                                checkbox.dispatchEvent(event);
                                                
                                                // Switchery görsel durumunu güncelle
                                                var switchery = switcheryElements[i];
                                                if (switchery && switchery.firstChild) {
                                                    switchery.style.boxShadow = "rgb(100, 189, 99) 0px 0px 0px 0px inset";
                                                    switchery.style.borderColor = "rgb(100, 189, 99)";
                                                    switchery.style.backgroundColor = "rgb(100, 189, 99)";
                                                    
                                                    var handle = switchery.firstChild;
                                                    handle.style.left = "13px";
                                                    handle.style.backgroundColor = "rgb(255, 255, 255)";
                                                }
                                            }
                                        }
                                    }
                                    
                                    return { status: 'success', message: 'Topraklama sorunu uygulandı' };
                                } else {
                                    // Topraklama seçenek elementi bulunamadı
                                    return { status: 'error', message: 'Topraklama formu bulunamadı' };
                                }
                            } catch(e) {
                                // Topraklama sorunu uygulanırken hata
                                return { status: 'error', message: e.toString() };
                            }
                        })();
                    """.trimIndent()

                    webView.evaluateJavascript(setTopraklamaSorunScript) { result ->

                        // İşlem sonrası DataHolder'ı temizle
                        DataHolder.hasTopraklamaSorunu = false
                    }
                } else {
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Tam ekran modu ve şeffaf gezinme çubuğu - klavye içerik kaymasını optimize eden versiyon
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.statusBarColor = android.graphics.Color.TRANSPARENT

        // Klavye için içerik ayarlama davranışını güçlendir - SORUN ÇÖZÜMÜ
        // WebView formları arasında geçiş yaparken klavye kapanıp açılma davranışını önle
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                               WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)

        // Bileşenleri başlat
        initializeTabComponents()
        setupTabRecyclerView()
        setupUrlInput()
        setupNavigationButtons()
        setupFloatingMenuButtons()
        setupActionButtons()

        // Direkt olarak WebView'a yazmak için test fonksiyonu ekle
        binding.btnLeft2.setOnLongClickListener {
            testSuggestionInsertion()
            return@setOnLongClickListener true
        }

        // Yeni sekme düğmesi - çift tıklama koruması güçlendirilmiş
        val newTabButton = binding.btnNewTab
        newTabButton.isEnabled = true
        newTabButton.setOnClickListener(object : View.OnClickListener {
            private var lastClickTime: Long = 0

            override fun onClick(v: View) {
                // Çift tıklama koruması - 1 saniye içinde tekrar tıklamayı önle
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime > 1000) {
                    lastClickTime = currentTime

                    // Düğme animasyonu
                    v.isEnabled = false
                    v.postDelayed({ v.isEnabled = true }, 1000) // 1 saniye sonra etkinleştir

                    // Yeni sekme aç
                    addNewTab()
                }
            }
        })

        // Initialize view model observers
        observeViewModel()

        // Handle intent (e.g., for opening URLs from external apps)
        handleIntent(intent)

        // Start resource monitoring
        startTabResourceMonitoring()

        // QR Kod tarayıcı butonunu ayarla
        setupQrScannerButton()

        // Initialize suggestion manager
        initializeSuggestionManager()
    }

    private fun initializeTabComponents() {
        // Önce callback'i oluştur
        val touchHelperCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.bindingAdapterPosition
                val toPosition = target.bindingAdapterPosition
                tabsAdapter.moveTab(fromPosition, toPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not used as we're only supporting drag
            }
        }

        // ItemTouchHelper'ı oluştur
        itemTouchHelper = ItemTouchHelper(touchHelperCallback)

        // Adapter'ı başlat
        tabsAdapter = TabsAdapter(
            onTabSelected = { tab -> selectTab(tab) },
            onTabClosed = { tab -> closeTab(tab) },
            onStartDrag = { holder -> itemTouchHelper.startDrag(holder) }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        val data = intent.data

        if (Intent.ACTION_VIEW == action && data != null) {
            val url = data.toString()
            addNewTab(url)
        }
    }

    private fun setupTabRecyclerView() {
        binding.tabsRecyclerView.adapter = tabsAdapter
        itemTouchHelper.attachToRecyclerView(binding.tabsRecyclerView)

        tabsAdapter.onTabDragListener = TabsAdapter.OnTabDragListener { fromPosition, toPosition ->
            // Update tab positions in view model
            val tabs = tabsAdapter.getTabs()
            viewModel.updateTabPositions(tabs)
        }
    }

    private fun setupUrlInput() {
        binding.urlInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val url = binding.urlInput.text.toString()
                loadUrl(url)
                hideKeyboard()
                return@setOnEditorActionListener true
            }
            false
        }

        // URL kutusuna tıklanınca metni tamamen seç ve klavyeyi göster - Google arama sorunu düzeltmesi
        binding.urlInput.setOnClickListener { v ->
            // Tüm metni seç
            (v as TextInputEditText).selectAll()

            // Mevcut URL'yi al
            val currentUrl = v.text.toString()

            // Klavyeyi göster - Google araması yapabilmek için gerekli
            v.requestFocus()
            showKeyboard(v)
            v.selectAll()
        }

        // URL kutusuna odaklanınca metni tamamen seç
        binding.urlInput.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                (v as TextInputEditText).selectAll()
            }
        }

        // Uzun tıklama işlemini devre dışı bırak - xml'de longClickable="false" ayarlandı
        // Ancak bazı cihazlarda ek güvenlik için buraya da koyalım
        binding.urlInput.setOnLongClickListener {
            // Tüm metni seç (kopyalama işlemi yapılmayacak)
            binding.urlInput.selectAll()
            true // Olayı tüket
        }
    }

    private fun setupNavigationButtons() {
        // Main menu button
        binding.btnMainMenu.setOnClickListener {
            showMainMenu()
        }

        // Back button
        binding.btnBack.setOnClickListener {
            val currentTab = viewModel.activeTab.value ?: return@setOnClickListener
            val webView = activeWebViews[currentTab.id] ?: return@setOnClickListener

            if (webView.canGoBack()) {
                webView.goBack()
            }
        }

        // Forward button
        binding.btnForward.setOnClickListener {
            val currentTab = viewModel.activeTab.value ?: return@setOnClickListener
            val webView = activeWebViews[currentTab.id] ?: return@setOnClickListener

            if (webView.canGoForward()) {
                webView.goForward()
            }
        }

        // Refresh button
        binding.btnRefresh.setOnClickListener {
            val currentTab = viewModel.activeTab.value ?: return@setOnClickListener
            val webView = activeWebViews[currentTab.id] ?: return@setOnClickListener

            // Önbelleği devre dışı bırakarak sayfayı yenile (force refresh)
            webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
            webView.reload()

            // Yenileme sonrası önbellek modunu eski haline döndür
            webView.postDelayed({
                webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
                webView.fixToastVisibility()
            }, 1000)  // Slight delay to ensure page has loaded
        }

        // Menu button
        binding.btnMenu.setOnClickListener {
            showMenu()
        }

        // URL kopyalama butonu
        binding.urlInputLayout.setEndIconOnClickListener {
            val url = binding.urlInput.text.toString()
            if (url.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("URL", url)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, R.string.url_copied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeViewModel() {
        // Observe tabs
        viewModel.allTabs.observe(this) { tabs ->
            tabsAdapter.updateTabs(tabs)

            // If no tabs, add a new one
            if (tabs.isEmpty()) {
                addNewTab()
            }
        }

        // Observe active tab
        viewModel.activeTab.observe(this) { tab ->
            if (tab != null) {
                updateActiveTab(tab)
            }
        }
    }

    /**
     * Yeni bir sekme ekler ve etkinleştirir
     */
    private fun addNewTab(url: String = "https://www.google.com") {
        // Ana thread'de işlem yapma garantisi
        runOnUiThread {
            // Yeni sekme ekle ve etkinleştir
            val newTabId = viewModel.addTab(url)

            // Kullanıcıya geri bildirim
            Toast.makeText(this, "Yeni sekme açıldı", Toast.LENGTH_SHORT).show()

        }
    }

    private fun closeTab(tab: Tab) {
        // Clean up the WebView - güvenlik kontrolleri ekle
        activeWebViews[tab.id]?.let { webView ->
            try {
                // WebView'in hala geçerli olup olmadığını kontrol et
                if (webView.isAttachedToWindow) {
                    webView.cleanup()
                    // Temizlikten sonra kısa bir bekleme süresi ekle
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            if (webView.parent === binding.webviewContainer) {
                                binding.webviewContainer.removeView(webView)
                            }
                        } catch (e: Exception) {
                            // View kaldırma hatası
                        }
                    }, 50) // 50ms gecikme
                }
            } catch (e: Exception) {
                // WebView temizleme hatası
            } finally {
                // Her durumda koleksiyondan çıkar
                activeWebViews.remove(tab.id)
            }
        }

        // Tab'i viewModel'den kapat
        viewModel.closeTab(tab)
    }

    private fun selectTab(tab: Tab) {
        viewModel.setActiveTab(tab)
    }

    private fun updateActiveTab(tab: Tab) {
        // Update URL bar and ensure the cursor is at the end for proper ellipsize behavior
        try {
            binding.urlInput.setText(tab.url)
            // Position cursor at the end to ensure the end of URL is visible when ellipsized from start
            binding.urlInput.setSelection(tab.url.length)
        } catch (e: Exception) {
            // URL güncelleme hatası
        }

        // Aktif tab değiştiğinde, URL'deki sayısal kod varsa DataHolder'a kaydet
        extractDigitsFromUrl(tab.url)

        // Show active tab's WebView, hide others
        for ((tabId, webView) in activeWebViews.entries.toList()) { // Concurrent modification için toList()
            try {
                // WebView'in geçerli olup olmadığını kontrol et
                if (!webView.isAttachedToWindow) {
                    // WebView geçersiz, koleksiyondan çıkar
                    activeWebViews.remove(tabId)
                    continue
                }

                webView.visibility = if (tabId == tab.id) View.VISIBLE else View.GONE

                // Update WebView state based on hibernation
                if (tabId != tab.id && tab.isHibernated) {
                    webView.hibernate()
                } else if (tabId == tab.id && tab.isHibernated) {
                    webView.wakeUp()
                }

                // Tab aktif olduğunda, manuel arama aktif değilse ve URL szutest.com.tr içeriyorsa QR kodu kontrol et
                if (tabId == tab.id && tab.url.contains("szutest.com.tr", ignoreCase = true) && !isManualSearchActive) {
                    // Kısa bir gecikme ile QR kodunu kontrol et (sayfanın tamamen yüklenmesi için)
                    Handler(Looper.getMainLooper()).postDelayed({
                        checkForQrCodeOnPage(webView)
                    }, 500)
                }
            } catch (e: Exception) {
                // WebView işleme hatası, bu sekmenin WebView'ini koleksiyondan çıkar
                activeWebViews.remove(tabId)
            }
        }

        // Create WebView for the tab if it doesn't exist yet
        if (!activeWebViews.containsKey(tab.id)) {
            createWebViewForTab(tab)
        }
    }

    private fun createWebViewForTab(tab: Tab) {
        val webView = TabWebView(this)

        // Set up WebView events
        setupWebViewEvents(webView)

        // Add to container
        binding.webviewContainer.addView(webView)

        // Initialize with tab and load URL
        webView.initialize(tab)

        // WebView için özel dokunma dinleyicisi ekle
        webView.setOnClickListener {
            // WebView'e tıklanma - odakla
            it.requestFocus()
        }

        // WebView için geliştirilmiş dokunma olayı işleyicisi - klavye ve imleç sorunlarını çözen versiyon
        webView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // WebView'in odak almasını sağla - imleç sorununu gider
                    v.requestFocus()
                }

                MotionEvent.ACTION_UP -> {
                    // Dokunma sonrası tıklanan elementi ve tipini belirle
                    val script = """
                    (function() {
                        try {
                            // Get the element at the touch point
                            var element = document.elementFromPoint(${event.x}, ${event.y});
                            if (!element) return "NO_ELEMENT";
                            
                            // Element touched
                            
                            // Check if touched element is an input field
                            var isInput = element.tagName === 'INPUT' || element.tagName === 'TEXTAREA';
                            var inputType = isInput ? (element.type || 'text').toLowerCase() : '';
                            
                            // If it's an input field, handle with enhanced focus logic
                            if (isInput) {
                                // Touch detected on input field
                                
                                // Ensure element is not disabled or readonly
                                if (element.disabled || element.readOnly) {
                                    return "INPUT_DISABLED";
                                }
                                
                                // Get or create key
                                var key = element.getAttribute('data-tkf-key');
                                if (!key) {
                                    key = (element.name || element.id || element.placeholder || 'input_' + Math.random().toString(36).substr(2, 9));
                                    key = key.replace(/[^a-zA-Z0-9_]/g, '_');
                                    element.setAttribute('data-tkf-key', key);
                                }
                                
                                // Make sure the element is visible in viewport
                                if (element.scrollIntoViewIfNeeded) {
                                    element.scrollIntoViewIfNeeded();
                                } else if (element.scrollIntoView) {
                                    element.scrollIntoView();
                                }
                                
                                // Odakla - buraya blur/focus döngüsü koymuyoruz, imleç sorununu önlemek için
                                element.focus();
                                
                                // ÖNEMLI: Odaklama ve klavye işlemlerini daha iyi yönetmek için element bilgilerini döndür
                                return JSON.stringify({
                                    tagName: element.tagName,
                                    id: element.id || '',
                                    type: inputType,
                                    isNumeric: (inputType === 'number' || inputType === 'tel'),
                                    value: element.value || '',
                                    key: key
                                });
                            }
                            
                            return "NOT_INPUT";
                        } catch(e) {
                            // Error in touch handling
                            return "ERROR: " + e.message;
                        }
                    })();
                    """

                    webView.evaluateJavascript(script) { result ->
                        try {
                            // Tırnak işaretlerini ve kaçış karakterlerini temizle
                            val cleanResult = result.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")

                            // Input alanı ise özel işlem yap
                            if (cleanResult.startsWith("{")) {
                                try {
                                    // JSON'u ayrıştır
                                    val jsonResult = org.json.JSONObject(cleanResult)
                                    val isNumeric = jsonResult.optBoolean("isNumeric", false)
                                    val inputType = jsonResult.optString("type", "text")
                                    val inputKey = jsonResult.optString("key", "")

                                    // Öneri sistemini aktifleştir
                                    if (inputKey.isNotEmpty()) {
                                        webView.evaluateJavascript("""
                                            (function() {
                                                if (window.SuggestionHandler) {
                                                    window.SuggestionHandler.onInputFocused('$inputKey');
                                                    return true;
                                                }
                                                return false;
                                            })();
                                        """.trimIndent(), null)
                                    }

                                    // Sayısal klavye için özel klavye tipi ayarla
                                    if (isNumeric) {
                                        // Sayısal klavye durumunda klavyeyi dikkatli şekilde göster
                                        // InputMethodManager'ı doğrudan kullan, showKeyboard'u bypass et
                                        val imm = this@MainActivity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                        imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                                    } else {
                                        // Text input için normal klavye göster
                                        showKeyboard(v)
                                    }
                                } catch (e: Exception) {
                                        // Hata durumunda normal klavyeyi göster
                                    showKeyboard(v)
                                }
                            }
                        } catch (e: Exception) {
                        }
                    }
                }
            }

            // WebView'in normal tıklama işlemesine izin ver
            // Bu önemli: WebView'in kendi dokunma olaylarını da işlemesi gerekiyor
            return@setOnTouchListener false
        }

        // Store in active WebViews
        activeWebViews[tab.id] = webView

        // Update the suggestion manager with the new WebView
        suggestionManager.trackEditText(binding.urlInput, "url_input", webView)
        suggestionManager.trackEditText(binding.aramaSearch, "aramaSearch_input", webView)
        suggestionManager.trackEditText(binding.aramaSearch2, "aramaSearch2_input", webView)

        // Set suggestion manager on WebView
        webView.setSuggestionManager(suggestionManager)
    }

    private fun setupWebViewEvents(webView: TabWebView) {
        webView.onPageStarted = { tabId, url ->
            val tabs = viewModel.allTabs.value ?: emptyList()
            val tab = tabs.find { it.id == tabId }
            if (tab != null) {
                // Update tab
                val updatedTab = tab.copy(
                    url = url,
                    isLoading = true
                )

                val position = tabs.indexOf(tab)
                viewModel.updateTab(updatedTab, position)

                // Update URL input
                if (updatedTab.isActive) {
                    binding.urlInput.setText(url)
                }

                // Show progress bar
                binding.progressBar.isVisible = true
                binding.progressBar.progress = 0

                // Input alanları için daha agresif tarama yap
                if (url.contains("szutest.com.tr", ignoreCase = true)) {
                    injectEnhancedFormDetection(webView)
                }
            }
        }

        webView.onPageFinished = { tabId, url, favicon ->
            val tabs = viewModel.allTabs.value ?: emptyList()
            val tab = tabs.find { it.id == tabId }
            if (tab != null) {
                // Update tab
                val updatedTab = tab.copy(
                    url = url,
                    isLoading = false,
                    favicon = favicon ?: tab.favicon
                )

                val position = tabs.indexOf(tab)
                viewModel.updateTab(updatedTab, position)

                // Hide progress bar
                binding.progressBar.isVisible = false

                // Sayfa yükleme tamamlanınca form işlemlerini ve gelişmiş form algılamayı çalıştır
                // Süreyi azalttık - daha hızlı tepki ver
                webView.postDelayed({
                    // Form işleme enjeksiyonu
                    webView.injectFormHandlers()

                    // Giriş alanı izleme
                    webView.injectInputTracking()

                    // Elle odaklama işleme
                    webView.injectManualFocusHandling()

                    // Giriş algılamayı iyileştir
                    webView.enhanceInputFocusDetection()

                    // SzuTest gibi özel siteler için gelişmiş form algılama yöntemini kullan
                    if (url.contains("szutest.com.tr", ignoreCase = true) ||
                        url.contains("equipmentid", ignoreCase = true) ||
                        url.contains("tkf", ignoreCase = true)) {
                        // Özel form algılamayı aktifleştir
                        injectEnhancedFormDetection(webView)

                        // Eğer manuel arama aktif değilse QR kodunu otomatik olarak kontrol et
                        if (!isManualSearchActive) {
                            checkForQrCodeOnPage(webView)
                        }
                    }

                }, 300) // 500ms -> 300ms (daha hızlı tepki için süreyi azalttık)
            }
        }

        webView.onProgressChanged = { tabId, progress ->
            val tabs = viewModel.allTabs.value ?: emptyList()
            val tab = tabs.find { it.id == tabId }
            if (tab != null && tab.isActive) {
                binding.progressBar.progress = progress
            }
        }

        webView.onReceivedTitle = { tabId, title ->
            val tabs = viewModel.allTabs.value ?: emptyList()
            val tab = tabs.find { it.id == tabId }
            if (tab != null) {
                // Update tab title
                val updatedTab = tab.copy(title = title)
                val position = tabs.indexOf(tab)
                viewModel.updateTab(updatedTab, position)
            }
        }

        webView.onReceivedError = { errorCode, description, failingUrl ->

            Snackbar.make(
                binding.root,
                "Error loading page: $description",
                Snackbar.LENGTH_LONG
            ).show()
        }

        webView.onReceivedSslError = { error ->
            // Show SSL error dialog
            val builder = MaterialAlertDialogBuilder(this)
                .setTitle(R.string.ssl_error_title)
                .setMessage(R.string.ssl_error_message)
                .setPositiveButton(R.string.ssl_error_continue) { _, _ -> true }
                .setNegativeButton(R.string.ssl_error_cancel) { _, _ -> false }
                .create()

            builder.show()

            // Don't proceed by default (safer)
            false
        }

        webView.onJsAlert = { url, message, result ->
            val builder = AlertDialog.Builder(this)
                .setTitle(url)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    result.confirm()
                }
                .setCancelable(false)
                .create()

            builder.show()
            true
        }

        webView.onJsConfirm = { url, message, result ->
            val builder = AlertDialog.Builder(this)
                .setTitle(url)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    result.confirm()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    result.cancel()
                }
                .setCancelable(false)
                .create()

            builder.show()
            true
        }

        webView.onFileChooser = { callback ->
            filePathCallback = callback

            showFileSourceDialog()
            true
        }

        webView.onDownloadRequested = { url, userAgent, contentDisposition, mimeType, contentLength ->
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)

            // Start download
            val downloadId = downloadManager.downloadFile(
                url, userAgent, contentDisposition, mimeType, contentLength
            )

            if (downloadId > 0) {
                Snackbar.make(
                    binding.root,
                    "${getString(R.string.download_started)}: $fileName",
                    Snackbar.LENGTH_LONG
                ).show()
            } else {
                Snackbar.make(
                    binding.root,
                    R.string.download_failed,
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }

        // Uzun basma için callback'i ayarla
        webView.onLongPress = { tabId, url ->
            showLongPressMenu(url)
        }
    }

    private fun loadUrl(url: String) {
        val currentTab = viewModel.activeTab.value ?: return
        val webView = activeWebViews[currentTab.id] ?: return

        // Google araması için özel kontrol ekle
        val formattedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (url.contains(" ") || !url.contains(".")) {
                // Boşluk içeriyorsa veya bir domain gibi görünmüyorsa, büyük olasılıkla bir arama terimidir
                "https://www.google.com/search?q=${Uri.encode(url)}"
            } else {
                // Domain gibi görünen bir şey ise https ekle
                "https://$url"
            }
        } else {
            url
        }

        webView.loadUrl(formattedUrl)
    }

    private fun showMainMenu() {
        // Ana menü öğeleri
        val items = arrayOf(
            "Ana Sayfa",
            "Favoriler",
            "Geçmiş",
            "İndirilenler",
            "Öneri Önbelleğini Temizle",
            "Ayarlar"
        )

        // PopupMenu kullanarak menüyü butonun altında göster
        val menuBtn = binding.btnMainMenu
        val popup = android.widget.PopupMenu(this, menuBtn)

        // Menü öğelerini ekle
        for (i in items.indices) {
            popup.menu.add(android.view.Menu.NONE, i, i, items[i])
        }

        // Tıklama olaylarını yönet
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                0 -> loadUrl("https://www.google.com")
                1 -> Toast.makeText(this, "Favoriler henüz uygulanmadı", Toast.LENGTH_SHORT).show()
                2 -> Toast.makeText(this, "Geçmiş henüz uygulanmadı", Toast.LENGTH_SHORT).show()
                3 -> Toast.makeText(this, "İndirilenler henüz uygulanmadı", Toast.LENGTH_SHORT).show()
                4 -> clearSuggestionCache()
                5 -> showMenu() // Standart ayarlar menüsünü göster
            }
            true
        }

        // Menü konumunu ayarla - butonun altında göstermek için
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            popup.setForceShowIcon(true)
        }

        popup.show()
    }

    /**
     * Clear the suggestion cache to optimize performance
     */
    private fun clearSuggestionCache() {
        // Önbelleği temizle ve kullanıcıya bilgi ver
        suggestionManager.clearAllSuggestionCaches()

        // Bilgilendirme mesajı göster
        Snackbar.make(
            binding.root,
            "Öneri önbelleği temizlendi",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    private fun showMenu() {
        // Tarayıcı ayarları menü öğeleri - Yeni menü yapısı
        val items = arrayOf(
            "Kaçak Akım",
            "Pano Fonksiyon",
            "Topraklama",
            "Termal Kamera"
        )

        // PopupMenu kullanarak menüyü butonun altında göster
        val menuBtn = binding.btnMenu
        val popup = android.widget.PopupMenu(this, menuBtn)

        // Menü öğelerini ekle
        for (i in items.indices) {
            popup.menu.add(android.view.Menu.NONE, i, i, items[i])
        }

        // Tıklama olaylarını yönet
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                0 -> handleKacakAkim()
                1 -> handlePanoFonksiyon()
                2 -> handleTopraklama()
                3 -> handleTermalKamera()
            }
            true
        }

        // Menü konumunu ayarla - butonun altında göstermek için
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            popup.setForceShowIcon(true)
        }

        popup.show()
    }

    // Yeni menü işlevleri
    private fun handleKacakAkim() {
        // Kaçak Akım aktivitesini başlat
        val intent = Intent(this, LeakageControlActivity::class.java)
        startActivity(intent)
    }

    /**
     * Kapsam Dışı ana menüsünü gösterir
     */
    private fun showScopeOutMenu() {
        // Ana menü kategorileri
        val mainCategories = arrayOf(
            "Aydınlatma Cihazları",
            "Elektrikli El Aletleri",
            "Şarjlı El Aletleri",
            "Elektrikli Kaynak Makinası",
            "Diğer Elektirikli Cihazlar"
        )

        // PopupMenu ile ana kategorileri göster
        val menuBtn = binding.btnScopeOut
        val popup = android.widget.PopupMenu(this, menuBtn)

        // Menü öğelerini ekle
        for (i in mainCategories.indices) {
            popup.menu.add(android.view.Menu.NONE, i, i, mainCategories[i])
        }

        // Tıklama olaylarını yönet
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                0 -> showAydinlatmaSubMenu()
                1 -> showElektrikElSubMenu()
                2 -> showSarjliElSubMenu()
                3 -> showKaynakSubMenu()
                4 -> showDıgerElektrikSubMenu()
            }
            true
        }

        // Menüyü göster
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            popup.setForceShowIcon(true)
        }

        popup.show()
    }

    /**
     * Aydınlatma alt menüsünü gösterir
     */
    private fun showAydinlatmaSubMenu() {
        // Aydınlatma alt menü öğeleri
        val subItems = arrayOf(
            "24V Kablolu Aydınlatma",
            "Akülü Alan Aydınlatma"
        )

        showSubMenu("Aydınlatma Cihazları", subItems) { position ->
            val currentTab = viewModel.activeTab.value
            val webView = currentTab?.let { activeWebViews[it.id] }

            if (webView != null) {
                when (position) {
                    0 -> OutOfScopeModule.set24VAydinlatmaOutOfScope(webView)
                    1 -> OutOfScopeModule.setAkuluAydinlatmaOutOfScope(webView)
                }
                showScopeOutSuccessMessage(subItems[position])
            } else {
                Toast.makeText(this, "Aktif sekme bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Elektrik Malzemeleri alt menüsünü gösterir
     */
    private fun showElektrikElSubMenu() {
        // Elektrik alt menü öğeleri
        val subItems = arrayOf(
            "Avuç Taşlama",
            "Sigorta Grubu",
            "Şalter Grubu",
            "Pano Aksesuarları"
        )

        showSubMenu("Elektrik El Aletleri", subItems) { position ->
            val currentTab = viewModel.activeTab.value
            val webView = currentTab?.let { activeWebViews[it.id] }

            if (webView != null) {
                when (position) {
                    0 -> OutOfScopeModule.setAvucTaslamaOutOfScope(webView)
                    1 -> OutOfScopeModule.setSigortaGrubuOutOfScope(webView)
                    2 -> OutOfScopeModule.setSalterGrubuOutOfScope(webView)
                    3 -> OutOfScopeModule.setPanoAksesuarlariOutOfScope(webView)
                }
                showScopeOutSuccessMessage(subItems[position])
            } else {
                Toast.makeText(this, "Aktif sekme bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Elektrik Malzemeleri alt menüsünü gösterir
     */
    private fun showSarjliElSubMenu() {
        // Elektrik alt menü öğeleri
        val subItems = arrayOf(
            "Şarjlı Avuç Taşlama",
            "Sigorta Grubu",
            "Şalter Grubu",
            "Pano Aksesuarları"
        )

        showSubMenu("Şarjlı El Aletleri", subItems) { position ->
            val currentTab = viewModel.activeTab.value
            val webView = currentTab?.let { activeWebViews[it.id] }

            if (webView != null) {
                when (position) {
                    0 -> OutOfScopeModule.setSarjliAvucTaslamaOutOfScope(webView)
                    1 -> OutOfScopeModule.setSigortaGrubuOutOfScope(webView)
                    2 -> OutOfScopeModule.setSalterGrubuOutOfScope(webView)
                    3 -> OutOfScopeModule.setPanoAksesuarlariOutOfScope(webView)
                }
                showScopeOutSuccessMessage(subItems[position])
            } else {
                Toast.makeText(this, "Aktif sekme bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Tesisat Malzemeleri alt menüsünü gösterir
     */
    private fun showKaynakSubMenu() {
        // Tesisat alt menü öğeleri
        val subItems = arrayOf(
            "Kablo Grubu",
            "Buat Grubu",
            "Kablo Kanalları"
        )

        showSubMenu("Tesisat Malzemeleri", subItems) { position ->
            val currentTab = viewModel.activeTab.value
            val webView = currentTab?.let { activeWebViews[it.id] }

            if (webView != null) {
                when (position) {
                    0 -> OutOfScopeModule.setKabloGrubuOutOfScope(webView)
                    1 -> OutOfScopeModule.setBuatGrubuOutOfScope(webView)
                    2 -> OutOfScopeModule.setKabloKanallariOutOfScope(webView)
                }
                showScopeOutSuccessMessage(subItems[position])
            } else {
                Toast.makeText(this, "Aktif sekme bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Ölçüm Aletleri alt menüsünü gösterir
     */
    private fun showDıgerElektrikSubMenu() {
        // Ölçüm alt menü öğeleri
        val subItems = arrayOf(
            "Multimetre",
            "Topraklama Ölçüm",
            "İzolasyon Ölçüm",
            "Termal Kamera"
        )

        showSubMenu("Ölçüm Aletleri", subItems) { position ->
            val currentTab = viewModel.activeTab.value
            val webView = currentTab?.let { activeWebViews[it.id] }

            if (webView != null) {
                when (position) {
                    0 -> OutOfScopeModule.setMultimetreOutOfScope(webView)
                    1 -> OutOfScopeModule.setTopraklamaOlcumOutOfScope(webView)
                    2 -> OutOfScopeModule.setIzolasyonOlcumOutOfScope(webView)
                    3 -> OutOfScopeModule.setTermalKameraOutOfScope(webView)
                }
                showScopeOutSuccessMessage(subItems[position])
            } else {
                Toast.makeText(this, "Aktif sekme bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Alt kategori menüsünü gösterir ve seçilen öğeyi işler
     * @param title Menü başlığı
     * @param items Menü öğeleri
     * @param onItemSelected Öğe seçildiğinde çalışacak fonksiyon
     */
    private fun showSubMenu(title: String, items: Array<String>, onItemSelected: (Int) -> Unit) {
        // Alt menü diyaloğunu göster
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setItems(items) { _, which ->
                onItemSelected(which)
            }
            .show()
    }

    /**
     * Kapsam dışı ayarlaması başarılı olduğunda bilgi mesajı gösterir
     * @param itemName Kapsam dışı yapılan öğenin adı
     */
    private fun showScopeOutSuccessMessage(itemName: String) {
        Snackbar.make(
            binding.root,
            "$itemName kapsam dışı olarak ayarlandı",
            Snackbar.LENGTH_SHORT
        ).show()
    }

    /**
     * URL'deki sayısal kodu ayıklayıp DataHolder'a kaydeder
     */
    private fun extractDigitsFromUrl(url: String) {
        try {
            // URL'nin sonundaki sayısal kısmı bul - herhangi bir uzunlukta sayı dizisi
            val regex = Regex("\\d+$")
            val matchResult = regex.find(url)

            matchResult?.let { result ->
                val digits = result.value
                // DataHolder'a kaydet
                com.asforce.asforcetkf2.util.DataHolder.url = digits
                }
        } catch (e: Exception) {
        }
    }

    private fun handlePanoFonksiyon() {
        // Pano Fonksiyon aktivitesini başlat
        val intent = Intent(this, com.asforce.asforcetkf2.ui.panel.kotlin.PanoFonksiyonActivity::class.java)
        startActivity(intent)
    }

    private fun handleTopraklama() {
        // Aktif sekmeden URL'yi al ve DataHolder'a kaydet
        val currentTab = viewModel.activeTab.value
        val currentUrl = currentTab?.url ?: ""

        // URL'den sayısal değeri çıkar
        try {
            val regex = Regex("\\d+$")
            val matchResult = regex.find(currentUrl)

            // Sayısal değer varsa kaydet, yoksa boş olarak bırak
            if (matchResult != null) {
                val digits = matchResult.value
                com.asforce.asforcetkf2.util.DataHolder.topraklama = digits
            } else {
                com.asforce.asforcetkf2.util.DataHolder.topraklama = ""
            }
        } catch (e: Exception) {
            com.asforce.asforcetkf2.util.DataHolder.topraklama = ""
        }

        // Topraklama Kontrol aktivitesini başlat
        val intent = Intent(this, com.asforce.asforcetkf2.ui.topraklama.kotlin.TopraklamaControlActivity::class.java)
        startActivity(intent)
    }

    private fun handleTermalKamera() {
        // Termal Kamera aktivitesini başlat
        val intent = Intent(this, com.asforce.asforcetkf2.ui.termal.kotlin.Menu4Activity::class.java)
        startActivity(intent)
    }

    private fun toggleResourceMonitoring() {
        val isEnabled = viewModel.resourceMonitoringEnabled.value ?: true
        viewModel.toggleResourceMonitoring(!isEnabled)

        Toast.makeText(
            this,
            "Kaynak İzleme: ${if (!isEnabled) "etkin" else "devre dışı"}",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun toggleHibernationMode() {
        val currentTab = viewModel.activeTab.value
        val tabs = viewModel.allTabs.value ?: emptyList()

        // Hibernate all inactive tabs
        tabs.forEach { tab ->
            if (!tab.isActive && !tab.isHibernated) {
                viewModel.hibernateTab(tab)
                activeWebViews[tab.id]?.hibernate()
            }
        }

        Toast.makeText(
            this,
            "Aktif olmayan sekmeler uyku moduna alındı",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun clearBrowsingData() {
        // Seçenekler için seçim kutusu göster
        val options = arrayOf(
            "Önbelleği temizle",
            "Geçmişi temizle",
            "Çerezleri temizle",
            "Tümünü temizle"
        )

        val checkedItems = booleanArrayOf(true, true, false, false) // Varsayılan olarak çerezleri silme

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_data)
            .setMultiChoiceItems(options, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Temizle") { _, _ ->
                var clearCache = checkedItems[0]
                var clearHistory = checkedItems[1]
                var clearCookies = checkedItems[2]
                var clearAll = checkedItems[3]

                if (clearAll) {
                    clearCache = true
                    clearHistory = true
                    clearCookies = true
                }

                if (clearCookies) {
                    // Clear cookies
                    android.webkit.CookieManager.getInstance().removeAllCookies(null)
                    android.webkit.CookieManager.getInstance().flush()
                }

                // Her sekme için işlem yap
                activeWebViews.values.forEach { webView ->
                    if (clearCache) {
                        webView.clearCache(true)
                    }

                    if (clearHistory) {
                        webView.clearHistory()
                    }
                }

                // Kullanıcıya bilgi ver
                val message = StringBuilder("Temizlendi: ")
                if (clearCache) message.append("Önbellek ")
                if (clearHistory) message.append("Geçmiş ")
                if (clearCookies) message.append("Çerezler ")

                Toast.makeText(
                    this,
                    message.toString(),
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    private fun showAboutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about)
            .setMessage("TKF Tarayıcı\nSürüm 1.0\n\nSekme yönetimi destekli hızlı ve güvenli bir tarayıcı")
            .setPositiveButton("Tamam", null)
            .show()
    }

    private fun startTabResourceMonitoring() {
        lifecycleScope.launch {
            // Flow'u doğrudan kullanmak yerine StateFlow'dan yararlanma
            try {
                viewModel.resourceMonitoringEnabled.collect { enabled ->
                    if (enabled) {
                        // Her sekme için ayrı bir coroutine başlat
                        activeWebViews.forEach { (tabId, webView) ->
                            // Yeni bir job olarak sekme kaynakları izleme
                            launch {
                                try {
                                    resourceMonitor.monitorTabResources(tabId, android.os.Process.myPid())
                                        .collect { metrics ->
                                            viewModel.updateTabResources(
                                                tabId,
                                                metrics.cpuUsage,
                                                metrics.memoryUsage
                                            )

                                            // Auto-hibernate if resource usage is too high
                                            val tab = viewModel.allTabs.value?.find { it.id == tabId }
                                            if (tab != null && !tab.isActive && !tab.isHibernated &&
                                                (metrics.cpuUsage > 30f || metrics.memoryUsage > 100 * 1024 * 1024)
                                            ) {
                                                viewModel.hibernateTab(tab)
                                                webView.hibernate()

                                            }
                                        }
                                } catch (e: Exception) {
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Dokunma olaylarını yöneterek klavyenin gerektiğinde kapanmasını sağlar
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is TextInputEditText && !isPointInsideView(ev.rawX, ev.rawY, v)) {
                hideKeyboard()
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Verilen noktanın belirtilen görünümün içinde olup olmadığını kontrol eder
     */
    private fun isPointInsideView(rawX: Float, rawY: Float, view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val x = location[0]
        val y = location[1]
        return (rawX >= x && rawX <= (x + view.width) &&
                rawY >= y && rawY <= (y + view.height))
    }

    /**
     * Klavyeyi gizler
     */
    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { view ->
            inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }

    /**
     * Klavyeyi görünür hale getirir
     * Geliştirilmiş klavye gösterimi - imleç sorununu çözmek için
     */
    private fun showKeyboard(view: View) {
        try {
            // Önce görünümün odaklandığından emin ol - imleç sorununu çözmek için
            if (!view.isFocused) {
                view.requestFocus()
            }

            // InputMethodManager ile klavyeyi göster
            val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)

            // Klavyenin gerçekten gösterildiğinden emin olmak için ek işlemler
            Handler(Looper.getMainLooper()).postDelayed({
                if (!inputMethodManager.isActive(view)) {
                    // Klavye hala açılmadıysa, zorla açmayı dene
                    inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_FORCED)
                }
            }, 200) // Klavyenin yüklenmesi için kısa bir gecikme
        } catch (e: Exception) {
        }
    }

    override fun onBackPressed() {
        val currentTab = viewModel.activeTab.value
        val webView = currentTab?.let { activeWebViews[it.id] }

        if (webView?.canGoBack() == true) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        // WebView'leri temizlerken güvenlik kontrolü ekle
        activeWebViews.values.forEach { webView ->
            try {
                if (webView.isAttachedToWindow && !isFinishing) {
                    webView.cleanup()
                }
            } catch (e: Exception) {
                // WebView temizleme hatası
            }
        }
        activeWebViews.clear()

        // Clean up suggestion manager
        try {
            suggestionManager.cleanup()
        } catch (e: Exception) {
            // Suggestion manager temizleme hatası
        }

        super.onDestroy()
    }

    /**
     * Uzun basma menüsünü gösterir - link için seçenekler
     */
    private fun showLongPressMenu(url: String) {
        // Menü seçenekleri
        val options = arrayOf(
            "Yeni sekmede aç",
            "Linki kopyala",
            "Dosyayı indir"
        )

        // Dialog menüsünü göster
        MaterialAlertDialogBuilder(this)
            .setTitle("Link seçenekleri")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // Yeni sekmede aç
                        addNewTab(url)
                        Toast.makeText(this, "Link yeni sekmede açılıyor", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        // Linki kopyala
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("URL", url)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "Link kopyalandı", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        // Linkteki dosyayı indir
                        val downloadId = downloadManager.downloadFile(
                            url, "Mozilla/5.0", "", "*/*", 0
                        )

                        if (downloadId > 0) {
                            Toast.makeText(this, "İndirme başlatıldı", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "İndirme başlatılamadı", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .show()
    }

    /**
     * Dosya kaynağı seçim menüsünü gösterir (Kamera, Galeri, Dosya Seçici)
     * Modern, özel bir tasarım ile
     */
    private fun showFileSourceDialog() {
        try {
            // BottomSheetDialog kullan - daha modern ve mobil dostu
            val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.BottomSheetDialogTheme)

            // Özel düzeni şişir
            val view = layoutInflater.inflate(R.layout.dialog_file_source, null)
            dialog.setContentView(view)

            // UI öğelerini ayarla
            val cameraCard = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cameraCard)
            val galleryCard = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.galleryCard)
            val filesCard = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.filesCard)
            val btnCancel = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)

            // Kamera kullanılabilirliğini kontrol et
            val isCameraAvailable = try {
                packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
            } catch (e: Exception) {
                true // Kontrol edilemezse, varsayılan olarak kullanılabilir olarak işaretlenir
            }

            // Kamera yoksa butonu devre dışı bırak
            if (!isCameraAvailable) {
                cameraCard.isEnabled = false
                cameraCard.alpha = 0.5f
            }

            // Kamera seçeneği için tıklama dinleyicisi
            cameraCard.setOnClickListener {
                dialog.dismiss()
                // Çift tıklamayı önle (bazı cihazlarda çift tetiklenme sorunu yaşanıyor)
                if (System.currentTimeMillis() - (cameraCard.tag as? Long ?: 0) > 1000) {
                    cameraCard.tag = System.currentTimeMillis()
                    takePictureFromCamera()
                }
            }

            // Galeri seçeneği için tıklama dinleyicisi
            galleryCard.setOnClickListener {
                dialog.dismiss()
                if (System.currentTimeMillis() - (galleryCard.tag as? Long ?: 0) > 1000) {
                    galleryCard.tag = System.currentTimeMillis()
                    selectImageFromGallery()
                }
            }

            // Dosyalar seçeneği için tıklama dinleyicisi
            filesCard.setOnClickListener {
                dialog.dismiss()
                if (System.currentTimeMillis() - (filesCard.tag as? Long ?: 0) > 1000) {
                    filesCard.tag = System.currentTimeMillis()
                    openFileChooser()
                }
            }

            // İptal düğmesi için tıklama dinleyicisi
            btnCancel.setOnClickListener {
                dialog.dismiss()
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }

            // Dialog iptal edildiğinde dosya seçimi iptal edilir
            dialog.setOnCancelListener {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
            }

            // Dialog hatalarına karşı koruma
            try {
                dialog.show()
            } catch (e: Exception) {
                e.printStackTrace()
                // Dialog gösterilemezse, alternatif olarak direkt dosya seçiciyi başlat
                openFileChooser()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Herhangi bir diyalog hatası durumunda dosya seçiciyi doğrudan aç
            openFileChooser()
        }
    }

    /**
     * Kamera ile fotoğraf çekmek için gerekli izinleri kontrol eder ve kamerayı başlatır
     */
    private fun takePictureFromCamera() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
            return
        }

        // Kamera ile fotoğraf çekme işlemi - iyileştirilmiş hata yakalama ve uyumluluk
        try {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

            // İyileştirilmiş resolveActivity kontrolü
            val cameraActivities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            val hasCameraApp = cameraActivities.isNotEmpty()

            if (hasCameraApp) {
                // Geçici dosya oluştur
                val photoFile = try {
                    createImageFile()
                } catch (e: Exception) {
                    // Hata günlüğü
                    e.printStackTrace()
                    // Varsayılan DCIM dizininde deneme
                    try {
                        val storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
                        java.io.File.createTempFile(
                            "JPEG_TKF_", ".jpg", storageDir
                        )
                    } catch (e2: Exception) {
                        e2.printStackTrace()
                        null
                    }
                }

                if (photoFile != null) {
                    try {
                        currentPhotoUri = FileProvider.getUriForFile(
                            this,
                            "com.asforce.asforcetkf2.fileprovider",
                            photoFile
                        )

                        intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)

                        // Diğer cihazlara okuma izni ver - kamera erişim hatası düzeltmesi
                        val resInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                        for (resolveInfo in resInfoList) {
                            val packageName = resolveInfo.activityInfo.packageName
                            grantUriPermission(
                                packageName,
                                currentPhotoUri,
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }

                        // İyileştirilmiş güncellikten emin olma hata kontrolü
                        try {
                            cameraLauncher.launch(intent)
                        } catch (e: Exception) {
                            // ActivityResultLauncher başlatma hatası
                            e.printStackTrace()
                            filePathCallback?.onReceiveValue(null)
                            filePathCallback = null
                            Toast.makeText(this, "Kamera başlatılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        // FileProvider hatası
                        e.printStackTrace()
                        filePathCallback?.onReceiveValue(null)
                        filePathCallback = null
                        Toast.makeText(this, "Kamera izinleri alınamadı: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                    Toast.makeText(this, R.string.camera_app_not_found, Toast.LENGTH_SHORT).show()
                }
            } else {
                // Kamera uygulaması bulunamadı, alternatif yöntem dene
                try {
                    // Direkt olarak aktivity başlatmayı dene
                    val plainIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    startActivityForResult(plainIntent, CAMERA_REQUEST_CODE)
                } catch (e: Exception) {
                    e.printStackTrace()
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                    Toast.makeText(this, "Kamera uygulaması bulunamadı.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            // Genel hata durumu
            e.printStackTrace()
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            Toast.makeText(this, "Kamera açılırken bir hata oluştu: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Galeri/Medya'dan görsel seçmek için intent oluşturur
     */
    private fun selectImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        fileChooserLauncher.launch(intent)
    }

    /**
     * Tüm dosya tiplerini gösteren dosya seçiciyi başlatır
     */
    private fun openFileChooser() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "*/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)

            fileChooserLauncher.launch(Intent.createChooser(
                intent,
                getString(R.string.file_chooser_title)
            ))
        } catch (e: Exception) {
            // Failed to open file chooser
            Toast.makeText(
                this,
                R.string.file_chooser_error,
                Toast.LENGTH_SHORT
            ).show()
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
        }
    }

    /**
     * Kamera için geçici resim dosyası oluşturur
     */
    private fun createImageFile(): java.io.File {
        val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
        val imageFileName = "JPEG_" + timeStamp + "_"

        // Birden fazla depolama konumunu dene
        var storageDir: java.io.File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

        // Ana konumun mevcut ve yazılabilir olduğundan emin ol
        if (storageDir == null || !storageDir.exists()) {
            // Uygulamaya özgü dahili depolamayı dene
            storageDir = filesDir

            // Hala başarısızsa, harici DCIM dizinini dene
            if (storageDir == null || !storageDir.exists()) {
                // Harici DCIM klasörünü dene
                storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)

                // Bu da başarısızsa, cache dizinini kullan
                if (storageDir == null || !storageDir.exists()) {
                    storageDir = cacheDir
                }
            }
        }

        // Son çare olarak kesinlikle bir dizin olduğundan emin ol
        if (storageDir == null) {
            storageDir = cacheDir // Cache dizini her zaman var olmalı
        }

        // Klasörün var olduğundan emin ol
        if (storageDir?.exists() != true) {
            storageDir?.mkdirs()
        }

        // Dosya oluştur
        val image = java.io.File.createTempFile(
            imageFileName,  /* önek */
            ".jpg",         /* uzantı */
            storageDir      /* dizin */
        )

        // Görüntü dosya yolu
        cameraPhotoPath = image.absolutePath
        return image
    }

    // Kamera için Activity sonuç işleyicisi - startActivityForResult için
    private val CAMERA_REQUEST_CODE = 1002

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == CAMERA_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                // Doğrudan kamera aktivitesinden sonuç - muhtemelen veri içerecek
                val imageUri = if (data?.data != null) {
                    data.data // Küçük resim (thumbnail) alındı
                } else if (currentPhotoUri != null) {
                    currentPhotoUri // Tam boyutlu resim alındı
                } else if (data?.extras?.get("data") is Bitmap) {
                    // Bitmap olarak gelen verileri Uri'ye dönüştür
                    try {
                        val bitmap = data.extras?.get("data") as Bitmap
                        val tempFile = java.io.File.createTempFile("camera_image", ".jpg", cacheDir)
                        val fos = java.io.FileOutputStream(tempFile)
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                        fos.close()
                        FileProvider.getUriForFile(this, "com.asforce.asforcetkf2.fileprovider", tempFile)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                } else null

                filePathCallback?.let { callback ->
                    if (imageUri != null) {
                        callback.onReceiveValue(arrayOf(imageUri))
                    } else {
                        callback.onReceiveValue(null)
                    }
                }
            } else {
                filePathCallback?.onReceiveValue(null)
            }
            filePathCallback = null
            currentPhotoUri = null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // İzin verildi, kamera fonksiyonunu çağır
                    takePictureFromCamera()
                } else {
                    // İzin yok, kullanıcıya bilgi ver
                    Toast.makeText(this, "Kamera için izin gerekli", Toast.LENGTH_SHORT).show()
                    filePathCallback?.onReceiveValue(null)
                    filePathCallback = null
                }
                return
            }
        }
    }

    /**
     * Initialize suggestion manager and track input fields
     */
    private fun initializeSuggestionManager() {
        // Create suggestion manager
        suggestionManager = SuggestionManager(this)

        // Track keyboard visibility to show/hide suggestions at appropriate times
        suggestionManager.trackKeyboardVisibility(binding.root)

        // Track URL input
        binding.urlInput.let { editText ->
            suggestionManager.trackEditText(editText, "url_input")
        }

        // Track aramaSearch input
        binding.aramaSearch.let { editText ->
            suggestionManager.trackEditText(editText, "aramaSearch_input")
        }

        // Track aramaSearch2 input (new)
        binding.aramaSearch2.let { editText ->
            suggestionManager.trackEditText(editText, "aramaSearch2_input")
        }

        // Monitor keyboard visibility changes to reposition suggestion bar above keyboard
        val contentView = window.decorView.findViewById<View>(android.R.id.content)
        contentView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            contentView.getWindowVisibleDisplayFrame(r)

            val screenHeight = contentView.height
            val keyboardHeight = screenHeight - r.bottom

            // Consider keyboard as visible if its height is more than 15% of screen
            if (keyboardHeight > screenHeight * 0.15) {
                // Pass keyboard height to suggestion manager for proper positioning
                val activeTab = viewModel.activeTab.value
                val webView = activeTab?.let { activeWebViews[it.id] }

                // Force refresh suggestions for current input
                webView?.evaluateJavascript("""
                    (function() {
                        // Get active element if any
                        if (document.activeElement && 
                            (document.activeElement.tagName === 'INPUT' || 
                             document.activeElement.tagName === 'TEXTAREA')) {
                            
                            var el = document.activeElement;
                            
                            // Get or create input key
                            var key = el.getAttribute('data-tkf-key');
                            if (!key) {
                                key = (el.name || el.id || el.placeholder || 'input_' + Math.random().toString(36).substr(2, 9));
                                key = key.replace(/[^a-zA-Z0-9_]/g, '_');
                                el.setAttribute('data-tkf-key', key);
                            }
                            
                            // Get input value
                            var value = el.value || '';
                            
                            // Notify suggestion handler
                            if (window.SuggestionHandler) {
                                window.SuggestionHandler.onInputFocused(key);
                                window.SuggestionHandler.onInputChanged(key, value);
                                return "NOTIFIED_SUGGESTION";
                                }
                                
                                return "NO_HANDLER";
                                }
                                return "NO_ACTIVE_INPUT";
                                })();
                                """) { _ -> }
            }
        }
    }

    /**
     * QR kod tarayıcı butonunu ayarla
     */
    private fun setupQrScannerButton() {
        binding.btnQr.setOnClickListener {
            // QR kod tarayıcıyı başlat
            openQrScanner()
        }
    }

    /**
     * QR kod tarayıcıyı aç
     */
    private fun openQrScanner() {
        // İzin kontrolü
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // İzin iste
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
            return
        }

        // QR kod tarayıcı fragment'ı oluştur ve başlat
        val qrScannerFragment = QRScannerFragment.newInstance { qrContent ->
            // QR kod algılandığında çalışacak callback
            handleQrCodeResult(qrContent)
        }

        qrScannerFragment.show(supportFragmentManager, "QR_SCANNER")
    }

    /**
     * QR kod tarama sonucunu işle
     */
    private fun handleQrCodeResult(content: String) {
        // QR kod içeriğine göre işlem yap
        if (URLUtil.isValidUrl(content)) {
            // İçerik bir URL ise doğrudan aç
            loadUrl(content)
            Snackbar.make(
                binding.root,
                "QR koddan URL açıldı: $content",
                Snackbar.LENGTH_SHORT
            ).show()
        } else {
            // URL değilse kullanıcıya ne yapmak istediğini sor
            showQrResultActionDialog(content)
        }
    }

    /**
     * Aksiyon butonlarını ayarla (Ekipman Listesi ve Kontrol Listesi butonları)
     */
    /**
     * Açılır kapanır menü butonlarını ayarla
     */
    private fun setupFloatingMenuButtons() {
        // Menü açma/kapama butonu
        binding.btnToggleButtons.setOnClickListener {
            toggleFloatingMenu()
        }

        // Uygulama ilk açıldığında menü görünürlüğünü ayarla
        binding.buttonsScrollView.visibility = View.GONE
    }

    /**
     * Açılır kapanır menüyü aç/kapat
     */
    private fun toggleFloatingMenu() {
        // Menü görünürlüğünü değiştir
        val isVisible = binding.buttonsScrollView.visibility == View.VISIBLE

        // Interpolator'ları tanımla (daha yumuşak animasyonlar için)
        val overshootInterpolator = android.view.animation.OvershootInterpolator(0.5f)
        val accelerateDecelerateInterpolator = android.view.animation.AccelerateDecelerateInterpolator()

        if (isVisible) {
            // Menü açıksa kapat
            binding.buttonsScrollView.animate()
                .alpha(0f)
                .translationX(-50f)
                .setDuration(350)
                .setInterpolator(accelerateDecelerateInterpolator)
                .withEndAction {
                    binding.buttonsScrollView.visibility = View.GONE
                    binding.buttonsScrollView.translationX = 0f
                }
                .start()

            // Açma/kapama butonunu zarif animasyonla döndür
            binding.btnToggleButtons.animate()
                .rotation(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(350)
                .setInterpolator(overshootInterpolator)
                .start()

            // Buton renk değişimi
            val colorAnimation = android.animation.ValueAnimator.ofArgb(
                binding.btnToggleButtons.backgroundTintList?.defaultColor ?: ContextCompat.getColor(this, R.color.primary),
                ContextCompat.getColor(this, R.color.primary)
            )
            colorAnimation.duration = 350
            colorAnimation.addUpdateListener { animator ->
                binding.btnToggleButtons.backgroundTintList = android.content.res.ColorStateList.valueOf(animator.animatedValue as Int)
            }
            colorAnimation.start()
        } else {
            // Menü kapalıysa aç
            binding.buttonsScrollView.alpha = 0f
            binding.buttonsScrollView.translationX = -50f
            binding.buttonsScrollView.visibility = View.VISIBLE
            binding.buttonsScrollView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(350)
                .setInterpolator(overshootInterpolator)
                .start()

            // Açma/kapama butonunu zarif animasyonla döndür
            binding.btnToggleButtons.animate()
                .rotation(90f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(350)
                .setInterpolator(overshootInterpolator)
                .start()

            // Buton renk değişimi (açıkken daha koyu ton)
            val colorAnimation = android.animation.ValueAnimator.ofArgb(
                binding.btnToggleButtons.backgroundTintList?.defaultColor ?: ContextCompat.getColor(this, R.color.primary),
                android.graphics.Color.parseColor("#005e9e") // Daha koyu mavi ton
            )
            colorAnimation.duration = 350
            colorAnimation.addUpdateListener { animator ->
                binding.btnToggleButtons.backgroundTintList = android.content.res.ColorStateList.valueOf(animator.animatedValue as Int)
            }
            colorAnimation.start()
        }
    }

    private fun setupActionButtons() {
        // Ekipman Listesi butonu - btn_left_1
        binding.btnLeft1.setOnClickListener {
            loadUrl("https://app.szutest.com.tr/EXT/PKControl/EquipmentList")
        }

        // Add listener for the aramaSearch EditText
        binding.aramaSearch.setOnEditorActionListener { _, actionId, keyEvent ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER && keyEvent.action == KeyEvent.ACTION_DOWN)) {
                hideKeyboard()
                performComboboxSearch(binding.aramaSearch.text.toString().trim())
                return@setOnEditorActionListener true
            }
            false
        }

        // Add listener for the aramaSearch2 EditText (new)
        binding.aramaSearch2.setOnEditorActionListener { _, actionId, keyEvent ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER && keyEvent.action == KeyEvent.ACTION_DOWN)) {
                hideKeyboard()
                performComboboxSearch(binding.aramaSearch2.text.toString().trim())
                return@setOnEditorActionListener true
            }
            false
        }

        // Add end icon for aramaSearch
        val textInputLayout1 = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.text_input_layout_1)
        textInputLayout1?.apply {
            setEndIconDrawable(R.drawable.ic_search)
            setEndIconOnClickListener {
                hideKeyboard()
                performComboboxSearch(binding.aramaSearch.text.toString().trim())
            }
        }

        // Add end icon for aramaSearch2 (new)
        val textInputLayout1_2 = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.text_input_layout_1_2)
        textInputLayout1_2?.apply {
            setEndIconDrawable(R.drawable.ic_search)
            setEndIconOnClickListener {
                hideKeyboard()
                performComboboxSearch(binding.aramaSearch2.text.toString().trim())
            }
        }

        // Kontrol Listesi butonu - btn_left_2
        binding.btnLeft2.setOnClickListener {
            // Sadece URL'yi yükle, özel form desteği yok
            loadUrl("https://app.szutest.com.tr/EXT/PKControl/EKControlList")
        }

        // Kapsam Dışı butonu - btn_scope_out
        binding.btnScopeOut.setOnClickListener {
            showScopeOutMenu()
        }

        // QR arama fonksiyonu için IME_ACTION_SEARCH işleyicisi ekle
        binding.qrNo.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                performQrCodeSearch()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        // QR Edittext için end icon ekle ve tıklama işleyicisi ayarla
        val textInputLayout2 = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.text_input_layout_2)
        textInputLayout2?.apply {
            setEndIconDrawable(R.drawable.ic_search)
            setEndIconOnClickListener {
                hideKeyboard()
                performQrCodeSearch()
            }
        }

        // Seri Numarası arama fonksiyonu için IME_ACTION_SEARCH işleyicisi ekle
        binding.srNo.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                performSerialNumberSearch() // SR Numarası için özel arama fonksiyonu
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }

        // SR Edittext için end icon ekle ve tıklama işleyicisi ayarla
        val textInputLayout3 = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.text_input_layout_3)
        textInputLayout3?.apply {
            setEndIconDrawable(R.drawable.ic_search)
            setEndIconOnClickListener {
                hideKeyboard()
                performSerialNumberSearch()
            }
        }

        // Cihaz Ekleme butonu - btn_right_top
        binding.btnRightTop.setOnClickListener {
            val currentTab = viewModel.activeTab.value
            val webView = currentTab?.let { activeWebViews[it.id] }

            if (webView != null) {
                // DeviceManager örneği oluştur ve cihaz listesini çek
                deviceManager = DeviceManager(this, webView)
                deviceManager.fetchDeviceList()
            } else {
                Toast.makeText(this, "Aktif sekme bulunamadı", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * QR kod sonuç işlemi için seçenekler sunan diyalog
     */
    private fun showQrResultActionDialog(content: String) {
        val options = arrayOf(
            "İnternet'te ara",
            "Panoya kopyala"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("QR Kod İçeriği")
            .setMessage(content)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        // İnternet'te ara
                        val searchUrl = "https://www.google.com/search?q=${Uri.encode(content)}"
                        loadUrl(searchUrl)
                    }
                    1 -> {
                        // Panoya kopyala
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("QR Code Content", content)
                        clipboard.setPrimaryClip(clip)

                        Toast.makeText(
                            this,
                            "İçerik panoya kopyalandı",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            .setPositiveButton("Kapat", null)
            .show()
    }

    /**
     * Test suggestion insertion directly for debugging purposes
     */
    private fun testSuggestionInsertion() {
        val currentTab = viewModel.activeTab.value
        val webView = currentTab?.let { activeWebViews[it.id] }

        if (webView != null) {
            // 1. First try to identify the active input field
            webView.evaluateJavascript("""
                (function() {
                    // Find active element or first visible input
                    var element = document.activeElement;
                    var elementInfo = "";
                    
                    if (element && (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA')) {
                        elementInfo = "Active element: " + element.tagName;
                        if (element.id) elementInfo += "#" + element.id;
                        if (element.name) elementInfo += " name=" + element.name;
                        
                        // Get or create key
                        var key = element.getAttribute('data-tkf-key');
                        if (!key) {
                            key = (element.name || element.id || 'input_' + Math.random().toString(36).substr(2, 9));
                            key = key.replace(/[^a-zA-Z0-9_]/g, '_');
                            element.setAttribute('data-tkf-key', key);
                        }
                        
                        return JSON.stringify({
                            found: true,
                            key: key,
                            info: elementInfo,
                            value: element.value || ""
                        });
                    }
                    
                    // If no active input, find first visible input
                    var inputs = document.querySelectorAll('input, textarea');
                    for (var i = 0; i < inputs.length; i++) {
                        var input = inputs[i];
                        if (input.type !== 'hidden' && input.offsetParent !== null) {
                            elementInfo = "Found input: " + input.tagName;
                            if (input.id) elementInfo += "#" + input.id;
                            if (input.name) elementInfo += " name=" + input.name;
                            
                            // Focus the input
                            input.focus();
                            
                            // Get or create key
                            var key = input.getAttribute('data-tkf-key');
                            if (!key) {
                                key = (input.name || input.id || 'input_' + Math.random().toString(36).substr(2, 9));
                                key = key.replace(/[^a-zA-Z0-9_]/g, '_');
                                input.setAttribute('data-tkf-key', key);
                            }
                            
                            return JSON.stringify({
                                found: true,
                                key: key,
                                info: elementInfo,
                                value: input.value || ""
                            });
                        }
                    }
                    
                    return JSON.stringify({
                        found: false,
                        info: "No input elements found"
                    });
                })();
            """) { result ->
                try {
                    // Clean up result string (remove quotes)
                    val jsonStr = result.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")

                    // Parse JSON
                    val jsonObj = org.json.JSONObject(jsonStr.toString())
                    val found = jsonObj.getBoolean("found")

                    if (found) {
                        val key = jsonObj.getString("key")
                        val info = jsonObj.getString("info")

                        // Generate a test value
                        val testValue = "TEST-${System.currentTimeMillis() / 1000}"

                        // 2. Save the test value as a suggestion
                        suggestionManager.saveSuggestion(key, testValue)

                        // 3. Use multiple approaches to set the value
                        (webView as? com.asforce.asforcetkf2.webview.TabWebView)?.apply {
                            // Direct simulation method
                            simulateKeyboardInput(testValue)

                            // JavaScript method
                            val setValueScript = """
                            (function() {
                                var element = document.querySelector('[data-tkf-key="$key"]');
                                if (!element) {
                                    if (document.activeElement && 
                                        (document.activeElement.tagName === 'INPUT' || 
                                         document.activeElement.tagName === 'TEXTAREA')) {
                                        element = document.activeElement;
                                    }
                                }
                                
                                if (element) {
                                    // Set value using all available methods
                                    element.value = '$testValue';
                                    
                                    // Select all text
                                    element.select();
                                    
                                    // Try execCommand
                                    if (document.execCommand) {
                                        document.execCommand('insertText', false, '$testValue');
                                    }
                                    
                                    // Dispatch events
                                    var inputEvent = new Event('input', {bubbles: true});
                                    element.dispatchEvent(inputEvent);
                                    var changeEvent = new Event('change', {bubbles: true});
                                    element.dispatchEvent(changeEvent);
                                    
                                    return "Successfully set value to: " + element.value;
                                }
                                return "No element found to set value";
                            })();
                            """.trimIndent()

                            evaluateJavascript(setValueScript) { _ -> }
                        }

                        // Show toast notification
                        Toast.makeText(this, "Test suggestion inserted: $testValue", Toast.LENGTH_SHORT).show()
                    } else {
                        val info = jsonObj.getString("info")
                        Toast.makeText(this, "No input element found to test", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error testing suggestions: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Toast.makeText(this, "No active WebView to test", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Otomatik QR kod kontrolü - her sayfada QR kodu olup olmadığını kontrol eder
     */
    private fun checkForQrCodeOnPage(webView: TabWebView) {
        val checkResultScript = """
            (function() {
                try {
                    // Find the result value in the specified format
                    var resultElements = document.querySelectorAll('div.col-sm-8 p.form-control-static');
                    var results = [];
                    
                    for (var i = 0; i < resultElements.length; i++) {
                        var text = resultElements[i].textContent.trim();
                        if (text && /^\d+$/.test(text)) {  // Sadece sayı içeren değerleri kontrol et
                            results.push(text);
                        }
                    }
                    
                    if (results.length > 0) {
                        return JSON.stringify(results);
                    } else {
                        return "NO_RESULTS";
                    }
                } catch(e) {
                    return "ERROR: " + e.message;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(checkResultScript) { result ->
            try {
                // Clean up the result string
                val cleanResult = result.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")

                if (cleanResult == "NO_RESULTS" || cleanResult.startsWith("ERROR")) {
                    // Herhangi bir hata veya sonuç bulunamazsa sessizce çık
                    return@evaluateJavascript
                }

                // Parse JSON result
                val jsonArray = org.json.JSONArray(cleanResult)

                if (jsonArray.length() > 0) {
                    val foundQrCode = jsonArray.getString(0)

                    // Bulunan değer şu anki değerden farklıysa güncelle
                    val currentQrText = binding.qrNo.text.toString().trim()
                    if (foundQrCode != currentQrText) {
                        runOnUiThread {
                            // Update the qrEditText with the found value
                            binding.qrNo.setText(foundQrCode)
                            // Toast mesajı kaldırıldı
                        }
                    }
                }
            } catch (e: Exception) {
                // Hata durumunda sessizce devam et
            }
        }
    }

    /**
     * QR code search functionality - handles searching on equipment list page
     */
    // Manuel arama aktif olduğunda kullanılacak bayrak
    private var isManualSearchActive = false

    /**
     * Flag to track if combobox search is active
     */
    private var isComboboxSearchActive = false

    private fun performQrCodeSearch() {
        val qrText = binding.qrNo.text.toString().trim()

        if (qrText.isEmpty()) {
            // Boş girdi durumunda sessizce çık
            return
        }

        // Get current active tab and WebView
        val currentTab = viewModel.activeTab.value
        val webView = currentTab?.let { activeWebViews[it.id] }

        if (webView == null) {
            // Aktif sekme bulunamadığında sessizce çık
            return
        }

        // Manuel arama modunu aktifleştir
        isManualSearchActive = true

        // Check if the current page is the EquipmentList
        val currentUrl = webView.url ?: ""
        if (!currentUrl.contains("EquipmentList")) {
            // Navigate to EquipmentList page first
            webView.loadUrl("https://app.szutest.com.tr/EXT/PKControl/EquipmentList")

            // Geçici onPageFinished listener'ı (sadece bu arama için)
            val originalOnPageFinished = webView.onPageFinished // Mevcut listener'ı sakla

            webView.onPageFinished = { tabId, url, favicon ->
                // Only proceed if we're on the right page
                if (url.contains("EquipmentList") && isManualSearchActive) {
                    // Wait a bit for the page to fully render
                    Handler(Looper.getMainLooper()).postDelayed({
                        executeQrCodeSearch(webView, qrText)
                        // Aramayı tamamladıktan sonra bayrağı sıfırla
                        isManualSearchActive = false
                        // Orijinal listener'ı geri yükle
                        webView.onPageFinished = originalOnPageFinished
                    }, 1000)
                }

                // Call the original onPageFinished handler's functionality
                val tabs = viewModel.allTabs.value ?: emptyList()
                val tab = tabs.find { it.id == tabId }
                if (tab != null) {
                    // Update tab
                    val updatedTab = tab.copy(
                        url = url,
                        isLoading = false,
                        favicon = favicon ?: tab.favicon
                    )

                    val position = tabs.indexOf(tab)
                    viewModel.updateTab(updatedTab, position)

                    // Hide progress bar
                    binding.progressBar.isVisible = false
                }
            }
        } else {
            // Already on the correct page, execute search directly
            executeQrCodeSearch(webView, qrText)
            isManualSearchActive = false // Aramayı tamamladıktan sonra bayrağı sıfırla
        }
    }

    private fun executeQrCodeSearch(webView: TabWebView, qrText: String) {
        // JavaScript to fill in the QR input field and click search button
        val searchScript = """
            (function() {
                try {
                    // Find the QR input field
                    var qrInput = document.querySelector('input#filter_qr');
                    if (!qrInput) {
                        return "QR input field not found";
                    }
                    
                    // Set the QR value
                    qrInput.value = "$qrText";
                    
                    // Find and click the search button
                    var searchButton = document.querySelector('i.fa.fa-search[title="Filtrele"]');
                    if (!searchButton) {
                        return "Search button not found";
                    }
                    
                    // Click the search button
                    searchButton.click();
                    
                    return "Search executed";
                } catch(e) {
                    return "Error: " + e.message;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(searchScript) { result ->
            // Wait briefly for the search to complete and then check the result
            Handler(Looper.getMainLooper()).postDelayed({
                checkQrCodeSearchResult(webView, qrText)
            }, 1500)
        }
    }

    private fun checkQrCodeSearchResult(webView: TabWebView, qrText: String) {
        val checkResultScript = """
            (function() {
                try {
                    // Find the result value in the specified format
                    var resultElements = document.querySelectorAll('div.col-sm-8 p.form-control-static');
                    var results = [];
                    
                    for (var i = 0; i < resultElements.length; i++) {
                        var text = resultElements[i].textContent.trim();
                        if (text) {
                            results.push(text);
                        }
                    }
                    
                    if (results.length > 0) {
                        return JSON.stringify(results);
                    } else {
                        return "NO_RESULTS";
                    }
                } catch(e) {
                    return "ERROR: " + e.message;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(checkResultScript) { result ->
            try {
                // Clean up the result string
                val cleanResult = result.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")

                if (cleanResult == "NO_RESULTS") {
                    // Sessizce devam et - bilgi mesajı gösterme
                    return@evaluateJavascript
                }

                if (cleanResult.startsWith("ERROR")) {
                    // Sessizce devam et - hata mesajı gösterme
                    return@evaluateJavascript
                }

                // Parse JSON result
                val jsonArray = org.json.JSONArray(cleanResult)

                if (jsonArray.length() > 0) {
                    val foundQrCode = jsonArray.getString(0)

                    runOnUiThread {
                        // Update the qrEditText with the found value
                        binding.qrNo.setText(foundQrCode)
                        // Toast mesajı kaldırıldı
                    }
                }
            } catch (e: Exception) {
                // Hata durumunda sessizce devam et
            }
        }
    }

    /**
     * Seri numarası arama fonksiyonu - serialnumber alanı ile arama yapar
     */
    private var isSerialSearchActive = false

    /**
     * Function to search comboboxes in the WebView for matching items
     * This will search across all comboboxes in the page and select the first match
     * @param searchText The specific text to search for
     */
    private fun performComboboxSearch(searchText: String) {
        if (searchText.isEmpty()) {
            // Empty input, silently return
            return
        }

        // Get current active tab and WebView
        val currentTab = viewModel.activeTab.value
        val webView = currentTab?.let { activeWebViews[it.id] }

        if (webView == null) {
            // No active WebView, show a message
            Toast.makeText(this, "Aktif sekme bulunamadı", Toast.LENGTH_SHORT).show()
            return
        }

        // Set combobox search flag to active
        isComboboxSearchActive = true

        // Show searching indicator
        val snackbar = Snackbar.make(
            binding.root,
            "Aranıyor: $searchText",
            Snackbar.LENGTH_SHORT
        )
        snackbar.show()

        // Create and use ComboboxSearchHelper
        val searchHelper = com.asforce.asforcetkf2.webview.ComboboxSearchHelper(webView)
        searchHelper.searchComboboxes(
            searchText = searchText,
            onItemFound = { comboboxName, itemText ->
                // Item found and selected, show success message
                runOnUiThread {
                    // Dismiss the searching indicator if it's still showing
                    snackbar.dismiss()

                    // Show found message
                    Snackbar.make(
                        binding.root,
                        "'$itemText' bulundu ve seçildi",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            },
            onSearchComplete = {
                // Search completed, reset flag
                isComboboxSearchActive = false
            },
            onNoResults = {
                // No results found, show message
                runOnUiThread {
                    // Dismiss the searching indicator if it's still showing
                    snackbar.dismiss()

                    // Show not found message
                    Snackbar.make(
                        binding.root,
                        "'$searchText' için eşleşme bulunamadı",
                        Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    private fun performSerialNumberSearch() {
        val serialText = binding.srNo.text.toString().trim()

        if (serialText.isEmpty()) {
            // Boş girdi durumunda sessizce çık
            return
        }

        // Get current active tab and WebView
        val currentTab = viewModel.activeTab.value
        val webView = currentTab?.let { activeWebViews[it.id] }

        if (webView == null) {
            // Aktif sekme bulunamadığında sessizce çık
            return
        }

        // Manuel seri numarası arama modunu aktifleştir
        isSerialSearchActive = true

        // Check if the current page is the EquipmentList
        val currentUrl = webView.url ?: ""
        if (!currentUrl.contains("EquipmentList")) {
            // Navigate to EquipmentList page first
            webView.loadUrl("https://app.szutest.com.tr/EXT/PKControl/EquipmentList")

            // Geçici onPageFinished listener'ı (sadece bu arama için)
            val originalOnPageFinished = webView.onPageFinished // Mevcut listener'ı sakla

            webView.onPageFinished = { tabId, url, favicon ->
                // Only proceed if we're on the right page
                if (url.contains("EquipmentList") && isSerialSearchActive) {
                    // Wait a bit for the page to fully render
                    Handler(Looper.getMainLooper()).postDelayed({
                        executeSerialNumberSearch(webView, serialText)
                        // Aramayı tamamladıktan sonra bayrağı sıfırla
                        isSerialSearchActive = false
                        // Orijinal listener'ı geri yükle
                        webView.onPageFinished = originalOnPageFinished
                    }, 1000)
                }

                // Call the original onPageFinished handler's functionality
                val tabs = viewModel.allTabs.value ?: emptyList()
                val tab = tabs.find { it.id == tabId }
                if (tab != null) {
                    // Update tab
                    val updatedTab = tab.copy(
                        url = url,
                        isLoading = false,
                        favicon = favicon ?: tab.favicon
                    )

                    val position = tabs.indexOf(tab)
                    viewModel.updateTab(updatedTab, position)

                    // Hide progress bar
                    binding.progressBar.isVisible = false
                }
            }
        } else {
            // Already on the correct page, execute search directly
            executeSerialNumberSearch(webView, serialText)
            isSerialSearchActive = false // Aramayı tamamladıktan sonra bayrağı sıfırla
        }
    }

    private fun executeSerialNumberSearch(webView: TabWebView, serialText: String) {
        // JavaScript to fill in the serial number input field and click search button
        val searchScript = """
            (function() {
                try {
                    // Find the serial number input field
                    var serialInput = document.querySelector('input#filter_serialnumber');
                    if (!serialInput) {
                        return "Serial number input field not found";
                    }
                    
                    // Set the serial number value
                    serialInput.value = "$serialText";
                    
                    // Find and click the search button
                    var searchButton = document.querySelector('i.fa.fa-search[title="Filtrele"]');
                    if (!searchButton) {
                        return "Search button not found";
                    }
                    
                    // Click the search button
                    searchButton.click();
                    
                    return "Search executed";
                } catch(e) {
                    return "Error: " + e.message;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(searchScript) { result ->
            // Wait briefly for the search to complete and then check the result
            Handler(Looper.getMainLooper()).postDelayed({
                checkSerialNumberSearchResult(webView, serialText)
            }, 1500)
        }
    }

    private fun checkSerialNumberSearchResult(webView: TabWebView, serialText: String) {
        val checkResultScript = """
            (function() {
                try {
                    // Find the result value in the specified format
                    var resultElements = document.querySelectorAll('div.col-sm-8 p.form-control-static');
                    var results = [];
                    
                    for (var i = 0; i < resultElements.length; i++) {
                        var text = resultElements[i].textContent.trim();
                        if (text) {
                            results.push(text);
                        }
                    }
                    
                    if (results.length > 0) {
                        return JSON.stringify(results);
                    } else {
                        return "NO_RESULTS";
                    }
                } catch(e) {
                    return "ERROR: " + e.message;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(checkResultScript) { result ->
            try {
                // Clean up the result string
                val cleanResult = result.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")

                if (cleanResult == "NO_RESULTS") {
                    // Sessizce devam et - bilgi mesajı gösterme
                    return@evaluateJavascript
                }

                if (cleanResult.startsWith("ERROR")) {
                    // Sessizce devam et - hata mesajı gösterme
                    return@evaluateJavascript
                }

                // Parse JSON result
                val jsonArray = org.json.JSONArray(cleanResult)

                if (jsonArray.length() > 0) {
                    val foundSerialNumber = jsonArray.getString(0)

                    runOnUiThread {
                        // Update the srNo EditText with the found value
                        binding.srNo.setText(foundSerialNumber)
                    }
                }
            } catch (e: Exception) {
                // Hata durumunda sessizce devam et
            }
        }
    }

    /**
     * Override onLowMemory to handle low memory conditions
     */
    /**
     * Gelişmiş form algılama ve giriş alanlarını hızlı bulma (SzuTest gibi özel siteler için)
     */
    private fun injectEnhancedFormDetection(webView: TabWebView) {

        // Form algılama için deneme sayısını takip et
        var formDetectionAttempts = 0
        val MAX_FORM_DETECTION_ATTEMPTS = 3

        // Form algılama işlevini tanımla - yeniden deneme mekanizması ile
        val tryFormDetection = object : Runnable {
            override fun run() {
                formDetectionAttempts++

                // Eğer maksimum deneme sayısına ulaşıldıysa daha fazla bekleme süresi ekle
                if (formDetectionAttempts > MAX_FORM_DETECTION_ATTEMPTS) {
                    return
                }

        // 250ms sonra form algılamayı başlat - DOM'un yüklenmesi için bekle
        Handler(Looper.getMainLooper()).postDelayed({
            val script = """
            (function() {
                try {
                    // Running enhanced form detection
                    
                    // Formlara otomatik key ekle
                    var forms = document.querySelectorAll('form');
                    forms.forEach(function(form, formIndex) {
                        form.setAttribute('data-tkf-form-index', formIndex);
                        // Found form
                    });
                    
                    // Tüm giriş elemanlarını tara ve kategorize et
                    var allInputs = Array.from(document.querySelectorAll('input, textarea, select'));
                    var visibleInputs = [];
                    
                    // Öncelikli giriş alanlarını belirle
                    var idMap = {};
                    var keyFields = [];
                    
                    // Özellikle ekipmanid, seri no, id vb. alanları öncelikle bul
                    var keyPatterns = [
                        /equip/i, /id$/i, /^id/i, /code/i, /number/i, /no$/i, 
                        /serial/i, /seri/i, /cihaz/i, /device/i
                    ];
                    
                    allInputs.forEach(function(input, index) {
                        // Görünürlük ve erişilebilirlik kontrollerini yap
                        var isVisible = input.offsetParent !== null && 
                                       !input.disabled && 
                                       !input.readOnly &&
                                       input.type !== 'hidden' &&
                                       (getComputedStyle(input).display !== 'none') &&
                                       (getComputedStyle(input).visibility !== 'hidden');
                                       
                        if (!isVisible) return;
                        
                        visibleInputs.push(input);
                        
                        // Öznitelikleri kontrol et
                        var inputId = input.id || '';
                        var inputName = input.name || '';
                        var inputPlaceholder = input.placeholder || '';
                        var labels = document.querySelectorAll('label[for="' + inputId + '"]');
                        var labelText = labels.length > 0 ? labels[0].textContent.trim() : '';
                        
                        // Bir key oluştur
                        var keyBase = inputName || inputId || inputPlaceholder || labelText || 'input_' + index;
                        var key = keyBase.replace(/[^a-zA-Z0-9_]/g, '_').toLowerCase();
                        input.setAttribute('data-tkf-key', key);
                        
                        // Arama kriterlerini birleştir 
                        var searchText = (inputId + ' ' + inputName + ' ' + inputPlaceholder + ' ' + labelText).toLowerCase();
                        
                        // Önemli giriş alanlarını işaretle
                        var isPriority = false;
                        keyPatterns.forEach(function(pattern) {
                            if (pattern.test(searchText)) {
                                isPriority = true;
                                keyFields.push(input);
                            }
                        });
                        
                        if (isPriority) {
                            input.setAttribute('data-tkf-priority', 'true');
                            // Found KEY field
                        }
                        
                        // Processing input field
                            
                        // ID tablosu için ekle
                        idMap[key] = {
                            element: input,
                            id: inputId,
                            name: inputName,
                            placeholder: inputPlaceholder,
                            label: labelText,
                            priority: isPriority
                        };
                    });
                    
                    // Otomatik odaklama davranışı kaldırıldı - kullanıcı artık kendisi dokunmalı
                    // Input alanlarının niteliklerini ekleyip etiketliyoruz ama otomatik odaklamıyoruz
                    if (keyFields.length > 0) {
                        console.log('TKF Browser: Key fields found but not auto-focusing');
                        // Form alanlarını hazırla ama odaklama
                    }
                    // Görünür giriş alanları için de aynı şekilde
                    else if (visibleInputs.length > 0) {
                        console.log('TKF Browser: Visible inputs found but not auto-focusing');
                        // Form alanlarını hazırla ama odaklama
                    }
                    
                    // DOM değişikliklerini izlemek için gözlemci oluştur
                    if (!window._tkfObserver) {
                        window._tkfObserver = new MutationObserver(function(mutations) {
                            mutations.forEach(function(mutation) {
                                if (mutation.addedNodes.length > 0) {
                                    // Yeni eklenen her düğümde input ara
                                    mutation.addedNodes.forEach(function(node) {
                                        if (node.querySelectorAll) {
                                            var newInputs = node.querySelectorAll('input, textarea, select');
                                            if (newInputs.length > 0) {
                                                // Found new inputs in DOM mutations
                                                // Yeni girişleri yeniden işle
                                                setTimeout(function() {
                                                    try {
                                                        newInputs.forEach(function(input, index) {
                                                            if (input.offsetParent !== null) {
                                                                var key = input.name || input.id || 'dynamic_input_' + Math.random().toString(36).substring(2, 9);
                                                                key = key.replace(/[^a-zA-Z0-9_]/g, '_').toLowerCase();
                                                                input.setAttribute('data-tkf-key', key);
                                                                // Added key for dynamic input
                                                            }
                                                        });
                                                    } catch(e) {
                                                        // Error processing new inputs
                                                    }
                                                }, 100);
                                            }
                                        }
                                    });
                                }
                            });
                        });
                        
                        window._tkfObserver.observe(document.body, {
                            childList: true,
                            subtree: true
                        });
                        // DOM mutation observer started
                    }
                    
                    // Özel aktivasyon fonksiyonu
                    window.activateInputField = function(key) {
                        if (idMap[key] && idMap[key].element) {
                            idMap[key].element.focus();
                            idMap[key].element.select();
                            // Activated field
                            return true;
                        }
                        return false;
                    };
                    
                    return JSON.stringify({
                        forms: forms.length,
                        inputs: allInputs.length,
                        visibleInputs: visibleInputs.length,
                        keyFields: keyFields.length,
                        idMapKeys: Object.keys(idMap)
                    });
                } catch(e) {
                    // Error in enhanced form detection
                    return JSON.stringify({error: e.message});
                }
            })();
            """.trimIndent()

            webView.evaluateJavascript(script) { result ->
                // Sonuç stringini temizle
                val cleanResult = result.trim().removeSurrounding("\"").replace("\\\"(", "\"(").replace("\\\"", "\"").replace("\\\\", "\\")

                try {
                    // JSON sonuç alındıysa işle
                    val jsonResult = org.json.JSONObject(cleanResult.toString())
                    val formCount = jsonResult.optInt("forms", 0)
                    val inputCount = jsonResult.optInt("inputs", 0)
                    val visibleCount = jsonResult.optInt("visibleInputs", 0)
                    val keyFieldCount = jsonResult.optInt("keyFields", 0)


                    // Form bulunduğunda ve alanlar tanımlandığında önerileri aktifleştir
                    if (formCount > 0 && visibleCount > 0) {
                        // 300ms sonra önerileri göstermeyi dene
                        Handler(Looper.getMainLooper()).postDelayed({
                            // Aktif tab ve webview hala geçerli mi kontrol et
                            val currentTab = viewModel.activeTab.value
                            val currentWebView = activeWebViews[currentTab?.id]

                            if (currentWebView == webView) {
                                // Öneri sistem aktivitesini kontrol et
                                webView.evaluateJavascript("""
                                (function() {
                                    var activeElement = document.activeElement;
                                    if (activeElement && (activeElement.tagName === 'INPUT' || activeElement.tagName === 'TEXTAREA')) {
                                        var key = activeElement.getAttribute('data-tkf-key') || '';
                                        if (key && window.SuggestionHandler) {
                                            window.SuggestionHandler.onInputFocused(key);
                                            return "SUGGESTION_ACTIVATED";
                                        }
                                    }
                                    return "NO_ACTIVE_INPUT";
                                })();
                                """.trimIndent()) { _ -> }
                            }
                        }, 300)
                    }
                } catch (e: Exception) {
                }
            }
        }, 250) // 250ms gecikme ile çalıştır

                // DOM tam olarak yüklenmemiş olabilir, JS hatasını kontrol et
                webView.evaluateJavascript("typeof document !== 'undefined' && !!document.body") { result ->
                    if (result?.contains("true") != true) {
                        // Daha uzun bir gecikme ile tekrar dene
                        Handler(Looper.getMainLooper()).postDelayed(this, 500)
                    }
                }
            }
        }

        // Form algılama işlevini başlat
        tryFormDetection.run()
    }

    override fun onLowMemory() {
        super.onLowMemory()

        // Öneri önbelleğini temizle
        suggestionManager.onLowMemory()

        // İhtiyaç duyulmayan webview'ları uykuya al
        val currentTab = viewModel.activeTab.value
        activeWebViews.forEach { (tabId, webView) ->
            if (currentTab?.id != tabId) {
                webView.hibernate()
                // Sekmeyi veritabanında da uyku moduna al
                viewModel.allTabs.value?.find { it.id == tabId }?.let { tab ->
                    if (!tab.isHibernated) {
                        viewModel.hibernateTab(tab)
                    }
                }
            }
        }

        // Web önbelleğini temizle (current tab hariç)
        activeWebViews.values.forEach { webView ->
            if (webView != activeWebViews[currentTab?.id]) {
                webView.clearCache(true)
            }
        }

        // Calculate memory usage
        val rt = Runtime.getRuntime()
        val usedMemInMB = (rt.totalMemory() - rt.freeMemory()) / 1048576L
        val maxHeapSizeInMB = rt.maxMemory() / 1048576L
        val availHeapSizeInMB = maxHeapSizeInMB - usedMemInMB

        // Bilgi mesajı göster
        Toast.makeText(this, "Düşük bellek: Önbellek temizleniyor", Toast.LENGTH_SHORT).show()
    }

    // Activity yaşam döngüsü yönetimi
    override fun onResume() {
        super.onResume()

        // Topraklama sorunu varsa form güncelleme
        if (DataHolder.hasTopraklamaSorunu) {

            // WebView'i al
            val currentTab = viewModel.activeTab.value
            val webView = currentTab?.let { activeWebViews[it.id] }

            if (webView != null) {
                // Seçeneği "Uygun Değil" olarak ayarla ve tickbox'ı işaretle
                val setTopraklamaSorunScript = """
                    (function() {
                        try {
                            console.log('Topraklama sorununu forma uygulama...');
                            // Topraklama sorusunu bul (soru 9)
                            var selectElement = document.querySelector('select[name="Questions[8].Option"]');
                            if (selectElement) {
                                console.log('Topraklama seçenek elementi bulundu');
                                
                                // "Uygun Değil" (değer: 2) seçeneğini seç
                                selectElement.value = "2";
                                
                                // Change event'i tetikle
                                var event = new Event('change', { bubbles: true });
                                selectElement.dispatchEvent(event);
                                
                                // Select elementi güncellemek için kullanılan Bootstrap selectpicker'ı güncelle
                                if (typeof $ !== 'undefined' && $('.selectpicker').length > 0) {
                                    $('.selectpicker').selectpicker('refresh');
                                }
                                
                                // Switchery elementi bul ve işaretle
                                var switcheryElements = document.querySelectorAll('.switchery');
                                for (var i = 0; i < switcheryElements.length; i++) {
                                    var parentLabel = switcheryElements[i].parentElement;
                                    var labelText = parentLabel ? parentLabel.textContent.trim() : '';
                                    
                                    if (labelText.indexOf('Topraklama direnç değeri ölçülemeyen') !== -1) {
                                        // Bu, bizim aranan elementimiz
                                        console.log('Topraklama checkbox elementi bulundu');
                                        
                                        // Checkbox'ı kontrol et
                                        var checkbox = parentLabel.querySelector('input[type="checkbox"]');
                                        if (checkbox && !checkbox.checked) {
                                            checkbox.checked = true;
                                            
                                            // Change event'i tetikle
                                            var event = new Event('change', { bubbles: true });
                                            checkbox.dispatchEvent(event);
                                            
                                            // Switchery görsel durumunu güncelle
                                            var switchery = switcheryElements[i];
                                            if (switchery && switchery.firstChild) {
                                                switchery.style.boxShadow = "rgb(100, 189, 99) 0px 0px 0px 0px inset";
                                                switchery.style.borderColor = "rgb(100, 189, 99)";
                                                switchery.style.backgroundColor = "rgb(100, 189, 99)";
                                                
                                                var handle = switchery.firstChild;
                                                handle.style.left = "13px";
                                                handle.style.backgroundColor = "rgb(255, 255, 255)";
                                            }
                                        }
                                    }
                                }
                                
                                return { status: 'success', message: 'Topraklama sorunu uygulandı' };
                            } else {
                                console.log('Topraklama seçenek elementi bulunamadı');
                                return { status: 'error', message: 'Topraklama formu bulunamadı' };
                            }
                        } catch(e) {
                            console.error('Topraklama sorunu uygulanırken hata:', e);
                            return { status: 'error', message: e.toString() };
                        }
                    })();
                """.trimIndent()

                webView.evaluateJavascript(setTopraklamaSorunScript) { result ->

                    // İşlem sonrası DataHolder'ı temizle
                    DataHolder.hasTopraklamaSorunu = false
                }
            } else {
            }
        }
    }
}
