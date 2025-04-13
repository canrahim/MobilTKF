package com.asforce.asforcetkf2.suggestion

import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import timber.log.Timber

/**
 * JavaScript interface for handling suggestions in WebView
 */
class WebViewSuggestionInterface(
    private val suggestionManager: SuggestionManager,
    private val webView: WebView
) {
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentInputKey = ""
    private var currentInputValue = ""
    
    /**
     * Called when an input field is focused in the WebView
     */
    @JavascriptInterface
    fun onInputFocused(inputKey: String) {
        Timber.d("Input focused in WebView: $inputKey")
        currentInputKey = sanitizeKey(inputKey)
        
        // Show suggestions immediately on UI thread
        mainHandler.post {
            // Check if webView is still active and visible
            if (webView.visibility == View.VISIBLE) {
                Timber.d("[SUGGESTION] WebView input focused with key: $currentInputKey")
                
                // Get active element info to ensure suggestions are relevant
                webView.evaluateJavascript(
                    """
                    (function() {
                        var activeElement = document.activeElement;
                        if (activeElement && (activeElement.tagName === 'INPUT' || activeElement.tagName === 'TEXTAREA')) {
                            // Get current value to show filtered suggestions
                            var value = activeElement.value || '';
                            
                            return JSON.stringify({
                                tagName: activeElement.tagName,
                                value: value,
                                id: activeElement.id || '',
                                name: activeElement.name || ''
                            });
                        }
                        return '{}';
                    })();
                    """,
                    { result ->
                        try {
                            // Clean the result string
                            val jsonStr = result.trim().removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
                            val json = org.json.JSONObject(jsonStr)
                            
                            // Update current value from active element
                            if (json.has("value")) {
                                currentInputValue = json.getString("value")
                                Timber.d("[SUGGESTION] Got current value from active element: '$currentInputValue'")
                            }
                            
                            // Force show suggestions
                            suggestionManager.showSuggestions(webView, currentInputKey, currentInputValue)
                        } catch (e: Exception) {
                            Timber.e(e, "[SUGGESTION] Error processing active element info")
                            
                            // Still show suggestions with empty filter as fallback
                            suggestionManager.showSuggestions(webView, currentInputKey, currentInputValue)
                        }
                    }
                )
            } else {
                Timber.d("[SUGGESTION] WebView not visible, skipping suggestion display")
            }
        }
    }
    
    /**
     * Called when input text changes in the WebView
     */
    @JavascriptInterface
    fun onInputChanged(inputKey: String, inputValue: String) {
        Timber.d("Input changed in WebView: $inputKey = $inputValue")
        currentInputKey = sanitizeKey(inputKey)
        currentInputValue = inputValue
        
        // Update suggestions immediately on UI thread
        mainHandler.post {
            if (webView.visibility == View.VISIBLE) {
                Timber.d("[SUGGESTION] WebView input changed: $currentInputKey = $currentInputValue")
                // Show updated suggestions without any delay
                suggestionManager.showSuggestions(webView, currentInputKey, currentInputValue)
            }
        }
    }
    
    /**
     * Save an input suggestion when a form is submitted or input is changed
     */
    @JavascriptInterface
    fun saveInputSuggestion(inputKey: String, inputValue: String) {
        Timber.d("Saving input suggestion: $inputKey = $inputValue")
        
        // Sanitize key and value
        val sanitizedKey = sanitizeKey(inputKey)
        if (inputValue.isNotBlank()) {
            // Save suggestion on UI thread
            mainHandler.post {
                Timber.d("[SUGGESTION] Saving WebView suggestion: $sanitizedKey = $inputValue")
                suggestionManager.saveSuggestion(sanitizedKey, inputValue)
            }
        }
    }
    
    /**
     * Sanitize input key to make it usable as a preference key
     */
    private fun sanitizeKey(key: String): String {
        // Remove any characters that could cause issues in preference keys
        return key.replace("[^a-zA-Z0-9_]".toRegex(), "_")
    }
}
