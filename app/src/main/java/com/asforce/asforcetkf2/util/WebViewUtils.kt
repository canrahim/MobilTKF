package com.asforce.asforcetkf2.util

import android.webkit.WebView

/**
 * WebView işlemleri için yardımcı metotlar içeren sınıf
 */
object WebViewUtils {
    
    /**
     * Belirtilen soruları kapsam dışı olarak işaretler
     * @param webView Hedef WebView
     * @param indices Kapsam dışı olarak işaretlenecek soru indeksleri
     * @param outOfScopeValue Kapsam dışı değeri
     * @param cssClass Eklenen CSS sınıfı
     */
    fun setQuestionsToOutOfScope(webView: WebView, indices: IntArray, outOfScopeValue: String, cssClass: String) {
        val jsBuilder = StringBuilder()
        for (index in indices) {
            jsBuilder.append("var sel = document.getElementById('Questions_")
                .append(index)
                .append("_.Option');")
                .append("if(sel){ sel.value = '")
                .append(outOfScopeValue)
                .append("'; sel.classList.add('")
                .append(cssClass)
                .append("'); }")
        }
        // Bootstrap Select güncellemesi
        jsBuilder.append("if(window.jQuery){ $('.selectpicker').selectpicker('refresh'); }")
        webView.evaluateJavascript(jsBuilder.toString(), null)
    }
    
    /**
     * Belirli bir fonksiyon testini kapsam dışı olarak işaretler
     * @param webView Hedef WebView
     * @param functionName Fonksiyon test alanının adı
     */
    fun setFunctionTestOutOfScope(webView: WebView, functionName: String) {
        val jsCode = "var sel = document.getElementsByName('$functionName')[0];" +
                "if(sel){ sel.value = '3'; " + // 3 = Kapsam Dışı
                "sel.classList.add('kapsam-disi'); " +
                "if(window.jQuery){ $(sel).selectpicker('refresh'); } }"
        webView.evaluateJavascript(jsCode, null)
    }
    
    /**
     * Tüm fonksiyon testlerini kapsam dışı olarak işaretler
     * @param webView Hedef WebView
     */
    fun setAllFunctionTestsOutOfScope(webView: WebView) {
        val functionNames = arrayOf(
            "SoilContinuityStatus",           // Toprak Sürekliliği
            "IzolationResistanceStatus",      // İzolasyon direnci
            "LeakageCurrentStatus"            // Kaçak akım ölçümü
        )
        
        val jsBuilder = StringBuilder()
        for (name in functionNames) {
            jsBuilder.append("var sel = document.getElementsByName('")
                .append(name)
                .append("')[0];")
                .append("if(sel){ sel.value = '3'; ") // 3 = Kapsam Dışı
                .append("sel.classList.add('kapsam-disi'); }")
        }
        jsBuilder.append("if(window.jQuery){ $('.selectpicker').selectpicker('refresh'); }")
        webView.evaluateJavascript(jsBuilder.toString(), null)
    }
    
    /**
     * Belirli fonksiyon testlerini kapsam dışı olarak işaretler
     * @param webView Hedef WebView
     * @param testIndices Kapsam dışı olarak işaretlenecek test indeksleri (0=Toprak, 1=İzolasyon, 2=Kaçak Akım)
     */
    fun setSelectedFunctionTestsOutOfScope(webView: WebView, testIndices: IntArray) {
        val functionNames = arrayOf(
            "SoilContinuityStatus",           // 0: Toprak Sürekliliği
            "IzolationResistanceStatus",      // 1: İzolasyon direnci
            "LeakageCurrentStatus"            // 2: Kaçak akım ölçümü
        )
        
        val jsBuilder = StringBuilder()
        for (index in testIndices) {
            if (index >= 0 && index < functionNames.size) {
                jsBuilder.append("var sel = document.getElementsByName('")
                    .append(functionNames[index])
                    .append("')[0];")
                    .append("if(sel){ sel.value = '3'; ") // 3 = Kapsam Dışı
                    .append("sel.classList.add('kapsam-disi'); }")
            }
        }
        jsBuilder.append("if(window.jQuery){ $('.selectpicker').selectpicker('refresh'); }")
        webView.evaluateJavascript(jsBuilder.toString(), null)
    }
    
    /**
     * Toprak sürekliliği testini kapsam dışı olarak işaretler
     * @param webView Hedef WebView
     */
    fun setToprakSurekliligiOutOfScope(webView: WebView) {
        setFunctionTestOutOfScope(webView, "SoilContinuityStatus")
    }
    
    /**
     * İzolasyon direnci testini kapsam dışı olarak işaretler
     * @param webView Hedef WebView
     */
    fun setIzolasyonDirenciOutOfScope(webView: WebView) {
        setFunctionTestOutOfScope(webView, "IzolationResistanceStatus")
    }
    
    /**
     * Kaçak akım ölçümü testini kapsam dışı olarak işaretler
     * @param webView Hedef WebView
     */
    fun setKacakAkimOlcumuOutOfScope(webView: WebView) {
        setFunctionTestOutOfScope(webView, "LeakageCurrentStatus")
    }
}