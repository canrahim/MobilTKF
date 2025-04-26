package com.asforce.asforcetkf2.webview

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.asforce.asforcetkf2.model.Tab
// import timber.log.Timber - removed for performance

/**
 * Custom WebViewClient for handling web page navigation and errors
 * Performans optimizasyonları eklenmiştir
 */
class TKFWebViewClient(
    private val tab: Tab,
    private val onPageStarted: (String, String) -> Unit,
    private val onPageFinished: (String, String, Bitmap?) -> Unit,
    private val onReceivedError: (Int, String, String) -> Unit,
    private val onReceivedSslError: (SslError) -> Boolean
) : WebViewClient() {
    
    // İstek sayacı ve son istek zamanı - aşırı güncellemeleri önlemek için
    private var requestCounter = 0
    private var lastRequestTime = 0L
    
    // Son script enjeksiyon zamanı - çift enjeksiyon önleme
    private var lastScriptInjectionTime = 0L
    
    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        // Page started loading: $url
        onPageStarted(tab.id, url)
    }
    
    override fun onPageFinished(view: WebView, url: String) {
        super.onPageFinished(view, url)
        // Page finished loading: $url
        
        // Sayfa yükleme tamamlandığında resimlerin gösterilmesine izin ver
        view.settings.blockNetworkImage = false
        
        // Sayfa yükleme tamamlandığında çerezlerin kalıcı saklanmasını sağla
        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.flush()
        
        // Ağır form script enjeksiyonunu sadece oturum sayfalarında yap
        if (isLoginPage(url)) {
            // Özel oturum sayfaları için bildirim gönderelim
            if (url.contains("szutest.com.tr", ignoreCase = true)) {
                // Form script'i yerine basit bir cookie ayarlayalım
                val simpleScript = "document.cookie = 'TKF_AUTH_PAGE=1; path=/; max-age=3600';"
                view.evaluateJavascript(simpleScript, null)
                // Set auth page cookie for: $url
            }
        } else {
            // Login sayfası değil, basit bir form check enjekte edelim
            try {
                val formScript = """
                    (function() {
                        // Daha önce monitor edilmişse tekrar yapma
                        if (window._tkfFormMonitor) return;
                        window._tkfFormMonitor = true;
                        
                        // Sayfa form içeriyor mu kontrol et
                        var forms = document.querySelectorAll('form');
                        if (forms.length > 0) {
                            console.log("TKF-DEBUG: Form found with action: " + forms[0].action);
                        }
                        return "Basic form check complete";
                    })();
                """
                
                // Form script'ini enjekte et - performans odaklı
                view.evaluateJavascript(formScript) { result ->
                    // Form script injection result: $result
                }
            } catch (e: Exception) {
                // Error injecting basic form script: ${e.message}
            }
        }
        
        // JavaScript minimalist form hendlerı ekle - sadece oturumun açık kalması için gerekli
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScriptInjectionTime > 2000) { // Süreyi uzattık
            lastScriptInjectionTime = currentTime
            
            // Sadece ana thread için önemli bir boğulluk varsa scriptleri geciktirme
            val startTime = System.currentTimeMillis()
            val minimalistScript = "if(!window._tkfBasic){window._tkfBasic=true;}"            
            view.evaluateJavascript(minimalistScript) { result ->
                val endTime = System.currentTimeMillis()
                if (endTime - startTime > 50) { // 50ms'den uzun sürerse log kaydet
                    // Script injection took ${endTime - startTime}ms
                }
            }
        }
        
        onPageFinished(tab.id, url, null)
    }
    
    override fun onReceivedError(
        view: WebView, 
        request: WebResourceRequest, 
        error: WebResourceError
    ) {
        // Sadece ana çerçeve yükleme hatalarını raporla, kaynakları (resimler, css vb.) görmezden gel
        if (request.isForMainFrame) {
            // Error loading page: ${error.description}, code: ${error.errorCode}
            onReceivedError(error.errorCode, error.description.toString(), request.url.toString())
        }
    }
    
    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        // SSL Error: ${error.primaryError}
        
        // If our handler decides to proceed, we proceed; otherwise, we cancel
        if (onReceivedSslError(error)) {
            handler.proceed()
        } else {
            handler.cancel()
        }
    }
    
    /**
     * Login sayfası olup olmadığını URL içeriğine göre kontrol et
     */
    private fun isLoginPage(url: String): Boolean {
        return url.contains("login", ignoreCase = true) ||
               url.contains("signin", ignoreCase = true) ||
               url.contains("auth", ignoreCase = true) ||
               url.contains("account", ignoreCase = true) ||
               url.contains("szutest.com.tr", ignoreCase = true)
    }
    
    override fun shouldOverrideUrlLoading(
        view: WebView, 
        request: WebResourceRequest
    ): Boolean {
        // URL'yi mevcut WebView'de yükle
        val url = request.url.toString()
        
        // GOOGLE ARAMA URL KONTROLÜ - Google aramasını engelleme sorununu düzelt
        if (url.contains("google.com/search") || url.contains("google.com.tr/search")) {
            // Google araması tespiti, varsayılan davranışa izin ver
            // POST istekleri için çerez ayarlarını güçlendirelim
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(view, true)
            cookieManager.flush()
            
            // Sayfayı yükle
            view.post {
                view.loadUrl(url)
            }
            return true
        }
        
        // Eğer URL'de form gönderimi varsa - POST istekleri için WebView'in kendi işlemini kullan
        if (request.method == "POST") {
            // Form submission detected to: $url, letting WebView handle it
            
            // POST istekleri için çerez ayarlarını güçlendirelim - minimalist yaklaşım
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(view, true)
            cookieManager.flush()
            
            // Form oturum işlemiyse daha fazla bir şey yapma
            if (isLoginPage(url)) {
                // Login form submission detected to: $url
                view.evaluateJavascript("document.cookie = 'TKF_FORM_SUBMIT=1; path=/; max-age=300';", null)
            }
            
            return false // WebView'in varsayılan işlemini kullan
        }
        
        // Eğer URL'de tel:, mailto:, sms: gibi özel protokoller varsa, sisteme devret
        if (url.startsWith("tel:") || 
            url.startsWith("mailto:") || 
            url.startsWith("sms:") ||
            url.startsWith("intent:") ||
            url.startsWith("geo:")) {
            return true 
        }
        
        // Aynı alan adı içinde kalıyorsa varsayılan davranışı kullan (daha hızlı)
        // Eğer mevcut domainde kalıyorsa işlemi hızlandırmak için direkt WebView'e bırak
        val currentHost = Uri.parse(view.url ?: "").host ?: ""
        val newHost = Uri.parse(url).host ?: ""
        
        if (currentHost.isNotEmpty() && currentHost == newHost) {
            // Oturum çerezlerinin korunması için flush çağrısı yap
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.flush()
            return false // WebView'in varsayılan işlemini kullan
        }
        
        // Farklı alan adına gidiyorsa manuel olarak yönlendir
        if (url.startsWith("http:") || url.startsWith("https:")) {
            // Sayfa yüklenmeden önce performans ayarları
            view.settings.blockNetworkImage = true
            
            // Çerezlerin saklanmasını sağla
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.flush() // Mevcut çerezleri kaydet
            
            // Ana thread'i bloklamamak için asenkron yükle
            view.post {
                view.loadUrl(url)
            }
            return true
        }
        
        // Varsayılan davranış
        return false
    }
}