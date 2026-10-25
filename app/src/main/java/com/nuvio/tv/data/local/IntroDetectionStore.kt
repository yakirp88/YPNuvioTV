package com.nuvio.tv.data.local

import android.content.Context
import android.util.AtomicFile
import com.nuvio.tv.core.intro.SubtitleEvidenceAnalyzer
import com.nuvio.tv.core.intro.IntroSubmissionMode
import com.nuvio.tv.core.profile.ProfileScopedCredentialStore
import com.nuvio.tv.core.profile.ProtectedProfilePreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Device-local: deliberately excluded from cloud profile synchronization. */
@Singleton
class IntroDetectionStore @Inject constructor(@ApplicationContext context: Context) : ProfileScopedCredentialStore {
    private val credentials = ProtectedProfilePreferences(context, "yp_intro_detection", "yp_intro_detection_v1")
    private val directory = File(context.noBackupFilesDir, "yp_intro_evidence")
    private val lock = Any()
    private val _revision = MutableStateFlow(0L)
    val revision = _revision.asStateFlow()

    data class Snapshot(
        val enabled: Boolean,
        val cloudEnabled: Boolean,
        val submissionMode: IntroSubmissionMode,
        val hasAiKey: Boolean,
        val hasIntroDbKey: Boolean,
        val latestHintCount: Int?,
        val latestEvidence: String
    )

    private fun record(profileId: Int): JSONObject =
        credentials.read(profileId)?.let(::JSONObject) ?: JSONObject()

    suspend fun snapshot(profileId: Int): Snapshot = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val record = record(profileId)
            val latest = File(directory, "p$profileId").listFiles()
                ?.filter { it.extension == "json" }?.maxByOrNull { it.lastModified() }
                ?.let { runCatching { JSONObject(AtomicFile(it).readFully().toString(Charsets.UTF_8)) }.getOrNull() }
            val hints = latest?.optJSONArray("hints")
            val proposalCount = latest?.optJSONArray("proposals")?.length() ?: 0
            val lines = if (hints == null) emptyList() else (0 until hints.length()).map { index ->
                val hint = hints.getJSONObject(index)
                "${hint.getString("kind")}: ${hint.getLong("observed_start_ms") / 1000}s–${hint.getLong("observed_end_ms") / 1000}s"
            }
            Snapshot(
                record.optBoolean("enabled", false),
                record.optBoolean("cloud_enabled", false),
                IntroSubmissionMode.fromStoredValue(record.optString("submission_mode")),
                record.optString("ai_key").isNotBlank(),
                record.optString("introdb_key").isNotBlank(),
                hints?.length(),
                lines.take(8).joinToString("\n") + if (proposalCount > 0) "\nAI drafts: $proposalCount (unverified)" else if (latest?.optString("cloud_status") == "failed") "\nGemini: request failed" else ""
            )
        }
    }

    suspend fun enabled(profileId: Int): Boolean = withContext(Dispatchers.IO) {
        synchronized(lock) { record(profileId).optBoolean("enabled", false) }
    }

    suspend fun cloudKey(profileId: Int): String? = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val data = record(profileId)
            if (data.optBoolean("enabled", false) && data.optBoolean("cloud_enabled", false))
                data.optString("ai_key").takeIf { it.isNotBlank() } else null
        }
    }

    suspend fun introDbKey(profileId: Int): String? = withContext(Dispatchers.IO) {
        synchronized(lock) { record(profileId).optString("introdb_key").takeIf { it.isNotBlank() } }
    }

    suspend fun setCloudEnabled(profileId: Int, value: Boolean) = edit(profileId) { put("cloud_enabled", value) }

    suspend fun setEnabled(profileId: Int, enabled: Boolean) = edit(profileId) { put("enabled", enabled) }

    suspend fun setSubmissionMode(profileId: Int, mode: IntroSubmissionMode) = edit(profileId) {
        put("submission_mode", mode.name)
    }

    suspend fun setKey(profileId: Int, ai: Boolean, value: String) {
        val trimmed = value.trim()
        require(trimmed.length <= 1024 && trimmed.none { it.isWhitespace() || it.isISOControl() })
        edit(profileId) {
            val name = if (ai) "ai_key" else "introdb_key"
            if (trimmed.isEmpty()) remove(name) else put(name, trimmed)
        }
    }

    private suspend fun edit(profileId: Int, change: JSONObject.() -> Unit) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val updated = record(profileId).apply(change)
            credentials.write(profileId, updated.toString())
        }
        _revision.update { it + 1 }
    }

    /** Identity contains a media fingerprint, never a stream URL or subtitle text. */
    suspend fun saveEvidence(
        profileId: Int,
        identity: String,
        durationMs: Long,
        offsetMs: Long,
        hints: List<SubtitleEvidenceAnalyzer.Hint>,
        proposals: List<com.nuvio.tv.core.intro.AiSegmentProposal> = emptyList(),
        cloudStatus: String = "not_requested"
    ) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            // Recheck after IO: disabling collection must prevent a late result from being saved.
            if (!record(profileId).optBoolean("enabled", false)) return@synchronized
            val profileDir = File(directory, "p$profileId").apply { mkdirs() }
            val array = JSONArray()
            hints.forEach { hint ->
                array.put(JSONObject().apply {
                    put("kind", hint.kind.name.lowercase())
                    put("observed_start_ms", hint.observedStartMs)
                    put("observed_end_ms", hint.observedEndMs)
                    put("supporting_cue_count", hint.supportingCueCount)
                    put("reason", hint.reason)
                })
            }
            val document = JSONObject().apply {
                put("schema_version", 1)
                put("engine", "subtitle-evidence-v1")
                put("identity_hash", fingerprint(identity))
                put("duration_ms", durationMs)
                put("subtitle_offset_ms", offsetMs)
                put("status", "evidence_only")
                put("can_auto_submit", false)
                put("hints", array)
                put("proposals", JSONArray().apply {
                    proposals.forEach { proposal -> put(JSONObject().put("type", proposal.type)
                        .put("start_ms", proposal.startMs).put("end_ms", proposal.endMs)) }
                })
                put("proposal_status", if (cloudStatus == "visual_completed") "unverified_visual" else "unverified_subtitle_only")
                put("cloud_status", cloudStatus)
            }
            val target = AtomicFile(File(profileDir, "${fingerprint(identity)}.json"))
            val output = target.startWrite()
            try {
                output.write(document.toString().toByteArray(Charsets.UTF_8))
                target.finishWrite(output)
            } catch (error: Exception) {
                target.failWrite(output)
                throw error
            }
            profileDir.listFiles()?.filter { it.extension == "json" }
                ?.sortedByDescending { it.lastModified() }?.drop(20)?.forEach { AtomicFile(it).delete() }
        }
        // UI refresh only; playback observes enabled.distinctUntilChanged(), avoiding feedback loops.
        _revision.update { it + 1 }
    }

    override fun removeProfile(profileId: Int) {
        synchronized(lock) {
            credentials.write(profileId, null)
            File(directory, "p$profileId").deleteRecursively()
        }
        _revision.update { it + 1 }
    }

    override fun clearAllProfiles() {
        synchronized(lock) {
            credentials.clear()
            directory.deleteRecursively()
        }
        _revision.update { it + 1 }
    }

    companion object {
        fun fingerprint(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
