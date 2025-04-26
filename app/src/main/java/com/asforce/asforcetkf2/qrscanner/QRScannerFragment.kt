package com.asforce.asforcetkf2.qrscanner

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.asforce.asforcetkf2.R
import com.asforce.asforcetkf2.databinding.DialogQrScannerBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.common.util.concurrent.ListenableFuture
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Gelişmiş QR Kod tarayıcı fragment
 */
class QRScannerFragment : DialogFragment() {

    private var _binding: DialogQrScannerBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var flashEnabled = false
    private var currentZoom = 1.0f
    private var scanLineAnimator: ObjectAnimator? = null
    private val handler = Handler(Looper.getMainLooper())
    
    private var qrCodeDetectionCallback: ((String) -> Unit)? = null

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 100
        private const val ANIMATION_DURATION = 2000L // 2 saniye
        
        fun newInstance(callback: (String) -> Unit): QRScannerFragment {
            return QRScannerFragment().apply {
                qrCodeDetectionCallback = callback
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.BottomSheetDialogTheme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogQrScannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Tam ekran gösterim için
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        // Buton işlevlerini ayarla
        setupButtons()
        
        // Tarama animasyonunu başlat
        startScanLineAnimation()
        
        // Kamera izinlerini kontrol et
        if (hasRequiredPermissions()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
        
        // Kamera executor'ı başlat
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun setupButtons() {
        // Flaş açma/kapama butonu
        binding.btnFlashlight.setOnClickListener {
            toggleFlash()
        }
        
        // Yakınlaştırma butonu
        binding.btnZoomIn.setOnClickListener {
            if (currentZoom < 5.0f) {
                currentZoom += 0.5f
                camera?.cameraControl?.setZoomRatio(currentZoom)
                showZoomLevel()
            }
        }
        
        // Uzaklaştırma butonu
        binding.btnZoomOut.setOnClickListener {
            if (currentZoom > 1.0f) {
                currentZoom -= 0.5f
                camera?.cameraControl?.setZoomRatio(currentZoom)
                showZoomLevel()
            }
        }
        
        // Kapatma butonu
        binding.btnClose.setOnClickListener {
            dismiss()
        }
        
        // Galeriden QR kod
        binding.btnGallery.setOnClickListener {
            // Galeriden QR kod seçme işlevi eklenebilir
            Toast.makeText(requireContext(), "Galeri özelliği yakında eklenecek", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFlash() {
        camera?.let {
            flashEnabled = !flashEnabled
            it.cameraControl.enableTorch(flashEnabled)
            
            // Buton metnini ve simgesini güncelle
            val flashIcon = if (flashEnabled) {
                R.drawable.ic_flashlight_on
            } else {
                R.drawable.ic_flashlight_off
            }
            binding.btnFlashlight.apply {
                setIconResource(flashIcon)
                text = if (flashEnabled) "Kapat" else "Flaş"
            }
        }
    }
    
    private fun showZoomLevel() {
        // Zoom seviyesini göster ve otomatik olarak gizle
        val formattedZoom = String.format("%.1fx", currentZoom)
        Toast.makeText(requireContext(), "Zoom: $formattedZoom", Toast.LENGTH_SHORT).show()
    }

    private fun startScanLineAnimation() {
        // Tarama animasyonu için çizgiyi hareket ettir
        binding.scanLine.visibility = View.VISIBLE
        
        // Eğer önceki animasyon varsa durdur
        scanLineAnimator?.cancel()
        
        // QR Scanner overlay'in yüksekliğini al
        val scannerHeight = binding.qrScannerOverlay.height.toFloat()
        
        // QR Scanner overlay'in yüksekliği 0 ise, sonraki frame için bekle
        if (scannerHeight <= 0) {
            binding.qrScannerOverlay.post {
                startScanLineAnimation()
            }
            return
        }
        
        // Animasyonu oluştur
        scanLineAnimator = ObjectAnimator.ofFloat(
            binding.scanLine,
            "translationY",
            0f, // Başlangıç pozisyonu (üst)
            binding.qrScannerOverlay.height.toFloat() // Bitiş pozisyonu (alt)
        ).apply {
            duration = ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            showPermissionDeniedDialog()
        }
    }

    private fun requestCameraPermission() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
    
    private fun showPermissionDeniedDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.permission_required)
            .setMessage(R.string.qr_camera_permission_required)
            .setPositiveButton(R.string.settings) { dialog, _ ->
                // Doğrudan ayarlara yönlendir
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireActivity().packageName, null)
                })
                dismiss()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> 
                dismiss() 
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                
                // Preview ayarlaması
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(binding.previewView.surfaceProvider)
                    }
                
                // Görüntü analizi ayarlaması - yüksek çözünürlükte
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also {
                        it.setAnalyzer(
                            cameraExecutor,
                            QRCodeAnalyzer { qrContent ->
                                // QR kod algılandığında bildir
                                onQRCodeDetected(qrContent)
                            }
                        )
                    }
                
                // Arka kamerayı seç ve yüksek çözünürlük/kalite ayarları
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()
                
                // Kamera kullanım durumlarını ayarla
                val useCaseGroup = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageAnalyzer)
                    .build()
                
                // Kamera bağlantılarını temizle ve yeniden bağla
                cameraProvider.unbindAll()
                
                // Kamerayı başlat
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    useCaseGroup
                )
                
