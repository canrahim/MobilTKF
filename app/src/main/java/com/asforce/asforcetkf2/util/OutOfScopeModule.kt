package com.asforce.asforcetkf2.util

import android.webkit.WebView

/**
 * Kapsam dışı işlemler için yardımcı metotlar içeren sınıf
 */
object OutOfScopeModule {
    private const val OUT_OF_SCOPE_VALUE = "3"
    private const val CSS_CLASS = "kapsam-disi"

    // AYDINLATMA MENÜSÜ METOTLARİ

    // 24V Aydınlatma
    fun set24VAydinlatmaOutOfScope(webView: WebView) {
        val indices = intArrayOf(2, 3, 4, 11, 12, 13, 14, 15, 16, 17)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Aydınlatma için toprak sürekliliği ve kaçak akım ölçümü kapsam dışı
        WebViewUtils.setSelectedFunctionTestsOutOfScope(webView, intArrayOf(0, 1, 2))
    }

    // 220V Aydınlatma
    fun set220VAydinlatmaOutOfScope(webView: WebView) {
        val indices = intArrayOf(1, 4, 6, 8, 10, 12, 14, 16)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Tüm fonksiyon testleri kapsam dışı
        WebViewUtils.setAllFunctionTestsOutOfScope(webView)
    }

    // Acil Durum Aydınlatma
    fun setAcilDurumAydinlatmaOutOfScope(webView: WebView) {
        val indices = intArrayOf(3, 5, 7, 9, 11, 13, 15, 17, 19)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Acil durum aydınlatma için kaçak akım ölçümü kapsam dışı
        WebViewUtils.setKacakAkimOlcumuOutOfScope(webView)
    }

    // Sensörlü Aydınlatma
    fun setSensorluAydinlatmaOutOfScope(webView: WebView) {
        val indices = intArrayOf(2, 4, 6, 8, 10, 12, 14, 16, 18)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Sensörlü aydınlatma için izolasyon direnci kapsam dışı
        WebViewUtils.setIzolasyonDirenciOutOfScope(webView)
    }

    // ELEKTRİK MALZEMELERİ MENÜSÜ METOTLARİ

    // Priz Grubu
    fun setPrizGrubuOutOfScope(webView: WebView) {
        val indices = intArrayOf(3, 5, 7, 9, 11, 13, 15)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Priz grubu için toprak sürekliliği kapsam dışı
        WebViewUtils.setToprakSurekliligiOutOfScope(webView)
    }

    // Sigorta Grubu
    fun setSigortaGrubuOutOfScope(webView: WebView) {
        val indices = intArrayOf(2, 4, 6, 8, 10, 12, 14, 16)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Sigorta grubu için izolasyon ve kaçak akım kapsam dışı
        WebViewUtils.setSelectedFunctionTestsOutOfScope(webView, intArrayOf(1, 2))
    }

    // Şalter Grubu
    fun setSalterGrubuOutOfScope(webView: WebView) {
        val indices = intArrayOf(1, 3, 5, 7, 9, 11, 13, 15, 17)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Şalter grubu için toprak ve izolasyon kapsam dışı
        WebViewUtils.setSelectedFunctionTestsOutOfScope(webView, intArrayOf(0, 1))
    }

    // Pano Aksesuarları
    fun setPanoAksesuarlariOutOfScope(webView: WebView) {
        val indices = intArrayOf(2, 4, 6, 8, 10, 12, 14, 16, 18)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Pano aksesuarları için tüm testler kapsam dışı
        WebViewUtils.setAllFunctionTestsOutOfScope(webView)
    }

    // TESİSAT MALZEMELERİ MENÜSÜ METOTLARİ

    // Kablo Grubu
    fun setKabloGrubuOutOfScope(webView: WebView) {
        val indices = intArrayOf(3, 6, 9, 12, 15, 18)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Kablo grubu için izolasyon testi kapsam dışı
        WebViewUtils.setIzolasyonDirenciOutOfScope(webView)
    }

    // Buat Grubu
    fun setBuatGrubuOutOfScope(webView: WebView) {
        val indices = intArrayOf(2, 5, 8, 11, 14, 17)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Buat grubu için toprak ve kaçak akım testleri kapsam dışı
        WebViewUtils.setSelectedFunctionTestsOutOfScope(webView, intArrayOf(0, 2))
    }

    // Kablo Kanalları
    fun setKabloKanallariOutOfScope(webView: WebView) {
        val indices = intArrayOf(1, 4, 7, 10, 13, 16, 19)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Kablo kanalları için tüm testler kapsam dışı
        WebViewUtils.setAllFunctionTestsOutOfScope(webView)
    }

    // ÖLÇÜM ALETLERİ MENÜSÜ METOTLARİ

    // Multimetre
    fun setMultimetreOutOfScope(webView: WebView) {
        val indices = intArrayOf(2, 5, 8, 11, 14, 17, 20)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Multimetre için hiçbir test kapsam dışı değil (örnek)
    }

