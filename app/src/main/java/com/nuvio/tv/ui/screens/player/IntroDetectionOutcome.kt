package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.intro.GeminiIntroAnalyzer

/** Fixed failure labels only: never exception messages, bodies or authenticated URLs. */
internal fun cloudFailureStatus(error: Exception): String = when (error) {
    is GeminiIntroAnalyzer.HttpFailure -> if (error.status in 100..599) "http_${error.status}" else "cloud_failed"
    is GeminiIntroAnalyzer.Failure -> when (error.kind) {
        GeminiIntroAnalyzer.FailureKind.NETWORK -> "network_failed"
        GeminiIntroAnalyzer.FailureKind.TIMEOUT -> "network_timeout"
        GeminiIntroAnalyzer.FailureKind.DNS -> "dns_failed"
        GeminiIntroAnalyzer.FailureKind.TLS -> "tls_failed"
        GeminiIntroAnalyzer.FailureKind.RESPONSE -> "response_invalid"
        GeminiIntroAnalyzer.FailureKind.OUTPUT -> "output_invalid"
        GeminiIntroAnalyzer.FailureKind.OUTPUT_LIMIT -> "output_token_limit"
        GeminiIntroAnalyzer.FailureKind.BLOCKED -> "output_blocked"
    }
    is org.json.JSONException -> "response_invalid"
    is IllegalArgumentException -> "output_invalid"
    else -> "cloud_failed"
}

internal fun isCloudFailureStatus(status: String): Boolean = status.startsWith("http_") || status in setOf(
    "cloud_failed", "network_failed", "network_timeout", "dns_failed", "tls_failed", "response_invalid",
    "output_invalid", "output_token_limit", "output_blocked")
