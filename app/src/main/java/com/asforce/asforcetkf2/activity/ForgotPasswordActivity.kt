package com.asforce.asforcetkf2.activity

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.asforce.asforcetkf2.data.AuthRepository
import com.asforce.asforcetkf2.data.TokenManager
import com.asforce.asforcetkf2.databinding.ActivityForgotPasswordBinding
import com.asforce.asforcetkf2.model.ForgotPasswordRequest
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var binding: ActivityForgotPasswordBinding
    private lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityForgotPasswordBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize repository
        val tokenManager = TokenManager(this)
        authRepository = AuthRepository(tokenManager)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Reset password button
        binding.resetPasswordButton.setOnClickListener {
            if (validateInput()) {
                sendResetPasswordLink()
            }
        }

        // Back to login
        binding.backToLoginTextView.setOnClickListener {
            finish() // Return to previous activity (LoginActivity)
        }
    }

    private fun validateInput(): Boolean {
        val email = binding.emailEditText.text.toString().trim()
        
        if (email.isEmpty()) {
            binding.emailInputLayout.error = "E-posta adresi gerekli"
            return false
        } else if (!isValidEmail(email)) {
            binding.emailInputLayout.error = "Geçerli bir e-posta adresi girin"
            return false
        }
        
        binding.emailInputLayout.error = null
        return true
    }

    private fun isValidEmail(email: String): Boolean {
        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        return email.matches(emailPattern.toRegex())
    }

    private fun sendResetPasswordLink() {
        showLoading(true)
        
        val email = binding.emailEditText.text.toString().trim()
        val forgotPasswordRequest = ForgotPasswordRequest(email)
        
        // Error handler for coroutine
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            lifecycleScope.launch(Dispatchers.Main) {
                showLoading(false)
                Toast.makeText(
                    this@ForgotPasswordActivity,
                    "Hata: ${throwable.localizedMessage ?: "Bilinmeyen hata"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        
        // Execute the API call
        lifecycleScope.launch(Dispatchers.IO + exceptionHandler) {
            try {
                val result = authRepository.forgotPassword(forgotPasswordRequest)
                
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    
                    // Always show success message even if email doesn't exist
                    // This is a security measure to prevent email enumeration attacks
                    Toast.makeText(
                        this@ForgotPasswordActivity,
                        "Şifre sıfırlama bağlantısı e-posta adresinize gönderildi.",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Clear the input
                    binding.emailEditText.text?.clear()
                    
                    // Wait a moment and then finish the activity
                    binding.root.postDelayed({
                        finish()
                    }, 2000)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(
                        this@ForgotPasswordActivity,
                        "Bağlantı hatası. Lütfen internet bağlantınızı kontrol edin.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.resetPasswordButton.isEnabled = !isLoading
        binding.emailEditText.isEnabled = !isLoading
        binding.backToLoginTextView.isEnabled = !isLoading
    }
}