    // Topraklama Ölçüm
    fun setTopraklamaOlcumOutOfScope(webView: WebView) {
        val indices = intArrayOf(3, 6, 9, 12, 15, 18)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Topraklama ölçüm için sadece izolasyon kapsam dışı
        WebViewUtils.setIzolasyonDirenciOutOfScope(webView)
    }

    // İzolasyon Ölçüm
    fun setIzolasyonOlcumOutOfScope(webView: WebView) {
        val indices = intArrayOf(4, 7, 10, 13, 16, 19)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // İzolasyon ölçüm için sadece topraklama kapsam dışı
        WebViewUtils.setToprakSurekliligiOutOfScope(webView)
    }

    // Termal Kamera
    fun setTermalKameraOutOfScope(webView: WebView) {
        val indices = intArrayOf(1, 4, 7, 10, 13, 16, 19, 22)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Termal kamera için tüm testler kapsam dışı
        WebViewUtils.setAllFunctionTestsOutOfScope(webView)
    }

    // Aşağıdaki ek metotlar orijinal koddan korunmuştur

    fun setIskeleMakarasiOutOfScope(webView: WebView) {
        val indices = intArrayOf(5, 7, 8, 9, 11, 12, 13, 14, 15)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // İskele makarası için kaçak akım kapsam dışı
        WebViewUtils.setKacakAkimOlcumuOutOfScope(webView)
    }

    fun setKancaOutOfScope(webView: WebView) {
        val indices = intArrayOf(5, 6, 7, 8, 9, 11, 12)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Kanca için tüm testler kapsam dışı
        WebViewUtils.setAllFunctionTestsOutOfScope(webView)
    }

    fun setMakaraKaldirmaOutOfScope(webView: WebView) {
        val indices = intArrayOf(5, 7, 8, 9, 10, 11, 12, 13, 14, 15)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Makara kaldırma için toprak ve izolasyon kapsam dışı
        WebViewUtils.setSelectedFunctionTestsOutOfScope(webView, intArrayOf(0, 1))
    }

    fun setSpreaderBeamOutOfScope(webView: WebView) {
        val indices = intArrayOf(5, 6, 7, 8, 9, 11, 12)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Spreader beam için tüm testler kapsam dışı
        WebViewUtils.setAllFunctionTestsOutOfScope(webView)
    }

    fun setSaryoOutOfScope(webView: WebView) {
        val indices = intArrayOf(5, 7, 8, 9, 11, 12, 13, 14, 15)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Şaryo için sadece izolasyon kapsam dışı
        WebViewUtils.setIzolasyonDirenciOutOfScope(webView)
    }

    fun setHidrolikPompaOutOfScope(webView: WebView) {
        val indices = intArrayOf(2, 3, 6, 7, 8, 10)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Hidrolik pompa için toprak ve kaçak akım kapsam dışı
        WebViewUtils.setSelectedFunctionTestsOutOfScope(webView, intArrayOf(0, 2))
    }

    fun setFlansAyirmaOutOfScope(webView: WebView) {
        val indices = intArrayOf(2, 3, 6, 7, 8, 9, 10)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Flanş ayırma için tüm testler kapsam dışı
        WebViewUtils.setAllFunctionTestsOutOfScope(webView)
    }

    fun setTestPompaOutOfScope(webView: WebView) {
        val indices = intArrayOf(4, 6)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Test pompası için izolasyon kapsam dışı
        WebViewUtils.setIzolasyonDirenciOutOfScope(webView)
    }

    fun setSuPompaOutOfScope(webView: WebView) {
        val indices = intArrayOf(2, 4, 6)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Su pompası için kaçak akım kapsam dışı
        WebViewUtils.setKacakAkimOlcumuOutOfScope(webView)
    }

    fun setDizelKompressorOutOfScope(webView: WebView) {
        val indices = intArrayOf(7, 8, 11, 13, 15, 17, 19, 20)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Dizel kompresör için toprak kapsam dışı
        WebViewUtils.setToprakSurekliligiOutOfScope(webView)
    }

    fun setElektrikliKompressorOutOfScope(webView: WebView) {
        val indices = intArrayOf(7, 8, 11, 13, 15, 16, 19, 20, 22)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Elektrikli kompresör için tüm testler kapsam dışı
        WebViewUtils.setAllFunctionTestsOutOfScope(webView)
    }

    fun setSuJetiOutOfScope(webView: WebView) {
        val indices = intArrayOf(5, 12, 13, 14, 15, 17)
        WebViewUtils.setQuestionsToOutOfScope(webView, indices, OUT_OF_SCOPE_VALUE, CSS_CLASS)
        // Su jeti için izolasyon ve kaçak akım kapsam dışı
        WebViewUtils.setSelectedFunctionTestsOutOfScope(webView, intArrayOf(1, 2))
    }
}