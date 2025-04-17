package com.asforce.asforcetkf2.webview

import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import com.asforce.asforcetkf2.model.Tab
import timber.log.Timber
import java.lang.ref.WeakReference

/**
 * TKF Tarayıcı için WebView performans ve bellek optimizasyonu
 * Szutest.com.tr uygulaması ile uyumlu geliştirilmiş versiyon 2.0
 */
class TKFWebViewOptimizer(webView: WebView) : WebViewOptimizer(webView) {
    
    companion object {
        // Statik optimizer instance, her bir WebView için ayrı ayrı oluşturmamak için
        @Volatile
        private var sharedOptimizer: TKFWebViewOptimizer? = null
        
        /**
         * Singleton Optimizer alıcı - bellek tasarrufu için
         */
        fun getInstance(webView: WebView): TKFWebViewOptimizer {
            return sharedOptimizer ?: synchronized(this) {
                sharedOptimizer ?: TKFWebViewOptimizer(webView).also { sharedOptimizer = it }
            }
        }
    }
    
    // WebViewOptimizer'dan miras alındığı için buradaki metotlar kaldırıldı
    
    // Ana optimizasyon metodları WebViewOptimizer'dan miras alındı
    // Burada sadece Szutest.com.tr için özel ek optimizasyonlar yer alacak
    
    /**
     * Szutest.com.tr için özel optimizasyon
     * Form ve oturum işlevlerini optimize eder
     */
    fun optimizeForSzutest(webView: WebView) {
        // Tam sayfa yüklenmesini zorla
        webView.evaluateJavascript("""
            (function() {
                // Sayfa yükleme durumunu kontrol et
                if (document.readyState !== 'complete') {
                    console.log('TKF: Forcing complete page load');
                    // DOM yüklendiğinde tekrar dene
                    window.addEventListener('DOMContentLoaded', function() {
                        setTimeout(function() {
                            // MutationObserver hatası için koruma ekle
                            try {
                                if (document.body) {
                                    console.log('TKF: Re-triggering load event');
                                    // Sayfa yüklenme olayını manuel olarak tetikle
                                    var loadEvent = new Event('load');
                                    window.dispatchEvent(loadEvent);
                                }
                            } catch(e) {
                                console.error('TKF: Error forcing load:', e);
                            }
                        }, 500);
                    });
                }
                
                // MutationObserver hatasını düzelt
                if (window.TKF_SZUTEST_OPTIMIZED) return "Already optimized";
                window.TKF_SZUTEST_OPTIMIZED = true;
                
                // Orijinal MutationObserver'ı düzeltilmiş versiyonla değiştir
                try {
                    var originalMutationObserver = window.MutationObserver;
                    window.MutationObserver = function(callback) {
                        var observer = new originalMutationObserver(callback);
                        var originalObserve = observer.observe;
                        
                        // observe metodunu güvenli hale getir
                        observer.observe = function(target, options) {
                            // target bir Node mu kontrol et
                            if (target && target.nodeType) {
                                originalObserve.call(this, target, options);
                            } else {
                                console.warn('TKF: Prevented MutationObserver error - invalid target:', target);
                            }
                        };
                        
                        return observer;
                    };
                    console.log('TKF: MutationObserver patched');
                } catch(e) {
                    console.error('TKF: Error patching MutationObserver:', e);
                }
                
                // Form alanları için otomatik doldurma
                function enhanceForms() {
                    var forms = document.querySelectorAll('form');
                    console.log('TKF: Szutest forms found: ' + forms.length);
                    
                    forms.forEach(function(form) {
                        // Form submit olayını takip et
                        form.addEventListener('submit', function(e) {
                            // Form verilerini topla (şifre alanları hariç)
                            var formData = {};
                            var inputs = form.querySelectorAll('input:not([type="password"])');
                            inputs.forEach(function(input) {
                                if (input.name && input.value) {
                                    formData[input.name] = input.value;
                                }
                            });
                            
                            // Form verilerini sakla
                            try {
                                localStorage.setItem('TKF_SZUTEST_FORM_' + (form.id || form.action), JSON.stringify(formData));
                            } catch(e) {}
                        });
                        
                        // Kaydedilmiş verileri geri yükle
                        try {
                            var savedData = localStorage.getItem('TKF_SZUTEST_FORM_' + (form.id || form.action));
                            if (savedData) {
                                var formData = JSON.parse(savedData);
                                
                                // Form alanlarını doldur
                                Object.keys(formData).forEach(function(key) {
                                    var input = form.querySelector('[name="' + key + '"]');
                                    if (input && !input.value) {
                                        input.value = formData[key];
                                        
                                        // Input olayını tetikle
                                        var event = new Event('input', { bubbles: true });
                                        input.dispatchEvent(event);
                                    }
                                });
                            }
                        } catch(e) {}
                    });
                }
                
                // Oturum koruma ve sayfa tamamlama kontrolü
                function setupSessionKeeper() {
                    // Her 5 dakikada bir oturum yenileme
                    setInterval(function() {
                        // Oturum doğrulama için basit bir istek
                        try {
                            fetch('/EXT/PKControl/KeepAlive', {
                                method: 'GET',
                                credentials: 'include'
                            });
                        } catch(e) {}
                    }, 5 * 60 * 1000);
                    
                    // Eksik içerik kontrolü ve tamamlama
                    setTimeout(function() {
                        // Sayfadaki eksik içerikleri kontrol et
                        var tables = document.querySelectorAll('table');
                        var forms = document.querySelectorAll('form');
                        
                        if ((tables.length === 0 && forms.length > 0) || 
                            document.body.innerHTML.includes("Yükleniyor...") ||
                            document.body.innerHTML.includes("Loading...")) {
                            
                            console.log('TKF: Detected incomplete page - forcing reload');
                            try {
                                // Sayfa yeniden yükleme veya AJAX yenileme yap
                                if (typeof $ !== 'undefined' && $.ajax) {
                                    // jQuery varsa AJAX ile içeriği yenile
                                    $.ajax({
                                        url: window.location.href,
                                        type: 'GET',
                                        success: function(data) {
                                            // İçeriği seçici olarak güncelle
                                            var content = $(data).find('#main-content');
                                            if (content.length) {
                                                $('#main-content').html(content.html());
                                            }
                                        }
                                    });
                                } else {
                                    // AJAX yoksa event tetikle
                                    var loadCompleteEvent = new CustomEvent('tkf_force_complete');
                                    window.dispatchEvent(loadCompleteEvent);
                                }
                            } catch(e) {
                                console.error('TKF: Error completing page:', e);
                            }
                        }
                    }, 3000); // 3 saniye sonra kontrol et
                }
                
                // DOM hazır olduğunda çalıştır
                if (document.readyState === 'complete') {
                    enhanceForms();
                    setupSessionKeeper();
                } else {
                    document.addEventListener('DOMContentLoaded', function() {
                        enhanceForms();
                        setupSessionKeeper();
                    });
                }
                
                return "Szutest optimization applied with MutationObserver fix";
            })();
        """.trimIndent(), null)
        
        // Ek olarak, WebView'a tam sayfa yüklenme garantisi için bir kontrol ekle
        // 5 saniye sonra hala sayfa yüklenmediyse sayfa yenileme işlemi yap
        Handler(Looper.getMainLooper()).postDelayed({
            webView.evaluateJavascript("""
                (function() {
                    // Sayfa içeriğinde eksiklik var mı kontrol et
                    if (document.querySelectorAll('.main-content table').length === 0 && 
                        (document.body.innerHTML.includes("Yükleniyor") || 
                         document.body.innerHTML.length < 5000)) {
                        
                        console.log('TKF: Page incomplete after timeout - refreshing');
                        
                        // Yeniden yükleme yap
                        window.location.reload();
                        return "Page refresh triggered";
                    }
                    return "Page load check completed";
                })();
            """.trimIndent(), null)
        }, 5000) // 5 saniye sonra kontrol et
    }
    
