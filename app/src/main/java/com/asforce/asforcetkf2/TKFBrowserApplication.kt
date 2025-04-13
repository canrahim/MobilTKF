package com.asforce.asforcetkf2

import android.app.Application
import android.content.Context
import android.webkit.WebView
import timber.log.Timber

class TKFBrowserApplication : Application() {
    
    companion object {
        // Instead of using BuildConfig.DEBUG, hardcode this value for now
        // This would be replaced by the actual BuildConfig in a complete project
        private const val DEBUG_MODE = true
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Timber for logging
        if (DEBUG_MODE) {
            Timber.plant(Timber.DebugTree())
        }
        
        // Enable WebView debugging if in debug build
        if (DEBUG_MODE) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        
        // Çerezlerin kalıcı olarak saklanması için ayarla
        setupCookieManager()
    }
    
    /**
     * Çerezlerin kalıcı olarak saklanması için CookieManager'ı yapılandırır
     */
    private fun setupCookieManager() {
        val cookieManager = android.webkit.CookieManager.getInstance()
        
        // Çerezlerin kabul edilmesi ve kalıcı olması için ayarlar
        cookieManager.setAcceptCookie(true)
        
        // Gizli mod yerine normal mod kullanarak oturum bilgilerinin saklanmasını sağla
        android.webkit.WebView.setWebContentsDebuggingEnabled(DEBUG_MODE)
        
        // Cookie Manager önbellek modunu ayarla
        val cookieSyncMgr = Class.forName("android.webkit.CookieSyncManager")
        val createInstance = cookieSyncMgr.getMethod("createInstance", Context::class.java)
        createInstance.invoke(null, this)
        
        // Çerezlerin kalıcı olarak depolanması için flush işlemi
        cookieManager.flush()
        
        Timber.d("Cookie Manager initialized to persistently store cookies")
        
        // Kalıcı depolama modunu kontrol et
        val acceptCookie = cookieManager.acceptCookie()
        Timber.d("Cookie acceptance status: $acceptCookie")
    }
}
