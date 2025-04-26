package com.asforce.asforcetkf2.util

import android.app.Dialog
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.view.View as AndroidView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcetkf2.R
import com.asforce.asforcetkf2.adapter.DeviceAdapter
import com.asforce.asforcetkf2.model.device.DeviceItem
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import org.json.JSONArray
import org.json.JSONException
import timber.log.Timber
import java.util.ArrayList
import java.util.HashSet

/**
 * Cihaz listesi yönetimi için modern manager sınıfı
 */
class DeviceManager(
    private val context: Context,
    private val webView: WebView
) {
    private val TAG = "DeviceManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("DeviceFavorites", Context.MODE_PRIVATE)
    
    // Dialog ve bileşenler
    private var dialog: Dialog? = null
    private var deviceAdapter: DeviceAdapter? = null
    private var progressIndicator: CircularProgressIndicator? = null
    private var emptyStateView: AndroidView? = null
    
    /**
     * Cihaz ID'si için favori durumunu kontrol eder
     */
    fun isDeviceFavorite(deviceId: String): Boolean {
        return prefs.getBoolean(deviceId, false)
    }
    
    /**
     * Cihaz eklemek için WebView'dan cihaz listesini çeker
     */
    fun fetchDeviceList() {
        Timber.d("Cihaz listesi çekiliyor")
        
        // Önce modern diyaloğu göster
        showModernDeviceDialog()
        
        progressIndicator?.visibility = View.VISIBLE
        emptyStateView?.visibility = View.GONE
        
        val jsCode = """
            (function() {
              try {
                  let devices = [];
                  document.querySelectorAll('input[name="DeviceIds[]"]').forEach(checkbox => {
                      let label = checkbox.closest('.checkbox')?.querySelector('label')?.textContent;
                      devices.push({
                          id: checkbox.value,
                          name: label?.replace(/\s+/g,' ')?.trim() || 'Unnamed Device'
                      });
                  });
                  return JSON.stringify(devices);
              } catch(e) {
                  return 'ERROR:'+e.message;
              }
            })()
        """.trimIndent()

        webView.evaluateJavascript(jsCode) { result ->
            try {
                // Yükleme göstergesini gizle
                progressIndicator?.visibility = View.GONE
                
                // Log verisi
                Timber.d("Alınan cihaz verisi: $result")

                // Hata kontrolü
                if (result.startsWith("ERROR:")) {
                    throw Exception(result.substring(6))
                }

                // Veriyi temizle
                val cleaned = result
                    .replace("^\"|\"$".toRegex(), "") // Tırnak işaretlerini kaldır
                    .replace("\\\"", "\"")     // Kaçış karakterlerini düzelt
                    .replace("\\n", "")        // Satır sonlarını kaldır
                    .trim()

                // Boş veri kontrolü
                if (cleaned.isEmpty() || cleaned == "null") {
                    emptyStateView?.visibility = View.VISIBLE
                    throw Exception("Boş veri döndü")
                }

                // JSON'a dönüştür
                val devices = JSONArray(cleaned)
                // Modern diyalogu güncelle
                updateDeviceDialog(devices)

            } catch (e: Exception) {
                Timber.e(e, "Hata: %s", e.message)
                Toast.makeText(
                    context,
                    "Cihaz listesi alınamadı: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                
                // Hata durumunda boş durum göster
                emptyStateView?.visibility = View.VISIBLE
            }
        }
    }

    /**
     * Modern cihaz seçim dialogunu gösterir
     */
    @SuppressLint("SetTextI18s")
    private fun showModernDeviceDialog() {
        // Standart Dialog oluştur - AlertDialog yerine
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_device_selection, null)
        dialog = Dialog(context).apply {
            setContentView(dialogView)
            setCancelable(true)
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        
        // Dialog'u göster
        dialog?.show()
        
        // ToolBar'ı ayarla
        val toolbar = dialogView.findViewById<MaterialToolbar>(R.id.topAppBar)
        toolbar.setNavigationOnClickListener {
            dialog?.dismiss()
        }
        
        // Toolbar menu işlemleri
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_refresh -> {
                    // Listeyi yenile
                    fetchDeviceList()
                    true
                }
                R.id.action_filter -> {
                    // Filtreleme seçenekleri
                    showFilterOptions()
                    true
                }
                else -> false
            }
        }
        
        // View'ları bağla
        progressIndicator = dialogView.findViewById(R.id.progressIndicator)
        emptyStateView = dialogView.findViewById(R.id.emptyState)
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.deviceRecyclerView)
        val chipFavorites = dialogView.findViewById<Chip>(R.id.chipFavorites)
        val chipAll = dialogView.findViewById<Chip>(R.id.chipAll)
        val btnSelectAll = dialogView.findViewById<Button>(R.id.btnSelectAll)
        val btnSubmit = dialogView.findViewById<Button>(R.id.btnSubmit)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnCancel)
        val searchEditText = dialogView.findViewById<TextInputEditText>(R.id.searchEditText)

        
        // RecyclerView ayarları
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        // Chip'ler için dinleyiciler
        chipFavorites.setOnClickListener {
            deviceAdapter?.toggleFavorites(true)
        }
        
        chipAll.setOnClickListener {
            deviceAdapter?.toggleFavorites(false)
        }
        
        // Arama için dinleyici
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                deviceAdapter?.filter?.filter(s)
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
        
        // Tümünü seç butonu için dinleyici
        var allSelected = false
        btnSelectAll.setOnClickListener {
            allSelected = !allSelected
            deviceAdapter?.toggleSelectAll(allSelected)
            btnSelectAll.text = if (allSelected) "Seçimi Kaldır" else "Tümünü Seç"
        }
        
        // Ekle butonu için dinleyici
        btnSubmit.setOnClickListener {
            val selectedDevices = deviceAdapter?.getSelectedDevices() ?: emptyList()
            
            if (selectedDevices.isEmpty()) {
                Toast.makeText(context, "Lütfen en az bir cihaz seçin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // Seçilen cihazları WebView formuna gönder
            val selectedIds = selectedDevices.map { it.id }
            submitSelectedDevices(selectedIds, selectedDevices.size == deviceAdapter?.itemCount)
            
            // Dialog'u kapat
            dialog?.dismiss()
        }
        
        // İptal butonu için dinleyici
        btnCancel.setOnClickListener {
            dialog?.dismiss()
        }
        
        // Yükleme göstergesini göster, cihaz listesi gelene kadar
        progressIndicator?.visibility = View.VISIBLE
    }
    
    /**
     * Filtreleme seçeneklerini gösteren dialog
     */
    private fun showFilterOptions() {
        val options = arrayOf("Sadece Favoriler", "Hepsi", "Alfabetik Sırala", "Tarih Sırala")
        val checkedItems = booleanArrayOf(true, false, false, false)
        
        MaterialAlertDialogBuilder(context)
            .setTitle("Filtreleme Seçenekleri")
            .setMultiChoiceItems(options, checkedItems) { _, which, isChecked ->
                // Filtreleme seçeneklerini işle
                when (which) {
                    0 -> deviceAdapter?.toggleFavorites(isChecked)
                    // Diğer filtreleme seçenekleri burada işlenebilir
                }
            }
            .setPositiveButton("Uygula") { _, _ ->
                // Filtreler uygulandı
            }
            .setNegativeButton("İptal", null)
            .show()
    }

    /**
     * Cihaz diyalogunu cihaz listesi ile güncelle
     */
    private fun updateDeviceDialog(devices: JSONArray) {
        try {
            // Cihaz listesini hazırla
            val deviceList = ArrayList<DeviceItem>()
            val deviceIds = HashSet<String>() // Çift kayıtları filtrelemek için

            for (i in 0 until devices.length()) {
                val obj = devices.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val isFavorite = isDeviceFavorite(id)

                if (!deviceIds.contains(id)) {
                    val device = DeviceItem(id, name, false, isFavorite)
                    deviceList.add(device)
                    deviceIds.add(id)
                }
            }
            
            // RecyclerView'ı bul ve adaptörü ayarla
            dialog?.findViewById<RecyclerView>(R.id.deviceRecyclerView)?.let { recyclerView ->
                deviceAdapter = DeviceAdapter(context, deviceList)
                recyclerView.adapter = deviceAdapter
                
                // İlk açılışta sadece favorileri göster
                deviceAdapter?.toggleFavorites(true)
                
                // Yükleme göstergesini gizle
                progressIndicator?.visibility = View.GONE
                
                // Eğer liste boşsa, boş durum göster
                if (deviceList.isEmpty()) {
                    emptyStateView?.visibility = View.VISIBLE
                } else {
                    emptyStateView?.visibility = View.GONE
                }
            }

        } catch (e: JSONException) {
            Timber.e(e, "JSON ayrıştırma hatası: %s", e.message)
            Toast.makeText(context, "Cihaz verisi okunamadı", Toast.LENGTH_SHORT).show()
            
            // Boş durum göster
            emptyStateView?.visibility = View.VISIBLE
        }
    }

    /**
     * Seçilen cihazları WebView formuna gönderir
     */
    private fun submitSelectedDevices(deviceIds: List<String>, selectAll: Boolean) {
        val js = StringBuilder()
        js.append("(function() {")
        // Modal DOM'da olduğundan emin olmak için küçük bir gecikme
        js.append("    setTimeout(function() {")
        js.append("        var form = document.getElementById('AddDeviceForm');")
        js.append("        if (form) {")
        // Önce tüm checkbox'ları temizle
        js.append("            var checkboxes = form.querySelectorAll('input[name=\"DeviceIds[]\"]');")
        js.append("            checkboxes.forEach(function(cb) { cb.checked = false; });")

        // Her cihaz ID'si için checkbox'ları işaretle - aynı cihazdan sadece bir tane seçmek için düzeltildi
        for (id in deviceIds) {
            js.append(
                """
                var cbs = form.querySelectorAll('input[value="$id"]');
                if (cbs.length > 0) {
                    // Her cihaz ID'si için sadece bir checkbox seç
                    cbs[0].checked = true;
                    console.log('Selected first checkbox for device ' + '$id');
                }
                """.trimIndent()
            )
        }

        // Formu gönder butonuna tıklayarak
        js.append("            var submitButton = form.querySelector('button[type=\"submit\"]');")
        js.append("            if (submitButton) {")
        js.append("                submitButton.click();")
        js.append("                console.log('Submit button clicked');")
        js.append("            } else {")
        js.append("                form.submit();")
        js.append("                console.log('Form submitted');")
        js.append("            }")
        js.append("        } else {")
        js.append("            console.log('AddDeviceForm not found');")
        js.append("        }")
        js.append("    }, 300);")
        js.append("    return true;")
        js.append("})();");

        webView.evaluateJavascript(js.toString()) { result ->
            if ("false" == result) {
                Toast.makeText(context, "Form bulunamadı!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}