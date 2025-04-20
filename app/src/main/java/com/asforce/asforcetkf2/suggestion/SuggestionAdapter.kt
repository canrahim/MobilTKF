package com.asforce.asforcetkf2.suggestion

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcetkf2.R
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import timber.log.Timber

/**
 * Horizontal adapter for suggestions shown as chips
 */
class SuggestionAdapter(
    private val context: Context,
    private var suggestions: MutableList<String>,
    private val inputKey: String,
    private val onSuggestionSelected: (String) -> Unit,
    private val onSuggestionDeleted: (String, Int) -> Unit
) : RecyclerView.Adapter<SuggestionAdapter.SuggestionViewHolder>() {

    private val TAG = "SuggestionAdapter"

    class SuggestionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val suggestionChip: Chip = view.findViewById(R.id.suggestion_chip)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SuggestionViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_suggestion_chip, parent, false)
        Timber.d("[SUGGESTION] Creating new suggestion view holder")
        return SuggestionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SuggestionViewHolder, position: Int) {
        // Eğer öneriler boşsa, "hiçbir öneri yok" mesajını göster
        if (suggestions.isEmpty()) {
            Timber.d("[SUGGESTION] Showing no suggestions message")
            holder.suggestionChip.text = context.getString(R.string.no_suggestions)
            holder.suggestionChip.isCloseIconVisible = false
            holder.suggestionChip.setOnClickListener(null)
            return
        }
        
        val suggestion = suggestions[position]
        Timber.d("[SUGGESTION] Binding suggestion at position $position: '$suggestion'")
        
        // Set text on chip
        holder.suggestionChip.text = suggestion
        
        // Handle click on chip - select the suggestion
        holder.suggestionChip.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                Timber.d("[SUGGESTION] Suggestion clicked: ${suggestions[pos]}")
                onSuggestionSelected(suggestions[pos])
            }
        }
        
        // Handle click on close icon - show delete confirmation dialog
        holder.suggestionChip.setOnCloseIconClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val suggestionText = suggestions[pos]
                Timber.d("[SUGGESTION] Delete icon clicked for suggestion: $suggestionText")
                showDeleteConfirmationDialog(suggestionText, pos)
            }
        }
    }

    override fun getItemCount(): Int {
        Timber.d("[SUGGESTION] Getting item count: ${suggestions.size}")
        return if (suggestions.isEmpty()) 1 else suggestions.size // Boşsa 1 döndür (mesaj için)
    }
    
    /**
     * Silme onayı için dialog gösterir
     * 3 seçenek sunar: Sadece seçilen öneriyi sil, tüm önerileri sil ve iptal
     */
    private fun showDeleteConfirmationDialog(suggestion: String, position: Int) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Öneri Silme")
            .setMessage("\"$suggestion\" önerisini silmek istediğinize emin misiniz?")
            .setPositiveButton("Bu Öneriyi Sil") { _, _ ->
                // Sadece seçilen öneriyi sil
                Timber.d("[SUGGESTION] User confirmed deletion of single suggestion: $suggestion")
                onSuggestionDeleted(suggestion, position)
            }
            .setNegativeButton("İptal") { dialog, _ ->
                dialog.dismiss()
                Timber.d("[SUGGESTION] User cancelled deletion")
            }
            .setNeutralButton("Tüm Önerileri Sil") { _, _ ->
                // Tüm önerileri silmek için SuggestionManager'a bildir
                Timber.d("[SUGGESTION] User chose to delete all suggestions for key: $inputKey")
                // Önce tüm öneri silme onayı sor
                confirmDeleteAllSuggestions()
            }
            .show()
    }
    
    /**
     * Tüm önerileri silme işlemi için ikinci bir onay isteği
     */
    private fun confirmDeleteAllSuggestions() {
        MaterialAlertDialogBuilder(context)
            .setTitle("Tüm Önerileri Sil")
            .setMessage("Bu alan için TÜM önerileri silmek istediğinize emin misiniz? Bu işlem geri alınamaz.")
            .setPositiveButton("Tümünü Sil") { _, _ ->
                Timber.d("[SUGGESTION] User confirmed deletion of ALL suggestions for key: $inputKey")
                // Özel bir işaretleyici ile silme işlemini çağır
                onSuggestionDeleted("__DELETE_ALL_SUGGESTIONS__", -1)
            }
            .setNegativeButton("İptal") { dialog, _ ->
                dialog.dismiss()
                Timber.d("[SUGGESTION] User cancelled deletion of all suggestions")
            }
            .show()
    }

    /**
     * Update the suggestions list with new items
     */
    fun updateSuggestions(newSuggestions: List<String>) {
        Timber.d("[SUGGESTION] Updating suggestions list: ${suggestions.size} -> ${newSuggestions.size}")
        suggestions.clear()
        suggestions.addAll(newSuggestions)
        notifyDataSetChanged()
        Timber.d("[SUGGESTION] Suggestions updated, adapter notified")
    }
}
