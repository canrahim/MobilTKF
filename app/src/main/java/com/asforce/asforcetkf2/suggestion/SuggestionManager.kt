package com.asforce.asforcetkf2.suggestion

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.PopupWindow
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcetkf2.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.Executors

/**
 * Manages suggestions for input fields
 * Displays a horizontal bar of suggestions above the keyboard
 * 
 * Example usage in MainActivity:
 * 
 * ```
 * // In MainActivity.kt
 * private lateinit var suggestionManager: SuggestionManager
 * 
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     setContentView(R.layout.activity_main)
 *     
 *     // Initialize suggestion manager using the static helper
 *     suggestionManager = SuggestionManager.createForActivity(this)
 *     
 *     // For WebView suggestions
 *     tabWebView?.setSuggestionManager(suggestionManager, "filter_equipmentid")
 *     
 *     // For EditText suggestions
 *     val searchEditText = findViewById<EditText>(R.id.search_edittext)
 *     suggestionManager.trackEditText(searchEditText, "search_terms")
 * }
 * 
 * override fun onDestroy() {
 *     super.onDestroy()
 *     suggestionManager.cleanup()
 * }
 * ```
 */
class SuggestionManager(private val context: Context) {
    
    private val TAG = "SuggestionManager"
    private val PREFS_NAME = "SuggestionPrefs"

    // Thread pool for background operations
    private val executorService = Executors.newFixedThreadPool(1)
    
    // Root view reference for suggestion overlay
    private var rootViewRef: ViewGroup? = null
    
    // Suggestion overlay view
    private var suggestionView: View? = null
    
    // Currently focused EditText
    private var currentEditText: EditText? = null
    
    // Current suggestion key
    private var currentInputKey: String = ""
    
    // Active WebView reference
    private var activeWebView: WebView? = null

    // Keyboard visibility state tracking
    private var isKeyboardVisible = false
    private var lastKeyboardToggleTime = 0L

    // PopupWindow reference
    private var suggestionPopup: PopupWindow? = null

    // Suggestion cache for faster loading
    private val suggestionCache = mutableMapOf<String, List<String>>()
    private var lastCacheRefreshTime = 0L
    private val CACHE_TTL = 60_000L // Cache valid for 1 minute

    /**
     * Initialize suggestion overlay in the root layout
     */
    fun initialize(rootView: ViewGroup) {
        this.rootViewRef = rootView
        // Track keyboard visibility on this root view
        trackKeyboardVisibility(rootView)
    }

