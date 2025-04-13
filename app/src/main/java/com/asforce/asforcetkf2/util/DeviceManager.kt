package com.asforce.asforcetkf2.util

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import com.asforce.asforcetkf2.R
import com.asforce.asforcetkf2.adapter.DeviceAdapter
import com.asforce.asforcetkf2.model.device.DeviceItem
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import timber.log.Timber
import java.util.HashSet
import java.util.Objects

/**
 * Cihaz listesi yönetimi için manager sınıfı
 */
class DeviceManager(
    private val context: Context,
    private val webView: WebView
) {
    private val TAG = "DeviceManager"
    private val prefs: SharedPreferences = context.getSharedPreferences("DeviceFavorites", Context.MODE_PRIVATE)
    
    /**
     * Cihaz eklemek için WebView'dan cihaz listesini çeker
     */
    fun fetchDeviceList() {
        Timber.d("Cihaz listesi çekiliyor")
        
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
                    throw Exception("Boş veri döndü")
                }

                // JSON'a dönüştür
                val devices = JSONArray(cleaned)
                showDeviceSelectionDialog(devices)

            } catch (e: Exception) {
                Timber.e(e, "Hata: %s", e.message)
                Toast.makeText(
                    context,
                    "Cihaz listesi alınamadı: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Cihaz ID'si için favori durumunu kontrol eder
     */
    fun isDeviceFavorite(deviceId: String): Boolean {
        return prefs.getBoolean(deviceId, false)
    }

    /**
     * Cihaz seçim dialogunu gösterir
     */
    @SuppressLint("SetTextI18n")
    private fun showDeviceSelectionDialog(devices: JSONArray) {
        try {
            val builder = AlertDialog.Builder(context)
            val inflater = LayoutInflater.from(context)
            val dialogView = inflater.inflate(R.layout.dialog_device_selection, null)
            builder.setView(dialogView)

            // View'ları bağla
            val listView: ListView = dialogView.findViewById(R.id.deviceListView)
            val btnSubmit: Button = dialogView.findViewById(R.id.btnSubmit)
            val btnCancel: Button = dialogView.findViewById(R.id.btnCancel)
            val btnToggleFavorites: Button = dialogView.findViewById(R.id.btnToggleFavorites)
            val btnSelectAllFavorites: Button = dialogView.findViewById(R.id.btnSelectAllFavorites)

            // Cihaz listesini hazırla
            val deviceList = ArrayList<DeviceItem>()
            val currentDeviceList = ArrayList<DeviceItem>()
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

            // Başlangıçta sadece favorileri göster
            for (device in deviceList) {
                if (device.isFavorite) {
                    currentDeviceList.add(device)
                }
            }

            // Buton metnini ayarla
            val buttonText = arrayOf("Tümünü Göster", "Favorileri Göster")

            // Adapter'ı ayarla
            val adapter = DeviceAdapter(context, currentDeviceList)
            listView.adapter = adapter
            listView.choiceMode = ListView.CHOICE_MODE_MULTIPLE

            // ListView item tıklama dinleyicisi
            listView.setOnItemClickListener { parent, _, position, _ ->
                val device = parent.getItemAtPosition(position) as DeviceItem
                device.isSelected = !device.isSelected
                adapter.notifyDataSetChanged()
            }

            // Favorileri göster/gizle butonu tıklama dinleyicisi
            btnToggleFavorites.setOnClickListener {
                if (btnToggleFavorites.text.toString() == "Tümünü Göster") {
                    btnToggleFavorites.text = "Favorileri Göster"
                    // Tüm cihazları göster
                    currentDeviceList.clear()
                    currentDeviceList.addAll(deviceList)
                } else {
                    btnToggleFavorites.text = "Tümünü Göster"
                    // Sadece favorileri göster
                    currentDeviceList.clear()
                    for (device in deviceList) {
                        if (device.isFavorite) {
                            currentDeviceList.add(device)
                        }
                    }
                }
                adapter.notifyDataSetChanged()
            }

            btnToggleFavorites.text = buttonText[0] // Başlangıç buton metni

            // Tümünü seç butonu tıklama dinleyicisi
            btnSelectAllFavorites.setOnClickListener {
                var allSelected = true
                for (device in currentDeviceList) {
                    if (!device.isSelected) {
                        allSelected = false
                        break
                    }
                }
                // Tüm cihazların seçim durumunu tersine çevir
                for (device in currentDeviceList) {
                    device.isSelected = !allSelected
                }
                adapter.notifyDataSetChanged()
            }

            val dialog = builder.create()
            dialog.show()

            // Dialog'u tam ekran yap
            dialog.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Ekle butonu tıklama dinleyicisi
            btnSubmit.setOnClickListener {
                val selectedIds = ArrayList<String>()
                for (device in deviceList) {
                    if (device.isSelected) {
                        selectedIds.add(device.id)
                    }
                }

                if (selectedIds.isNotEmpty()) {
                    // Tüm cihazlar seçili mi?
                    val selectAll = selectedIds.size == deviceList.size
                    submitSelectedDevices(selectedIds, selectAll)

                    // Seçimleri sıfırla
                    for (device in deviceList) {
                        device.isSelected = false
                    }

                    // Görüntülenen listeyi sıfırla
                    currentDeviceList.clear()
                    currentDeviceList.addAll(deviceList)
                    adapter.notifyDataSetChanged()
                    dialog.dismiss()
                } else {
                    Toast.makeText(context, "Lütfen en az bir cihaz seçin", Toast.LENGTH_SHORT).show()
                }
            }

            // İptal butonu tıklama dinleyicisi
            btnCancel.setOnClickListener {
                dialog.dismiss()
                // WebView'daki checkbox'ları temizle
                webView.evaluateJavascript(
                    "document.querySelectorAll('input[type=\"checkbox\"]').forEach(cb => cb.checked = false);",
                    null
                )
            }

        } catch (e: JSONException) {
            Timber.e(e, "JSON ayrıştırma hatası: %s", e.message)
            Toast.makeText(context, "Cihaz verisi okunamadı", Toast.LENGTH_SHORT).show()
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

        // Her cihaz ID'si için checkbox'ları işaretle
        for (id in deviceIds) {
            js.append(
                """
                var cbs = form.querySelectorAll('input[value="$id"]');
                if (cbs.length > 0) {
                """.trimIndent()
            )
            // Tüm seçim modu ise, eşleşen tüm checkbox'ları işaretle
            js.append("                if (").append(selectAll).append(") {")
            js.append("                    cbs.forEach(function(cb) { cb.checked = true; });")
            js.append("                    console.log('All checkboxes selected for device ").append(id).append("');")
            js.append("                } else {")
            // Aksi takdirde rastgele birini seç
            js.append("                    var randomIndex = Math.floor(Math.random() * cbs.length);")
            js.append("                    cbs[randomIndex].checked = true;")
            js.append("                    console.log('Selected checkbox index for device ").append(id).append(": ' + randomIndex);")
            js.append("                }")
            js.append("            }")
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
        js.append("})();")

        webView.evaluateJavascript(js.toString()) { result ->
            if ("false" == result) {
                Toast.makeText(context, "Form bulunamadı!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}