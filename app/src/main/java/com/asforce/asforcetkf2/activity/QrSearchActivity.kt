package com.asforce.asforcetkf2.activity

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.asforce.asforcetkf2.R
import com.asforce.asforcetkf2.databinding.ActivityQrSearchBinding
import android.webkit.WebViewClient
import android.annotation.SuppressLint
import android.content.Context
import android.view.inputmethod.InputMethodManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QrSearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrSearchBinding
    private lateinit var webView: WebView
    private lateinit var qrEditText: EditText
    private lateinit var searchButton: Button
    
    private val baseUrl = "https://app.szutest.com.tr/EXT/PKControl/EquipmentList"
    
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize views
        webView = binding.webView
        qrEditText = binding.qrEditText
        searchButton = binding.searchButton
        
        // Setup WebView
        setupWebView()
        
        // Search button click listener
        searchButton.setOnClickListener {
            hideKeyboard()
            performSearch()
        }
        
        // Handle keyboard "search" or "enter" action
        qrEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                performSearch()
                return@setOnEditorActionListener true
            }
            return@setOnEditorActionListener false
        }
    }
    
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        // Enable JavaScript
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        
        // Set WebViewClient to handle page navigation
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                
                // Check if we're on the EquipmentList page
                if (url?.contains("EquipmentList") == true) {
                    // If we have a QR code in the EditText, perform search
                    val qrText = qrEditText.text.toString().trim()
                    if (qrText.isNotEmpty()) {
                        executeSearch(qrText)
                    }
                }
            }
        }
        
        // Load the base URL
        webView.loadUrl(baseUrl)
    }
    
    private fun performSearch() {
        val qrText = qrEditText.text.toString().trim()
        
        if (qrText.isEmpty()) {
            Toast.makeText(this, "QR kodu girin", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Check if the current page is the EquipmentList
        val currentUrl = webView.url ?: ""
        if (!currentUrl.contains("EquipmentList")) {
            // Navigate to EquipmentList page first
            webView.loadUrl(baseUrl)
            // The onPageFinished will handle executing the search
        } else {
            // Already on the correct page, execute search directly
            executeSearch(qrText)
        }
    }
    
    private fun executeSearch(qrText: String) {
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
            lifecycleScope.launch {
                // Wait briefly for the search to complete
                withContext(Dispatchers.IO) {
                    Thread.sleep(1500)
                }
                
                // Check for the result after search
                checkSearchResult(qrText)
            }
        }
    }
    
    private fun checkSearchResult(qrText: String) {
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
                    runOnUiThread {
                        Toast.makeText(this, "Sonuç bulunamadı", Toast.LENGTH_SHORT).show()
                    }
                    return@evaluateJavascript
                }
                
                if (cleanResult.startsWith("ERROR")) {
                    runOnUiThread {
                        Toast.makeText(this, "Hata: $cleanResult", Toast.LENGTH_SHORT).show()
                    }
                    return@evaluateJavascript
                }
                
                // Parse JSON result
                val jsonArray = org.json.JSONArray(cleanResult)
                
                if (jsonArray.length() > 0) {
                    val foundQrCode = jsonArray.getString(0)
                    
                    runOnUiThread {
                        // Update the qrEditText with the found value
                        qrEditText.setText(foundQrCode)
                        Toast.makeText(this, "QR kodu bulundu: $foundQrCode", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Sonuç işlenirken hata oluştu: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocus = currentFocus
        if (currentFocus != null) {
            imm.hideSoftInputFromWindow(currentFocus.windowToken, 0)
        }
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
