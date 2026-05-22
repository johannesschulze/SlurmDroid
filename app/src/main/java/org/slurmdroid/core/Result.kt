package org.slurmdroid.core

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class AuthError(val message: String) : Result<Nothing>()
    data class ConnectionError(val message: String) : Result<Nothing>()
    data class ParseError(val message: String) : Result<Nothing>()
    data class UnknownError(val message: String, val cause: Throwable? = null) : Result<Nothing>()
}

/**
 * Re-types any error variant to [Result<T>] without a cast.
 * Safe because all error subtypes extend Result<Nothing>, and Result<out T> is covariant.
 * Throws if called on Result.Success.
 */
fun <T> Result<*>.asError(): Result<T> = when (this) {
    is Result.Success<*> -> error("asError() called on Success")
    is Result.AuthError -> this
    is Result.ConnectionError -> this
    is Result.ParseError -> this
    is Result.UnknownError -> this
}
