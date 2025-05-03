package com.asforce.asforcetkf2.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Token manager for storing and retrieving auth tokens
 */
class TokenManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    /**
     * Save the access token to shared preferences
     */
    fun saveAccessToken(token: String) {
        prefs.edit().putString(KEY_ACCESS_TOKEN, token).apply()
    }
    
    /**
     * Get the stored access token
     */
    fun getAccessToken(): String? {
        return prefs.getString(KEY_ACCESS_TOKEN, null)
    }
    
    /**
     * Save user info
     */
    fun saveUserInfo(userId: String, username: String, email: String, role: String = "Kullanıcı") {
        prefs.edit()
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USERNAME, username)
            .putString(KEY_EMAIL, email)
            .putString(KEY_USER_ROLE, role)
            .apply()
    }
    
    /**
     * Get user id
     */
    fun getUserId(): String? {
        return prefs.getString(KEY_USER_ID, null)
    }
    
    /**
     * Get username
     */
    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }
    
    /**
     * Get email
     */
    fun getUserEmail(): String? {
        return prefs.getString(KEY_EMAIL, null)
    }
    
    /**
     * Get user role
     */
    fun getUserRole(): String? {
        return prefs.getString(KEY_USER_ROLE, "Kullanıcı")
    }
    
    /**
     * Clear all stored tokens and user info
     */
    fun clearTokens() {
        prefs.edit().clear().apply()
    }
    
    companion object {
        private const val PREF_NAME = "asforce_auth_prefs"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_USERNAME = "username"
        private const val KEY_EMAIL = "email"
        private const val KEY_USER_ROLE = "user_role"
    }
}
