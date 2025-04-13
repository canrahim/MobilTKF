package com.asforce.asforcetkf2.adapter

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.Filter
import android.widget.Filterable
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcetkf2.R
import com.asforce.asforcetkf2.model.device.DeviceItem
import com.google.android.material.card.MaterialCardView
import java.util.Locale

/**
 * Modern kart görünümlü cihaz listesi adaptörü
 */
class DeviceAdapter(
    private val context: Context,
    private val deviceList: MutableList<DeviceItem>
) : RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder>(), Filterable {

    private val TAG = "DeviceAdapter"
    private val prefs: SharedPreferences = context.getSharedPreferences("DeviceFavorites", Context.MODE_PRIVATE)
    
    // Filtreleme için
    private var filteredList: MutableList<DeviceItem> = ArrayList()
    private var showOnlyFavorites = true  // Varsayılan olarak sadece favorileri göster
    
    init {
        // Başlangıçta sadece favorileri filtrele
        val favoritesOnly = deviceList.filter { it.isFavorite }
        filteredList.addAll(favoritesOnly)
    }
    
    // Arama için filtre
    private val deviceFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val filteredDevices = ArrayList<DeviceItem>()
            
            // Önce favorileri filtrele
            val baseList = if (showOnlyFavorites) {
                deviceList.filter { it.isFavorite }
            } else {
                deviceList
            }
            
            // Sonra arama filtresi uygula
            if (constraint == null || constraint.isEmpty()) {
                filteredDevices.addAll(baseList)
            } else {
                val filterPattern = constraint.toString().lowercase(Locale.getDefault()).trim()
                baseList.forEach {
                    if (it.name.lowercase(Locale.getDefault()).contains(filterPattern)) {
                        filteredDevices.add(it)
                    }
                }
            }
            
            return FilterResults().apply { 
                values = filteredDevices
                count = filteredDevices.size
            }
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            filteredList = (results?.values as? List<DeviceItem>)?.toMutableList() ?: mutableListOf()
            notifyDataSetChanged()
        }
    }
    
    class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardView: MaterialCardView = view as MaterialCardView
        val deviceName: TextView = view.findViewById(R.id.deviceName)
        val favoriteIcon: ImageView = view.findViewById(R.id.favoriteIcon)
        val deviceIcon: ImageView = view.findViewById(R.id.deviceIcon)
        val deviceCheckbox: CheckBox = view.findViewById(R.id.deviceCheckbox)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_device_card, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = filteredList[position]
        
        // Cihaz adını ayarla
        holder.deviceName.text = device.name
        
        // Kart seçili durumunu ayarla
        holder.cardView.isChecked = device.isSelected
        
        // Favori ikonunu ayarla
        holder.favoriteIcon.setImageResource(
            if (device.isFavorite) android.R.drawable.btn_star_big_on
            else android.R.drawable.btn_star_big_off
        )
        
        // Karta tıklama - seçim durumunu değiştir
        holder.cardView.setOnClickListener {
            device.isSelected = !device.isSelected
            holder.cardView.isChecked = device.isSelected
            Log.d(TAG, "Device selected: ${device.name}, isSelected: ${device.isSelected}")
        }
        
        // Favori ikonuna tıklama
        holder.favoriteIcon.setOnClickListener {
            val newFavoriteStatus = !device.isFavorite
            device.isFavorite = newFavoriteStatus
            holder.favoriteIcon.setImageResource(
                if (newFavoriteStatus) android.R.drawable.btn_star_big_on
                else android.R.drawable.btn_star_big_off
            )
            
            // Favori durumunu kaydet
            saveFavoriteStatus(device.id, newFavoriteStatus)
            Log.d(TAG, "Favorite status updated: ${device.name}, isFavorite: $newFavoriteStatus")
            
            // Eğer sadece favoriler gösteriliyorsa ve bir favori kaldırıldıysa, listeyi güncelle
            if (showOnlyFavorites && !newFavoriteStatus) {
                filteredList.removeAt(position)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, filteredList.size)
            }
        }
    }

    override fun getItemCount(): Int = filteredList.size

    // Favorileri göster/gizle
    fun toggleFavorites(showOnlyFavorites: Boolean) {
        this.showOnlyFavorites = showOnlyFavorites
        filter.filter("")  // Mevcut filtreyi yeniden uygula
    }
    
    // Tüm öğeleri seç/seçimi kaldır
    fun toggleSelectAll(selectAll: Boolean) {
        for (device in filteredList) {
            device.isSelected = selectAll
        }
        notifyDataSetChanged()
    }
    
    // Seçili cihazları getir
    fun getSelectedDevices(): List<DeviceItem> {
        return deviceList.filter { it.isSelected }
    }

    // Favori durumunu kaydet
    private fun saveFavoriteStatus(deviceId: String, isFavorite: Boolean) {
        val editor = prefs.edit()
        editor.putBoolean(deviceId, isFavorite)
        editor.apply()
        Log.d(TAG, "Saving favorite status: DeviceId=$deviceId, isFavorite=$isFavorite")
    }
    
    override fun getFilter(): Filter = deviceFilter
}