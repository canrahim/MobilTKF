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
        // İnput key değerini daha güçlü şekilde işle ve önemini artır
        currentInputKey = sanitizeKey(inputKey)
        
        Timber.d("[SUGGESTION_JS] Input focused with key: $currentInputKey - Önemli bir input alanına odaklanıldı!")
        
        // Show suggestions immediately on UI thread with retry mechanism
        mainHandler.post {
            // Check if webView is still active and visible
            if (webView.visibility == View.VISIBLE) {
                // Görünürlüğü yazdır
                Timber.d("[SUGGESTION_JS] WebView görünür durumda, önerileri göstermeye çalışıyoruz")
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
                            
                            Timber.d("[SUGGESTION_JS] Active element info: $jsonStr")
                            
                            // Update current value from active element
                            if (json.has("value")) {
                                currentInputValue = json.getString("value")
                                Timber.d("[SUGGESTION_JS] Current input value: $currentInputValue")
                            }
                            
                            // Öneri gösterme stratejisini iyileştir - 5 kez deneme yap
                            // İlk deneme: Tüm önerileri almak için boş filtreyle göster
                            Timber.d("[SUGGESTION_JS] Deneme 1/5: Tüm önerileri gösteriyoruz")
                            suggestionManager.showSuggestions(webView, currentInputKey, "")
                            
                            // İkinci deneme: Güncel değere göre filtrele
                            mainHandler.postDelayed({
                                Timber.d("[SUGGESTION_JS] Deneme 2/5: Filtrelenmiş önerileri gösteriyoruz")
                                suggestionManager.showSuggestions(webView, currentInputKey, currentInputValue)
                                
                                // Üçüncü deneme: Daha uzun bir gecikmeyle tekrar dene
                                mainHandler.postDelayed({
                                    Timber.d("[SUGGESTION_JS] Deneme 3/5: Konum ayarlama için son çağrı")
                                    suggestionManager.showSuggestions(webView, currentInputKey, currentInputValue)
                                    
                                    // Dördüncü deneme: Daha uzun bir gecikmeyle tekrar dene
                                    mainHandler.postDelayed({
                                        Timber.d("[SUGGESTION_JS] Deneme 4/5: Ek final çağrı")
                                        suggestionManager.showSuggestions(webView, currentInputKey, currentInputValue)
                                        
                                        // Beşinci ve son deneme: En uzun gecikmeyle
                                        mainHandler.postDelayed({
                                            Timber.d("[SUGGESTION_JS] Deneme 5/5: Son şans deneme")
                                            suggestionManager.saveSuggestion(currentInputKey, "Test Öneri") // Test verisi ekle
                                            suggestionManager.showSuggestions(webView, currentInputKey, "")
                                        }, 500)
                                    }, 300)
                                }, 200)
                            }, 100)
                        } catch (e: Exception) {
                            Timber.e(e, "[SUGGESTION_JS] Error processing element info")
                            
                            // Still show suggestions with empty filter as fallback
                            suggestionManager.showSuggestions(webView, currentInputKey, "")
                            
                            // Try again after a delay
                            mainHandler.postDelayed({
                                suggestionManager.showSuggestions(webView, currentInputKey, "")
                            }, 100)
                        }
                    }
                )
            } else {
                Timber.d("[SUGGESTION_JS] WebView not visible, skipping suggestion display")
            }
        }
    }
    
    /**
     * Called when input text changes in the WebView
     */
    @JavascriptInterface
    fun onInputChanged(inputKey: String, inputValue: String) {
        currentInputKey = sanitizeKey(inputKey)
        currentInputValue = inputValue
        
        // Update suggestions immediately on UI thread
        mainHandler.post {
            if (webView.visibility == View.VISIBLE) {
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
        // Sanitize key and value
        val sanitizedKey = sanitizeKey(inputKey)
        if (inputValue.isNotBlank()) {
            // Save suggestion on UI thread
            mainHandler.post {
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
