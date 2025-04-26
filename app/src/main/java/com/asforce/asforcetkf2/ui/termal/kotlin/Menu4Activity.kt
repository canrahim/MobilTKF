package com.asforce.asforcetkf2.ui.termal.kotlin

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.Request
import com.android.volley.RequestQueue
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.asforce.asforcetkf2.R
import com.asforce.asforcetkf2.ui.termal.kotlin.adapter.PersonAdapter
import com.asforce.asforcetkf2.ui.termal.kotlin.model.Person
import com.asforce.asforcetkf2.util.DataHolder
import com.asforce.asforcetkf2.util.SimpleTextWatcher
import org.json.JSONArray
import org.json.JSONException
import java.util.ArrayList
import java.util.Collections
import java.util.HashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Menu4Activity : AppCompatActivity() {

    private lateinit var adapter: PersonAdapter
    private lateinit var etValue1: EditText
    private lateinit var etValue2: EditText
    private lateinit var etValue3: EditText
    private lateinit var etSearch: EditText
    private lateinit var btnFetchData: Button
    private lateinit var btnAdd: Button
    private lateinit var btnUpdate: Button
    private lateinit var btnDeleteSelected: Button
    private lateinit var btnSelectAll: Button
    private lateinit var rvItems: RecyclerView
    private lateinit var webView: WebView
    private lateinit var backButton: ImageButton
    private lateinit var menuContent1: LinearLayout

    private var selectedId: String? = null
    private var selectedPosition = -1
    
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    companion object {
        private const val API_URL = "https://abdurrahimsenturk.com.tr/termalapi.php"
        private const val TAG = "Menu4Activity"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu4)
        
        initializeViews()
        setupClickListeners()
        
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        if (DataHolder.measuredLocation0 != null && DataHolder.measuredLocation0!!.isNotEmpty()) {
            etValue3.setText(DataHolder.measuredLocation0)
        }

        val measuredLocation = DataHolder.measuredLocation0
        if (!measuredLocation.isNullOrEmpty()) {
            etValue3.setText(measuredLocation)
            etValue3.clearFocus()
        } else {
            etValue3.setText("")
        }

        if (DataHolder.url.isNotEmpty()) {
            etValue1.setText(DataHolder.url)
        }

        // WebView ayarları
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d(TAG, "WebView: Page started loading: $url")
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                Log.d(TAG, "WebView: Page finished loading: $url")
                extractDataFromWebView(view)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                super.onReceivedError(view, request, error)
                Log.e(TAG, "WebView Error: ${error.description} URL: ${request.url}")
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(this@Menu4Activity, "Web sayfasından veri çekilirken hata oluştu.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        if (etValue1.text.isNotEmpty()) {
            val initialUrl = buildUrl(etValue1.text.toString())
            webView.loadUrl(initialUrl)
            Log.d(TAG, "WebView: Initial URL loading: $initialUrl")
        }

        adapter = PersonAdapter(object : PersonAdapter.OnItemClickListener {
            override fun onItemClick(person: Person, position: Int) {
                for (i in 0 until adapter.itemCount) {
                    adapter.getItem(i).isHighlighted = false
                }

                person.isHighlighted = true

                etValue1.setText(person.value1)
                etValue2.setText(person.value2)
                etValue3.setText(person.value3)

                selectedId = person.id
                adapter.notifyDataSetChanged()
            }

            override fun onDeleteClick(position: Int) {
                performAction("delete", adapter.getItem(position).id)
            }

            override fun onItemLongClick(person: Person, position: Int) {
                person.isSelected = !person.isSelected
                adapter.notifyItemChanged(position)

                updateDeleteSelectedButtonText()
                updateSelectAllButtonText()
            }
        })

        etSearch.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = etSearch.text.toString().trim()
                filterList(query)

                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(etSearch.windowToken, 0)
                return@setOnEditorActionListener true
            }
            false
        }

        etValue1.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(v.windowToken, 0)
                return@setOnEditorActionListener true
            }
            false
        }

        etValue2.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(v.windowToken, 0)
                return@setOnEditorActionListener true
            }
            false
        }

        etValue3.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(v.windowToken, 0)
                return@setOnEditorActionListener true
            }
            false
        }

        rvItems.layoutManager = LinearLayoutManager(this)
        rvItems.adapter = adapter

        btnFetchData.setOnClickListener { fetchAndDisplayData() }
        btnAdd.setOnClickListener {
            if (etValue1.text.toString().trim().isEmpty() ||
                etValue2.text.toString().trim().isEmpty() ||
                etValue3.text.toString().trim().isEmpty()) {
                Toast.makeText(this, "Lütfen tüm alanları doldurun!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performAction("insert", null)
        }

        btnUpdate.setOnClickListener {
            if (selectedId == null) {
                Toast.makeText(this, "Güncellemek için bir kayıt seçin!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (etValue1.text.toString().trim().isEmpty() ||
                etValue2.text.toString().trim().isEmpty() ||
                etValue3.text.toString().trim().isEmpty()) {
                Toast.makeText(this, "Lütfen tüm alanları doldurun!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            performAction("update", selectedId)
        }

        btnDeleteSelected.setOnClickListener { deleteSelectedItems() }
        btnSelectAll.setOnClickListener { toggleSelectAll() }

        setupTextWatchers()

        etSearch.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable) {
                filterList(s.toString())
            }
        })

        fetchAndDisplayData()
        updateSelectAllButtonText()
        updateDeleteSelectedButtonText()

        val btnClearFields = findViewById<Button>(R.id.btnClearFields)
        btnClearFields.setOnClickListener {
            etValue1.setText("")
            etValue2.setText("")
            etValue3.setText("")
            etSearch.setText("")
            Toast.makeText(this, "Alanlar temizlendi!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun initializeViews() {
        // Görünüm öğelerini bağlama
        etValue1 = findViewById(R.id.etValue1)
        etValue2 = findViewById(R.id.etValue2)
        etValue3 = findViewById(R.id.etValue3)
        etSearch = findViewById(R.id.etSearch)
        btnFetchData = findViewById(R.id.btnFetchData)
        btnAdd = findViewById(R.id.btnAdd)
        btnUpdate = findViewById(R.id.btnUpdate)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)
        btnSelectAll = findViewById(R.id.btnSelectAll)
        rvItems = findViewById(R.id.rvItems)
        webView = findViewById(R.id.webView)
        
        // Menü içerikleri
        backButton = findViewById(R.id.backButton)
        menuContent1 = findViewById(R.id.menuContent1)
    }

    private fun setupClickListeners() {
        // Geri butonu
        backButton.setOnClickListener {
            // Ana ekrana geri dön (MainActivity'ye git)
            val intent = Intent(this, com.asforce.asforcetkf2.MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
        }
        
        // Menü seçenekleri
        findViewById<View>(R.id.menu1Option1).setOnClickListener {
            finish()
            menuContent1.visibility = View.GONE
        }

        findViewById<View>(R.id.menu1Option2).setOnClickListener {
            finish()
            menuContent1.visibility = View.GONE
        }
        
        findViewById<View>(R.id.menu1Option3).setOnClickListener {
            finish()
            menuContent1.visibility = View.GONE
        }

        findViewById<View>(R.id.menu1Option4).setOnClickListener {
            // Ana ekrana geri dön (MainActivity'ye git)
            val intent = Intent(this, com.asforce.asforcetkf2.MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            finish()
            menuContent1.visibility = View.GONE
        }
    }

    private fun toggleMenu() {
        // Menüyü aç/kapat
        menuContent1.visibility = if (menuContent1.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun setupTextWatchers() {
        etValue1.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable) {
                DataHolder.url = s.toString()
                val input = s.toString()
                if (input.matches(".*\\d{6}$".toRegex())) {
                    val lastSixDigits = input.substring(input.length - 6)
                    etValue1.removeTextChangedListener(this)
                    etValue1.setText(lastSixDigits)
                    etValue1.setSelection(lastSixDigits.length)
                    etValue1.addTextChangedListener(this)
                    DataHolder.url = lastSixDigits
                    val url = buildUrl(lastSixDigits)
                    if (DataHolder.measuredLocation0 == null || DataHolder.measuredLocation0!!.isEmpty()) {
                        webView.loadUrl(url)
                        Log.d(TAG, "WebView: URL loading: $url")
                    } else {
                        etValue3.setText(DataHolder.measuredLocation0)
                    }
                }
            }
        })

        etValue2.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable) {
                if (selectedPosition >= 0) {
                    adapter.getItem(selectedPosition).value2 = s.toString()
                }
            }
        })

        etValue3.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable) {
                if (selectedPosition >= 0) {
                    adapter.getItem(selectedPosition).value3 = s.toString()
                }
            }
        })
    }

    private fun updateDeleteSelectedButtonText() {
        val selectedCount = adapter.getSelectedIds().size
        btnDeleteSelected.text = "Sil ($selectedCount)"
    }

    private fun updateSelectAllButtonText() {
        val allSelected = adapter.areAllItemsSelected()
        btnSelectAll.text = if (allSelected) "Seçimi Kaldır" else "Tümünü Seç"
    }

    private fun fetchAndDisplayData() {
        val stringRequest = object : StringRequest(
            Request.Method.POST, API_URL,
            Response.Listener { response ->
                try {
                    val jsonArray = JSONArray(response)
                    val newPersonList = ArrayList<Person>()
                    for (i in 0 until jsonArray.length()) {
                        val jsonObject = jsonArray.getJSONObject(i)

                        val id = jsonObject.optString("id", "")
                        val value1 = jsonObject.optString("value1", "Eksik")
                        val value2 = jsonObject.optString("value2", "Eksik")
                        val value3 = jsonObject.optString("value3", "Eksik")

                        newPersonList.add(Person(id, value1, value2, value3))
                    }

                    Collections.reverse(newPersonList)

                    Handler(Looper.getMainLooper()).post {
                        adapter.clearItems()
                        adapter.addItems(newPersonList)
                        updateSelectAllButtonText()
                        updateDeleteSelectedButtonText()
                        Toast.makeText(this, "Veriler başarıyla yüklendi!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: JSONException) {
                    Log.e(TAG, "JSON Hatası: ${e.message}")
                    Toast.makeText(this, "Veri işlenirken hata oluştu", Toast.LENGTH_SHORT).show()
                }
            },
            Response.ErrorListener { error ->
                Log.e(TAG, "Sunucu Hatası: $error")
                Toast.makeText(this, "Veri alımı sırasında hata oluştu", Toast.LENGTH_SHORT).show()
            }) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["action"] = "select"
                return params
            }
        }

        val requestQueue = Volley.newRequestQueue(this)
        requestQueue.add(stringRequest)
    }

    private fun buildUrl(lastSixDigits: String): String {
        return "https://app.szutest.com.tr/EXT/PKControl/FillForm/$lastSixDigits"
    }

    private fun extractDataFromWebView(view: WebView) {
        view.evaluateJavascript(
            """(function() { 
                var element = document.querySelector('input[name="Properties[4].Value"]');
                return (element != null) ? element.value : null;
            })()""".trimIndent()
        ) { value ->
            if (value != null && value != "null") {
                Log.d(TAG, "Value from webView: $value")
                Handler(Looper.getMainLooper()).post {
                    etValue3.setText(value.replace("\"", ""))
                    DataHolder.measuredLocation0 = value.replace("\"", "")
                }
            } else {
                Log.d(TAG, "Value not found or null in webView")
                Handler(Looper.getMainLooper()).post {
                    etValue3.setText("")
                    Toast.makeText(this@Menu4Activity, "Web sayfasından veri çekilemedi.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun filterList(query: String) {
        val filteredList = ArrayList<Person>()
        for (person in adapter.getFullList()) {
            if (person.value1.lowercase().contains(query.lowercase()) ||
                person.value2.lowercase().contains(query.lowercase()) ||
                person.value3.lowercase().contains(query.lowercase())) {
                filteredList.add(person)
            }
        }
        adapter.updateList(filteredList)
    }

    private fun deleteSelectedItems() {
        val selectedIds = adapter.getSelectedIds()
        if (selectedIds.isEmpty()) {
            Toast.makeText(this, "Silmek için öğe seçin!", Toast.LENGTH_SHORT).show()
            return
        }
        for (id in selectedIds) {
            performAction("delete", id)
        }
        fetchAndDisplayData()
    }

    private fun toggleSelectAll() {
        val allSelected = adapter.areAllItemsSelected()
        adapter.setAllItemsSelected(!allSelected)
        adapter.notifyDataSetChanged()
        updateDeleteSelectedButtonText()
        updateSelectAllButtonText()
    }

    private fun performAction(action: String, id: String?) {
        Log.d(TAG, "performAction başladı - action: $action, id: $id")
        Log.d(TAG, "Gönderilen veriler - value1: ${etValue1.text}, value2: ${etValue2.text}, value3: ${etValue3.text}")
        
        val stringRequest = object : StringRequest(
            Request.Method.POST, API_URL,
            Response.Listener { 
                Log.d(TAG, "Sunucu yanıtı: $it")
                fetchAndDisplayData() 
            },
            Response.ErrorListener { error -> 
                Log.e(TAG, "Sunucu hatası: ${error.message}")
                Log.e(TAG, "Hata detayı: ${error.networkResponse?.data?.toString(Charsets.UTF_8)}")
                Toast.makeText(this, "İşlem sırasında hata oluştu: ${error.message}", Toast.LENGTH_LONG).show()
            }
        ) {
            override fun getParams(): MutableMap<String, String> {
                val params = HashMap<String, String>()
                params["action"] = action
                if ("insert" == action || "update" == action) {
                    params["value1"] = etValue1.text.toString().trim()
                    params["value2"] = etValue2.text.toString().trim()
                    params["value3"] = etValue3.text.toString().trim()
                }
                if ("update" == action || "delete" == action) {
                    params["id"] = id ?: ""
                }
                Log.d(TAG, "Gönderilen parametreler: $params")
                return params
            }
        }

        val requestQueue = Volley.newRequestQueue(this)
        requestQueue.add(stringRequest)
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val v = currentFocus
            if (v is EditText) {
                val outRect = Rect()
                v.getGlobalVisibleRect(outRect)
                if (!outRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    v.clearFocus()
                    hideKeyboard(v) // Klavyeyi kapat
                }
            }
        }
        return super.dispatchTouchEvent(event)
    }

    // Klavyeyi kapatma metodu
    private fun hideKeyboard(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
} 