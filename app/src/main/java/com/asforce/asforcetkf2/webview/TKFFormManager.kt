package com.asforce.asforcetkf2.webview

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONObject
import timber.log.Timber

/**
 * Form yönetimi ve otomatik doldurma için sınıf
 * Özellikle Szutest.com.tr formları için optimize edilmiştir
 */
class TKFFormManager(private val webView: WebView) {

    private val handler = Handler(Looper.getMainLooper())
    private var isFormEnhancementActive = false
    
    /**
     * Form geliştirmelerini aktif et
     */
    fun enableFormEnhancements() {
        if (isFormEnhancementActive) return
        
        isFormEnhancementActive = true
        injectFormHandlers()
    }
    
    /**
     * Form yönetim scriptlerini enjekte et
     * - Form otomatik doldurma
     * - Form veri kaydetme
     * - Özel form eylem işleme
     */
    private fun injectFormHandlers() {
        val script = """
            (function() {
                // TKF Form Manager Global Objesi
                if (window.TKF_FORM_MANAGER) return 'Already initialized';
                
                window.TKF_FORM_MANAGER = {
                    version: '2.0',
                    forms: [],
                    formData: {},
                    
                    // Sayfa içindeki formları tara ve işaretle
                    scanForms: function() {
                        var forms = document.querySelectorAll('form');
                        console.log('TKF: Found ' + forms.length + ' forms');
                        
                        // Her form için işlem yap
                        for (var i = 0; i < forms.length; i++) {
                            var form = forms[i];
                            
                            // Daha önce işaretlenmemiş formları işaretle
                            if (!form.hasAttribute('data-tkf-processed')) {
                                form.setAttribute('data-tkf-processed', 'true');
                                
                                // Form ID'si oluştur
                                var formId = this.getFormId(form);
                                form.setAttribute('data-tkf-id', formId);
                                
                                // Form tipini belirle
                                var formType = this.detectFormType(form);
                                form.setAttribute('data-tkf-type', formType);
                                
                                // Form dinleyicilerini ekle
                                this.setupFormListeners(form, formId, formType);
                                
                                // Formları kayıtlı listeye ekle
                                this.forms.push({
                                    id: formId,
                                    type: formType,
                                    element: form
                                });
                                
                                // Önceden kaydedilmiş verileri yükle
                                this.loadSavedFormData(form, formId, formType);
                                
                                console.log('TKF: Registered form: ' + formId + ' type: ' + formType);
                            }
                        }
                    },
                    
                    // Form için benzersiz ID oluştur
                    getFormId: function(form) {
                        var id = form.id || form.name || '';
                        var action = form.action || '';
                        
                        // Form ID yoksa URL'den oluştur
                        if (!id) {
                            id = action.split('?')[0]; // Query parametrelerini kaldır
                            id = id.split('/').pop(); // Son path segmentini al
                        }
                        
                        // Yine boşsa rastgele bir ID oluştur
                        if (!id) {
                            id = 'form_' + Math.random().toString(36).substring(2, 9);
                        }
                        
                        // ID'yi temizle ve döndür
                        return id.replace(/[^a-zA-Z0-9_]/g, '_').toLowerCase();
                    },
                    
                    // Form tipini belirle (login, search, vb.)
                    detectFormType: function(form) {
                        var html = form.innerHTML.toLowerCase();
                        var action = (form.action || '').toLowerCase();
                        
                        // Szutest formları için özel kontrol
                        if (action.indexOf('szutest') !== -1 || window.location.href.indexOf('szutest') !== -1) {
                            // Szutest login formu kontrolü
                            if (html.indexOf('password') !== -1 || action.indexOf('login') !== -1) {
                                return 'szutest_login';
                            }
                            
                            // Ekipman listesi formu
                            if (action.indexOf('equipmentlist') !== -1 || 
                                action.indexOf('equipment') !== -1) {
                                return 'szutest_equipment';
                            }
                            
                            // Kontrol listesi formu
                            if (action.indexOf('controllist') !== -1 || 
                                action.indexOf('control') !== -1) {
                                return 'szutest_control';
                            }
                            
                            return 'szutest_form';
                        }
                        
                        // Genel form tiplerini kontrol et
                        if (html.indexOf('password') !== -1 || 
                            action.indexOf('login') !== -1 || 
                            action.indexOf('signin') !== -1 || 
                            html.indexOf('user') !== -1) {
                            return 'login';
                        }
                        
                        if (html.indexOf('search') !== -1 || 
                            action.indexOf('search') !== -1) {
                            return 'search';
                        }
                        
                        return 'general';
                    },
                    
                    // Form için olay dinleyicileri ekle
                    setupFormListeners: function(form, formId, formType) {
                        var self = this;
                        
                        // Form gönderildiğinde
                        form.addEventListener('submit', function(e) {
                            // Form verilerini topla
                            var data = self.collectFormData(this);
                            
                            // Verileri sakla
                            self.saveFormData(formId, formType, data);
                            
                            console.log('TKF: Form submitted - ' + formId);
                        });
                        
                        // Form giriş alanları için otomatik doldurma
                        var inputs = form.querySelectorAll('input, textarea, select');
                        for (var i = 0; i < inputs.length; i++) {
                            var input = inputs[i];
                            
                            // Şifre alanlarını atla
                            if (input.type === 'password') continue;
                            
                            // Her alanı işaretle
                            if (!input.hasAttribute('data-tkf-id')) {
                                var inputId = (input.name || input.id || 'input_' + i).toLowerCase();
                                input.setAttribute('data-tkf-id', inputId);
                                
                                // Değişiklik dinleyicisi ekle
                                input.addEventListener('change', function() {
                                    // Değişiklikleri kaydet
                                    var inputData = {};
                                    inputData[this.getAttribute('data-tkf-id')] = this.value;
                                    
                                    // Form ID ve input değerini sakla
                                    var formId = form.getAttribute('data-tkf-id');
                                    self.updateFormData(formId, inputData);
                                });
                            }
                        }
                    },
                    
                    // Form verilerini topla
                    collectFormData: function(form) {
                        var data = {};
                        var elements = form.elements;
                        
                        for (var i = 0; i < elements.length; i++) {
                            var element = elements[i];
                            
                            // Sadece ismi olan ve şifre olmayan alanları topla
                            if (element.name && element.type !== 'password' && element.type !== 'file') {
                                if (element.type === 'checkbox' || element.type === 'radio') {
                                    if (element.checked) {
                                        data[element.name] = element.value;
                                    }
                                } else {
                                    data[element.name] = element.value;
                                }
                            }
                        }
                        
                        return data;
                    },
                    
                    // Form verilerini kaydet
                    saveFormData: function(formId, formType, data) {
                        try {
                            // Kaydetme stratejisini forma göre belirle
                            if (formType.indexOf('szutest') === 0 || formType === 'login') {
                                // Kalıcı veri olarak sakla
                                localStorage.setItem('TKF_FORM_' + formId, JSON.stringify(data));
                                console.log('TKF: Saved form data permanently - ' + formId);
                            } else {
                                // Geçici veri olarak sakla
                                sessionStorage.setItem('TKF_FORM_' + formId, JSON.stringify(data));
                                console.log('TKF: Saved form data for session - ' + formId);
                            }
                            
                            // Form yöneticisi veri önbelleğine ekle
                            this.formData[formId] = data;
                        } catch(e) {
                            console.error('TKF: Error saving form data', e);
                        }
                    },
                    
                    // Form verilerini güncelle
                    updateFormData: function(formId, newData) {
                        try {
                            // Mevcut verileri al
                            var data = this.formData[formId] || {};
                            var formType = '';
                            
                            // Formu bul
                            for (var i = 0; i < this.forms.length; i++) {
                                if (this.forms[i].id === formId) {
                                    formType = this.forms[i].type;
                                    break;
                                }
                            }
                            
                            // Verileri güncelle
                            for (var key in newData) {
                                data[key] = newData[key];
                            }
                            
                            // Güncellenen verileri kaydet
                            this.formData[formId] = data;
                            
                            // Depolama alanına kaydet
                            if (formType.indexOf('szutest') === 0 || formType === 'login') {
                                localStorage.setItem('TKF_FORM_' + formId, JSON.stringify(data));
                            } else {
                                sessionStorage.setItem('TKF_FORM_' + formId, JSON.stringify(data));
                            }
                        } catch(e) {
                            console.error('TKF: Error updating form data', e);
                        }
                    },
                    
                    // Kaydedilmiş form verilerini yükle
                    loadSavedFormData: function(form, formId, formType) {
                        try {
                            var data = null;
                            
                            // Depolama tipine göre verileri al
                            if (formType.indexOf('szutest') === 0 || formType === 'login') {
                                var savedData = localStorage.getItem('TKF_FORM_' + formId);
                                if (savedData) {
                                    data = JSON.parse(savedData);
                                }
                            } else {
                                var savedData = sessionStorage.getItem('TKF_FORM_' + formId);
                                if (savedData) {
                                    data = JSON.parse(savedData);
                                }
                            }
                            
                            // Veri varsa form alanlarını doldur
                            if (data) {
                                // Önbelleğe ekle
                                this.formData[formId] = data;
                                
                                // Form alanlarını doldur
                                var elements = form.elements;
                                for (var i = 0; i < elements.length; i++) {
                                    var element = elements[i];
                                    
                                    // Şifre alanlarını atla
                                    if (element.type === 'password') continue;
                                    
                                    // Eşleşen değerleri doldur
                                    if (element.name && data[element.name]) {
                                        if (element.type === 'checkbox' || element.type === 'radio') {
                                            element.checked = (element.value === data[element.name]);
                                        } else {
                                            element.value = data[element.name];
                                            
                                            // Input olayını tetikle
                                            var event = new Event('input', { bubbles: true });
                                            element.dispatchEvent(event);
                                        }
                                    }
                                }
                                
                                console.log('TKF: Loaded saved data for form - ' + formId);
                                return true;
                            }
                        } catch(e) {
                            console.error('TKF: Error loading form data', e);
                        }
                        
                        return false;
                    },
                    
                    // Formu manuel olarak doldur - JavaScript API
                    fillForm: function(formId, data) {
                        // Formu bul
                        var form = null;
                        for (var i = 0; i < this.forms.length; i++) {
                            if (this.forms[i].id === formId) {
                                form = this.forms[i].element;
                                break;
                            }
                        }
                        
                        if (!form) {
                            console.error('TKF: Form not found - ' + formId);
                            return false;
                        }
                        
                        // Verileri doldur
                        for (var key in data) {
                            var element = form.elements[key];
                            if (element) {
                                if (element.type === 'checkbox' || element.type === 'radio') {
                                    element.checked = (element.value === data[key]);
                                } else {
                                    element.value = data[key];
                                    
                                    // Input olayını tetikle
                                    var event = new Event('input', { bubbles: true });
                                    element.dispatchEvent(event);
                                    
                                    // Change olayını tetikle
                                    var changeEvent = new Event('change', { bubbles: true });
                                    element.dispatchEvent(changeEvent);
                                }
                            }
                        }
                        
                        console.log('TKF: Manually filled form - ' + formId);
                        return true;
                    }
                };
                
                // İlk form taramasını başlat
                if (document.readyState === 'complete' || document.readyState === 'interactive') {
                    window.TKF_FORM_MANAGER.scanForms();
                } else {
                    document.addEventListener('DOMContentLoaded', function() {
                        window.TKF_FORM_MANAGER.scanForms();
                    });
                }
                
                // DOM değişikliklerini izle - yeni formlar eklendiğinde algılamak için
                if (window.MutationObserver) {
                    var observer = new MutationObserver(function(mutations) {
                        // Sayfa içeriği değiştiğinde formları tekrar tara
                        window.TKF_FORM_MANAGER.scanForms();
                    });
                    
                    // Tüm DOM değişikliklerini izle
                    observer.observe(document.body, {
                        childList: true,
                        subtree: true
                    });
                    
                    console.log('TKF: Form mutation observer started');
                }
                
                // Sayfa yönlendirmelerini izleyerek formları yeniden taramak için
                window.addEventListener('hashchange', function() {
                    setTimeout(function() {
                        window.TKF_FORM_MANAGER.scanForms();
                    }, 500);
                });
                
                return 'TKF Form Manager 2.0 initialized';
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            Timber.d("Form manager initialization result: $result")
            
            // İlk tarama sonrası belli bir gecikme ile formları otomatik doldurmayı dene
            handler.postDelayed({
                checkForSzutestForms()
            }, 1000)
        }
    }
    
    /**
     * Szutest formlarını özel olarak işle ve doldur
     */
    private fun checkForSzutestForms() {
        if (!isFormEnhancementActive) return
        
        val url = webView.url ?: return
        
        // Szutest formları için özel kontrol
        if (url.contains("szutest.com.tr")) {
            // Mevcut formları kontrol et
            val script = """
                (function() {
                    var szutestForms = [];
                    var isSzutestPage = window.location.href.indexOf('szutest.com.tr') !== -1;
                    
                    if (isSzutestPage && window.TKF_FORM_MANAGER) {
                        // Szutest form tiplerini ara
                        for (var i = 0; i < window.TKF_FORM_MANAGER.forms.length; i++) {
                            var form = window.TKF_FORM_MANAGER.forms[i];
                            if (form.type.indexOf('szutest') === 0) {
                                szutestForms.push({
                                    id: form.id,
                                    type: form.type
                                });
                            }
                        }
                    }
                    
                    return JSON.stringify({
                        isSzutestPage: isSzutestPage,
                        forms: szutestForms,
                        url: window.location.href
                    });
                })();
            """.trimIndent()
            
            webView.evaluateJavascript(script) { result ->
                try {
                    // Tırnak işaretlerini temizle ve JSON olarak ayrıştır
                    val jsonResult = result.replace("\\\"", "\"")
                                          .replace("^\"|\"$".toRegex(), "")
                    
                    val data = JSONObject(jsonResult)
                    val isSzutestPage = data.optBoolean("isSzutestPage", false)
                    
                    if (isSzutestPage) {
                        Timber.d("Szutest page detected: ${data.optString("url")}")
                        
                        // Form enhancements
                        enhanceSzutestForms()
                    }
                } catch (e: Exception) {
                    Timber.e("Error checking Szutest forms: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Szutest.com.tr için form geliştirmeleri ekle
     */
    private fun enhanceSzutestForms() {
        val script = """
            (function() {
                // Szutest için özel form işleme
                if (!window.TKF_FORM_MANAGER || window.TKF_SZUTEST_FORMS_ENHANCED) {
                    return 'Not ready or already enhanced';
                }
                
                window.TKF_SZUTEST_FORMS_ENHANCED = true;
                
                // Szutest'e özel form geliştirmeleri
                var enhanceSzutestForm = function(form) {
                    var formId = form.getAttribute('data-tkf-id');
                    var formType = form.getAttribute('data-tkf-type');
                    
                    // Form tipi kontrolleri ve özel işlemler
                    if (formType === 'szutest_equipment') {
                        console.log('TKF: Enhancing equipment form - ' + formId);
                        
                        // Ekipman listesi form alanları için otomatik doldurma
                        var equipmentFormData = {
                            // Ekipman formları için varsayılan değerler
                            'SerialNumber': '',
                            'Brand': 'Tipik',
                            'Model': 'Standart'
                        };
                        
                        // Varsayılan değerleri form yöneticisine kaydet
                        window.TKF_FORM_MANAGER.fillForm(formId, equipmentFormData);
                    }
                    
                    // Kontrol listesi formları için
                    if (formType === 'szutest_control') {
                        console.log('TKF: Enhancing control form - ' + formId);
                        
                        // Kontrol listesi form alanları için otomatik doldurma
                        var controlFormData = {
                            // Kontrol formları için varsayılan değerler
                            'ControlDate': new Date().toISOString().split('T')[0]
                        };
                        
                        // Varsayılan değerleri form yöneticisine kaydet
                        window.TKF_FORM_MANAGER.fillForm(formId, controlFormData);
                    }
                    
                    // Form geçerliliğini doğrulama için script
                    form.addEventListener('submit', function(e) {
                        var isValid = true;
                        var requiredFields = form.querySelectorAll('[required]');
                        
                        // Tüm zorunlu alanları kontrol et
                        for (var i = 0; i < requiredFields.length; i++) {
                            var field = requiredFields[i];
                            if (!field.value) {
                                isValid = false;
                                field.classList.add('tkf-invalid');
                                
                                // İlk boş alana odaklan
                                if (i === 0) {
                                    field.focus();
                                }
                            } else {
                                field.classList.remove('tkf-invalid');
                            }
                        }
                        
                        // Form geçerli değilse uyarı göster
                        if (!isValid) {
                            alert('Lütfen tüm zorunlu alanları doldurun.');
                            e.preventDefault();
                            return false;
                        }
                        
                        return true;
                    });
                };
                
                // Tüm Szutest formlarını geliştir
                var forms = document.querySelectorAll('form[data-tkf-type^="szutest"]');
                for (var i = 0; i < forms.length; i++) {
                    enhanceSzutestForm(forms[i]);
                }
                
                // Sayfa spesifik iyileştirmeler
                var url = window.location.href;
                
                // Kontrol listesi sayfası
                if (url.indexOf('EKControlList') !== -1 || url.indexOf('ControlList') !== -1) {
                    console.log('TKF: Control list page detected');
                    
                    // Tabloları ve formları otomatik olarak yükselt
                    var tables = document.querySelectorAll('table');
                    tables.forEach(function(table) {
                        // Tablolara sıralama kabiliyeti ekle
                        if (!table.classList.contains('tkf-enhanced')) {
                            table.classList.add('tkf-enhanced');
                            
                            // Tablo başlıklarına tıklama dinleyicisi ekle
                            var headers = table.querySelectorAll('th');
                            headers.forEach(function(header) {
                                header.style.cursor = 'pointer';
                                header.addEventListener('click', function() {
                                    console.log('TKF: Sorting table by ' + this.textContent);
                                    // Burada sıralama işlevi eklenebilir
                                });
                            });
                        }
                    });
                }
                
                // Ekipman listesi sayfası
                if (url.indexOf('EquipmentList') !== -1) {
                    console.log('TKF: Equipment list page detected');
                    
                    // Ekipman formlarını optimize et
                    var equipmentForms = document.querySelectorAll('form[action*="Equipment"]');
                    equipmentForms.forEach(function(form) {
                        if (!form.classList.contains('tkf-enhanced')) {
                            form.classList.add('tkf-enhanced');
                            
                            // Form alanlarına otomatik tamamlama önerileri ekle
                            var brandInput = form.querySelector('[name="Brand"]');
                            if (brandInput) {
                                brandInput.setAttribute('list', 'tkf-brands');
                                
                                // Marka önerileri için datalist ekle
                                if (!document.getElementById('tkf-brands')) {
                                    var datalist = document.createElement('datalist');
                                    datalist.id = 'tkf-brands';
                                    
                                    // Yaygın markalar
                                    var brands = ['Tipik', 'Standart', 'ABC', 'XYZ', 'Test'];
                                    brands.forEach(function(brand) {
                                        var option = document.createElement('option');
                                        option.value = brand;
                                        datalist.appendChild(option);
                                    });
                                    
                                    document.body.appendChild(datalist);
                                }
                            }
                        }
                    });
                }
                
                return 'Szutest forms enhanced';
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            Timber.d("Szutest form enhancement result: $result")
        }
    }
    
    /**
     * Form veriyi gönder - JavaScript API aracılığıyla
     */
    fun submitFormData(formId: String, data: Map<String, String>) {
        val dataJson = JSONObject(data).toString().replace("\"", "\\\"")
        
        val script = """
            (function() {
                if (!window.TKF_FORM_MANAGER) return 'Form manager not initialized';
                
                var result = window.TKF_FORM_MANAGER.fillForm('$formId', $dataJson);
                return result ? 'Data filled successfully' : 'Error filling form data';
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            Timber.d("Form data submission result: $result")
        }
    }
    
    /**
     * Formları sıfırla
     */
    fun resetForms() {
        isFormEnhancementActive = false
        
        val script = """
            (function() {
                if (!window.TKF_FORM_MANAGER) return 'Form manager not initialized';
                
                // Tüm formları temizle
                var forms = document.querySelectorAll('form');
                forms.forEach(function(form) {
                    form.reset();
                });
                
                return 'Forms reset';
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            Timber.d("Forms reset result: $result")
        }
    }
}