    /**
     * Geliştirilmiş bellek yönetimi - DOM ağacında gereksiz öğeleri temizleme
     */
    fun enhancedDomOptimization(webView: WebView) {
        webView.evaluateJavascript("""
            (function() {
                // DOM optimizasyon işlemlerini batch haline getir - yeniden çizim sayısını azalt
                document.documentElement.style.visibility = 'hidden';
                
                const startTime = performance.now();
                try {
                    // Temizlenecek öğeleri topla - tek seferde DOM manipülasyonu
                    const elementsToClean = [];
                    const offscreenElements = [];
                    
                    // Görünmeyen elementleri işaretle
                    const elements = document.body.querySelectorAll('*');
                    for (let i = 0; i < elements.length; i++) {
                        const el = elements[i];
                        const rect = el.getBoundingClientRect();
                        
                        // Ekrandan 3 ekran boyu uzakta mı?
                        if (rect.top > window.innerHeight * 3 || rect.bottom < -window.innerHeight * 2) {
                            offscreenElements.push(el);
                        }
                        
                        // Görünmeyen büyük DOM elementleri
                        if (el.offsetParent === null && 
                            !['SCRIPT', 'STYLE', 'LINK', 'META'].includes(el.tagName) && 
                            el.childNodes && el.childNodes.length > 10) {
                            elementsToClean.push(el);
                        }
                    }
                    
                    // Batch halinde DOM temizliği
                    elementsToClean.forEach(el => {
                        while (el.firstChild) {
                            el.removeChild(el.firstChild);
                        }
                    });
                    
                    // Ekran dışı elementler için optimizasyon (ağır görseller vb.)
                    offscreenElements.forEach(el => {
                        // Görselleri devre dışı bırak
                        if (el.tagName === 'IMG' && el.src) {
                            el.setAttribute('data-tkf-src', el.src);
                            el.src = '';
                        }
                        // İframe ve gömülü içerikleri durdur
                        else if (el.tagName === 'IFRAME' && el.src) {
                            el.setAttribute('data-tkf-src', el.src);
                            el.src = 'about:blank';
                        }
                        // Video ve sesleri durdur
                        else if (el.tagName === 'VIDEO' || el.tagName === 'AUDIO') {
                            if (el.pause) el.pause();
                        }
                    });
                    
                    // Event temizleme
                    if (window.TKF_cleanupEvents) {
                        window.TKF_cleanupEvents();
                    }
                                        
                } catch(e) {
                    console.error('Enhanced DOM optimization error:', e);
                } finally {
                    document.documentElement.style.visibility = '';
                    console.log('TKF Enhanced DOM optimization completed in ' + 
                              (performance.now() - startTime) + 'ms');
                }
                
                return 'Enhanced DOM optimization applied';
            })();
        """.trimIndent(), null)
    }
}