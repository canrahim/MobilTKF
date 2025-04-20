package com.asforce.asforcetkf2.ui.panel.kotlin

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.asforce.asforcetkf2.R
import com.asforce.asforcetkf2.util.DataHolder

/**
 * Form doldurma dialog fragment
 * Pano fonksiyon sayfası için form değerlerini girmek üzere açılan dialog
 */
class FormDialogFragment(private val webView: WebView?) : DialogFragment() {

    interface FormDialogListener {
        fun onFormSubmitted(
            continuity: String,
            extremeProtection: String,
            voltage: String,
            findings: String,
            cycleImpedance: String
        )
    }

    private lateinit var editContinuity: EditText
    private lateinit var editExtremeIncomeProtection: EditText
    private lateinit var editVoltage: EditText
    private lateinit var editFindings: EditText
    private lateinit var editCycleImpedance: EditText
    private lateinit var buttonSubmit: Button
    private lateinit var buttonCancel: Button
    private var listener: FormDialogListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is FormDialogListener) {
            listener = context
        } else {
            throw RuntimeException("$context must implement FormDialogListener")
        }
    }

    @SuppressLint("InflateParams")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Dialog özellikleri burada ayarlanır
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.setCanceledOnTouchOutside(false)
        
        return inflater.inflate(R.layout.fragment_form_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initializeViews(view)
        loadSavedValues()
        setupClickListeners()
    }

    private fun initializeViews(view: View) {
        editContinuity = view.findViewById(R.id.editContinuity)
        editExtremeIncomeProtection = view.findViewById(R.id.editExtremeIncomeProtection)
        editVoltage = view.findViewById(R.id.editVoltage)
        editFindings = view.findViewById(R.id.editFindings)
        editCycleImpedance = view.findViewById(R.id.editCycleImpedance)
        buttonSubmit = view.findViewById(R.id.buttonSubmit)
        buttonCancel = view.findViewById(R.id.buttonCancel)
    }

    private fun loadSavedValues() {
        // En son kaydedilen değerleri yükle
        editContinuity.setText(DataHolder.continuity)
        editExtremeIncomeProtection.setText(DataHolder.extremeIncomeProtection)
        editVoltage.setText(DataHolder.voltage)
        editFindings.setText(DataHolder.findings)
        editCycleImpedance.setText(DataHolder.cycleImpedance)
    }

    private fun setupClickListeners() {
        buttonCancel.setOnClickListener {
            dismiss()
        }

        buttonSubmit.setOnClickListener {
            val continuity = editContinuity.text.toString()
            val extremeProtection = editExtremeIncomeProtection.text.toString()
            val voltage = editVoltage.text.toString()
            val findings = editFindings.text.toString()
            val cycleImpedance = editCycleImpedance.text.toString()

            listener?.onFormSubmitted(
                continuity,
                extremeProtection,
                voltage,
                findings,
                cycleImpedance
            )
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
} 