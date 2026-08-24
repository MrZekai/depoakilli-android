package com.mrzekai.depoakilli.diagnostics

import android.content.Context
import android.util.Log
import com.mrzekai.depoakilli.BuildConfig
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid

/**
 * Privacy-minimal crash diagnostics.
 *
 * Normal operations are breadcrumbs only; they are sent with an error/crash,
 * not as standalone behavioral analytics. Never pass file names, paths,
 * content, fingerprints, package lists, or user-entered text here.
 */
internal object AppDiagnostics {
    @Volatile
    private var enabled = false

    fun initialize(context: Context) {
        val dsn = BuildConfig.SENTRY_DSN.trim()
        if (dsn.isBlank()) {
            Log.i(TAG, "Sentry disabled: SENTRY_DSN is empty for this build.")
            return
        }

        SentryAndroid.init(context) { options ->
            options.dsn = dsn
            options.environment =
                if (BuildConfig.APPLICATION_ID.endsWith(".qa")) "qa" else "production"
            options.release =
                "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.isSendDefaultPii = false
        }
        enabled = true
        breadcrumb(
            "app_diagnostics_initialized",
            mapOf(
                "build" to
                    if (BuildConfig.APPLICATION_ID.endsWith(".qa")) "qa" else "production",
            ),
        )
    }

    fun breadcrumb(
        name: String,
        data: Map<String, Any> = emptyMap(),
    ) {
        if (!enabled) return
        val details = data.entries
            .sortedBy { it.key }
            .joinToString(separator = ",") { (key, value) -> "$key=$value" }
        Sentry.addBreadcrumb(if (details.isBlank()) name else "$name [$details]")
    }

    fun captureException(
        throwable: Throwable,
        stage: String,
        data: Map<String, Any> = emptyMap(),
    ) {
        breadcrumb(stage, data)
        if (enabled) {
            Sentry.captureException(throwable)
        } else {
            Log.e(TAG, stage, throwable)
        }
    }

    private const val TAG = "AppDiagnostics"
}