                // Kamera başlangıç ayarları
                camera?.cameraControl?.setLinearZoom(0f) // Başlangıç zoom seviyesi
                
                // Otomatik odaklama için periyodik olarak odaklama yap
                val cameraControl = camera?.cameraControl
                val executor = ContextCompat.getMainExecutor(requireContext())
                
                // 3 saniyede bir otomatik odaklama yap
                handler.postDelayed(object : Runnable {
                    override fun run() {
                        // Merkez noktaya odaklan
                        try {
                            val centerX = binding.previewView.width / 2f
                            val centerY = binding.previewView.height / 2f
                            
                            if (centerX > 0 && centerY > 0) {
                                val factory = binding.previewView.meteringPointFactory
                                val point = factory.createPoint(centerX, centerY)
                                val action = FocusMeteringAction.Builder(point).build()
                                cameraControl?.startFocusAndMetering(action)
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Odaklama hatası")
                        }
                        
                        // Tekrar çağır
                        if (isAdded && !isDetached) {
                            handler.postDelayed(this, 3000) // 3 saniye sonra tekrar
                        }
                    }
                }, 1000) // 1 saniye sonra başlat
                
            } catch (e: Exception) {
                Timber.e(e, "Kamera başlatma hatası")
                Toast.makeText(requireContext(), "Kamera başlatılamadı: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun onQRCodeDetected(content: String) {
        activity?.runOnUiThread {
            // QR kod algılandığında animasyonu göster
            binding.qrDetectedCard.visibility = View.VISIBLE
            binding.tvQrDetected.text = "QR kod başarıyla algılandı!"
            
            // Titreşim ile geri bildirim eklenebilir (opsiyonel)
            // Vibrator etkisi eklemek için gerekli izinler alınmalı
            
            // Kısa bir süre bekleyip sonlandır
            handler.postDelayed({
                qrCodeDetectionCallback?.invoke(content)
                dismiss()
            }, 1000) // 1 saniye göster sonra kapat
        }
    }

    override fun onResume() {
        super.onResume()
        // Animasyonu devam ettir
        if (scanLineAnimator?.isPaused == true) {
            scanLineAnimator?.resume()
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Animasyonu duraklat
        scanLineAnimator?.pause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            // Animasyonu durdur
            scanLineAnimator?.cancel()
            scanLineAnimator = null
            
            // Handler'ı temizle
            handler.removeCallbacksAndMessages(null)
            
            // Kamera kaynaklarını temizle
            cameraExecutor.shutdown()
        } catch (e: Exception) {
            Timber.e(e, "Kaynakları temizlerken hata oluştu")
        } finally {
            _binding = null
        }
    }
}
