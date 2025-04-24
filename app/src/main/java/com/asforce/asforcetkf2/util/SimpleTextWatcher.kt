package com.asforce.asforcetkf2.util

import android.text.Editable
import android.text.TextWatcher

/**
 * Simple implementation of TextWatcher that allows only overriding methods that are needed
 */
abstract class SimpleTextWatcher : TextWatcher {
    override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        // Default empty implementation
    }

    override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
        // Default empty implementation
    }

    override fun afterTextChanged(s: Editable) {
        // Default empty implementation
    }
} 