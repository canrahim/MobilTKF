package com.asforce.asforcetkf2.data

import android.util.Log
import com.asforce.asforcetkf2.model.AuthResult
import com.asforce.asforcetkf2.model.ErrorResponse
import com.asforce.asforcetkf2.model.LoginRequest
import com.asforce.asforcetkf2.model.ForgotPasswordRequest
import com.asforce.asforcetkf2.model.ResetPasswordRequest
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * Repository for authentication-related operations
 */
class AuthRepository(private val tokenManager: TokenManager) {
    
    private val apiService = NetworkService.apiService
    private val gson = Gson()
    
    /**
     * Attempt to login with email and password
     */
    suspend fun login(loginRequest: LoginRequest): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.login(loginRequest)
                
                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!
                    
                    // Save the token to token manager
                    tokenManager.saveAccessToken(loginResponse.accessToken)
                    
                    // Save user info
                    loginResponse.user.let { user ->
                        tokenManager.saveUserInfo(
                            userId = user.id,
                            username = user.username,
                            email = user.email
                        )
                    }
                    
                    // Update interceptor with token
                    NetworkService.authInterceptor.setAccessToken(loginResponse.accessToken)
                    
                    // Return success
                    return@withContext AuthResult(
                        isSuccess = true,
                        user = loginResponse.user,
                        accessToken = loginResponse.accessToken
                    )
                } else {
                    // Parse error response
                    val errorBody = response.errorBody()?.string()
                    val errorResponse = try {
                        gson.fromJson(errorBody, ErrorResponse::class.java)
                    } catch (e: Exception) {
                        ErrorResponse(message = "Giriş başarısız oldu")
                    }
                    
                    return@withContext AuthResult(
                        isSuccess = false,
                        errorMessage = errorResponse.message
                    )
                }
            } catch (e: HttpException) {
                Log.e("AuthRepository", "HTTP Error: ${e.message}")
                return@withContext AuthResult(
                    isSuccess = false,
                    errorMessage = "Sunucu hatası: ${e.message}"
                )
            } catch (e: IOException) {
                Log.e("AuthRepository", "Network Error: ${e.message}")
                return@withContext AuthResult(
                    isSuccess = false,
                    errorMessage = "Ağ hatası: İnternet bağlantınızı kontrol edin"
                )
            } catch (e: Exception) {
                Log.e("AuthRepository", "Unknown Error: ${e.message}")
                return@withContext AuthResult(
                    isSuccess = false,
                    errorMessage = "Beklenmeyen bir hata oluştu: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Refresh the access token
     */
    suspend fun refreshToken(): AuthResult {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.refreshToken()
                
                if (response.isSuccessful && response.body() != null) {
                    val refreshResponse = response.body()!!
                    val newAccessToken = refreshResponse["accessToken"]
                    
                    if (newAccessToken != null) {
                        // Save the new token
                        tokenManager.saveAccessToken(newAccessToken)
                        
                        // Update interceptor with new token
                        NetworkService.authInterceptor.setAccessToken(newAccessToken)
                        
                        return@withContext AuthResult(
                            isSuccess = true,
                            accessToken = newAccessToken
                        )
                    } else {
                        return@withContext AuthResult(
                            isSuccess = false,
                            errorMessage = "Token yenileme başarısız"
                        )
                    }
                } else {
                    // Parse error
                    val errorBody = response.errorBody()?.string()
                    val errorResponse = try {
                        gson.fromJson(errorBody, ErrorResponse::class.java)
                    } catch (e: Exception) {
                        ErrorResponse(message = "Token yenileme başarısız oldu")
                    }
                    
                    return@withContext AuthResult(
                        isSuccess = false,
                        errorMessage = errorResponse.message
                    )
                }
            } catch (e: Exception) {
                Log.e("AuthRepository", "Refresh token error: ${e.message}")
                return@withContext AuthResult(
                    isSuccess = false,
                    errorMessage = "Token yenileme hatası: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Logout the user
     */
    suspend fun logout(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.logout()
                
                // Regardless of the response, clear tokens from local storage
                tokenManager.clearTokens()
                
                // Clear token from interceptor
                NetworkService.authInterceptor.clearAccessToken()
                
                return@withContext response.isSuccessful
            } catch (e: Exception) {
                Log.e("AuthRepository", "Logout error: ${e.message}")
                
                // Still clear tokens if the network request fails
                tokenManager.clearTokens()
                
                // Clear token from interceptor
                NetworkService.authInterceptor.clearAccessToken()
                
                return@withContext false
            }
        }
    }
    
    /**
     * Send a password reset link to the specified email
     */
    suspend fun forgotPassword(forgotPasswordRequest: ForgotPasswordRequest): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.forgotPassword(forgotPasswordRequest)
                
                // For security reasons, the API always returns a success message
                // even if the email does not exist in the database
                return@withContext response.isSuccessful
            } catch (e: Exception) {
                Log.e("AuthRepository", "Password reset error: ${e.message}")
                throw e
            }
        }
    }
    
    /**
     * Reset password with token
     */
    suspend fun resetPassword(resetPasswordRequest: ResetPasswordRequest): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = apiService.resetPassword(resetPasswordRequest)
                return@withContext response.isSuccessful
            } catch (e: Exception) {
                Log.e("AuthRepository", "Reset password error: ${e.message}")
                throw e
            }
        }
    }
}
