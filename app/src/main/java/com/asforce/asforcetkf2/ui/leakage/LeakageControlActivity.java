package com.asforce.asforcetkf2.ui.leakage;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.asforce.asforcetkf2.R;
import com.asforce.asforcetkf2.util.DataHolder;
import com.asforce.asforcetkf2.util.SimpleTextWatcher;
import com.asforce.asforcetkf2.util.WebViewPool;
// import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.HashMap;
import java.util.Map;

/**
 * Kaçak Akım Kontrolü ekranı
 * WebView kullanarak harici bir form sayfasını yükler ve manipüle eder
 */
public class LeakageControlActivity extends AppCompatActivity {
    private static final String TAG = "LeakageControlActivity";
    private static final String PREFS_NAME = "leakage_control_settings";
    private static final String KEY_URL = "saved_url";
    private static final String BASE_URL = "https://app.szutest.com.tr/EXT/PKControl/EditLeakage/";
    private static final long FETCH_DELAY = 1000L;
    
    private Toolbar toolbar; // Toolbar kullanım dışı ancak referans korundu
    private WebView webView;
    private TextInputLayout urlInputLayout;
    private TextInputEditText editTextUrl;
    private ImageView buttonLoadPage, buttonFillForm, buttonSaveItems, backButton;
    private ImageView settingsButton;
    private WebViewPool webViewPool;
    
    private final Map<String, String> columnWidths = new HashMap<>();

    private static final String[] columnNames = {
            "MeasuredLocation", "LeakageRole", "TestCoefficient", "OpenningCurrent",
            "OpenningValue", "Otomet", "TestPoint", "RCDNo", "Result"
    };

    private static final String[] columnLabels = {
            "Ölçüm yapılan Yer", "Kaç. Ak. Rolesi", "Test Katsayısı", "Açma Akımı(mA)",
            "Açma Zamanı(mS)", "Otomat", "Test Noktası", "RCD No", "Sonuç"
    };

    private static final String[] defaultColumnWidths = {
            "140px", "100px", "60px", "70px", "70px", "80px", "130px", "80px", "130px"
    };

