package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.core.intro.GeminiIntroAnalyzer

/** Preserve the numeric HTTP outcome, never a response body, URL or exception message. */
internal fun cloudFailureStatus(error: Exception): String =
    if (error is GeminiIntroAnalyzer.HttpFailure && error.status in 100..599) "http_${error.status}"
    else "cloud_failed"
