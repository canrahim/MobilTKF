package com.asforce.asforcetkf2.util;

import android.text.Editable;
import android.text.TextWatcher;

/**
 * TextWatcher arayüzü için yardımcı sınıf
 * Sadece ihtiyaç duyulan metodu override etmek için kullanılır
 */
public abstract class SimpleTextWatcher implements TextWatcher {

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // Boş implementasyon
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // Boş implementasyon
    }

    @Override
    public abstract void afterTextChanged(Editable s);
}