    private SharedPreferences sharedPreferences;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // NestedScrollView kaldırıldığı için onPostCreate metodu silindi

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_leakage_control);

        // WebView havuzunu başlat
        webViewPool = WebViewPool.getInstance(this);
        
        initializeViews();
        setupToolbar();
        setupSharedPreferences();
        setupWebView();
        setupClickListeners();
        setupTouchHandling();
        restoreState(savedInstanceState);

        // URL'yi Shared Preferences'tan yükle
        SharedPreferences prefs = getSharedPreferences("LeakageControlPrefs", MODE_PRIVATE);
        String lastUrl = prefs.getString("lastUrl", ""); // Varsayılan olarak boş dize
        editTextUrl.setText(lastUrl);

        loadWebPage();
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private void setupTouchHandling() {
        // WebView için direkt dokunma olayı iyileştirmesi
        if (webView != null) {
            // Kaydırma performansını artırmak için
            webView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
            
            // Size parametrelerini güncelle
            webView.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            
            // Dokunma ve kaydırma iyileştirmesi
            webView.setFocusable(true);
            webView.setFocusableInTouchMode(true);
            
            // Direkt WebView'e geçerli onTouch olayları için
            webView.setOnTouchListener((v, event) -> {
                v.requestFocus();
                v.performClick();
                return false; // Olayları işlemeye devam et
            });
        }
        
        // Kaydırmayı düzenli aralıklarla optimize et (animasyon frame'leriyle senkronize et)
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (webView != null) {
                    // Performans için webview'i yenile
                    webView.invalidate();
                }
                // Kendimizi sıradaki frame'e zamanla
                handler.postDelayed(this, 16); // ~60fps
            }
        });
    }

    private void initializeViews() {
        // toolbar = findViewById(R.id.toolbar); // Toolbar kaldırıldı
        urlInputLayout = findViewById(R.id.urlInputLayout);
        editTextUrl = findViewById(R.id.editTextUrl);
        buttonLoadPage = findViewById(R.id.buttonLoadPage);
        buttonFillForm = findViewById(R.id.buttonFillForm);
        buttonSaveItems = findViewById(R.id.buttonSaveItems);
        settingsButton = findViewById(R.id.settingsButton);
        backButton = findViewById(R.id.backButton);
        
        // WebView havuzundan al
        webView = webViewPool.acquireWebView();
        
        // WebView'in mevcut bir parent'i varsa önce ondan kaldır
        ViewParent parent = webView.getParent();
        if (parent instanceof ViewGroup) {
            Log.d(TAG, "WebView already has a parent, removing from parent first");
            ((ViewGroup) parent).removeView(webView);
        }
        
        // WebView'i container'a ekle
        LinearLayout webViewContainer = findViewById(R.id.webViewContainer);
        webViewContainer.addView(webView);
    }
    
    private void setupToolbar() {
        // Toolbar kaldırıldığı için bu metod artık kullanılmıyor
        // Ancak geriye dönük uyumluluk için boş olarak bırakıldı
    }

    private void setupSharedPreferences() {
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadColumnWidths();
    }

    private void loadColumnWidths() {
        for (int i = 0; i < columnNames.length; i++) {
            String width = sharedPreferences.getString(columnNames[i], defaultColumnWidths[i]);
            columnWidths.put(columnNames[i], width);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupWebView() {
        // WebView null kontrolü ekleyelim
        if (webView == null) {
            Log.e(TAG, "WebView is null in setupWebView");
            // Tekrar webview havuzundan almayı deneyelim
            webView = webViewPool.acquireWebView();
            
            // WebView'in mevcut bir parent'i varsa önce ondan kaldır
            ViewParent parent = webView.getParent();
            if (parent instanceof ViewGroup) {
                Log.d(TAG, "WebView already has a parent in setupWebView, removing it first");
                ((ViewGroup) parent).removeView(webView);
            }
            
            LinearLayout webViewContainer = findViewById(R.id.webViewContainer);
            if (webViewContainer != null && webView != null) {
                webViewContainer.addView(webView);
            } else {
                Log.e(TAG, "Could not recover WebView in setupWebView");
                return;
            }
        }
        
        // WebView dokunma hassasiyeti ve sürükleme ayarlarını iyileştir
        webView.getSettings().setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        webView.getSettings().setUseWideViewPort(true);
        webView.getSettings().setLoadWithOverviewMode(true);
        
        // Kaydırma davranışını iyileştir
        webView.setVerticalScrollBarEnabled(true);
        webView.setHorizontalScrollBarEnabled(true);
        webView.setScrollContainer(true);
        webView.setScrollbarFadingEnabled(false); // Kaydırma çubuğunu her zaman göster
        
        // WebView kaydırma davranışını iyileştir
        webView.setOnTouchListener((v, event) -> {
            // Her türlü dokunma olayında WebView'in olayları işlemesine izin ver
            // Yönü ne olursa olsun (yatay/dikey) kaydırmaya izin ver
            // WebView kendi içinde kaydırmayı yönetmesi için parent'tan dokunma olaylarını engelle
            v.getParent().requestDisallowInterceptTouchEvent(true);
            
            // WebView için odak ayarla (klavye girişi vb. için)
            v.requestFocus();
            
            // false döndür ki WebView olayları işlemeye devam etsin
            return false;
        });
        
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                
                // Web sayfası yüklenme işlemi tamamlandığında, WebView kontrolü
                // Aktivite kapanırken WebView null olabilir, bu durumda işlemi atlayarak çöküşü önleyelim
                if (webView == null) {
                    Log.w(TAG, "WebView is null in onPageFinished, skipping operations");
                    return;
                }
                
                try {
                    applyColumnWidths();
                    
                    // URL'yi kaydet
                    SharedPreferences prefs = getSharedPreferences("LeakageControlPrefs", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("lastUrl", url);
                    editor.apply();
                    
                    // Özel kaydırma optimizasyonu - daha agresif bir yaklaşım
                    String scrollOptimizationJS = ""
                        + "(function() {"
                        + "  document.body.style.overflow = 'scroll';"
                        + "  document.body.style.height = 'auto';"
                        + "  document.documentElement.style.overflow = 'scroll';"
                        + "  document.documentElement.style.height = 'auto';"
                        + "  document.body.style.webkitOverflowScrolling = 'touch';"
                        + "  document.documentElement.style.webkitOverflowScrolling = 'touch';"
                        + "  var style = document.createElement('style');"
                        + "  style.textContent = '"
                        + "    * { -webkit-overflow-scrolling: touch !important; }\n"
                        + "    body, html { overflow: scroll !important; height: auto !important; }\n"
                        + "    div, table, tr, td { overflow: visible !important; }\n"
                        + "    .scroll-wrapper { overflow: scroll !important; -webkit-overflow-scrolling: touch !important; }\n"
                        + "  ';"
                        + "  document.head.appendChild(style);"
                        + "  "
                        + "  // Form elementlerini kaydırılabilir yap"
                        + "  var forms = document.querySelectorAll('form');"
                        + "  for (var i = 0; i < forms.length; i++) {"
                        + "    forms[i].style.overflow = 'auto';"
                        + "    forms[i].style.webkitOverflowScrolling = 'touch';"
                        + "  }"
                        + "  "
                        + "  // Tüm container'ları kaydırılabilir yap"
                        + "  var containers = document.querySelectorAll('.container, .container-fluid, .panel, .panel-body, .row, .col');"
                        + "  for (var i = 0; i < containers.length; i++) {"
                        + "    containers[i].style.overflow = 'visible';"
                        + "  }"
                        + "})();";
                webView.evaluateJavascript(scrollOptimizationJS, null);
                    
                    // ProgressBar'ı göster ve gizle (yükleme tamamlandığında)
                    runOnUiThread(() -> {
                        View progressBar = findViewById(R.id.progressBar);
                        if (progressBar != null) {
                            progressBar.setVisibility(View.GONE);
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error in onPageFinished: " + e.getMessage());
                }
            }
            
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                // WebView kontrolü
                if (webView == null) {
                    Log.w(TAG, "WebView is null in onPageStarted, skipping operations");
                    return;
                }
                
                // Sayfa yüklenmeye başladığında progress bar'ı göster
                runOnUiThread(() -> {
                    View progressBar = findViewById(R.id.progressBar);
                    if (progressBar != null) {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private void setupClickListeners() {
        buttonLoadPage.setOnClickListener(v -> loadWebPage());
        buttonFillForm.setOnClickListener(v -> fillForm());
        buttonSaveItems.setOnClickListener(v -> saveItems());
        settingsButton.setOnClickListener(v -> showSettingsDialog());
        backButton.setOnClickListener(v -> {
            finish(); // Ana ekrana geri dön
        });

        setupTextWatchers();
    }

    private void setupTextWatchers() {
        editTextUrl.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String input = s.toString();
                DataHolder.url = input;

                if (input.matches(".*\\d{7}$")) {
                    String lastSixDigits = input.substring(input.length() - 7);
                    editTextUrl.removeTextChangedListener(this);
                    editTextUrl.setText(lastSixDigits);
                    editTextUrl.setSelection(lastSixDigits.length());
                    editTextUrl.addTextChangedListener(this);
                    DataHolder.url = lastSixDigits;
                }
            }
        });
    }

    private void loadWebPage() {
        String url = editTextUrl.getText().toString().trim();

        if (url.isEmpty()) {
            Toast.makeText(this, "Lütfen bir URL veya 6 haneli kod girin", Toast.LENGTH_SHORT).show();
            return;
        }

        if (url.matches("\\d{7}")) {
            url = BASE_URL + url;
        } else if (!url.startsWith("http")) {
            url = "https://" + url;
        }

        buttonLoadPage.setEnabled(false);
        
        // WebView null kontrolü ekleyelim
        if (webView == null) {
            Log.e(TAG, "WebView is null before loadUrl, trying to recover...");
            webView = webViewPool.acquireWebView();
            LinearLayout webViewContainer = findViewById(R.id.webViewContainer);
            if (webViewContainer != null && webView != null) {
                webViewContainer.addView(webView);
                setupWebView(); // WebView client'i yeniden ayarlayalım
            } else {
                Log.e(TAG, "Could not recover WebView, cannot load URL");
                Toast.makeText(this, "Sayfa yüklenemedi, lütfen tekrar deneyin", Toast.LENGTH_SHORT).show();
                buttonLoadPage.setEnabled(true);
                return;
            }
        }
        
        webView.loadUrl(url);
        handler.postDelayed(this::fetchAndSaveMeasuredLocation0, FETCH_DELAY);
        handler.postDelayed(() -> buttonLoadPage.setEnabled(true), 1000);
    }

    private void fillForm() {
        // WebView null kontrolü ekleyelim
        if (webView == null) {
            Log.e(TAG, "WebView is null in fillForm");
            Toast.makeText(this, "Form doldurulamadı, lütfen sayfayı yeniden yükleyin", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String jsCode = buildFormFillJavaScript();
        webView.evaluateJavascript(jsCode, result -> {
            Toast.makeText(this, "Form dolduruldu", Toast.LENGTH_SHORT).show();
        });
    }

    private String buildFormFillJavaScript() {
        return "function fillForm() {" +
                "    var rows = document.querySelectorAll('[name^=\"LeakageRole\"]');" +
                "    for (var i = 0; i < rows.length; i++) {" +
                "        var leakageRole = document.getElementsByName('LeakageRole' + i)[0];" +
                "        var result = document.getElementsByName('Result' + i)[0];" +
                "        var openingCurrent = document.getElementsByName('OpenningCurrent' + i)[0];" +
                "        var openingValue = document.getElementsByName('OpenningValue' + i)[0];" +
                "        var testCoefficient = document.getElementsByName('TestCoefficient' + i)[0];" +

                "        if (testCoefficient) {" +
                "            Array.from(testCoefficient.options).forEach(option => {" +
                "                if (option.label.trim() === '1') {" +
                "                    option.selected = true;" +
                "                }" +
                "            });" +
                "            testCoefficient.dispatchEvent(new Event('change', { bubbles: true }));" +
                "        }" +

                "        if (leakageRole && result && openingCurrent && openingValue) {" +
                "            if (result.value === 'string:Uygun Değil' || " +
                "                result.value === 'string:Test Edilemedi' || " +
                "                result.value === 'string:Test Edilemedi (NOT 1)') {" +
                "                openingCurrent.value = '---';" +
                "                openingValue.value = '---';" +
                "            } else {" +
                "                if (leakageRole.value === 'string:30ma') {" +
                "                    openingCurrent.value = (15.0 + Math.random() * (27.9 - 15.0)).toFixed(1);" +
                "                } else if (leakageRole.value === 'string:300ma') {" +
                "                    openingCurrent.value = (150.0 + Math.random() * (279.9 - 150.0)).toFixed(1);" +
                "                }" +

                "                let randomValue = Math.random() * 100;" +
                "                if (randomValue < 80) {" +
                "                    openingValue.value = (20.0 + Math.random() * (30.9 - 20.0)).toFixed(1);" +
                "                } else {" +
                "                    openingValue.value = (30.9 + Math.random() * (55.9 - 30.9)).toFixed(1);" +
                "                }" +
                "            }" +

                "            openingCurrent.dispatchEvent(new Event('input', { bubbles: true }));" +
                "            openingValue.dispatchEvent(new Event('input', { bubbles: true }));" +
                "        }" +
                "    }" +
                "    return 'completed';" +
                "}" +
                "fillForm();";
    }

    private void saveItems() {
        // WebView null kontrolü ekleyelim
        if (webView == null) {
            Log.e(TAG, "WebView is null in saveItems");
            Toast.makeText(this, "Değişiklikler kaydedilemedi, lütfen sayfayı yeniden yükleyin", Toast.LENGTH_SHORT).show();
            return;
        }
        
        String saveScript =
                "var saveButton = document.querySelector('.actionB .btn-group .btn.btn-default.waves-effect.m-r-5');" +
                "if (saveButton) { " +
                "    saveButton.click(); " +
                "    return 'saved';" +
                "} else {" +
                "    return 'save-button-not-found';" +
                "}";
                
        webView.evaluateJavascript(saveScript, result -> {
            if (result.contains("saved")) {
                Toast.makeText(this, "Değişiklikler kaydedildi", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Kaydetme butonu bulunamadı", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static final int MAX_RETRY_COUNT = 5;
    private static final long RETRY_DELAY_MS = 500;
    private int retryCount = 0;

    private void applyColumnWidths() {
        // WebView null kontrolü ekleyelim
        if (webView == null) {
            Log.e(TAG, "WebView is null in applyColumnWidths");
            return;
        }

        try {
            String checkElementsScript = "(function() {" +
                    "var allFound = true;" +
                    "for (var i = 0; i < " + columnNames.length + "; i++) {" +
                    "    var elements = document.getElementsByName('" + columnNames[0] + "0');" +
                    "    if (!elements || elements.length === 0) {" +
                    "        allFound = false;" +
                    "        break;" +
                    "    }" +
                    "}" +
                    "return allFound;" +
                    "})()";

        webView.evaluateJavascript(checkElementsScript, result -> {
            if ("true".equals(result)) {
                applyWidthsToColumns();
                retryCount = 0; // Reset retry count on success
            } else if (retryCount < MAX_RETRY_COUNT) {
                retryCount++;
                Log.d(TAG, "Retrying column width application. Attempt: " + retryCount);
                handler.postDelayed(this::applyColumnWidths, RETRY_DELAY_MS);
            } else {
                Log.e(TAG, "Failed to apply column widths after " + MAX_RETRY_COUNT + " attempts");
                retryCount = 0; // Reset for next time
            }
        });
        } catch (Exception e) {
            Log.e(TAG, "Error in applyColumnWidths: " + e.getMessage());
            retryCount = 0; // Reset for next time
        }
    }

    private void applyWidthsToColumns() {
        // WebView null kontrolü ekleyelim
        if (webView == null) {
            Log.e(TAG, "WebView is null in applyWidthsToColumns");
            return;
        }
        
        try {
            StringBuilder jsBuilder = new StringBuilder();
            jsBuilder.append("(function() {");

        // Column width adjustments with verification
        jsBuilder.append("try {");
        for (String columnName : columnNames) {
            String columnWidth = columnWidths.get(columnName);

            jsBuilder.append("var elements = document.getElementsByName('")
                    .append(columnName)
                    .append("0');")
                    .append("if(elements && elements.length > 0) {")
                    .append("    for(var i=0; i<elements.length; i++) {")
                    .append("        elements[i].style.width = '")
                    .append(columnWidth)
                    .append("';")
                    .append("        elements[i].style.minWidth = '")
                    .append(columnWidth)
                    .append("';")
                    .append("    }")
                    .append("}");
        }

        // Panel padding adjustment with mutation observer
        jsBuilder.append("var style = document.createElement('style');")
                .append("style.type = 'text/css';")
                .append("style.innerHTML = '.panel-body { padding: 1px !important; }';")
                .append("document.head.appendChild(style);")

                // Add MutationObserver to maintain column widths
                .append("var observer = new MutationObserver(function(mutations) {")
                .append("    mutations.forEach(function(mutation) {")
                .append("        if (mutation.type === 'attributes' || mutation.type === 'childList') {");

        // Re-apply widths in observer
        for (String columnName : columnNames) {
            String columnWidth = columnWidths.get(columnName);

            jsBuilder.append("var elements = document.getElementsByName('")
                    .append(columnName)
                    .append("0');")
                    .append("if(elements && elements.length > 0) {")
                    .append("    for(var i=0; i<elements.length; i++) {")
                    .append("        elements[i].style.width = '")
                    .append(columnWidth)
                    .append("';")
                    .append("        elements[i].style.minWidth = '")
                    .append(columnWidth)
                    .append("';")
                    .append("    }")
                    .append("}");
        }

        jsBuilder.append("        }")
                .append("    });")
                .append("});")

                // Start observing
                .append("observer.observe(document.body, {")
                .append("    childList: true,")
                .append("    subtree: true,")
                .append("    attributes: true")
                .append("});")

                .append("} catch(e) { console.error('Error applying column widths:', e); }")
                .append("return 'applied';")
                .append("})()");

        webView.evaluateJavascript(jsBuilder.toString(), result -> {
            if ("\"applied\"".equals(result)) {
                Log.d(TAG, "Column widths applied successfully");
            } else {
                Log.e(TAG, "Error applying column widths: " + result);
            }
        });
        } catch (Exception e) {
            Log.e(TAG, "Error in applyWidthsToColumns: " + e.getMessage());
        }
    }

    @SuppressLint("SetTextI18n")
    private void showSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sütun Genişliklerini Ayarla");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = getResources().getDimensionPixelSize(android.R.dimen.app_icon_size) / 4;
        layout.setPadding(padding, padding, padding, padding);

        final EditText[] editTexts = new EditText[columnNames.length];

        for (int i = 0; i < columnNames.length; i++) {
            final String columnName = columnNames[i];
            final String columnLabel = columnLabels[i];

            TextView label = new TextView(this);
            label.setText(columnLabel + ":");
            layout.addView(label);

            final EditText editText = new EditText(this);
            editText.setHint("Genişlik (" + columnLabel + ")");
            editText.setText(columnWidths.get(columnName));
            editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
            editText.setInputType(InputType.TYPE_CLASS_TEXT);

            editText.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    hideKeyboard(v);
                    return true;
                }
                return false;
            });

            editTexts[i] = editText;
            layout.addView(editText);
        }

        builder.setView(layout);
        builder.setPositiveButton("Kaydet", (dialog, which) -> {
            saveColumnSettings(editTexts);
            showPageRefreshConfirmation();
        });

        builder.setNegativeButton("İptal", null);
        builder.show();
    }

    private void saveColumnSettings(EditText[] editTexts) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        for (int i = 0; i < columnNames.length; i++) {
            String newWidth = editTexts[i].getText().toString();
            columnWidths.put(columnNames[i], newWidth);
            editor.putString(columnNames[i], newWidth);
        }
        editor.apply();
    }

    private void showPageRefreshConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Sayfa Yenileme")
                .setMessage("Ayarları uygulamak için sayfa yenilenecektir. Onaylıyor musunuz?")
                .setPositiveButton("Evet", (dialog, which) -> webView.reload())
                .setNegativeButton("Hayır", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private void fetchAndSaveMeasuredLocation0() {
        // WebView null kontrolü ekleyelim
        if (webView == null) {
            Log.e(TAG, "WebView is null in fetchAndSaveMeasuredLocation0");
            return;
        }
        
        String script =
                "(function() {" +
                        "    var element = document.querySelector('[name=\"MeasuredLocation0\"]');" +
                        "    return element ? element.value : '';" +
                        "})()";

        webView.evaluateJavascript(script, value -> {
            if (value != null && !value.equals("null") && !value.equals("\"\"")) {
                DataHolder.measuredLocation0 = value.replaceAll("^\"|\"$", "");
                Log.d(TAG, "MeasuredLocation0: " + DataHolder.measuredLocation0);
            } else {
                Log.d(TAG, "MeasuredLocation0 değeri alınamadı");
            }
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_URL, editTextUrl.getText().toString());
    }

    private void restoreState(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            String savedUrl = savedInstanceState.getString(KEY_URL);
            if (savedUrl != null) {
                editTextUrl.setText(savedUrl);
            }
        }

        if (!DataHolder.url.isEmpty()) {
            editTextUrl.setText(DataHolder.url);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            handleTouchEvent(event);
        }
        return super.dispatchTouchEvent(event);
    }

    private void handleTouchEvent(MotionEvent event) {
        View focusedView = getCurrentFocus();
        if (!(focusedView instanceof EditText)) {
            return;
        }

        Rect outRect = new Rect();
        focusedView.getGlobalVisibleRect(outRect);
        if (!outRect.contains((int) event.getRawX(), (int) event.getRawY())) {
            focusedView.clearFocus();
            hideKeyboard(focusedView);
        }
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
    
    // Aktivite yaşam döngüsü yönetimi
    @Override
    protected void onPause() {
        super.onPause();
        // WebView durdurma işlemlerini yap
        if (webView != null) {
            webView.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // WebView yeniden başlatma işlemlerini yap
        if (webView != null) {
            webView.onResume();
        } else {
            // WebView yoksa yeniden oluşturmayı dene
            Log.w(TAG, "WebView is null in onResume, trying to recover");
            initializeViews();
            setupWebView();
        }
    }
    
    @Override
    protected void onDestroy() {
        try {
            // WebView'i havuza geri ver
            if (webView != null) {
                // WebView içeriğini temizleyelim
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.clearHistory();
                webView.clearCache(true);
                
                // WebView'in parent'i varsa önce kaldır
                ViewParent parent = webView.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(webView);
                }
                
                // Havuza geri verelim
                webViewPool.releaseWebView(webView);
                webView = null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error cleaning up WebView in onDestroy", e);
        }
        super.onDestroy();
    }
}