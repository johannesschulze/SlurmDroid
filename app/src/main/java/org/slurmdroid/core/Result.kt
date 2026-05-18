package org.slurmdroid.core

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class AuthError(val message: String) : Result<Nothing>()
    data class ConnectionError(val message: String) : Result<Nothing>()
    data class ParseError(val message: String) : Result<Nothing>()
    data class UnknownError(val message: String, val cause: Throwable? = null) : Result<Nothing>()
}
