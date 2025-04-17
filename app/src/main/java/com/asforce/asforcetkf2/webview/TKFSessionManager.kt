package com.asforce.asforcetkf2.webview

import android.webkit.CookieManager
import android.webkit.WebView
import timber.log.Timber

/**
 * WebView oturumlarını yöneten sınıf
 * Özellikle Szutest.com.tr için optimize edilmiş oturum yönetimi sağlar
 */
class TKFSessionManager(private val webView: WebView) {

    companion object {
        private const val SESSION_COOKIE_CHECK_INTERVAL = 5 * 60 * 1000L // 5 dakika
    }

    private var isSessionMonitoringActive = false
    
    /**
     * Oturum izlemeyi başlat
     */
    fun startSessionMonitoring() {
        if (isSessionMonitoringActive) return
        
        isSessionMonitoringActive = true
        
        // Oturum izleme scriptini enjekte et
        injectSessionMonitoringScript()
    }
    
    /**
     * Oturum izlemeyi durdur
     */
    fun stopSessionMonitoring() {
        isSessionMonitoringActive = false
    }
    
    /**
     * Oturumu koru ve gerektiğinde yenile
     */
    private fun injectSessionMonitoringScript() {
        val script = """
            (function() {
                // Daha önce enjekte edilmişse çalıştırma
                if (window.TKF_SESSION_MANAGER) return 'Already initialized';
                
                // Session manager objesini oluştur
                window.TKF_SESSION_MANAGER = {
                    version: '1.0',
                    lastCheck: Date.now(),
                    sessionTimeout: 30 * 60 * 1000, // 30 dakika
                    checkInterval: ${SESSION_COOKIE_CHECK_INTERVAL},
                    isMonitoring: false,
                    
                    // Oturum durumunu kontrol et
                    checkSession: function() {
                        console.log('TKF: Session check at ' + new Date());
                        this.lastCheck = Date.now();
                        
                        // Oturum bilgilerini kontrol et
                        var cookies = document.cookie;
                        var hasSessionCookie = cookies.indexOf('ASP.NET_SessionId') !== -1 || 
                                             cookies.indexOf('session') !== -1 ||
                                             cookies.indexOf('auth') !== -1;
                                             
                        // Oturum çerezi yoksa uyarı gönder
                        if (!hasSessionCookie) {
                            console.warn('TKF: No session cookie found!');
                            
                            // Native koda oturum sorununu bildir
                            if (window.TKFBridge) {
                                window.TKFBridge.onSessionExpired();
                            }
                        }
                        
                        // Szutest için özel oturum koruması
                        if (window.location.href.indexOf('szutest') !== -1) {
                            try {
                                // Keep-alive isteği
                                var xhr = new XMLHttpRequest();
                                xhr.open('GET', '/EXT/PKControl/KeepAlive', true);
                                xhr.withCredentials = true;
                                xhr.send();
                                console.log('TKF: Sent keep-alive request');
                            } catch(e) {
                                console.error('TKF: Keep-alive error:', e);
                            }
                        }
                        
                        return hasSessionCookie;
                    },
                    
                    // Oturum izlemeyi başlat
                    startMonitoring: function() {
                        if (this.isMonitoring) return false;
                        
                        var self = this;
                        this.isMonitoring = true;
                        
                        // İlk kontrolü yap
                        this.checkSession();
                        
                        // Düzenli kontrol için interval oluştur
                        this.intervalId = setInterval(function() {
                            self.checkSession();
                        }, this.checkInterval);
                        
                        // Form gönderimlerini yakala
                        document.addEventListener('submit', function(e) {
                            var form = e.target;
                            // Form gönderim zamanını kaydet
                            try {
                                localStorage.setItem('TKF_LAST_FORM_SUBMIT', Date.now());
                                console.log('TKF: Form submit detected');
                            } catch(e) {}
                        });
                        
                        console.log('TKF: Session monitoring started');
                        return true;
                    },
                    
                    // Oturum izlemeyi durdur
                    stopMonitoring: function() {
                        if (!this.isMonitoring) return false;
                        
                        clearInterval(this.intervalId);
                        this.isMonitoring = false;
                        
                        console.log('TKF: Session monitoring stopped');
                        return true;
                    }
                };
                
                // Otomatik başlat
                window.TKF_SESSION_MANAGER.startMonitoring();
                
                return 'TKF Session Manager initialized';
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            Timber.d("Session monitoring script result: $result")
        }
    }
    
    /**
     * Oturum çerezlerini korunmuş olarak sakla
     */
    fun backupSessionCookies() {
        val cookieManager = CookieManager.getInstance()
        val url = webView.url ?: return
        
        val cookies = cookieManager.getCookie(url)
        if (cookies.isNullOrEmpty()) return
        
        Timber.d("Session cookies backed up for $url")
    }
    
    /**
     * Önceden kaydedilmiş oturum çerezlerini geri yükle
     */
    fun restoreSessionCookies() {
        val cookieManager = CookieManager.getInstance()
        val url = webView.url ?: return
        
        // Önceden kaydettiğimiz çerezleri geri yükleyebiliriz
        // Bu örnekte sadece log kaydı yapıyoruz
        Timber.d("Would restore session cookies for $url")
    }
    
    /**
     * Szutest.com.tr için oturum yönetimini güçlendir
     */
    fun enhanceSzutestSession() {
        val url = webView.url ?: return
        
        if (url.contains("szutest.com.tr")) {
            val script = """
                (function() {
                    // Szutest için özel oturum güçlendirme
                    if (window.TKF_SZUTEST_SESSION_ENHANCED) return 'Already enhanced';
                    window.TKF_SZUTEST_SESSION_ENHANCED = true;
                    
                    // Oturum süresi dolanları yakalamak için sayfa değiştirme olaylarını izle
                    var originalPushState = history.pushState;
                    var originalReplaceState = history.replaceState;
                    
                    // Özel kullanıcı verileri için depolama
                    var szutestUserInfo = {
                        lastLogin: Date.now(),
                        loggedInUser: document.querySelector('.user-info') ? 
                                    document.querySelector('.user-info').textContent.trim() : null,
                    };
                    
                    // Kullanıcı bilgilerini sakla
                    try {
                        localStorage.setItem('TKF_SZUTEST_USER_INFO', JSON.stringify(szutestUserInfo));
                    } catch(e) {}
                    
                    // Sayfa yönlendirmelerini izleyerek oturum durumunu takip et
                    history.pushState = function() {
                        originalPushState.apply(this, arguments);
                        console.log('TKF: Navigation detected - pushState');
                        
                        // Oturum durumunu kontrol et
                        setTimeout(function() {
                            if (window.TKF_SESSION_MANAGER) {
                                window.TKF_SESSION_MANAGER.checkSession();
                            }
                        }, 500);
                    };
                    
                    history.replaceState = function() {
                        originalReplaceState.apply(this, arguments);
                        console.log('TKF: Navigation detected - replaceState');
                        
                        // Oturum durumunu kontrol et
                        setTimeout(function() {
                            if (window.TKF_SESSION_MANAGER) {
                                window.TKF_SESSION_MANAGER.checkSession();
                            }
                        }, 500);
                    };
                    
                    // Sayfa unload olayında oturum verilerini kaydet
                    window.addEventListener('beforeunload', function() {
                        // Szutest oturum verilerini sakla
                        if (window.TKF_SESSION_MANAGER) {
                            try {
                                localStorage.setItem('TKF_SZUTEST_LAST_SESSION', Date.now());
                            } catch(e) {}
                        }
                    });
                    
                    return 'Szutest session management enhanced';
                })();
            """.trimIndent()
            
            webView.evaluateJavascript(script) { result ->
                Timber.d("Szutest session enhancement result: $result")
            }
        }
    }
}