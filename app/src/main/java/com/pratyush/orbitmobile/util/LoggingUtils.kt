package com.pratyush.orbitmobile.util

import android.util.Log

/**
 * Structured logging helper.
 */
object LoggingUtils {
    private const val GLOBAL_TAG = "OrbitMobile"

    fun d(tag: String, message: String) {
        Log.d("$GLOBAL_TAG:$tag", message)
    }

    fun i(tag: String, message: String) {
        Log.i("$GLOBAL_TAG:$tag", message)
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        Log.w("$GLOBAL_TAG:$tag", message, throwable)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e("$GLOBAL_TAG:$tag", message, throwable)
    }
}
