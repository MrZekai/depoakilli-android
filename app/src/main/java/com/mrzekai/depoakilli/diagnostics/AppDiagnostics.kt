package com.mrzekai.depoakilli.diagnostics

import android.content.Context
import android.util.Log

/**
 * Local-only diagnostic logging. No crash or usage data leaves the device.
 */
internal object AppDiagnostics {
    fun initialize(@Suppress("UNUSED_PARAMETER") context: Context) {
        Log.i(TAG, "Local-only diagnostics initialized.")
    }

    fun breadcrumb(
        name: String,
        data: Map<String, Any> = emptyMap(),
    ) {
        val details = data.entries
            .sortedBy { it.key }
            .joinToString(separator = ",") { (key, value) -> "$key=$value" }
        Log.d(TAG, if (details.isBlank()) name else "$name [$details]")
    }

    fun captureException(
        throwable: Throwable,
        stage: String,
        data: Map<String, Any> = emptyMap(),
    ) {
        val details = data.entries
            .sortedBy { it.key }
            .joinToString(separator = ",") { (key, value) -> "$key=$value" }
        Log.e(TAG, if (details.isBlank()) stage else "$stage [$details]", throwable)
    }

    private const val TAG = "AppDiagnostics"
}
