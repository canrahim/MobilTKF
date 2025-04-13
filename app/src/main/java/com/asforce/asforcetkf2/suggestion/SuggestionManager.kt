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
        // Titreme önleme için daha uzun debounce değeri kullanılacak
        val debounceTime = 800L
        
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val r = Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            
            val screenHeight = rootView.height
            val keyboardHeight = screenHeight - r.bottom
            val now = System.currentTimeMillis()
            
            // Consider keyboard as visible if its height is more than 15% of screen
            val keyboardNowVisible = keyboardHeight > screenHeight * 0.15
            
            // Prevent rapid toggling (debounce) with longer timeout
            if (keyboardNowVisible != isKeyboardVisible && (now - lastKeyboardToggleTime > debounceTime)) {
                lastKeyboardToggleTime = now
                isKeyboardVisible = keyboardNowVisible
                
                if (keyboardNowVisible) {
                    // Keyboard is visible - but don't refresh suggestions immediately
                    Timber.d("[SUGGESTION] Keyboard detected as visible (height: $keyboardHeight)")
                    
                    // Wait longer for the keyboard animation to complete fully
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (isKeyboardVisible) {  // Double-check it's still visible
                            // Show suggestions for current field
                            currentEditText?.let { editText ->
                                showSuggestions(editText, currentInputKey, editText.text.toString())
                            } ?: activeWebView?.let { webView ->
                                if (currentInputKey.isNotEmpty()) {
                                    showSuggestions(webView, currentInputKey, "")
                                }
                            }
                        }
                    }, 700)  // Daha uzun gecikme süresi
                } else {
                    // Keyboard is hidden, hide suggestions after a longer delay
                    Timber.d("[SUGGESTION] Keyboard detected as hidden, hiding suggestions")
                    Handler(Looper.getMainLooper()).postDelayed({
                        // Only hide if keyboard is still not visible
                        if (!isKeyboardVisible) {
                            hideSuggestions()
                        }
                    }, 500)  // Daha uzun gecikme
                }
            }
        }
    }

    /**
     * Show suggestions for the given input field
     */
    fun showSuggestions(anchorView: View, inputKey: String, filterText: String) {
        Timber.d("[SUGGESTION] Showing suggestions for key: $inputKey, filter text: '$filterText'")
        
        // Use coroutines for background processing
        CoroutineScope(Dispatchers.IO).launch {
            // Load and filter suggestions
            val filteredSuggestions = loadAndFilterSuggestions(inputKey, filterText)
            
            withContext(Dispatchers.Main) {
                // If no suggestions, hide suggestion view and return
                if (filteredSuggestions.isEmpty()) {
                    Timber.d("[SUGGESTION] No matching suggestions found, hiding suggestions")
                    hideSuggestions()
                    return@withContext
                }
                
                Timber.d("[SUGGESTION] Found ${filteredSuggestions.size} matching suggestions")
                
                // Use PopupWindow approach like Menu5Activity
                showSuggestionsInPopup(anchorView, inputKey, filteredSuggestions)
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
            
            // Style the suggestion view - use dark background like example code
            val backgroundDrawable = android.graphics.drawable.ColorDrawable(
                android.graphics.Color.parseColor("#1d1b30") // dark blue like in the example
            )
            suggestionView.background = backgroundDrawable 
            suggestionView.elevation = 24f
            
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
            val popupHeight = (65 * context.resources.displayMetrics.density).toInt() // Yüksekliği arttırdık (45'ten 65'e)
            
            val popup = PopupWindow(
                suggestionView,
                ViewGroup.LayoutParams.MATCH_PARENT,
                popupHeight,
                false // NOT focusable - this is important to not steal focus
            ).apply {
                isOutsideTouchable = true
                elevation = 24f
                animationStyle = 0 // Animasyon yok - titreme önlemek için
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
            
            // Set height to a fixed percentage from top - try to be more aggressive with this placement
            val fixedPosition = (screenHeight * 0.64).toInt() // %67 from the top (higher on screen)
            
            Timber.d("[SUGGESTION] Screen height: $screenHeight, position from top: $fixedPosition")
            
            // Show popup at fixed position - closer to keyboard
            popup.showAtLocation(
                anchorView,
                android.view.Gravity.TOP,  // Position from TOP 
                0,
                fixedPosition
            )
            
            // Save reference to popup
            suggestionPopup = popup
            
            Timber.d("[SUGGESTION] Showing popup with ${suggestions.size} suggestions at fixed position")
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
            // Save to preferences regardless of insertion success
            saveSuggestion(currentInputKey, suggestion)
            
            // Sanitize text for JavaScript injection - prevent JS issues
            val safeText = suggestion.replace("'", "\\'").replace("\"", "\\\"")
            
            // Direct approach for WebView - prioritize this since it's our main use case
            activeWebView?.let { webView ->
                // DO NOT hide suggestions yet - this can cause focus issues
                // Let's insert the text first while focus is still active
                
                // Set focus to webview first
                webView.requestFocus()
                
                // Önce TabWebView'ın simulateKeyboardInput metodunu kullan
                if (webView is com.asforce.asforcetkf2.webview.TabWebView) {
                    Timber.d("[SUGGESTION] Using TabWebView.simulateKeyboardInput with '$suggestion'")
                    webView.simulateKeyboardInput(suggestion)
                }
                
                // Paralel olarak JavaScript yaklaşımını da kullan
                val directScript = """
                (function() {
                    try {
                        console.log('Direct insertion of: $safeText');
                        
                        // APPROACH 1: DIRECT DOM MANIPULATION
                        function setValueAndTriggerEvents(element) {
                            // Save original for verification
                            var origVal = element.value;
                            
                            // Set value directly
                            element.value = '$safeText';
                            
                            // If the value didn't change, try innerHTML as backup
                            if (element.value === origVal) {
                                element.innerHTML = '$safeText';
                            }
                            
                            // Trigger standard events
                            var events = ['input', 'change', 'blur', 'focus'];
                            for (var i = 0; i < events.length; i++) {
                                try {
                                    var event = new Event(events[i], {bubbles: true});
                                    element.dispatchEvent(event);
                                } catch(e) {
                                    console.log('Event error: ' + e.message);
                                    try {
                                        var fallbackEvent = document.createEvent('HTMLEvents');
                                        fallbackEvent.initEvent(events[i], true, true);
                                        element.dispatchEvent(fallbackEvent);
                                    } catch(e2) {}
                                }
                            }
                            
                            // Final check and debug
                            console.log('Final value = ' + element.value);
                            return element.value;
                        }
                        
                        // First try active element
                        if (document.activeElement && 
                            (document.activeElement.tagName === 'INPUT' || 
                             document.activeElement.tagName === 'TEXTAREA')) {
                            console.log('Found active element to insert text');
                            setValueAndTriggerEvents(document.activeElement);
                            return "SUCCESS:ACTIVE_ELEMENT";
                        }
                        
                        // Try data-tkf-key
                        var keyElement = document.querySelector('[data-tkf-key="$currentInputKey"]');
                        if (keyElement) {
                            console.log('Found element by data-tkf-key');
                            keyElement.focus();
                            setValueAndTriggerEvents(keyElement);
                            return "SUCCESS:KEYED_ELEMENT";
                        }
                        
                        // Find any input that's visible
                        var allInputs = document.querySelectorAll('input, textarea');
                        for (var i = 0; i < allInputs.length; i++) {
                            var input = allInputs[i];
                            if (input.offsetParent !== null && !input.disabled && !input.readOnly) {
                                // This input is visible
                                input.focus();
                                input.select();
                                setValueAndTriggerEvents(input);
                                return "SUCCESS:VISIBLE_INPUT_" + i;
                            }
                        }
                        
                        return "FAILURE:NO_INPUT_FOUND";
                    } catch(e) {
                        return "ERROR:" + e.message;
                    }
                })();
                """.trimIndent()
                
                webView.evaluateJavascript(directScript) { result ->
                    Timber.d("[SUGGESTION] Direct script result: $result")
                    
                    // NOW we can hide suggestions AFTER the text has been inserted
                    hideSuggestions()
                    
                    // Prevent keyboard closing by using a short delay for hiding
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            // Don't explicitly hide keyboard here - let user interactions do that naturally
                            // This prevents keyboard flashing issues
                            Timber.d("[SUGGESTION] Finished suggestion handling with WebView")
                        } catch (e: Exception) {
                            Timber.e(e, "[SUGGESTION] Error in WebView suggestion handling")
                        }
                    }, 300)
                }
            }
            
            // Set text in EditText if available
            currentEditText?.let { editText ->
                editText.setText(suggestion)
                editText.setSelection(suggestion.length)
                
                // Wait before hiding suggestions to prevent focus issues
                Handler(Looper.getMainLooper()).postDelayed({
                    hideSuggestions()
                    
                    // Don't explicitly hide keyboard, let natural interactions handle it
                    Timber.d("[SUGGESTION] Finished suggestion handling with EditText")
                }, 300)
                return // Exit early
            }
            
            // If we get here, neither WebView nor EditText was available
            hideSuggestions()
            
        } catch (e: Exception) {
            // If anything fails, log error
            Timber.e(e, "[SUGGESTION] Error handling suggestion: $suggestion")
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
     * Load and filter suggestions from preferences
     */
    private suspend fun loadAndFilterSuggestions(inputKey: String, filterText: String): List<String> {
        return withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val allSuggestions = getSuggestionsFromPrefs(prefs, inputKey)
            
            Timber.d("[SUGGESTION] Loading suggestions for key $inputKey: found ${allSuggestions.size} total suggestions")
            
            // Filter suggestions based on input text
            if (filterText.isEmpty()) {
                Timber.d("[SUGGESTION] No filter applied, returning all suggestions")
                return@withContext allSuggestions.toList()
            } else {
                val filtered = allSuggestions
                    .filter { it.contains(filterText, ignoreCase = true) }
                    .sortedBy { !it.startsWith(filterText, ignoreCase = true) } // Prioritize starts-with matches
                Timber.d("[SUGGESTION] Applied filter '$filterText': found ${filtered.size} matching suggestions")
                return@withContext filtered
            }
        }
    }
    
    /**
     * Delete a suggestion from preferences and update UI
     */
    private fun deleteSuggestion(inputKey: String, suggestion: String, position: Int) {
        Timber.d("[SUGGESTION] Deleting suggestion: '$suggestion' at position $position")
        
        // Remove from preferences in background
        executorService.execute {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val suggestions = getSuggestionsFromPrefs(prefs, inputKey).toMutableSet()
            
            if (suggestions.remove(suggestion)) {
                // Save updated set
                prefs.edit().putStringSet(inputKey, suggestions).apply()
                Timber.d("[SUGGESTION] Removed suggestion from preferences: '$suggestion', ${suggestions.size} remaining")
                
                // Update UI on main thread
                val suggestionsList = suggestions.toList()
                CoroutineScope(Dispatchers.Main).launch {
                    // If view exists, update it
                    suggestionView?.let { view ->
                        val recyclerView = view.findViewById<RecyclerView>(R.id.suggestion_recycler_view)
                        val adapter = recyclerView?.adapter as? SuggestionAdapter
                        adapter?.updateSuggestions(suggestionsList)
                        
                        // If no suggestions left, hide view
                        if (suggestionsList.isEmpty()) {
                            hideSuggestions()
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Get suggestions from SharedPreferences
     */
    private fun getSuggestionsFromPrefs(prefs: SharedPreferences, inputKey: String): Set<String> {
        return prefs.getStringSet(inputKey, setOf()) ?: setOf()
    }
    
    /**
     * Save a suggestion to SharedPreferences
     */
    fun saveSuggestion(inputKey: String, suggestion: String) {
        if (suggestion.isBlank()) return
        
        executorService.execute {
            try {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val suggestions = getSuggestionsFromPrefs(prefs, inputKey).toMutableSet()
                
                // Add new suggestion
                if (suggestions.add(suggestion.trim())) {
                    // Save updated set
                    prefs.edit().putStringSet(inputKey, suggestions).apply()
                    Timber.d("[SUGGESTION] Saved suggestion to preferences: '$suggestion' for key $inputKey, total count: ${suggestions.size}")
                } else {
                    Timber.d("[SUGGESTION] Suggestion '$suggestion' already exists, not saving")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error saving suggestion")
            }
        }
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        Timber.d("[SUGGESTION] Cleaning up suggestion manager resources")
        hideSuggestions()
        executorService.shutdown()
        currentEditText = null
        activeWebView = null
        rootViewRef = null
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
