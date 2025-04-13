package com.asforce.asforcetkf2.adapter

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.asforce.asforcetkf2.R
import com.asforce.asforcetkf2.model.device.DeviceItem

/**
 * Cihaz listesi için adaptor sınıfı
 */
class DeviceAdapter(
    context: Context,
    private val devices: List<DeviceItem>
) : ArrayAdapter<DeviceItem>(context, android.R.layout.simple_list_item_1, devices) {

    private val TAG = "DeviceAdapter"
    private val inflater: LayoutInflater = LayoutInflater.from(context)
    private val prefs: SharedPreferences = context.getSharedPreferences("DeviceFavorites", Context.MODE_PRIVATE)

    class ViewHolder {
        var textView: CheckedTextView? = null
        var favoriteIcon: ImageView? = null
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        Log.d(TAG, "getView() called for position: $position")
        val device = getItem(position)
        val holder: ViewHolder
        val view: View

        if (convertView == null) {
            Log.d(TAG, "Creating new view")
            view = inflater.inflate(R.layout.item_device, parent, false)

            holder = ViewHolder()
            holder.textView = view.findViewById(android.R.id.text1)
            holder.favoriteIcon = view.findViewById(R.id.favoriteIcon)
            view.tag = holder
        } else {
            Log.d(TAG, "Reusing view")
            view = convertView
            holder = view.tag as ViewHolder
        }

        device?.let {
            Log.d(TAG, "Device: ${it.name}, isSelected: ${it.isSelected}, isFavorite: ${it.isFavorite}")
            holder.textView?.text = it.name
            holder.textView?.isChecked = it.isSelected

            // Favori ikonuna tıklama
            holder.favoriteIcon?.setImageResource(
                if (it.isFavorite) android.R.drawable.btn_star_big_on 
                else android.R.drawable.btn_star_big_off
            )
            
            holder.favoriteIcon?.setOnClickListener { v ->
                Log.d(TAG, "Favorite icon clicked for device: ${device.name}")
                val newFavoriteStatus = !device.isFavorite
                device.isFavorite = newFavoriteStatus
                (v as ImageView).setImageResource(
                    if (newFavoriteStatus) android.R.drawable.btn_star_big_on 
                    else android.R.drawable.btn_star_big_off
                )
                saveFavoriteStatus(device.id, newFavoriteStatus) // Favori durumunu kaydet
                notifyDataSetChanged() // Adaptörü yenile
                Log.d(TAG, "Favorite status updated: ${device.name}, isFavorite: $newFavoriteStatus")
            }

            // Checkbox tıklama
            holder.textView?.setOnClickListener {
                Log.d(TAG, "Checkbox clicked for device: ${device.name}")
                device.isSelected = !device.isSelected
                notifyDataSetChanged() // Adaptörü yenile
                Log.d(TAG, "Checkbox status updated: ${device.name}, isSelected: ${device.isSelected}")
            }
        } ?: Log.w(TAG, "Device is null!")

        Log.d(TAG, "getView() finished for position: $position")
        return view
    }

    // Favori durumunu kaydet
    private fun saveFavoriteStatus(deviceId: String, isFavorite: Boolean) {
        val editor = prefs.edit()
        editor.putBoolean(deviceId, isFavorite)
        editor.apply()
        Log.d(TAG, "Saving favorite status: DeviceId=$deviceId, isFavorite=$isFavorite")
    }
}