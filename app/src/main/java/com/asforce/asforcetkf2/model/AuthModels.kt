package com.asforce.asforcetkf2.model

/**
 * Login request model that matches the API
 */
data class LoginRequest(
    val email: String,
    val password: String
)

/**
 * Login response model that matches the API response
 */
data class LoginResponse(
    val user: User,
    val accessToken: String
)

/**
 * User model
 */
data class User(
    val id: String,
    val email: String,
    val username: String,
    val displayName: String?,
    val role: String,
    val isEmailVerified: Boolean,
    val createdAt: String,
    val updatedAt: String
)

/**
 * Auth result wrapper
 */
data class AuthResult(
    val isSuccess: Boolean,
    val user: User? = null,
    val accessToken: String? = null,
    val errorMessage: String? = null
)

/**
 * Error response model
 */
data class ErrorResponse(
    val message: String,
    val error: String? = null,
    val statusCode: Int? = null
)

/**
 * Forgot password request model
 */
data class ForgotPasswordRequest(
    val email: String
)

/**
 * Reset password request model
 */
data class ResetPasswordRequest(
    val token: String,
    val password: String
)
