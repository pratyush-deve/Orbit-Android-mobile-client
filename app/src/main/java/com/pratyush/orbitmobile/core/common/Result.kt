package com.pratyush.orbitmobile.core.common

/**
 * Generic sealed result type representing operation states across layers.
 */
sealed interface OrbitResult<out T> {
    data class Success<out T>(val data: T) : OrbitResult<T>
    data class Error(val exception: Throwable, val message: String? = exception.message) : OrbitResult<Nothing>
    data object Loading : OrbitResult<Nothing>

    val isSuccess: Boolean
        get() = this is Success

    val isError: Boolean
        get() = this is Error

    val isLoading: Boolean
        get() = this is Loading

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }
}

inline fun <T, R> OrbitResult<T>.map(transform: (T) -> R): OrbitResult<R> = when (this) {
    is OrbitResult.Success -> OrbitResult.Success(transform(data))
    is OrbitResult.Error -> this
    is OrbitResult.Loading -> OrbitResult.Loading
}

inline fun <T> OrbitResult<T>.onSuccess(action: (T) -> Unit): OrbitResult<T> {
    if (this is OrbitResult.Success) action(data)
    return this
}

inline fun <T> OrbitResult<T>.onError(action: (exception: Throwable, message: String?) -> Unit): OrbitResult<T> {
    if (this is OrbitResult.Error) action(exception, message)
    return this
}

