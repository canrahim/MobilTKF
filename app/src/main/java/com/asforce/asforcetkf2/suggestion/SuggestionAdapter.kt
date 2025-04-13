package com.asforce.asforcetkf2.suggestion

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcetkf2.R
import com.google.android.material.chip.Chip
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
        
        // Handle click on close icon - delete the suggestion
        holder.suggestionChip.setOnCloseIconClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                val suggestionText = suggestions[pos]
                Timber.d("[SUGGESTION] Delete icon clicked for suggestion: $suggestionText")
                onSuggestionDeleted(suggestionText, pos)
            }
        }
    }

    override fun getItemCount(): Int {
        Timber.d("[SUGGESTION] Getting item count: ${suggestions.size}")
        return suggestions.size
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
