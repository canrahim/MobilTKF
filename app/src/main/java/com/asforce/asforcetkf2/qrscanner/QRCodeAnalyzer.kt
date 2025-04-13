package com.asforce.asforcetkf2.qrscanner

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import timber.log.Timber

/**
 * QR kod taraması için görüntü analizi sınıfı
 */
class QRCodeAnalyzer(private val onQRCodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val scanner = BarcodeScanning.getClient()
    
    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage, 
                imageProxy.imageInfo.rotationDegrees
            )
            
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        // QR kod içeriğini kontrol et
                        if (barcode.valueType == Barcode.TYPE_URL || 
                            barcode.valueType == Barcode.TYPE_TEXT) {
                            barcode.rawValue?.let { qrContent ->
                                if (qrContent.isNotEmpty()) {
                                    Timber.d("QR kod içeriği: $qrContent")
                                    onQRCodeDetected(qrContent)
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Timber.e(e, "QR kod işleme hatası")
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
