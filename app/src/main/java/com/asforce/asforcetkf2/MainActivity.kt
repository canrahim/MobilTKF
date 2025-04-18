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
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.JsResult
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.widget.EditText
import android.widget.Toast
import com.asforce.asforcetkf2.suggestion.SuggestionManager
import com.asforce.asforcetkf2.util.DeviceManager
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
import com.asforce.asforcetkf2.util.TabResourceMonitor
import com.asforce.asforcetkf2.util.TKFDownloadManager
import com.asforce.asforcetkf2.viewmodel.TabViewModel
import com.asforce.asforcetkf2.webview.TabWebView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Tam ekran modu ve şeffaf gezinme çubuğu
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or 
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        
        // Bileşenleri başlat
        initializeTabComponents()
        setupTabRecyclerView()
        setupUrlInput()
        setupNavigationButtons()
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
        
        // URL kutusuna tıklanınca mevcut metni seçme ve kopyalama seçeneği
        binding.urlInput.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                (v as TextInputEditText).selectAll()
            }
        }
        
        // URL kutusuna uzun tıklama işlemi
        binding.urlInput.setOnLongClickListener {
            val url = binding.urlInput.text.toString()
            if (url.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("URL", url)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, R.string.url_copied, Toast.LENGTH_SHORT).show()
                true
            } else {
                false
            }
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
            
            webView.reload()
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
            
            // Log kaydı ekle
            Timber.d("Added new tab with ID: $newTabId, URL: $url")
        }
    }
    
    private fun closeTab(tab: Tab) {
        // Clean up the WebView
        activeWebViews[tab.id]?.let { webView ->
            webView.cleanup()
            binding.webviewContainer.removeView(webView)
            activeWebViews.remove(tab.id)
        }
        
        viewModel.closeTab(tab)
    }
    
    private fun selectTab(tab: Tab) {
        viewModel.setActiveTab(tab)
    }
    
    private fun updateActiveTab(tab: Tab) {
        // Update URL bar
        binding.urlInput.setText(tab.url)
        
        // Show active tab's WebView, hide others
        for ((tabId, webView) in activeWebViews) {
            webView.visibility = if (tabId == tab.id) View.VISIBLE else View.GONE
            
            // Update WebView state based on hibernation
            if (tabId != tab.id && tab.isHibernated) {
                webView.hibernate()
            } else if (tabId == tab.id && tab.isHibernated) {
                webView.wakeUp()
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
                            
                            console.log('TKF Browser: Touched element: ' + element.tagName + 
                                (element.id ? '#' + element.id : '') + 
                                (element.className ? '.' + element.className : ''));
                            
                            // Check if touched element is an input field
                            var isInput = element.tagName === 'INPUT' || element.tagName === 'TEXTAREA';
                            var inputType = isInput ? (element.type || 'text').toLowerCase() : '';
                            
                            // If it's an input field, handle with enhanced focus logic
                            if (isInput) {
                                console.log('TKF Browser: Touch detected on input field: ' + element.tagName + 
                                    ' type=' + inputType);
                                
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
                            console.error('TKF Browser: Error in touch handling:', e);
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
                                    Timber.e(e, "[INPUT] JSON parsing error: $cleanResult")
                                    // Hata durumunda normal klavyeyi göster
                                    showKeyboard(v)
                                }
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "[INPUT] Error processing touch event result")
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
                    Timber.d("[SZUTEST] Loading SzuTest page, enabling enhanced form detection")
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
                        Timber.d("[WEBVIEW] Special site detected, applying enhanced form detection")
                        injectEnhancedFormDetection(webView)
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
            Timber.e("WebView error: code=$errorCode, description=$description, url=$failingUrl")
            
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
        
        webView.loadUrl(url)
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
        
        // Show dialog menu
        MaterialAlertDialogBuilder(this)
            .setTitle("Ana Menü")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> loadUrl("https://www.google.com")
                    1 -> Toast.makeText(this, "Favoriler henüz uygulanmadı", Toast.LENGTH_SHORT).show()
                    2 -> Toast.makeText(this, "Geçmiş henüz uygulanmadı", Toast.LENGTH_SHORT).show()
                    3 -> Toast.makeText(this, "İndirilenler henüz uygulanmadı", Toast.LENGTH_SHORT).show()
                    4 -> clearSuggestionCache()
                    5 -> showMenu() // Standart ayarlar menüsünü göster
                }
            }
            .show()
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
        
        // Show dialog menu
        MaterialAlertDialogBuilder(this)
            .setTitle("Menü")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> handleKacakAkim()
                    1 -> handlePanoFonksiyon()
                    2 -> handleTopraklama()
                    3 -> handleTermalKamera()
                }
            }
            .show()
    }
    
    // Yeni menü işlevleri
    private fun handleKacakAkim() {
        // Kaçak Akım aktivitesini başlat
        val intent = Intent(this, LeakageControlActivity::class.java)
        startActivity(intent)
    }
    
    private fun handlePanoFonksiyon() {
        Toast.makeText(this, "Pano Fonksiyon işlevi seçildi", Toast.LENGTH_SHORT).show()
        // Pano Fonksiyon işlevselliği buraya eklenecek
    }
    
    private fun handleTopraklama() {
        Toast.makeText(this, "Topraklama işlevi seçildi", Toast.LENGTH_SHORT).show()
        // Topraklama işlevselliği buraya eklenecek
    }
    
    private fun handleTermalKamera() {
        Toast.makeText(this, "Termal Kamera işlevi seçildi", Toast.LENGTH_SHORT).show()
        // Termal Kamera işlevselliği buraya eklenecek
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
                                                
                                                Timber.d("Auto-hibernated tab ${tab.id} due to high resource usage")
                                            }
                                        }
                                } catch (e: Exception) {
                                    Timber.e(e, "Error monitoring resources for tab $tabId")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error in resource monitoring")
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
            Timber.e(e, "Klavye gösterme hatası")
        }
    }
    
    override fun onBackPressed() {
        // Handle back navigation in the active WebView
        val currentTab = viewModel.activeTab.value
        val webView = currentTab?.let { activeWebViews[it.id] }
        
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onDestroy() {
        // Clean up WebViews
        activeWebViews.values.forEach { it.cleanup() }
        activeWebViews.clear()
        
        // Clean up suggestion manager
        suggestionManager.cleanup()
        
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
        
        // Kamera seçeneği için tıklama dinleyicisi
        cameraCard.setOnClickListener {
            dialog.dismiss()
            takePictureFromCamera()
        }
        
        // Galeri seçeneği için tıklama dinleyicisi
        galleryCard.setOnClickListener {
            dialog.dismiss()
            selectImageFromGallery()
        }
        
        // Dosyalar seçeneği için tıklama dinleyicisi
        filesCard.setOnClickListener {
            dialog.dismiss()
            openFileChooser()
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
        
        // Dialog'u göster
        dialog.show()
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
        
        // Kamera ile fotoğraf çekme işlemi
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { intent ->
            intent.resolveActivity(packageManager)?.also {
                // Geçici dosya oluştur
                val photoFile = try {
                    createImageFile()
                } catch (e: Exception) {
                    Timber.e(e, "Kamera için geçici dosya oluşturulamadı")
                    null
                }
                
                photoFile?.also {
                    currentPhotoUri = FileProvider.getUriForFile(
                        this,
                        "com.asforce.asforcetkf2.fileprovider",
                        it
                    )
                    
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
                    cameraLauncher.launch(intent)
                }
            } ?: run {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = null
                Toast.makeText(this, R.string.camera_app_not_found, Toast.LENGTH_SHORT).show()
            }
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
            Timber.e(e, "Failed to open file chooser")
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
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        
        val image = java.io.File.createTempFile(
            imageFileName,  /* önek */
            ".jpg",         /* uzantı */
            storageDir      /* dizin */
        )
        
        // Görüntü dosya yolu
        cameraPhotoPath = image.absolutePath
        return image
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
                """) { result ->
                    Timber.d("[SUGGESTION] Keyboard visible, refreshing suggestions: $result")
                }
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
    private fun setupActionButtons() {
        // Ekipman Listesi butonu - btn_left_1
        binding.btnLeft1.setOnClickListener {
            loadUrl("https://app.szutest.com.tr/EXT/PKControl/EquipmentList")
        }

        // Kontrol Listesi butonu - btn_left_2
        binding.btnLeft2.setOnClickListener {
            // Sadece URL'yi yükle, özel form desteği yok
            loadUrl("https://app.szutest.com.tr/EXT/PKControl/EKControlList")
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
        Timber.d("[TEST] Starting suggestion insertion test")
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
                        Timber.d("[TEST] Found input element: $info with key: $key")
                        
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
                            
                            evaluateJavascript(setValueScript) { jsResult ->
                                Timber.d("[TEST] JavaScript set value result: $jsResult")
                            }
                        }
                        
                        // Show toast notification
                        Toast.makeText(this, "Test suggestion inserted: $testValue", Toast.LENGTH_SHORT).show()
                    } else {
                        val info = jsonObj.getString("info")
                        Timber.d("[TEST] No input element found: $info")
                        Toast.makeText(this, "No input element found to test", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[TEST] Error parsing input element info")
                    Toast.makeText(this, "Error testing suggestions: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Timber.d("[TEST] No active WebView found")
            Toast.makeText(this, "No active WebView to test", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Override onLowMemory to handle low memory conditions
     */
    /**
     * Gelişmiş form algılama ve giriş alanlarını hızlı bulma (SzuTest gibi özel siteler için)
     */
    private fun injectEnhancedFormDetection(webView: TabWebView) {
        Timber.d("[FORM] Injecting enhanced form detection")
        
        // Form algılama için deneme sayısını takip et
        var formDetectionAttempts = 0
        val MAX_FORM_DETECTION_ATTEMPTS = 3
        
        // Form algılama işlevini tanımla - yeniden deneme mekanizması ile
        val tryFormDetection = object : Runnable {
            override fun run() {
                formDetectionAttempts++
                Timber.d("[FORM] Enhanced form detection attempt $formDetectionAttempts")
                
                // Eğer maksimum deneme sayısına ulaşıldıysa daha fazla bekleme süresi ekle
                if (formDetectionAttempts > MAX_FORM_DETECTION_ATTEMPTS) {
                    Timber.w("[FORM] Maximum form detection attempts reached")
                    return
                }
        
        // 250ms sonra form algılamayı başlat - DOM'un yüklenmesi için bekle
        Handler(Looper.getMainLooper()).postDelayed({
            val script = """
            (function() {
                try {
                    console.log('TKF Browser: Running enhanced form detection...');
                    
                    // Formlara otomatik key ekle
                    var forms = document.querySelectorAll('form');
                    forms.forEach(function(form, formIndex) {
                        form.setAttribute('data-tkf-form-index', formIndex);
                        console.log('Found form #' + formIndex + (form.id ? ' id=' + form.id : ''));
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
                            console.log('Found KEY field: ' + key + ' (' + searchText + ')');
                        }
                        
                        console.log('Input #' + index + ': ' + input.tagName + 
                            (inputId ? '#'+inputId : '') + 
                            (inputName ? ' name='+inputName : '') + 
                            ' → key=' + key);
                            
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
                    
                    // Öncelikli alanlardan biri aktif hale getir (varsa)
                    if (keyFields.length > 0) {
                        console.log('Activating key field: ' + keyFields[0].getAttribute('data-tkf-key'));
                        keyFields[0].focus();
                        keyFields[0].select();
                        
                        // SuggestionHandler'a bildir
                        if (window.SuggestionHandler) {
                            setTimeout(function() {
                                var key = keyFields[0].getAttribute('data-tkf-key') || '';
                                window.SuggestionHandler.onInputFocused(key);
                            }, 100);
                        }
                    } 
                    // Yoksa ilk görünür girişi odakla
                    else if (visibleInputs.length > 0) {
                        console.log('No key fields found, activating first visible input');
                        visibleInputs[0].focus();
                        visibleInputs[0].select();
                        
                        // SuggestionHandler'a bildir
                        if (window.SuggestionHandler) {
                            setTimeout(function() {
                                var key = visibleInputs[0].getAttribute('data-tkf-key') || '';
                                window.SuggestionHandler.onInputFocused(key);
                            }, 100);
                        }
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
                                                console.log('Found ' + newInputs.length + ' new inputs in DOM mutations');
                                                // Yeni girişleri yeniden işle
                                                setTimeout(function() {
                                                    try {
                                                        newInputs.forEach(function(input, index) {
                                                            if (input.offsetParent !== null) {
                                                                var key = input.name || input.id || 'dynamic_input_' + Math.random().toString(36).substring(2, 9);
                                                                key = key.replace(/[^a-zA-Z0-9_]/g, '_').toLowerCase();
                                                                input.setAttribute('data-tkf-key', key);
                                                                console.log('Added key for dynamic input: ' + key);
                                                            }
                                                        });
                                                    } catch(e) {
                                                        console.error('Error processing new inputs:', e);
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
                        console.log('DOM mutation observer started');
                    }
                    
                    // Özel aktivasyon fonksiyonu
                    window.activateInputField = function(key) {
                        if (idMap[key] && idMap[key].element) {
                            idMap[key].element.focus();
                            idMap[key].element.select();
                            console.log('Activated field: ' + key);
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
                    console.error('Error in enhanced form detection:', e);
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
                    
                    Timber.d("[FORM] Enhanced detection results: Forms=$formCount, Inputs=$inputCount, Visible=$visibleCount, KeyFields=$keyFieldCount")
                    
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
                                """.trimIndent()) { suggestionResult ->
                                    Timber.d("[FORM] Suggestion check: $suggestionResult")
                                }
                            }
                        }, 300)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[FORM] Error parsing enhanced form detection result: $cleanResult")
                }
            }
        }, 250) // 250ms gecikme ile çalıştır
                
                // DOM tam olarak yüklenmemiş olabilir, JS hatasını kontrol et
                webView.evaluateJavascript("typeof document !== 'undefined' && !!document.body") { result ->
                    if (result?.contains("true") != true) {
                        Timber.w("[FORM] DOM not ready yet, retrying form detection later...")
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
        Timber.w("[MEMORY] Low memory condition detected")
        
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
        
        // Log memory usage
        val rt = Runtime.getRuntime()
        val usedMemInMB = (rt.totalMemory() - rt.freeMemory()) / 1048576L
        val maxHeapSizeInMB = rt.maxMemory() / 1048576L
        val availHeapSizeInMB = maxHeapSizeInMB - usedMemInMB
        Timber.w("[MEMORY] Memory usage: $usedMemInMB MB used, $availHeapSizeInMB MB available, $maxHeapSizeInMB MB max")
        
        // Bilgi mesajı göster
        Toast.makeText(this, "Düşük bellek: Önbellek temizleniyor", Toast.LENGTH_SHORT).show()
    }
}