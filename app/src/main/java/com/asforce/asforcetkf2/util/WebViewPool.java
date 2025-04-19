package com.asforce.asforcetkf2.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * WebView havuz yöneticisi
 * WebView nesneleri oluşturulması ve yeniden kullanılması maliyetli işlemlerdir.
 * Bu sınıf, WebView nesnelerini yönetmek ve yeniden kullanmak için bir havuz sağlar.
 */
public class WebViewPool {
    private static final String TAG = "WebViewPool";
    private static final int MAX_POOL_SIZE = 3;
    
    private static WebViewPool instance;
    private final Deque<WebView> webViewPool = new ArrayDeque<>();
    private final Context appContext;
    
    private WebViewPool(Context context) {
        this.appContext = context.getApplicationContext();
    }
    
    public static synchronized WebViewPool getInstance(Context context) {
        if (instance == null) {
            instance = new WebViewPool(context);
        }
        return instance;
    }
    
    /**
     * Havuzdan bir WebView alır veya yeni bir tane oluşturur
     */
    @SuppressLint("SetJavaScriptEnabled")
    public synchronized WebView acquireWebView() {
        WebView webView;
        
        if (!webViewPool.isEmpty()) {
            webView = webViewPool.pop();
            Log.d(TAG, "WebView havuzdan alındı. Havuzda kalan: " + webViewPool.size());
            
            // Temizleme işlemlerini yap
            webView.stopLoading();
            webView.clearHistory();
            webView.clearCache(true);
            webView.loadUrl("about:blank");
            
            // WebView'in mevcut bir ebeveyni varsa, onu önce kaldır
            ViewParent parent = webView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(webView);
                Log.d(TAG, "WebView eski ebeveyninden kaldırıldı");
            }
        } else {
            webView = new WebView(appContext);
            Log.d(TAG, "Yeni WebView oluşturuldu. Havuz boştu.");
        }
        
        // WebView ayarlarını yapılandır
        configureWebSettings(webView.getSettings());
        
        return webView;
    }
    
    /**
     * Kullanılmış bir WebView'i havuza geri verir
     */
    public synchronized void releaseWebView(WebView webView) {
        if (webView == null) return;
        
        try {
            // WebView'in mevcut bir parent'i varsa önce kaldır
            ViewParent parent = webView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(webView);
                Log.d(TAG, "WebView parent view'dan kaldırıldı");
            }
            
            // WebView'in içeriğini temizle
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.clearHistory();
            webView.clearCache(true);
            
            // Havuzun boyutunu kontrol et
            if (webViewPool.size() < MAX_POOL_SIZE) {
                webViewPool.push(webView);
                Log.d(TAG, "WebView havuza geri verildi. Havuz boyutu: " + webViewPool.size());
            } else {
                Log.d(TAG, "WebView havuzu dolu. WebView atılıyor.");
                webView.destroy();
            }
        } catch (Exception e) {
            Log.e(TAG, "WebView havuza geri verilirken hata oluştu", e);
            // Hata olsa bile WebView'i temizlemeye çalış
            try {
                webView.destroy();
            } catch (Exception ignored) {
                // Yoksay
            }
        }
    }
    
    /**
     * Havuzdaki tüm WebView'leri temizler
     */
    public synchronized void clearPool() {
        while (!webViewPool.isEmpty()) {
            WebView webView = webViewPool.pop();
            webView.destroy();
        }
        Log.d(TAG, "WebView havuzu temizlendi");
    }
    
    /**
     * WebView ayarlarını yapılandır
     */
    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebSettings(WebSettings settings) {
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        
        // Touch ve sürükleme hassasiyeti için gelişmiş ayarlar
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        settings.setTextZoom(100); // Varsayılan metin yakınlaştırma seviyesi
        settings.setDefaultTextEncodingName("UTF-8");
        
        // Kaydırma performansı için optimize edilmiş ayarlar
        // AppCache artık kullanımdan kaldırıldı (API 33+)
        // settings.setAppCacheEnabled(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setEnableSmoothTransition(true); // Geçişleri pürüzsüzleştir
        
        // API 23+ için desteklenen özellik
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            settings.setOffscreenPreRaster(true);  // Hızlı renderlamayı etkinleştir
        }
        
        // Hafıza iyileştirmeleri
        settings.setGeolocationEnabled(false); // Geolocation kapalı (gereksiz)
        
        // Medya ayarları
        settings.setMediaPlaybackRequiresUserGesture(true); // Otomatik oynatmayı devre dışı bırak
    }
}