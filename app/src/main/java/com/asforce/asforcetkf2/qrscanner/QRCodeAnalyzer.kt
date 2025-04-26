package com.asforce.asforcetkf2.qrscanner

import android.annotation.SuppressLint
import android.graphics.Rect
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gelişmiş QR kod taraması için görüntü analizi sınıfı
 */
class QRCodeAnalyzer(private val onQRCodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {

    // Optimize edilmiş tarayıcı ayarları
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_QR_CODE,
            Barcode.FORMAT_AZTEC,
            Barcode.FORMAT_DATA_MATRIX
        )
        .enableAllPotentialBarcodes() // Tüm potansiyel barkodları etkinleştir
        .build()
    
    // Scanner tanımla
    private val scanner: BarcodeScanner = BarcodeScanning.getClient(options)
    
    // Tarama durumunu takip etmek için (thread-safe bir şekilde)
    private val isScanning = AtomicBoolean(false)
    
    // Son algılanan kod ve timestamp
    private var lastDetectedCode: String? = null
    private var lastDetectionTime: Long = 0
    private val DETECTION_COOLDOWN = 2000L // 2 saniye
    
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        // Eğer halihazırda tarama işlemi devam ediyorsa, görüntüyü kapat ve çık
        if (isScanning.get()) {
            imageProxy.close()
            return
        }
        
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            // Tarama başlıyor işaretini ayarla
            isScanning.set(true)
            
            try {
                val image = InputImage.fromMediaImage(
                    mediaImage, 
                    imageProxy.imageInfo.rotationDegrees
                )
                
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        if (barcodes.isNotEmpty()) {
                            processBarcodes(barcodes)
                        }
                    }
                    .addOnFailureListener { e ->
                        Timber.e(e, "QR kod işleme hatası")
                    }
                    .addOnCompleteListener {
                        // Tarama bitti işaretini sıfırla
                        isScanning.set(false)
                        imageProxy.close()
                    }
            } catch (e: Exception) {
                Timber.e(e, "Görüntü işleme hatası")
                isScanning.set(false)
                imageProxy.close()
            }
        } else {
            imageProxy.close()
        }
    }
    
    private fun processBarcodes(barcodes: List<Barcode>) {
        // Şu anki zamanı al
        val currentTime = System.currentTimeMillis()
        
        // En yüksek güvenilirlik puanına sahip QR kodu bul
        val bestBarcode = barcodes
            .filter { it.rawValue != null && it.rawValue!!.isNotEmpty() }
            .maxByOrNull { calculateBarcodeConfidence(it) }
        
        bestBarcode?.let { barcode ->
            val qrContent = barcode.rawValue ?: return@let
            
            // Aynı kodu tekrar tekrar göndermemek için kontrol et
            if (qrContent != lastDetectedCode || 
                (currentTime - lastDetectionTime) > DETECTION_COOLDOWN) {
                
                Timber.d("QR kod algılandı: $qrContent (Tip: ${getBarcodeTypeText(barcode.valueType)})")
                
                // Son algılanan kodu ve zamanı güncelle
                lastDetectedCode = qrContent
                lastDetectionTime = currentTime
                
                // Geri bildirim fonksiyonunu çağır
                onQRCodeDetected(qrContent)
            }
        }
    }
    
    // Barkod güvenilirliğini hesapla (alan büyüklüğü, kodun uzunluğu vb. fakterlere göre)
    private fun calculateBarcodeConfidence(barcode: Barcode): Float {
        var confidence = 1.0f
        
        // Algılanan alan büyüklüğüne göre güvenilirliği artır
        barcode.boundingBox?.let { box ->
            // Daha büyük bir alana sahip kodlar daha güvenilir olma eğilimindedir
            confidence += (box.width() * box.height()) / 10000f
        }
        
        // İçerik uzunluğuna göre güvenilirliği artır
        barcode.rawValue?.let { value ->
            // Makul bir uzunlukta içeriğe sahip kodlar daha güvenilir olabilir
            // Çok kısa veya çok uzun değiller
            if (value.length in 5..200) {
                confidence += 0.5f
            }
        }
        
        return confidence
    }
    
    // Barkod tip bilgisini metin olarak dönüştür
    /**
     * Barkod tipini metin olarak dönüştürür
     * 
     * @param valueType Barkod değer tipi
     * @return Tip için okunabilir ad
     */
    private fun getBarcodeTypeText(valueType: Int): String {
        return when (valueType) {
            Barcode.TYPE_URL -> "URL"
            Barcode.TYPE_TEXT -> "Metin"
            Barcode.TYPE_CONTACT_INFO -> "Kişi"
            Barcode.TYPE_EMAIL -> "E-Posta"
            Barcode.TYPE_PHONE -> "Telefon"
            Barcode.TYPE_SMS -> "SMS"
            Barcode.TYPE_WIFI -> "Wi-Fi"
            Barcode.TYPE_GEO -> "Konum"
            Barcode.TYPE_CALENDAR_EVENT -> "Takvim"
            Barcode.TYPE_DRIVER_LICENSE -> "Ehliyet"
            Barcode.TYPE_PRODUCT -> "Ürün"
            else -> "Bilinmeyen"
        }
    }
}
