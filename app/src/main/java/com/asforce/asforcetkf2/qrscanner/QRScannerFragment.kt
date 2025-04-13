package com.asforce.asforcetkf2.qrscanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import com.asforce.asforcetkf2.R
import com.asforce.asforcetkf2.databinding.DialogQrScannerBinding
import com.google.common.util.concurrent.ListenableFuture
import timber.log.Timber
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * QR Kod tarayıcı fragment
 */
class QRScannerFragment : DialogFragment() {

    private var _binding: DialogQrScannerBinding? = null
    private val binding get() = _binding!!

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var flashEnabled = false
    private var currentZoom = 1.0f
    
    private var qrCodeDetectionCallback: ((String) -> Unit)? = null

    companion object {
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
            }
        }
        
        // Uzaklaştırma butonu
        binding.btnZoomOut.setOnClickListener {
            if (currentZoom > 1.0f) {
                currentZoom -= 0.5f
                camera?.cameraControl?.setZoomRatio(currentZoom)
            }
        }
        
        // Kapatma butonu
        binding.btnClose.setOnClickListener {
            dismiss()
        }
    }

    private fun toggleFlash() {
        camera?.let {
            flashEnabled = !flashEnabled
            it.cameraControl.enableTorch(flashEnabled)
            
            // Simge değiştir
            val flashIcon = if (flashEnabled) {
                R.drawable.ic_flashlight_on
            } else {
                R.drawable.ic_flashlight_off
            }
            binding.btnFlashlight.setImageResource(flashIcon)
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
            Toast.makeText(
                requireContext(),
                R.string.qr_camera_permission_required,
                Toast.LENGTH_LONG
            ).show()
            dismiss()
        }
    }

    private fun requestCameraPermission() {
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
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
                
                // Görüntü analizi ayarlaması
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(
                            cameraExecutor,
                            QRCodeAnalyzer { qrContent ->
                                // QR kod algılandığında kamerayı durdur
                                onQRCodeDetected(qrContent)
                            }
                        )
                    }
                
                // Arka kamerayı seç
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                
                // Kamera bağlantılarını temizle ve yeniden bağla
                cameraProvider.unbindAll()
                
                // Kamerayı başlat
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
                
            } catch (e: Exception) {
                Timber.e(e, "Kamera başlatma hatası")
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun onQRCodeDetected(content: String) {
        activity?.runOnUiThread {
            qrCodeDetectionCallback?.invoke(content)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        _binding = null
    }
}
