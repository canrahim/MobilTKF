package com.asforce.asforcetkf2.data

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Interceptor to add authentication token to requests
 */
class AuthInterceptor : Interceptor {
    
    private var accessToken: String? = null
    
    /**
     * Update the access token
     */
    fun setAccessToken(token: String) {
        this.accessToken = token
    }
    
    /**
     * Clear the access token
     */
    fun clearAccessToken() {
        this.accessToken = null
    }
    
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        
        // Skip authentication for login and token refresh endpoints
        val path = originalRequest.url.encodedPath
        if (path.contains("login") || path.contains("refresh")) {
            return chain.proceed(originalRequest)
        }
        
        // Add Authorization header if we have a token
        if (accessToken != null) {
            val newRequest = originalRequest.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
            
            Log.d("AuthInterceptor", "Adding auth header to: ${originalRequest.url}")
            return chain.proceed(newRequest)
        }
        
        // Proceed with original request if no token
        return chain.proceed(originalRequest)
    }
}
