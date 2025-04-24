package com.asforce.asforcetkf2.webview

import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.JsResult
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.asforce.asforcetkf2.model.Tab
// import timber.log.Timber - removed for performance

/**
 * Custom WebChromeClient for handling JavaScript dialogs, console messages, and file uploads
 * Performans optimizasyonları eklenmiştir
 */
class TKFWebChromeClient(
    private val tab: Tab,
    private val onProgressChanged: (String, Int) -> Unit,
    private val onReceivedTitle: (String, String) -> Unit,
    private val onJsAlert: (String, String, JsResult) -> Boolean,
    private val onJsConfirm: (String, String, JsResult) -> Boolean,
    private val onFileChooser: (ValueCallback<Array<Uri>>) -> Boolean
) : WebChromeClient() {
    
    private var lastProgressUpdate: Long = 0
    
    override fun onProgressChanged(view: WebView, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        
        val currentTime = System.currentTimeMillis()
        
        // İlerleme bildirimi optimizasyonu - aşağıdaki durumlarda bildirimi güncelle:
        // 1. Son güncellemeden bu yana 200ms geçtiğinde (ana thread yükünü azaltma)
        // 2. Yükleme başlangıcında (0-10% arası olduğunda)
        // 3. Yükleme sonunda (90-100% arası olduğunda)
        // 4. %25, %50, %75 gibi önemli noktalarda
        if (currentTime - lastProgressUpdate > 200 || // Zaman aşımı
            newProgress <= 10 || // Başlangıç
            newProgress >= 90 || // Bitiş
            newProgress % 25 == 0 || // Önemli noktalar 
            newProgress >= 100) { // Tamamlanma
            
            lastProgressUpdate = currentTime
            view.post { // UI thread işlerini geciktir
                onProgressChanged(tab.id, newProgress)
            }
            
            // Performans optimizasyonu: %100 tamamlandığında resimlerin gösterilmesine izin ver
            if (newProgress >= 100) {
                view.settings.blockNetworkImage = false
            }
        }
    }
    
    override fun onReceivedTitle(view: WebView, title: String) {
        super.onReceivedTitle(view, title)
        onReceivedTitle(tab.id, title)
    }
    
    override fun onJsAlert(
        view: WebView, 
        url: String, 
        message: String, 
        result: JsResult
    ): Boolean {
        return onJsAlert(url, message, result)
    }
    
    override fun onJsConfirm(
        view: WebView, 
        url: String, 
        message: String, 
        result: JsResult
    ): Boolean {
        return onJsConfirm(url, message, result)
    }
    
    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        // Performans optimizasyonu: 
        // 1. Sadece aktif tab için konsol kayıtları tutulur
        // 2. Önemli uyarı ve hatalar için daha detaylı kayıt, 
        //    normal mesajlar için daha az bilgi (sourceId ve lineNumber gibi)
        if (tab.isActive) {
            when (consoleMessage.messageLevel()) {
                ConsoleMessage.MessageLevel.TIP,
                ConsoleMessage.MessageLevel.LOG,
                ConsoleMessage.MessageLevel.WARNING,
                ConsoleMessage.MessageLevel.ERROR,
                ConsoleMessage.MessageLevel.DEBUG -> {
                    // Console message handling removed for performance
                }
            }
        }
        return true
    }
    
    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
        // Kullanıcı hareketi ile tetiklenen pencereler için odaklanmayı etkinleştir
        if (isUserGesture) {
            view.requestFocus()
        }
        return false
    }

    override fun onRequestFocus(view: WebView) {
        super.onRequestFocus(view)
        view.requestFocus()
    }
    
    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        // Performans optimizasyonu: dosya yükleme işlemini UI thread'den çıkar
        webView.post {
            onFileChooser(filePathCallback)
        }
        return true
    }
}