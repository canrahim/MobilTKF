package com.asforce.asforcetkf2.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.asforce.asforcetkf2.MainActivity
import com.asforce.asforcetkf2.R
import com.asforce.asforcetkf2.data.AuthRepository
import com.asforce.asforcetkf2.data.TokenManager
import com.asforce.asforcetkf2.databinding.ActivityLoginBinding
import com.asforce.asforcetkf2.model.LoginRequest
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var authRepository: AuthRepository
    private lateinit var tokenManager: TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize dependencies
        tokenManager = TokenManager(this)
        authRepository = AuthRepository(tokenManager)

        // Check if user is already logged in
        if (tokenManager.getAccessToken() != null) {
            navigateToMainActivity()
            return
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        // Login button click
        binding.loginButton.setOnClickListener {
            if (validateInputs()) {
                performLogin()
            }
        }

        // Forgot password click
        binding.forgotPasswordTextView.setOnClickListener {
            // TODO: Implement "Forgot Password" functionality
            Toast.makeText(this, "Şifremi Unuttum özelliği henüz uygulanmadı", Toast.LENGTH_SHORT).show()
        }

        // Register click
        binding.registerTextView.setOnClickListener {
            // TODO: Implement "Register" functionality
            Toast.makeText(this, "Kayıt Ol özelliği henüz uygulanmadı", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        // Validate email
        val email = binding.emailEditText.text.toString().trim()
        if (email.isEmpty()) {
            binding.emailInputLayout.error = "E-posta adresi gerekli"
            isValid = false
        } else if (!isValidEmail(email)) {
            binding.emailInputLayout.error = "Geçerli bir e-posta adresi girin"
            isValid = false
        } else {
            binding.emailInputLayout.error = null
        }

        // Validate password
        val password = binding.passwordEditText.text.toString()
        if (password.isEmpty()) {
            binding.passwordInputLayout.error = "Şifre gerekli"
            isValid = false
        } else if (password.length < 6) {
            binding.passwordInputLayout.error = "Şifre en az 6 karakter olmalıdır"
            isValid = false
        } else {
            binding.passwordInputLayout.error = null
        }

        return isValid
    }

    private fun isValidEmail(email: String): Boolean {
        val emailPattern = "[a-zA-Z0-9._-]+@[a-z]+\\.+[a-z]+"
        return email.matches(emailPattern.toRegex())
    }

    private fun performLogin() {
        showLoading(true)

        // Create login request
        val email = binding.emailEditText.text.toString().trim()
        val password = binding.passwordEditText.text.toString()
        val loginRequest = LoginRequest(email, password)

        // Error handler for coroutine
        val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            lifecycleScope.launch(Dispatchers.Main) {
                showLoading(false)
                Toast.makeText(
                    this@LoginActivity,
                    "Giriş hatası: ${throwable.localizedMessage ?: "Bilinmeyen hata"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // Launch coroutine for login
        lifecycleScope.launch(Dispatchers.IO + exceptionHandler) {
            try {
                val loginResult = authRepository.login(loginRequest)
                
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    if (loginResult.isSuccess) {
                        navigateToMainActivity()
                    } else {
                        Toast.makeText(
                            this@LoginActivity,
                            loginResult.errorMessage ?: "Giriş yapılamadı",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(
                        this@LoginActivity,
                        "Giriş hatası: ${e.localizedMessage ?: "Bilinmeyen hata"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.loginButton.isEnabled = !isLoading
        binding.emailEditText.isEnabled = !isLoading
        binding.passwordEditText.isEnabled = !isLoading
        binding.forgotPasswordTextView.isEnabled = !isLoading
        binding.registerTextView.isEnabled = !isLoading
    }

    private fun navigateToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
