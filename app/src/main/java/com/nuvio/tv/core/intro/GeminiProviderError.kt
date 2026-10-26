package com.nuvio.tv.core.intro

import com.google.gson.JsonParser

/** Never return a provider message, metadata, identifier or unknown reason. */
internal object GeminiProviderError {
    private val reasons = mapOf(
        "API_KEY_INVALID" to "provider_key_invalid", "API_KEY_EXPIRED" to "provider_key_invalid",
        "API_KEY_SERVICE_BLOCKED" to "provider_key_restricted", "API_KEY_HTTP_REFERRER_BLOCKED" to "provider_key_restricted",
        "API_KEY_IP_ADDRESS_BLOCKED" to "provider_key_restricted", "API_KEY_ANDROID_APP_BLOCKED" to "provider_key_restricted",
        "SERVICE_DISABLED" to "provider_service_disabled", "BILLING_DISABLED" to "provider_billing",
        "UNAVAILABLE" to "provider_unavailable", "PERMISSION_DENIED" to "provider_permission",
        "RESOURCE_EXHAUSTED" to "provider_quota", "INVALID_ARGUMENT" to "provider_request_invalid",
        "NOT_FOUND" to "provider_not_found", "INTERNAL" to "provider_internal")
    fun reason(body: String): String = try {
        if (body.length > 16_384) "provider_unknown" else {
            val error = JsonParser.parseString(body).asJsonObject.getAsJsonObject("error")
            val detail = error?.getAsJsonArray("details")?.firstNotNullOfOrNull { item ->
                val obj = item.takeIf { it.isJsonObject }?.asJsonObject
                obj?.get("reason")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.let(reasons::get)
            }
            detail ?: error?.get("status")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                ?.asString?.let(reasons::get) ?: "provider_unknown"
        }
    } catch (_: Exception) { "provider_unknown" }
}
