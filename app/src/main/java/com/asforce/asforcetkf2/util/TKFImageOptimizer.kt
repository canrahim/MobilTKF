package com.asforce.asforcetkf2.util

import android.content.Context
import android.webkit.WebView
import timber.log.Timber

/**
 * WebView içindeki görüntüleri optimize eden sınıf
 * Görüntü yükleme performansını artırır ve 
 * bellek kullanımını azaltır
 * Geliştirilmiş sürüm: Görüntü önbelleği ve sıkıştırma desteği
 */
class TKFImageOptimizer(
    private val context: Context,
    private val webView: WebView
) {
    
    // Görüntü önbellek modulü açık mı?
    private var isImageCacheEnabled = true
    
    // Performans ölçümleri
    private var imageOptimizationStats = mutableMapOf<String, Any>()
    
    /**
     * Görüntü optimizasyonlarını uygula
     * JavaScript ile görüntüleri optimize eder
     */
    fun optimizeImages() {
        val script = """
            (function() {
                // Zaten optimize edilmişse çalıştırma
                if (window.TKF_IMAGES_OPTIMIZED) {
                    return 'Already optimized';
                }
                window.TKF_IMAGES_OPTIMIZED = true;
                
                // Görüntü optimizsyonu fonksiyonları
                var TKFImageOptimizer = {
                    // Görüntüleri tarayıp optimize et
                    optimize: function() {
                        var images = document.querySelectorAll('img:not([data-tkf-optimized])');
                        var viewportHeight = window.innerHeight;
                        var scrollTop = window.scrollY || document.documentElement.scrollTop;
                        
                        console.log('TKF Image Optimizer: Optimizing ' + images.length + ' images');
                        
                        // Her görüntüyü işaretle ve optimize et
                        for (var i = 0; i < images.length; i++) {
                            var img = images[i];
                            img.setAttribute('data-tkf-optimized', 'true');
                            
                            // Görüntü boyutlarını orijinal genişlik/yükseklik veya CSS'ye göre sakla
                            if ((img.naturalWidth > 0 && img.naturalHeight > 0) || 
                                (img.width > 0 && img.height > 0)) {
                                img.setAttribute('data-tkf-width', img.naturalWidth || img.width);
                                img.setAttribute('data-tkf-height', img.naturalHeight || img.height);
                            }
                            
                            // Resmin konum ve görünürlük durumunu kontrol et
                            var rect = img.getBoundingClientRect();
                            var isInViewport = rect.top < viewportHeight * 1.5 && rect.bottom > -viewportHeight * 0.5;
                            
                            if (isInViewport) {
                                // Görünür resimler için eager yükleme
                                img.loading = 'eager';
                                img.decoding = 'sync'; // Hızlı dekodlama
                            } else {
                                // Görünmeyen resimler için lazy loading
                                img.loading = 'lazy';
                                img.decoding = 'async';
                                
                                // Çok uzaktaki resimleri geçici olarak gizle
                                if (rect.top > viewportHeight * 3 || rect.bottom < -viewportHeight * 2) {
                                    if (img.src) {
                                        img.setAttribute('data-tkf-src', img.src);
                                        img.src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg"%3E%3C/svg%3E';
                                    }
                                }
                            }
                            
                            // Büyük resimlerin boyutunu düşür
                            if (img.naturalWidth > 1000 || img.naturalHeight > 1000) {
                                img.style.maxWidth = '100%';
                                img.style.height = 'auto';
                            }
                        }
                        
                        return images.length + ' images optimized';
                    },
                    
                    // Ertelenen resimleri geri yükle
                    restoreDeferredImages: function() {
                        var deferredImages = document.querySelectorAll('img[data-tkf-src]');
                        var viewportHeight = window.innerHeight;
                        var scrollTop = window.scrollY || document.documentElement.scrollTop;
                        
                        for (var i = 0; i < deferredImages.length; i++) {
                            var img = deferredImages[i];
                            var rect = img.getBoundingClientRect();
                            
                            // Görünür alana yaklaşan resimleri yükle
                            if (rect.top < viewportHeight * 2 && rect.bottom > -viewportHeight) {
                                img.src = img.getAttribute('data-tkf-src');
                                img.removeAttribute('data-tkf-src');
                            }
                        }
                    },
                    
                    // Olay dinleyicileri ekle
                    setupListeners: function() {
                        // Kaydırma olayında görüntüleri kontrol et
                        var scrollTimeout;
                        window.addEventListener('scroll', function() {
                            // Performans için throttle
                            clearTimeout(scrollTimeout);
                            scrollTimeout = setTimeout(function() {
                                TKFImageOptimizer.restoreDeferredImages();
                            }, 200);
                        }, { passive: true });
                        
                        // Sayfa boyut değişiminde görüntüleri kontrol et
                        var resizeTimeout;
                        window.addEventListener('resize', function() {
                            clearTimeout(resizeTimeout);
                            resizeTimeout = setTimeout(function() {
                                TKFImageOptimizer.optimize();
                            }, 500);
                        }, { passive: true });
                    }
                };
                
                // İlk optimizasyonu yap
                TKFImageOptimizer.optimize();
                
                // Olay dinleyicileri ekle
                TKFImageOptimizer.setupListeners();
                
                // Görüntü yükleme performansı takibi
                window.addEventListener('load', function() {
                    var imageStats = {
                        total: document.querySelectorAll('img').length,
                        loaded: 0,
                        failed: 0,
                        avgLoadTime: 0,
                        totalLoadTime: 0
                    };
                    
                    // Yüklenen görüntüleri izle
                    var images = document.querySelectorAll('img');
                    images.forEach(function(img) {
                        if (img.complete) {
                            imageStats.loaded++;
                        } else {
                            var startTime = performance.now();
                            
                            img.addEventListener('load', function() {
                                var loadTime = performance.now() - startTime;
                                imageStats.loaded++;
                                imageStats.totalLoadTime += loadTime;
                                imageStats.avgLoadTime = imageStats.totalLoadTime / imageStats.loaded;
                            });
                            
                            img.addEventListener('error', function() {
                                imageStats.failed++;
                            });
                        }
                    });
                    
                    console.log('TKF Image Stats: ' + JSON.stringify(imageStats));
                });
                
                // Tüm görüntü yüklemeleri için sayfa performans ölçümü
                if (window.performance && window.performance.getEntriesByType) {
                    window.addEventListener('load', function() {
                        // 100ms sonra ölçüm yaparak kaynakların yüklenmesini bekle
                        setTimeout(function() {
                            var resources = window.performance.getEntriesByType('resource');
                            var imageResources = resources.filter(function(resource) {
                                return resource.initiatorType === 'img';
                            });
                            
                            var totalImageSize = 0;
                            var totalImageTime = 0;
                            
                            imageResources.forEach(function(resource) {
                                totalImageSize += resource.transferSize || 0;
                                totalImageTime += resource.duration || 0;
                            });
                            
                            console.log('TKF Image Performance: ' + 
                                      imageResources.length + ' images, ' + 
                                      Math.round(totalImageSize / 1024) + ' KB total, ' +
                                      Math.round(totalImageTime) + ' ms total load time');
                        }, 100);
                    });
                }
                
                return 'TKF Image Optimizer active: optimized ' + 
                       document.querySelectorAll('img[data-tkf-optimized]').length + ' images';
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            Timber.d("Image optimization result: $result")
        }
    }
    
    /**
     * Düşük bellek durumunda daha agresif görüntü optimizasyonu
     */
    fun applyLowMemoryImageOptimization() {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        // Bellek kritik durumda mı?
        val isLowMemory = memoryInfo.lowMemory || 
                        (memoryInfo.availMem < memoryInfo.totalMem * 0.2)
        
        if (isLowMemory) {
            val aggressiveScript = """
                (function() {
                    // Görüntüleri agresif optimize et - düşük bellek durumu
                    console.log('TKF: Applying aggressive image optimization (low memory)');
                    
                    var images = document.querySelectorAll('img');
                    var viewportHeight = window.innerHeight;
                    
                    // Aktif görüntü sayısını sınırla
                    var visibleCount = 0;
                    
                    for (var i = 0; i < images.length; i++) {
                        var img = images[i];
                        var rect = img.getBoundingClientRect();
                        
                        // Sadece görünür alandaki resimleri göster
                        var isVisible = rect.top < viewportHeight && rect.bottom > 0;
                        
                        if (isVisible) {
                            visibleCount++;
                            
                            // Görünür alandaki ilk 5 resmi göster, diğerlerini düşük kalitede
                            if (visibleCount <= 5) {
                                if (img.getAttribute('data-tkf-src')) {
                                    img.src = img.getAttribute('data-tkf-src');
                                    img.removeAttribute('data-tkf-src');
                                }
                            } else {
                                // Görünür ama sınırı aşan resimleri düşük kalitede göster
                                if (img.src && !img.getAttribute('data-tkf-src')) {
                                    img.setAttribute('data-tkf-src', img.src);
                                    // Düşük kalite placeholder
                                    img.src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg" width="' + 
                                             (img.width || 100) + '" height="' + (img.height || 100) + 
                                             '"%3E%3Crect width="100%" height="100%" fill="%23eee"/%3E%3C/svg%3E';
                                }
                            }
                        } else {
                            // Görünür olmayan tüm resimleri boşalt
                            if (img.src && !img.getAttribute('data-tkf-src')) {
                                img.setAttribute('data-tkf-src', img.src);
                                img.src = 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg"%3E%3C/svg%3E';
                            }
                        }
                    }
                    
                    return 'Aggressive optimization: ' + visibleCount + ' visible, ' + 
                          (images.length - visibleCount) + ' deferred images';
                })();
            """.trimIndent()
            
            webView.evaluateJavascript(aggressiveScript) { result ->
                Timber.d("Aggressive image optimization result: $result")
            }
        }
    }
    
    /**
     * SVG ve ikon optimizasyonu - sayfayı tarayıp SVG ve ikonları
     * optimize eder - genellikle bu elementler fazla bellek ve işlemci kullanır
     */
    fun optimizeSvgAndIcons() {
        val script = """
            (function() {
                // SVG elementleri için optimizasyon
                var svgs = document.querySelectorAll('svg');
                
                if (svgs.length === 0) {
                    return 'No SVG elements found';
                }
                
                console.log('TKF: Optimizing ' + svgs.length + ' SVG elements');
                
                // Her SVG elementi optimize et
                var optimizedCount = 0;
                
                for (var i = 0; i < svgs.length; i++) {
                    var svg = svgs[i];
                    
                    // Zaten optimize edilmişse atla
                    if (svg.getAttribute('data-tkf-optimized')) continue;
                    
                    // SVG'yi optimize et
                    try {
                        // 1. Gereksiz detayları kaldır
                        svg.querySelectorAll('title, desc, metadata').forEach(function(el) {
                            el.remove();
                        });
                        
                        // 2. Animasyonları dondur
                        svg.querySelectorAll('animate, animateTransform, animateMotion').forEach(function(el) {
                            el.setAttribute('begin', 'indefinite');
                        });
                        
                        // 3. İşaretleme
                        svg.setAttribute('data-tkf-optimized', 'true');
                        
                        optimizedCount++;
                    } catch(e) {
                        console.error('Error optimizing SVG: ' + e.message);
                    }
                }
                
                // İkon fontları optimize et (Font Awesome, Material Icons, vb.)
                var iconElements = document.querySelectorAll('.fa, .material-icons, .icon, [class*="icon-"]');
                var iconCount = 0;
                
                iconElements.forEach(function(icon) {
                    // Görünür alanda değilse işlem yapma
                    var rect = icon.getBoundingClientRect();
                    if (rect.top > window.innerHeight * 2 || rect.bottom < 0) {
                        // İkon stili değiştir veya temizle
                        if (!icon.hasAttribute('data-tkf-style')) {
                            icon.setAttribute('data-tkf-style', icon.getAttribute('style') || '');
                            icon.style.visibility = 'hidden';
                        }
                        iconCount++;
                    } else if (icon.hasAttribute('data-tkf-style')) {
                        // Görünür alana girdiğinde geri yükle
                        icon.setAttribute('style', icon.getAttribute('data-tkf-style') || '');
                        icon.removeAttribute('data-tkf-style');
                    }
                });
                
                return 'Optimized ' + optimizedCount + ' SVGs and ' + iconCount + ' icons';
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            Timber.d("SVG and icon optimization result: $result")
        }
    }
    
    /**
     * Görüntü önbelleği kur
     * WebView içindeki görüntüleri önbellek ile optimize eder
     */
    fun setupImageCache() {
        val script = """
            (function() {
                if (window.TKF_IMAGE_CACHE) return 'Image cache already setup';
                
                // Görüntü önbelleği nesnesi
                window.TKF_IMAGE_CACHE = {
                    items: {},
                    maxSize: 10 * 1024 * 1024, // 10 MB maksimum önbellek
                    currentSize: 0,
                    
                    // Görüntüyü önbelleğe ekle
                    addImage: function(url, blob, size) {
                        // Önbellek dolu mu kontrol et
                        if (this.currentSize + size > this.maxSize) {
                            this.pruneCache(size);
                        }
                        
                        // Görüntüyü önbelleğe ekle
                        this.items[url] = {
                            blob: blob,
                            size: size,
                            timestamp: Date.now()
                        };
                        
                        this.currentSize += size;
                        return true;
                    },
                    
                    // Önbellekten görüntü al
                    getImage: function(url) {
                        const entry = this.items[url];
                        if (entry) {
                            // Son erişim zamanını güncelle
                            entry.timestamp = Date.now();
                            return entry.blob;
                        }
                        return null;
                    },
                    
                    // Önbelleği temizle
                    pruneCache: function(requiredSpace) {
                        // En eski görüntüleri öncelikle temizle
                        const entries = Object.entries(this.items);
                        entries.sort((a, b) => a[1].timestamp - b[1].timestamp);
                        
                        let freedSpace = 0;
                        for (let [url, entry] of entries) {
                            delete this.items[url];
                            freedSpace += entry.size;
                            this.currentSize -= entry.size;
                            
                            if (freedSpace >= requiredSpace) break;
                        }
                        
                        console.log('TKF Image Cache: Pruned ' + freedSpace + ' bytes');
                    }
                };
                
                // Görüntü taleplerini yakala ve optimize et
                const originalFetch = window.fetch;
                window.fetch = function(url, options) {
                    // Görüntü talepleri için önbellek kontrolü
                    if (typeof url === 'string' && 
                        (url.match(/\.(jpg|jpeg|png|gif|webp)(\?.*)?$/i) || 
                        url.indexOf('image') > -1)) {
                        
                        const cachedImage = window.TKF_IMAGE_CACHE.getImage(url);
                        if (cachedImage) {
                            console.log('TKF Image Cache: Hit for ' + url);
                            return Promise.resolve(new Response(cachedImage, {
                                status: 200,
                                statusText: 'OK',
                                headers: new Headers({'Content-Type': 'image/jpeg'})
                            }));
                        }
                        
                        // Önbellekte yoksa getir ve önbelleğe ekle
                        return originalFetch(url, options).then(response => {
                            if (response.ok) {
                                response.clone().blob().then(blob => {
                                    // Sadece belirli bir boyuta kadar önbelleğe al
                                    if (blob.size < 500 * 1024) { // 500 KB'dan küçük görüntüler
                                        window.TKF_IMAGE_CACHE.addImage(url, blob, blob.size);
                                    }
                                });
                            }
                            return response;
                        });
                    }
                    
                    // Görüntü olmayan talepler için normal işlem
                    return originalFetch(url, options);
                };
                
                // Görüntü sıkıştırma fonksiyonu
                window.TKF_compressImage = function(img) {
                    try {
                        // Görünmez tuval oluştur
                        const canvas = document.createElement('canvas');
                        const ctx = canvas.getContext('2d');
                        
                        // Orijinal boyutları al
                        const width = img.naturalWidth;
                        const height = img.naturalHeight;
                        
                        // Görüntü çok büyükse yeniden boyutlandır
                        let targetWidth = width;
                        let targetHeight = height;
                        
                        // Maksimum boyutlar (1000x1000 pikselden büyük görüntüleri küçült)
                        const MAX_SIZE = 1000;
                        
                        if (width > MAX_SIZE || height > MAX_SIZE) {
                            if (width > height) {
                                targetWidth = MAX_SIZE;
                                targetHeight = Math.round(height * (MAX_SIZE / width));
                            } else {
                                targetHeight = MAX_SIZE;
                                targetWidth = Math.round(width * (MAX_SIZE / height));
                            }
                        }
                        
                        // Tuval boyutlarını ayarla
                        canvas.width = targetWidth;
                        canvas.height = targetHeight;
                        
                        // Görüntüyü tuvale çiz
                        ctx.drawImage(img, 0, 0, targetWidth, targetHeight);
                        
                        // Sıkıştırılmış görüntüyü oluştur (0.8 kalite)
                        return canvas.toDataURL('image/jpeg', 0.8);
                    } catch(e) {
                        console.error('Error compressing image:', e);
                        return img.src; // Hata durumunda orijinal görüntüyü kullan
                    }
                };
                
                return 'TKF Image Cache activated';
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            Timber.d("Image cache setup result: $result")
            imageOptimizationStats["cache_enabled"] = true
        }
    }
    
    /**
     * Yüksek kaliteli görüntüleri sıkıştır
     * Büyük görüntüleri ve fotoları bellek tasarrufu için optimize eder
     */
    fun compressHighQualityImages() {
        val script = """
            (function() {
                // Sayfa yüklenme durumunu kontrol et
                if (document.readyState !== 'complete') {
                    console.log('TKF: Page not fully loaded, delaying image compression');
                    return 'Page loading, compression deferred';
                }
                
                var largeImages = [];
                var compressedCount = 0;
                
                // Büyük görüntüleri bul
                var images = document.querySelectorAll('img:not([data-tkf-compressed])');
                
                for (var i = 0; i < images.length; i++) {
                    var img = images[i];
                    
                    // Görüntünün doğal boyutlarını kontrol et
                    if (img.complete && img.naturalWidth > 0) {
                        // Büyük görüntüleri sıkıştır (800x600'den büyükler)
                        if (img.naturalWidth > 800 || img.naturalHeight > 600) {
                            largeImages.push(img);
                        }
                    }
                }
                
                // Asenkron sıkıştırma - browser'da donma olmasın diye
                function compressNext(index) {
                    if (index >= largeImages.length) {
                        console.log('TKF: Compression complete. Compressed ' + compressedCount + ' of ' + largeImages.length + ' large images');
                        return;
                    }
                    
                    var img = largeImages[index];
                    
                    // TKF_compressImage fonksiyonu setupImageCache ile enjekte edildi
                    if (window.TKF_compressImage) {
                        try {
                            var originalSrc = img.src;
                            var compressedSrc = window.TKF_compressImage(img);
                            
                            // Sıkıştırılmış görüntüyü ayarla
                            img.src = compressedSrc;
                            img.setAttribute('data-tkf-compressed', 'true');
                            img.setAttribute('data-tkf-original-src', originalSrc);
                            compressedCount++;
                        } catch (e) {
                            console.error('TKF: Error compressing image:', e);
                        }
                    }
                    
                    // Sonraki görüntüyü işle (100ms gecikme ile tarayıcıyı bloklamayı önle)
                    setTimeout(function() {
                        compressNext(index + 1);
                    }, 100);
                }
                
                // Sıkıştırma işlemine başla
                if (largeImages.length > 0) {
                    compressNext(0);
                }
                
                return 'Found ' + largeImages.length + ' large images to compress';
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            Timber.d("Image compression started: $result")
            imageOptimizationStats["compression_initiated"] = true
        }
    }
    
    /**
     * Optimizasyon istatistiklerini al
     */
    fun getOptimizationStats(): Map<String, Any> {
        return imageOptimizationStats
    }
}