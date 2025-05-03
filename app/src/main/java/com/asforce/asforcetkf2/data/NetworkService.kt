package com.asforce.asforcetkf2.data

import com.asforce.asforcetkf2.model.LoginRequest
import com.asforce.asforcetkf2.model.LoginResponse
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Interface for the API endpoints
 */
interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body loginRequest: LoginRequest): Response<LoginResponse>
    
    @POST("auth/refresh")
    suspend fun refreshToken(): Response<Map<String, String>>
    
    @POST("auth/logout")
    suspend fun logout(): Response<Map<String, String>>
}

/**
 * Network service responsible for creating and providing the API service
 */
object NetworkService {
    // Base URL for the API from the website's .env.production file
    private const val BASE_URL = "https://asforce.net/api/"
    
    // Auth interceptor instance that can be referenced from repository
    val authInterceptor = AuthInterceptor()
    
    // Create OkHttpClient with interceptors
    val okHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        
        // Use the shared auth interceptor instance
        
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(authInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    // Create Gson converter
    private val gson = GsonBuilder()
        .setLenient()
        .create()
    
    // Create Retrofit instance
    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    // Create and provide the API service
    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}
