package com.asforce.asforcetkf2.suggestion

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asforce.asforcetkf2.R
import timber.log.Timber

/**
 * DialogFragment implementation for showing suggestions
 * This is an alternative to using a custom view in the layout
 */
class SuggestionDialogFragment : DialogFragment() {
    private var suggestions: Array<String> = arrayOf()
    private var inputKey: String = ""
    private var onSuggestionSelected: ((String) -> Unit)? = null
    private var onSuggestionDeleted: ((String, Int) -> Unit)? = null
    
    companion object {
        /**
         * Create a new instance of the dialog with suggestions
         */
        fun newInstance(
            suggestions: Array<String>,
            inputKey: String,
            onSuggestionSelected: (String) -> Unit,
            onSuggestionDeleted: (String, Int) -> Unit
        ): SuggestionDialogFragment {
            val fragment = SuggestionDialogFragment()
            fragment.suggestions = suggestions
            fragment.inputKey = inputKey
            fragment.onSuggestionSelected = onSuggestionSelected
            fragment.onSuggestionDeleted = onSuggestionDeleted
            return fragment
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_suggestion_bar, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Set up RecyclerView
        val recyclerView = view.findViewById<RecyclerView>(R.id.suggestion_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        
        // Apply styling to make it look like the normal suggestion bar
        val backgroundDrawable = android.graphics.drawable.GradientDrawable().apply {
            setColor(android.graphics.Color.WHITE)
            setStroke(2, android.graphics.Color.parseColor("#4285f4"))
            cornerRadius = 12f
        }
        view.background = backgroundDrawable
        view.setPadding(16, 8, 16, 8)
        
        // Create adapter with callbacks
        val suggestionAdapter = SuggestionAdapter(
            context = requireContext(),
            suggestions = suggestions.toMutableList(),
            inputKey = inputKey,
            onSuggestionSelected = { suggestion -> 
                onSuggestionSelected?.invoke(suggestion)
                dismiss()
            },
            onSuggestionDeleted = { suggestion, position ->
                onSuggestionDeleted?.invoke(suggestion, position)
                // If all suggestions are deleted, dismiss the dialog
                if (suggestions.size <= 1) {
                    dismiss()
                }
            }
        )
        
        recyclerView.adapter = suggestionAdapter
        Timber.d("[SUGGESTION_DIALOG] Dialog setup complete with ${suggestions.size} suggestions")
    }
    
    override fun onStart() {
        super.onStart()
        
        // Configure dialog appearance
        dialog?.window?.let { window ->
            // Make the dialog appear at the BOTTOM of the screen
            val params = window.attributes
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.gravity = android.view.Gravity.BOTTOM  // Changed back to BOTTOM
            window.attributes = params
            
            // Set the background to be transparent
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))
            
            // Remove dim background
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            
            // Allow touches outside to dismiss
            setCancelable(true)
            
            // Add bottom margin to position just above keyboard - kullan daha büyük bir mesafe
            params.y = 350 // Daha fazla mesafe
            window.attributes = params
            
            // Set input mode - değiştirildi
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            
            // EXTREMELY IMPORTANT: Not consume touch events outside dialog bounds
            // This ensures touches on input fields will work even with dialog showing
            window.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            )
            
            // Allow window to be at bottom but not prevent input
            window.setFlags(
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            )
            
            // Ensure it doesn't gain focus automatically
            window.setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            )
            
            // Gecikmeli güncelleme ile pozisyonu sabitliyoruz
            Handler(android.os.Looper.getMainLooper()).postDelayed({
                params.y = 350 // Pozisyonu yeniden ayarla
                window.attributes = params
            }, 200)
        }
    }
} 