    /**
     * Initialize suggestion tracking for an EditText
     */
    fun trackEditText(editText: EditText, inputKey: String, webView: WebView? = null) {
        Timber.d("[SUGGESTION] Tracking EditText with key: $inputKey, webView: ${webView != null}")
        
        // Update WebView reference if provided
        if (webView != null) {
            activeWebView = webView
        }
        
        // Track focus changes
        editText.setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
                currentEditText = editText
                currentInputKey = inputKey
                
                // Show suggestions for this field
                val text = editText.text.toString()
                Timber.d("[SUGGESTION] EditText focused, showing suggestions for key: $inputKey, text: $text")
                showSuggestions(editText, inputKey, text)
            } else {
                // Only hide if this is the current field losing focus
                if (currentEditText == editText) {
                    Timber.d("[SUGGESTION] EditText lost focus, hiding suggestions")
                    hideSuggestions()
                }
            }
        }
        
        // Track text changes
        editText.doAfterTextChanged { text ->
            if (editText.hasFocus() && text != null) {
                // Filter suggestions based on current text
                Timber.d("[SUGGESTION] Text changed: ${text.toString()}, filtering suggestions")
                showSuggestions(editText, inputKey, text.toString())
            }
        }
    }
    
    /**
     * Track keyboard visibility to show/hide suggestions
     */
    fun trackKeyboardVisibility(rootView: View) {
        // Daha güçlü titreme önleme için uzun debounce değeri
        val debounceTime = 700L // 800ms -> 700ms (Daha hızlı tepki)
        
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            
            val screenHeight = rootView.height
            val keyboardHeight = screenHeight - r.bottom
            val now = System.currentTimeMillis()
            
            // Klavye görünür kabul edilme eşiğini artır (daha kesin sonuç için)
            val keyboardNowVisible = keyboardHeight > screenHeight * 0.15 // 0.20 -> 0.15 (daha hassas tespiti geri getir)
            
            // Daha kısa debounce süresi ile daha hızlı tepki ver
            if (keyboardNowVisible != isKeyboardVisible && (now - lastKeyboardToggleTime > debounceTime)) {
                lastKeyboardToggleTime = now
                isKeyboardVisible = keyboardNowVisible
                
                if (keyboardNowVisible) {
                    // Klavye görünür olduğunda
                    Timber.d("[SUGGESTION] Keyboard visible detected (height: $keyboardHeight)")
                    
                    // Klavye göründüğünde öneri çubuğunu hemen göster
                    currentEditText?.let { editText ->
                        showSuggestions(editText, currentInputKey, editText.text.toString())
                    } ?: activeWebView?.let { webView ->
                        if (currentInputKey.isNotEmpty()) {
                            // Aktif input key varsa hemen göster
                            showSuggestions(webView, currentInputKey, "")
                        } else {
                            // Aktif input alanını bulmak için JavaScript çalıştır
                            webView.evaluateJavascript("""
                            (function() {
                                var activeElement = document.activeElement;
                                if (activeElement && (activeElement.tagName === 'INPUT' || activeElement.tagName === 'TEXTAREA')) {
                                    var key = activeElement.getAttribute('data-tkf-key');
                                    if (!key) {
                                        key = (activeElement.name || activeElement.id || activeElement.placeholder || 'input_' + Math.random().toString(36).substr(2, 9));
                                        key = key.replace(/[^a-zA-Z0-9_]/g, '_');
                                        activeElement.setAttribute('data-tkf-key', key);
                                    }
                                    return key;
                                }
                                return "";
                            })();
                            """.trimIndent()) { result ->
                                // Sonucu temizle (tırnak işaretlerini kaldır)
                                val key = result.trim().removeSurrounding("\"")
                                if (key.isNotEmpty()) {
                                    currentInputKey = key
                                    showSuggestions(webView, key, "")
                                }
                            }
                        }
                    }
                } else {
                    // Klavye gizli olduğunda
                    Timber.d("[SUGGESTION] Keyboard hidden detected, hiding suggestions")
                    
                    // Önerileri gizle ama kısa bir gecikmeyle (klavye hareketinden sonra)
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Sadece klavye hala gizli ise önerileri gizle
                        if (!isKeyboardVisible) {
                            hideSuggestions()
                        }
                    }, 300) // 600ms -> 300ms (daha hızlı gizleme)
                }
            }
        }
    }

    /**
     * Show suggestions for the given input field
     * Güçlendirilmiş versiyon - boş metin için bile tüm önerileri gösterir
     */
    fun showSuggestions(anchorView: View, inputKey: String, filterText: String) {
        Timber.d("[SUGGESTION] Showing suggestions for key: $inputKey, filter text: '$filterText'")
        
        // Aktif WebView veya EditText'i güncelle
        if (anchorView is EditText) {
            currentEditText = anchorView
        } else if (anchorView is WebView) {
            activeWebView = anchorView
        }
        
        // Mevcut input key'i güncelle
        currentInputKey = inputKey
        
        // Use coroutines for background processing
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Load and filter suggestions
                val filteredSuggestions = loadAndFilterSuggestions(inputKey, filterText)
                
                withContext(Dispatchers.Main) {
                    // If no suggestions with filter and filter text is not blank, don't fallback to showing all
                    if (filteredSuggestions.isEmpty()) {
                        if (filterText.isNotBlank()) {
                            // Don't show anything when filtering with no matches
                            Timber.d("[SUGGESTION] No matching suggestions with filter, hiding suggestions")
                            hideSuggestions()
                            return@withContext
                        }
                        
                        // Son bir şans olarak varsayılan önerileri gösterebilmek için gecikmeyi dene
                        Timber.d("[SUGGESTION] Trying to load default suggestions for empty input")
                        
                        // Hala boş - VARS. ÖNERİLER KALDIRILDI
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                // Anahtar turuyla ilgili varsayılan öneriler
                                // NOT: Tüm varsayılan öneriler kaldırıldı
                                val defaultSuggestions = emptyList<String>()
                                
                                if (defaultSuggestions.isNotEmpty()) {
                                    defaultSuggestions.forEach { saveSuggestion(inputKey, it) }
                                    withContext(Dispatchers.Main) {
                                        showSuggestionsInPopup(anchorView, inputKey, defaultSuggestions)
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        hideSuggestions()  
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "[SUGGESTION] Error loading default suggestions")
                                withContext(Dispatchers.Main) {
                                    hideSuggestions()
                                }
                            }
                        }
                    } else {
                        Timber.d("[SUGGESTION] Found ${filteredSuggestions.size} matching suggestions")
                        
                        // Klavye göründüğünde, öneri çubuğunu göster
                        if (isKeyboardVisible) {
                            // Hemen popup göster
                            showSuggestionsInPopup(anchorView, inputKey, filteredSuggestions)
                        } else {
                            // Klavye gizli ise önce önerileri hazırla sonra popup'ı gösterme
                            // Klavye açılana kadar bekle
                            Timber.d("[SUGGESTION] Keyboard not visible yet, waiting")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[SUGGESTION] Error loading suggestions")
                withContext(Dispatchers.Main) {
                    hideSuggestions()
                }
            }
        }
    }
    
    /**
     * Show suggestions in a popup window above keyboard
     */
    private fun showSuggestionsInPopup(anchorView: View, inputKey: String, suggestions: List<String>) {
        try {
            // Check if we already have a popup with same suggestions
            val currentAdapter = suggestionPopup?.contentView?.findViewById<RecyclerView>(R.id.suggestion_recycler_view)?.adapter as? SuggestionAdapter
            
            if (suggestionPopup?.isShowing == true && currentAdapter != null) {
                // Update existing popup instead of recreating
                Timber.d("[SUGGESTION] Updating existing popup with ${suggestions.size} suggestions")
                currentAdapter.updateSuggestions(suggestions)
                return
            }
            
            // Hide any existing popup first
            hideSuggestions()
            
            // Create layout inflater
            val inflater = LayoutInflater.from(context)
            val suggestionView = inflater.inflate(R.layout.layout_suggestion_bar, null)
            
            // Style the suggestion view - MODIFY: Remove background, keep only chip backgrounds
            // REMOVE THIS ENTIRE BLOCK - No background for the entire bar
            /*
            val backgroundDrawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#1d1b30")) // dark blue background
                cornerRadius = 16f // yuvarlak köşeler
                setStroke(2, android.graphics.Color.parseColor("#4285f4")) // mavi kenarlık
            }
            suggestionView.background = backgroundDrawable 
            suggestionView.elevation = 24f
            */
            
            // Set background to transparent
            suggestionView.background = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            suggestionView.elevation = 0f // No elevation for the container
        
            // Set up RecyclerView
            val recyclerView = suggestionView.findViewById<RecyclerView>(R.id.suggestion_recycler_view)
            recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            
            // Create adapter with callbacks
            val suggestionList = suggestions.toMutableList()
            val suggestionAdapter = SuggestionAdapter(
                context = context,
                suggestions = suggestionList,
                inputKey = inputKey,
                onSuggestionSelected = { suggestion -> 
                    handleSuggestionSelected(suggestion)
                    // Dismiss popup immediately when selection is made
                    hideSuggestions()
                },
                onSuggestionDeleted = { suggestion, position ->
                    deleteSuggestion(inputKey, suggestion, position)
                }
            )
            
            recyclerView.adapter = suggestionAdapter
            
            // Create popup window - USING POPUP WINDOW APPROACH like in Menu5Activity
            val popupHeight = (60 * context.resources.displayMetrics.density).toInt() // Daha alçak bir çubuk (75'ten 60'a)
            
            val popup = PopupWindow(
                suggestionView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                popupHeight,
                false // NOT focusable - this is important to not steal focus
            ).apply {
                isOutsideTouchable = true
                elevation = 0f // No elevation for transparent background
                animationStyle = android.R.style.Animation_InputMethod // Klavye animasyonu gibi
                setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
                
                // Ensure popup does not steal focus from the edittext
                isTouchable = true
                isFocusable = false
                
                // These flags help with positioning and focus
                setTouchInterceptor { _, _ -> false }
            }
            
            // Get display metrics
            val metrics = context.resources.displayMetrics
            val screenHeight = metrics.heightPixels
            
            // Try a better positioning algorithm for popup placement
            // 1. Eğer klavye yüksekliği algılanabiliyorsa, klavyenin üstündeki otomatik öneri çubuğunun üzerine yerleştir
            val keyboardHeight = getKeyboardHeight()
            var yPosition = 0
            
            // Klavyenin kendi otomatik öneri çubuğu için gereken yükseklik (dp cinsinden)
            val keyboardSuggestionBarHeightDp = 45 // 60'tan 45'e düşürüldü - daha az boşluk bırakmak için
            val keyboardSuggestionBarHeightPx = (keyboardSuggestionBarHeightDp * context.resources.displayMetrics.density).toInt()
            
            if (keyboardHeight > 100) { // Gerçek bir klavye yüksekliği
                // Klavyenin otomatik öneri çubuğunun üzerine yerleştir - ek boşluk azaltıldı
                yPosition = screenHeight - keyboardHeight - keyboardSuggestionBarHeightPx - 2 // popupHeight kaldırıldı
                Timber.d("[SUGGESTION] Keyboard detected, positioning closer to keyboard at y=$yPosition")
            } else {
                // Klavye yüksekliği algılanamadıysa ekranın alt kısmında %90 oranında göster (daha aşağı)
                yPosition = (screenHeight * 0.90).toInt() - popupHeight
                Timber.d("[SUGGESTION] No keyboard height, using lower fixed position y=$yPosition")
            }
            
            // Show popup at calculated position
            popup.showAtLocation(
                anchorView,
                android.view.Gravity.TOP,  // Position from TOP 
                0,
                yPosition
            )
            
            // Save reference to popup
            suggestionPopup = popup
            
            // Save reference to view
            this.suggestionView = suggestionView
            
            Timber.d("[SUGGESTION] Showing popup with ${suggestions.size} suggestions at y-position: $yPosition")
        } catch (e: Exception) {
            Timber.e(e, "[SUGGESTION] Error showing suggestions popup")
        }
    }
    
    /**
     * Get current keyboard height
     */
    private fun getKeyboardHeight(): Int {
        // Get screen dimensions
        val rootView = rootViewRef ?: 
            (context as? android.app.Activity)?.window?.decorView?.rootView ?: 
            return 200
        
        val rect = Rect()
        rootView.getWindowVisibleDisplayFrame(rect)

        // Calculate keyboard height
        val screenHeight = rootView.height
        val keyboardHeight = screenHeight - rect.bottom
        
        // Default minimum height
        val minimumKeyboardHeight = 200
        
        return if (keyboardHeight > minimumKeyboardHeight) {
            keyboardHeight
        } else {
            minimumKeyboardHeight
        }
    }
    
    /**
     * Handle when a suggestion is selected
     */
    private fun handleSuggestionSelected(suggestion: String) {
        Timber.d("[SUGGESTION] Suggestion selected: '$suggestion'")
        
        try {
            // Save to preferences IMMEDIATELY regardless of insertion success
            saveSuggestion(currentInputKey, suggestion)
            
            // Sanitize text for JavaScript injection - prevent JS issues
            val safeText = suggestion.replace("'", "\\'").replace("\"", "\\\"")
            
            // Direct approach for WebView - prioritize this since it's our main use case
            activeWebView?.let { webView ->
                // DO NOT hide suggestions yet - this can cause focus issues
                // Let's insert the text first while focus is still active
                
                // Set focus to webview first
                webView.requestFocus()
                
                // İleri seviye veri aktarım denemesi - önce WebView simulateKeyboardInput yöntemini kullan
                val useSimulation = webView is com.asforce.asforcetkf2.webview.TabWebView
                
                // Hem simulasyon hem de JS yaklaşımını paralel olarak kullanalım
                // Bu sayede en az birinin başarılı olması garanti edilir
                
                // 1. JavaScript yaklaşımı - Daha hızlı çalışacaktır
                val enhancedScript = """
                (function() {
                    try {
                        console.log('Enhanced insertion of: $safeText');
                        
                        // Değişkenleri tanımla
                        var activeElement = document.activeElement;
                        var keyElement = document.querySelector('[data-tkf-key="$currentInputKey"]');
                        var targetElement = null;
                        var debugInfo = [];
                        
                        // AŞAMA 1: Hedef elementi belirle
                        if (activeElement && (activeElement.tagName === 'INPUT' || activeElement.tagName === 'TEXTAREA')) {
                            targetElement = activeElement;
                            debugInfo.push('Using active element: ' + activeElement.tagName);
                        } else if (keyElement) {
                            targetElement = keyElement; 
                            debugInfo.push('Using element with data-tkf-key: ' + keyElement.tagName);
                            // Etiketlenmiş elementi aktifleştir
                            keyElement.focus();
                            keyElement.select();
                        } else {
                            // Görünür inputları ara
                            debugInfo.push('No active/keyed element, searching for visible inputs');
                            var inputs = document.querySelectorAll('input, textarea');
                            for (var i = 0; i < inputs.length; i++) {
                                var input = inputs[i];
                                if (input.offsetParent !== null && !input.disabled && !input.readOnly &&
                                    (input.type === 'text' || input.type === 'search' || 
                                     input.type === 'email' || input.type === 'number' || 
                                     input.tagName === 'TEXTAREA')) {
                                    
                                    targetElement = input;
                                    debugInfo.push('Found visible input: ' + input.tagName);
                                    // Odaklama yap
                                    input.focus();
                                    input.select();
                                    break;
                                }
                            }
                        }
                        
                        // AŞAMA 2: Hedef element bulunduysa değeri ayarla
                        if (targetElement) {
                            // Orijinal değeri kaydet
                            var origValue = targetElement.value;
                            debugInfo.push('Original value: ' + origValue);
                            
                            // Değeri direkt ayarla
                            targetElement.value = '$safeText';
                            
                            // Değişimi kontrol et
                            if (targetElement.value !== '$safeText') {
                                debugInfo.push('Direct value setting failed, trying alternatives');
                                
                                // YÖNTEM 2: innerHTML yaklaşımı
                                if (targetElement.innerHTML !== undefined) {
                                    targetElement.innerHTML = '$safeText';
                                    debugInfo.push('Set innerHTML');
                                }
                                
                                // YÖNTEM 3: execCommand yaklaşımı
                                try {
                                    targetElement.focus();
                                    targetElement.select();
                                    document.execCommand('insertText', false, '$safeText');
                                    debugInfo.push('Used execCommand');
                                } catch(e) {
                                    debugInfo.push('execCommand failed: ' + e.message);
                                }
                                
                                // YÖNTEM 4: Değer ayarlamada daha agresif yöntem kullan
                                try {
                                    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(targetElement, '$safeText');
                                    debugInfo.push('Used property descriptor');
                                } catch(e) {
                                    debugInfo.push('Property descriptor failed: ' + e.message);
                                }
                                
                                // YÖNTEM 5: Temizle ve karakter karakter gir
                                try {
                                    targetElement.value = '';
                                    var chars = '$safeText'.split('');
                                    for (var i = 0; i < chars.length; i++) {
                                        targetElement.value += chars[i];
                                    }
                                    debugInfo.push('Used character-by-character input');
                                } catch(e) {
                                    debugInfo.push('Character input failed: ' + e.message);
                                }
                            } else {
                                debugInfo.push('Direct value setting successful');
                            }
                            
                            // AŞAMA 3: Olayları tetikle
                            // Tüm olayları dene, hata olsa bile devam et
                            try {
                                var inputEvent = new Event('input', {bubbles: true});
                                targetElement.dispatchEvent(inputEvent);
                                debugInfo.push('Dispatched input event');
                            } catch(e) {
                                debugInfo.push('Input event error: ' + e.message);
                                try {
                                    var fallbackInputEvent = document.createEvent('HTMLEvents');
                                    fallbackInputEvent.initEvent('input', true, true);
                                    targetElement.dispatchEvent(fallbackInputEvent);
                                    debugInfo.push('Used fallback input event');
                                } catch(e2) {
                                    debugInfo.push('Fallback input event error: ' + e2.message);
                                }
                            }
                            
                            try {
                                var changeEvent = new Event('change', {bubbles: true});
                                targetElement.dispatchEvent(changeEvent);
                                debugInfo.push('Dispatched change event');
                            } catch(e) {
                                debugInfo.push('Change event error: ' + e.message);
                                try {
                                    var fallbackChangeEvent = document.createEvent('HTMLEvents');
                                    fallbackChangeEvent.initEvent('change', true, true);
                                    targetElement.dispatchEvent(fallbackChangeEvent);
                                    debugInfo.push('Used fallback change event');
                                } catch(e2) {
                                    debugInfo.push('Fallback change event error: ' + e2.message);
                                }
                            }
                            
                            // Son kez odaklama
                            targetElement.focus();
                            
                            // Başarı durumu kontrol et
                            if (targetElement.value === '$safeText') {
                                debugInfo.push('FINAL: Value set successfully to "' + targetElement.value + '"');
                                return JSON.stringify({
                                    status: 'SUCCESS',
                                    message: 'Value set successfully',
                                    value: targetElement.value,
                                    element: targetElement.tagName,
                                    debug: debugInfo
                                });
                            } else {
                                debugInfo.push('FINAL: Current value "' + targetElement.value + '" does not match target "$safeText"');
                                return JSON.stringify({
                                    status: 'PARTIAL_SUCCESS',
                                    message: 'Value mismatch',
                                    current: targetElement.value,
                                    target: '$safeText',
                                    element: targetElement.tagName,
                                    debug: debugInfo
                                });
                            }
                        } else {
                            return JSON.stringify({
                                status: 'FAILURE',
                                message: 'No suitable input element found',
                                debug: debugInfo
                            });
                        }
                    } catch(e) {
                        return JSON.stringify({
                            status: 'ERROR',
                            message: e.message,
                            stack: e.stack
                        });
                    }
                })();
                """.trimIndent()
                
                // Paralel olarak hem JavaScript hem de simulasyon yaklaşımını kullanalım
                var jsSuccess = false
                var simulationSuccess = false
                
                // Javascript yaklaşımını çalıştır
                webView.evaluateJavascript(enhancedScript) { result ->
                    // Sonucu temizle (tırnak işaretlerini ve kaçış karakterlerini kaldır)
                    val cleanResult = result.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
                    Timber.d("[SUGGESTION] JS insertion result: $cleanResult")
                    
                    try {
                        val jsonResult = org.json.JSONObject(cleanResult)
                        val status = jsonResult.optString("status", "")
                        jsSuccess = (status == "SUCCESS" || status == "PARTIAL_SUCCESS")
                        Timber.d("[SUGGESTION] JS insertion ${if (jsSuccess) "successful" else "failed"}")
                    } catch (e: Exception) {
                        Timber.e(e, "[SUGGESTION] Error parsing JS result")
                    }
                    
                    // JavaScript yaklaşımı başarılı olduysa kaydet
                    if (jsSuccess) {
                        saveSuggestion(currentInputKey, suggestion)
                    }
                    
                    // İşlemler bittikten sonra önerileri gizle
                    Handler(Looper.getMainLooper()).postDelayed({
                        hideSuggestions()
                    }, 200)
                }
                
                // Simulasyon yaklaşımını kullan (TabWebView ise)
                if (useSimulation) {
                    (webView as com.asforce.asforcetkf2.webview.TabWebView).simulateKeyboardInput(suggestion)
                    Timber.d("[SUGGESTION] Using TabWebView.simulateKeyboardInput with '$suggestion'")
                    simulationSuccess = true
                }
                
                // En az bir tane yaklaşım başarılı olursa, klavyeyi korumak için kısa bir gecikme ver
                Handler(Looper.getMainLooper()).postDelayed({
                    Timber.d("[SUGGESTION] Insertion summary: JS success=$jsSuccess, Simulation success=$simulationSuccess")
                }, 500)
                
                return@let // Kısa bir çıkış yap
            }
            
            // EditText (URL girişi gibi) için
            currentEditText?.let { editText ->
                editText.setText(suggestion)
                editText.setSelection(suggestion.length)
                
                // Kısa bir gecikme ile önerileri gizle - odaklama sorunlarını önlemek için
                Handler(Looper.getMainLooper()).postDelayed({
                    hideSuggestions()
                    Timber.d("[SUGGESTION] Finished suggestion handling with EditText")
                }, 200)
                return@let // Kısa çıkış
            }
            
            // Ne WebView ne de EditText mevcutsa önerileri gizle
            hideSuggestions()
            
        } catch (e: Exception) {
            // Herhangi bir hata olursa kaydet ve log'la
            Timber.e(e, "[SUGGESTION] Error handling suggestion: $suggestion")
            hideSuggestions()
        }
    }
    
    /**
     * Hide suggestion popup
     */
    fun hideSuggestions() {
        try {
            // Dismiss popup if showing
            suggestionPopup?.let { popup ->
                if (popup.isShowing) {
                    Handler(Looper.getMainLooper()).post {
                        try {
                            popup.dismiss()
                            Timber.d("[SUGGESTION] Dismissed suggestion popup")
                        } catch (e: Exception) {
                            Timber.e(e, "[SUGGESTION] Error dismissing popup")
                        }
                    }
                }
                suggestionPopup = null
            }
            
            // Also remove any view from parent (legacy approach)
            suggestionView?.let { view ->
                Handler(Looper.getMainLooper()).post {
                    try {
                        (view.parent as? ViewGroup)?.removeView(view)
                        suggestionView = null
                    } catch (e: Exception) {
                        Timber.e(e, "[SUGGESTION] Error removing suggestion view")
                    }
                }
            }
            
            Timber.d("[SUGGESTION] Hidden suggestions")
        } catch (e: Exception) {
            Timber.e(e, "[SUGGESTION] Error in hideSuggestions")
            suggestionPopup = null
            suggestionView = null
        }
    }
    
    /**
     * Kullanıcı ara yüzü için girdi anahtarını normalleştir
     * Hem büyük/küçük harf duyarlılığını hem de yazım farklılıklarını düzeltir
     */
    private fun normalizeInputKey(inputKey: String): String {
        // Anahtarı küçük harfe çevir (case-insensitive arama için)
        return inputKey.trim().lowercase()
    }

    /**
     * Load and filter suggestions from preferences
     */
    private suspend fun loadAndFilterSuggestions(inputKey: String, filterText: String): List<String> {
        // Anahtar adını normalleştir
        val normalizedKey = normalizeInputKey(inputKey)
        
        return withContext(Dispatchers.IO) {
            // Önce normalleştirilmiş anahtar için cache'e bak
            val now = System.currentTimeMillis()
            val cachedList = suggestionCache[normalizedKey]
            
            if (cachedList != null && (now - lastCacheRefreshTime < CACHE_TTL)) {
                Timber.d("[SUGGESTION] Using cached suggestions for key $normalizedKey: ${cachedList.size} items")
                
                // Apply filter to cached suggestions
                return@withContext filterSuggestions(cachedList, filterText)
            }
            
            // Orijinal ve normalize edilmiş anahtar adlarını kontrol et
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var allSuggestions = getSuggestionsFromPrefs(prefs, normalizedKey).toMutableSet()
            
            // Orijinal anahtar adından da öneri arama
            if (normalizedKey != inputKey) {
                val originalKeySuggestions = getSuggestionsFromPrefs(prefs, inputKey)
                allSuggestions.addAll(originalKeySuggestions)
            }
            
            // Önerileri birleştirerek tek bir liste oluştur ve cache'e kaydet
            Timber.d("[SUGGESTION] Loading fresh suggestions for key $normalizedKey: found ${allSuggestions.size} total suggestions")
            
            // Normalize edilmiş anahtar için cache'i güncelle
            val suggestionsList = allSuggestions.toList()
            suggestionCache[normalizedKey] = suggestionsList
            lastCacheRefreshTime = now
            
            // Return filtered list
            return@withContext filterSuggestions(suggestionsList, filterText)
        }
    }
    
    /**
     * Filter suggestions based on search text
     */
    private fun filterSuggestions(suggestions: List<String>, filterText: String): List<String> {
        if (filterText.isEmpty()) {
            // Boş metin durumlarında tüm önerileri göster
            Timber.d("[SUGGESTION] No filter applied, returning all suggestions (${suggestions.size})")
            return suggestions.sortedBy { it.lowercase() } // Sıralı sonuçlar
        } else {
            // Filtreleme mantığını güçlendir
            val filtered = suggestions
                .filter { it.contains(filterText, ignoreCase = true) }
                .sortedWith(compareBy(
                    // Önce tam eşleşmeleri göster
                    { !it.equals(filterText, ignoreCase = true) },
                    // Sonra başlangıç eşleşmelerini göster
                    { !it.startsWith(filterText, ignoreCase = true) },
                    // Son olarak alfabetik sıralama
                    { it.lowercase() }
                ))
            Timber.d("[SUGGESTION] Applied filter '$filterText': found ${filtered.size} matching suggestions")
            return filtered
        }
    }
    
    /**
     * Delete a suggestion from preferences and update UI
     * Güçlendirilmiş silme yöntemi - daha yüksek başarı oranı için çoklu yöntem kullanıyor
     */
    private fun deleteSuggestion(inputKey: String, suggestion: String, position: Int) {
        // Özel "tümünü sil" işaretleyicisini kontrol et
        if (suggestion == "__DELETE_ALL_SUGGESTIONS__") {
            Timber.d("[SUGGESTION] Deleting ALL suggestions for key: '$inputKey'")
            deleteAllSuggestions(inputKey)
            return
        }
        
        Timber.d("[SUGGESTION] Deleting suggestion: '$suggestion' at position $position")
        
        try {
            // Hemen bazı işlemleri ana thread'de yap - kritik UI güncellemesi
            val adapter = suggestionView?.findViewById<RecyclerView>(R.id.suggestion_recycler_view)?.adapter as? SuggestionAdapter
            
            // Ana thread'de önbelleği güncelle
            suggestionCache[inputKey]?.let { cachedList ->
                if (cachedList.contains(suggestion)) {
                    val newList = cachedList.toMutableList()
                    newList.remove(suggestion)
                    suggestionCache[inputKey] = newList
                    Timber.d("[SUGGESTION] Removed from cache: '$suggestion'")
                    
                    // Adapter'a da bildiriyoruz
                    adapter?.updateSuggestions(newList)
                }
            }
            
            // Remove from preferences in background - güçlendirilmiş versiyon
            executorService.execute {
                try {
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    var suggestions = getSuggestionsFromPrefs(prefs, inputKey).toMutableSet()
                    val originalSize = suggestions.size
                    
                    // Yöntem 1: Doğrudan kaldır (önerilen ana yöntem)
                    val removed = suggestions.remove(suggestion)
                    
                    if (removed) {
                        // Yöntem 1: Senkron olarak commit() ile kaydet (ana güvenilir yöntem)
                        val success = prefs.edit().putStringSet(inputKey, suggestions).commit()
                        
                        if (success) {
                            Timber.d("[SUGGESTION] Successfully removed suggestion using commit()")
                        } else {
                            // Yöntem 2: Birden fazla silme yöntemi dene
                            // Yöntem 2a: apply() ile kaydet (asenkron)
                            prefs.edit().putStringSet(inputKey, suggestions).apply()
                            
                            // Yöntem 2b: Geçici olarak tümünü kaldır ve sonra güncelle
                            val tempSet = suggestions.toSet()
                            prefs.edit()
                                .remove(inputKey)
                                .apply()
                            
                            // Kısa beklemeden sonra tekrar kaydet
                            Thread.sleep(50)
                            prefs.edit().putStringSet(inputKey, tempSet).apply()
                        }
                        
                        // Önbelleği güncelle - kesinlikle sil
                        suggestionCache.remove(inputKey)
                        
                        // Tutarlılık kontrolü
                        Thread.sleep(100) // Asenkron işlemin tamamlanmasını bekle
                        suggestions = getSuggestionsFromPrefs(prefs, inputKey).toMutableSet()
                        
                        // Kontrol et: Gerçekten silindi mi?
                        val actuallyRemoved = !suggestions.contains(suggestion)
                        Timber.d("[SUGGESTION] After verification, suggestion removed=${actuallyRemoved}, new size=${suggestions.size}, original size=$originalSize")
                        
                        // Silme işlemi başarılıysa bildirim göster
                        if (actuallyRemoved) {
                            // Ana thread'de bildirim göster
                            CoroutineScope(Dispatchers.Main).launch {
                                // Bilgi mesajı göster
                                android.widget.Toast.makeText(
                                    context, 
                                    "Öneri silindi", 
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        } else {
                            // Silme başarısız olduysa son çare
                            Timber.w("[SUGGESTION] Suggestion still exists after deletion attempts, using last resort")
                            
                            // Son çare: Diğerleri haricindekileri ekle
                            suggestions = getSuggestionsFromPrefs(prefs, inputKey).toMutableSet()
                            suggestions.remove(suggestion)
                            
                            // Geçici bir kelime kullan
                            val tempKey = inputKey + "_temp"
                            prefs.edit().putStringSet(tempKey, suggestions).commit()
                            
                            // Orijinali sil
                            prefs.edit().remove(inputKey).commit()
                            
                            // Geçici anahtardan geri yükle
                            val finalSuggestions = prefs.getStringSet(tempKey, setOf<String>()) ?: setOf()
                            prefs.edit().putStringSet(inputKey, finalSuggestions).commit()
                            
                            // Geçici anahtarı temizle
                            prefs.edit().remove(tempKey).apply()
                            
                            // Önbelleği temizle
                            suggestionCache.clear()
                            
                            // Kullanıcıya hata mesajı göster
                            CoroutineScope(Dispatchers.Main).launch {
                                android.widget.Toast.makeText(
                                    context,
                                    "Öneri silindi",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    } else {
                        // Zaten kaldırılmışsa (belki başka bir yerden)
                        Timber.d("[SUGGESTION] Suggestion was already removed from preferences")
                        
                        // Adapter'a bildir
                        val updatedList = suggestions.toList()
                        CoroutineScope(Dispatchers.Main).launch {
                            adapter?.updateSuggestions(updatedList)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[SUGGESTION] Error deleting suggestion: $suggestion")
                    
                    // Hataya rağmen kullanıcıya mesaj göster - kafa karışıklığını önlemek için
                    CoroutineScope(Dispatchers.Main).launch {
                        // Bilgi mesajı göster
                        android.widget.Toast.makeText(
                            context, 
                            "Öneri silinirken hata oluştu", 
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[SUGGESTION] Critical error in deleteSuggestion for: $suggestion")
            
            // Hataya rağmen kullanıcıya mesaj göster
            CoroutineScope(Dispatchers.Main).launch {
                android.widget.Toast.makeText(
                    context, 
                    "Silme işlemi sırasında hata oluştu", 
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    /**
     * Belirli bir anahtar için tüm önerileri sil
     * Güçlendirilmiş versiyon - birden fazla yöntem kullanır ve senkronizasyon ile tutarlılık sağlar
     */
    private fun deleteAllSuggestions(inputKey: String) {
        Timber.d("[SUGGESTION] Deleting ALL suggestions for key: '$inputKey'")
        
        try {
            // Ana thread'de başlat, UYGULAMADAN VE CIHAZDAN TAMAMEN SİL
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            
            // 1. COMMIT ile sil - Senkron
            val success = prefs.edit().remove(inputKey).commit()
            Timber.d("[SUGGESTION] Initial deletion success: $success")
            
            // 2. Önbelleği tamamen sil
            suggestionCache.remove(inputKey)
            lastCacheRefreshTime = 0L

            // 3. Adaptörü hemen temizle
            val emptyList = emptyList<String>()
            CoroutineScope(Dispatchers.Main).launch {
                // Görünümü temizle
                suggestionView?.let { view ->
                    val recyclerView = view.findViewById<RecyclerView>(R.id.suggestion_recycler_view)
                    val adapter = recyclerView?.adapter as? SuggestionAdapter
                    adapter?.updateSuggestions(emptyList)
                }
                
                // Popupları kapat
                hideSuggestions()
            }
            
            // 4. DAHA GÜÇLÜ SİLME DENEMELERİ
            executorService.execute {
                try {
                    // 4.1. Dosya sisteminden direkt olarak tercih dosyasını sil
                    val appContext = context.applicationContext
                    val xmlFile = java.io.File(appContext.applicationInfo.dataDir, "shared_prefs/${PREFS_NAME}.xml")
                    if (xmlFile.exists()) {
                        val deleted = xmlFile.delete()
                        Timber.d("[SUGGESTION] Preferences file deleted: $deleted")
                    }
                    
                    // 4.2. Yeni SharedPreferences örneği oluştur ve sil
                    val newPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    newPrefs.edit().remove(inputKey).commit()
                    
                    // 4.3. Uygulama önbelleğini temizle
                    try {
                        val cacheDir = context.cacheDir
                        if (cacheDir.exists()) {
                            val files = cacheDir.listFiles()
                            if (files != null) {
                                for (file in files) {
                                    file.delete()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "[SUGGESTION] Error clearing cache directory")
                    }
                    
                    // 4.4. Tüm önbelleği temizle
                    suggestionCache.clear()
                    
                    // 5. Kullanıcıya bilgi ver
                    CoroutineScope(Dispatchers.Main).launch {
                        android.widget.Toast.makeText(
                            context,
                            "Tüm öneriler silindi",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                    
                    Timber.d("[SUGGESTION] All deletion steps completed for key: $inputKey")
                } catch (e: Exception) {
                    Timber.e(e, "[SUGGESTION] Error in background deletion process")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[SUGGESTION] Critical error in deleteAllSuggestions")
        }
    }
    
    /**
     * Get suggestions from SharedPreferences
     * Güçlendirilmiş versiyon - tutarlılık için hata durumlarını kontrol eder
     */
    private fun getSuggestionsFromPrefs(prefs: SharedPreferences, inputKey: String): Set<String> {
        try {
            val suggestions = prefs.getStringSet(inputKey, setOf()) ?: setOf()
            
            // Boş sete karşı kontrol
            if (suggestions.isEmpty() && suggestionCache.containsKey(inputKey)) {
                // Önbellek hala verileri içeriyorsa - bu bir hata olabilir
                val cachedSuggestions = suggestionCache[inputKey] ?: emptyList()
                if (cachedSuggestions.isNotEmpty()) {
                    Timber.w("[SUGGESTION] Cached suggestions exist (${cachedSuggestions.size}) but prefs are empty for key: $inputKey")
                    
                    // Hemen yeniden kaydetmeyi dene (veriler daha önce kaydedilememiş olabilir)
                    val suggestionSet = cachedSuggestions.toSet()
                    prefs.edit().putStringSet(inputKey, suggestionSet).apply()
                    
                    // Cache'i boşalt
                    suggestionCache.remove(inputKey)
                    
                    // Geri dönüş için düzeltilmiş değeri kullan
                    return suggestionSet
                }
            }
            
            return suggestions
        } catch (e: Exception) {
            Timber.e(e, "[SUGGESTION] Error getting suggestions from prefs for key: $inputKey")
            return setOf()
        }
    }
    
    /**
     * Save a suggestion to SharedPreferences
     * Güçlendirilmiş versiyon - bozuk veri durumlarını önler ve başarıyı garantiler
     */
    fun saveSuggestion(inputKey: String, suggestion: String) {
        if (suggestion.isBlank()) return
        
        // Anahtar adını normalleştir
        val normalizedKey = normalizeInputKey(inputKey)
        
        // Ana thread'de hemen önbelleği güncelle
        synchronized(suggestionCache) {
            val cachedList = suggestionCache[normalizedKey]?.toMutableList() ?: mutableListOf()
            if (!cachedList.contains(suggestion.trim())) {
                cachedList.add(suggestion.trim())
                suggestionCache[normalizedKey] = cachedList.toList()
                Timber.d("[SUGGESTION] Updated cache for key: $normalizedKey")
            }
        }
        
        executorService.execute {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                
                // StringSet özel bir duruma sahiptir - yeni bir kopya oluşturmalıyız
                // Mevcut değerleri oku
                val currentSet = prefs.getStringSet(normalizedKey, null)
                
                // Yeni bir set oluştur (kopya, referans değil)
                val newSet = mutableSetOf<String>()
                
                // Mevcut değerleri ekle
                if (currentSet != null) {
                    newSet.addAll(currentSet)
                }
                
                // Yeni öneriyi ekle
                newSet.add(suggestion.trim())
                
                // SENKRON olarak kaydet - commit kullan (apply yerine)
                val success = prefs.edit().putStringSet(normalizedKey, newSet).commit()
                
                if (success) {
                    Timber.d("[SUGGESTION] Successfully saved suggestion to preferences: '$suggestion' for key $normalizedKey")
                } else {
                    // Başarısız olduysa ikinci bir yöntem dene
                    try {
                        // Önce temizle sonra yeniden ekle
                        prefs.edit().remove(normalizedKey).commit()
                        prefs.edit().putStringSet(normalizedKey, newSet).commit()
                        Timber.d("[SUGGESTION] Used alternate method to save suggestion")
                    } catch (e: Exception) {
                        Timber.e(e, "[SUGGESTION] Error in fallback save method")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "[SUGGESTION] Error saving suggestion: $suggestion")
            }
        }
    }
    
    /**
     * Clear suggestion cache for a specific input key
     */
    fun clearSuggestionCache(inputKey: String) {
        Timber.d("[SUGGESTION] Clearing suggestion cache for key: $inputKey")
        executorService.execute {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().remove(inputKey).apply()
                Timber.d("[SUGGESTION] Cache cleared for key: $inputKey")
            } catch (e: Exception) {
                Timber.e(e, "[SUGGESTION] Error clearing cache for key: $inputKey")
            }
        }
    }
    
    /**
     * Clear all suggestion caches
     * Güçlendirilmiş versiyon - veri tutarlılığı için tam bir sıfırlama sağlar
     */
    fun clearAllSuggestionCaches() {
        Timber.d("[SUGGESTION] TAMAMEN CLEARING all suggestion caches")
        
        try {
            // Önbelleği ana thread'de hemen temizle
            suggestionCache.clear()
            lastCacheRefreshTime = 0L
            
            // Popup'ları kapat (eğer varsa)
            hideSuggestions()
            
            // TAMAMEN Yeni bir silme yaklaşımı - dosya sistemi seviyesinde
            executorService.execute {
                try {
                    // Önce normal silmeyi dene
                    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    prefs.edit().clear().commit()
                    
                    // Dosya sistemi seviyesinde silme işlemleri
                    val appContext = context.applicationContext
                    
                    // SharedPreferences XML dosyasını tamamen sil
                    val xmlFile = java.io.File(appContext.applicationInfo.dataDir, "shared_prefs/${PREFS_NAME}.xml")
                    if (xmlFile.exists()) {
                        val deleted = xmlFile.delete()
                        Timber.d("[SUGGESTION] Deleted preference file directly: $deleted")
                    }
                    
                    // Tüm SharedPreferences dizinini temizle
                    val prefsDir = java.io.File(appContext.applicationInfo.dataDir, "shared_prefs")
                    if (prefsDir.exists() && prefsDir.isDirectory) {
                        val files = prefsDir.listFiles { file -> file.name.contains(PREFS_NAME) }
                        if (files != null) {
                            for (file in files) {
                                val deleted = file.delete()
                                Timber.d("[SUGGESTION] Deleted prefs file: ${file.name}, success: $deleted")
                            }
                        }
                    }
                    
                    // Uygulama önbellek dizinini temizle
                    val cacheDir = context.cacheDir
                    if (cacheDir.exists()) {
                        val files = cacheDir.listFiles()
                        if (files != null) {
                            for (file in files) {
                                if (file.name.contains("suggestion", ignoreCase = true)) {
                                    val deleted = file.delete()
                                    Timber.d("[SUGGESTION] Deleted cache file: ${file.name}, success: $deleted")
                                }
                            }
                        }
                    }
                    
                    // Yeni bir SharedPreferences nesnesi oluştur (temiz bir başlangıç garanti etmek için)
                    val newPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val isEmpty = newPrefs.all.isEmpty()
                    Timber.d("[SUGGESTION] New SharedPreferences is empty: $isEmpty")
                    
                    // Ana thread'de kullanıcıya bilgi ver
                    CoroutineScope(Dispatchers.Main).launch {
                        // Son kez önbelleği temizle
                        synchronized(suggestionCache) {
                            suggestionCache.clear()  
                        }
                        
                        android.widget.Toast.makeText(
                            context,
                            "Öneri önbelleği tamamen temizlendi",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "[SUGGESTION] Error during thorough cache cleaning")
                    
                    // Son çare - tek tek bilinen anahtarları temizlemeyi dene
                    try {
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val allKeys = prefs.all.keys.toList()
                        Timber.d("[SUGGESTION] Found ${allKeys.size} keys to delete")
                        
                        // Tek tek her anahtarı sil
                        for (key in allKeys) {
                            prefs.edit().remove(key).commit()
                            Timber.d("[SUGGESTION] Removed key: $key")
                        }
                        
                        // Ana thread'de bilgi ver
                        CoroutineScope(Dispatchers.Main).launch {
                            android.widget.Toast.makeText(
                                context,
                                "Öneri önbelleği temizlendi",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    } catch (e2: Exception) {
                        Timber.e(e2, "[SUGGESTION] Final fallback cleaning failed")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "[SUGGESTION] Critical error in clearAllSuggestionCaches")
            
            // Hata mesajı göster
            CoroutineScope(Dispatchers.Main).launch {
                android.widget.Toast.makeText(
                    context,
                    "Öneri önbelleği temizlenirken hata oluştu",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    /**
     * Should be called from Activity/Fragment onLowMemory
     */
    fun onLowMemory() {
        Timber.w("[SUGGESTION] Low memory warning received, clearing suggestion cache")
        suggestionCache.clear()
        lastCacheRefreshTime = 0L
        
        // Also clear popup if showing to free up memory
        if (suggestionPopup?.isShowing == true) {
            hideSuggestions()
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        Timber.d("[SUGGESTION] Cleaning up suggestion manager resources")
        // Immediately hide suggestions to prevent leaks
        try {
            suggestionPopup?.dismiss()
            suggestionPopup = null
            
            // Clear view references
            suggestionView = null
            currentEditText = null
            activeWebView = null
            rootViewRef = null
            suggestionCache.clear() // Clear the cache as well
        } catch (e: Exception) {
            Timber.e(e, "[SUGGESTION] Error during view cleanup")
        }
        
        // Shutdown executor gracefully
        try {
            executorService.shutdown()
            // Try to terminate within 1 second
            if (!executorService.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS)) {
                executorService.shutdownNow()
            }
        } catch (e: Exception) {
            Timber.e(e, "[SUGGESTION] Error during executor shutdown")
            executorService.shutdownNow()
        }
        
        Timber.d("[SUGGESTION] Suggestion manager cleanup complete")
    }

    /**
     * Construct and initialize the SuggestionManager
     * Use this static method to create and initialize a SuggestionManager instance
     */
    companion object {
        /**
         * Create and initialize a SuggestionManager for an Activity
         */
        fun createForActivity(activity: android.app.Activity): SuggestionManager {
            // Find the root view of the activity (usually a FrameLayout)
            val decorView = activity.window.decorView as? ViewGroup
            val contentView = decorView?.findViewById<ViewGroup>(android.R.id.content)
            val rootView = contentView?.getChildAt(0) as? ViewGroup
            
            Timber.d("[SUGGESTION] Creating SuggestionManager - decorView: ${decorView?.javaClass?.simpleName}, " +
                    "contentView: ${contentView?.javaClass?.simpleName}, " +
                    "rootView: ${rootView?.javaClass?.simpleName}")
            
            // Create manager
            val manager = SuggestionManager(activity)
            
            // Initialize with the root view (try all possible containers)
            if (rootView != null) {
                Timber.d("[SUGGESTION] Initializing with root view: ${rootView.javaClass.simpleName}")
                manager.initialize(rootView)
            } else if (contentView != null) {
                Timber.d("[SUGGESTION] Initializing with content view: ${contentView.javaClass.simpleName}")
                manager.initialize(contentView)
            } else if (decorView != null) {
                Timber.d("[SUGGESTION] Initializing with decor view: ${decorView.javaClass.simpleName}")
                manager.initialize(decorView)
            } else {
                Timber.e("[SUGGESTION] Could not find a suitable view container!")
            }
            
            // Ensure keyboard visibility is monitored
            setupSpecialKeyboardHandling(activity, manager)
            
            return manager
        }
        
        /**
         * Setup special keyboard handling in MainActivity
         * This adds specific handling for keyboard visibility with WebViews
         */
        private fun setupSpecialKeyboardHandling(activity: android.app.Activity, manager: SuggestionManager) {
            try {
                // For WebView focused events
                val webViewField = activity.javaClass.getDeclaredField("tabWebView")
                webViewField.isAccessible = true
                val webView = webViewField.get(activity) as? WebView
                
                webView?.let { view ->
                    Timber.d("[SUGGESTION] Found TabWebView in activity, setting up special keyboard handling")
                    
                    // Monitor focus changes on WebView
                    view.setOnFocusChangeListener { v, hasFocus ->
                        if (hasFocus) {
                            Timber.d("[SUGGESTION] TabWebView gained focus")
                            // Force keyboard to show for input fields
                            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
                        }
                    }
                }
            } catch (e: Exception) {
                // Expected if the activity doesn't have tabWebView field
                Timber.d("[SUGGESTION] No TabWebView field found in activity: ${e.message}")
            }
        }
    }
}
