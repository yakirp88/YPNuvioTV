package com.nuvio.tv.core.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.AtomicFile
import com.nuvio.tv.BuildConfig
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Local, bounded and manually copied diagnostics. No network transmission or raw trace import. */
internal object YpCrashDiagnostics {
    private lateinit var app: Context
    private val installed = AtomicBoolean()
    private val writingFatal = AtomicBoolean()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var stage = "uninitialized"
    private val _pendingCrash = MutableStateFlow<Long?>(null)
    val pendingCrash = _pendingCrash.asStateFlow()
    private val preferences get() = app.getSharedPreferences("yp_diagnostics", Context.MODE_PRIVATE)
    private val directory get() = File(app.noBackupFilesDir, "yp_diagnostics").apply { mkdirs() }

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        app = context.applicationContext
        val previousStage = YpCrashReportText.safeStage(preferences.getString("stage", "app_start").orEmpty())
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                if (writingFatal.compareAndSet(false, true)) {
                    val now = System.currentTimeMillis()
                    write("fatal.txt", YpCrashReportText.failure("uncaught", stage, error, now))
                    preferences.edit().putLong("fatal_at", now).commit()
                }
            } catch (_: Throwable) {
                // A failed diagnostic write must never replace the original crash handling.
            } finally {
                if (previousHandler != null) previousHandler.uncaughtException(thread, error)
                else { Process.killProcess(Process.myPid()); exitProcess(10) }
            }
        }
        markStage("app_start")
        scope.launch {
            runCatching {
                importSystemExit(previousStage)
                val incident = maxOf(preferences.getLong("fatal_at", 0), preferences.getLong("exit_at", 0))
                if (incident > preferences.getLong("ack_at", 0)) _pendingCrash.value = incident
            }
        }
    }

    fun markStage(value: String) {
        val safe = YpCrashReportText.safeStage(value)
        if (stage == safe) return
        stage = safe
        if (::app.isInitialized) runCatching { preferences.edit().putString("stage", safe).apply() }
    }

    fun recordFailure(error: Throwable) {
        if (!::app.isInitialized) return
        val capturedStage = stage
        val now = System.currentTimeMillis()
        scope.launch {
            runCatching { write("caught.txt", YpCrashReportText.failure("intro_caught", capturedStage, error, now)) }
        }
    }

    private val detectionJournal = DetectionJournal()

    fun beginDetectionLog() {
        if (!::app.isInitialized) return
        detectionJournal.begin(System.currentTimeMillis())
        persistDetectionLog()
    }

    fun recordDetectionStatus(status: String) = logDetectionEvent("status", status)

    fun logDetectionEvent(event: String, status: String = "waiting", metrics: Map<String, Long> = emptyMap()) {
        if (!::app.isInitialized) return
        detectionJournal.add(event, status, metrics, System.currentTimeMillis())
        persistDetectionLog()
    }

    private fun persistDetectionLog() {
        scope.launch { runCatching { synchronized(this@YpCrashDiagnostics) { write("detection.txt", detectionJournal.text(), 24_000) } } }
    }

    suspend fun latestDetectionLog(): String = withContext(Dispatchers.IO) {
        check(::app.isInitialized)
        val text = synchronized(this@YpCrashDiagnostics) {
            AtomicFile(File(directory, "detection.txt")).readFully().toString(Charsets.UTF_8)
        }
        check(text.startsWith("NUVIO YP DETECTION LOG"))
        "version=${YpCrashReportText.safeToken(BuildConfig.VERSION_NAME)}\nandroid_sdk=${Build.VERSION.SDK_INT}\n" + text.take(24_000)
    }

    fun acknowledge() {
        if (!::app.isInitialized) return
        runCatching { preferences.edit().putLong("ack_at", System.currentTimeMillis()).apply() }
        _pendingCrash.value = null
    }

    suspend fun report(): String = withContext(Dispatchers.IO) {
        if (!::app.isInitialized) return@withContext "Diagnostics unavailable"
        buildString {
            appendLine("NUVIO YP LOCAL DIAGNOSTICS")
            appendLine("current_version=${YpCrashReportText.safeToken(BuildConfig.VERSION_NAME)} (${BuildConfig.VERSION_CODE})")
            appendLine("android_sdk=${Build.VERSION.SDK_INT}")
            // Device identifiers, account names, URLs, headers and exception messages are omitted.
            appendLine("current_stage=${YpCrashReportText.safeStage(stage)}")
            appendLine("No API keys, stream URLs or subtitle text included.")
            var found = false
            for (name in listOf("fatal.txt", "exit.txt", "caught.txt", "detection.txt")) {
                val text = runCatching { AtomicFile(File(directory, name)).readFully().toString(Charsets.UTF_8) }.getOrNull()
                if (text != null) { found = true; appendLine(); appendLine(text.take(if (name == "detection.txt") 24_000 else 7200)) }
            }
            if (!found) appendLine("No incident recorded yet. Reproduce once, then reopen this screen.")
        }
    }

    @Synchronized private fun write(name: String, text: String, limit: Int = 7200) {
        val target = AtomicFile(File(directory, name))
        val stream = target.startWrite()
        try { stream.write(text.take(limit).toByteArray()); target.finishWrite(stream) }
        catch (failure: Throwable) { target.failWrite(stream); throw failure }
    }

    private fun importSystemExit(previousStage: String) {
        if (Build.VERSION.SDK_INT < 30) return
        val manager = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val exit = manager.getHistoricalProcessExitReasons(app.packageName, 0, 5).firstOrNull {
            it.reason in setOf(ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE,
                ApplicationExitInfo.REASON_ANR, ApplicationExitInfo.REASON_LOW_MEMORY)
        } ?: return
        if (exit.timestamp <= preferences.getLong("exit_at", 0)) return
        val reason = when (exit.reason) {
            ApplicationExitInfo.REASON_CRASH -> "java_crash"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "native_crash"
            ApplicationExitInfo.REASON_ANR -> "not_responding"
            else -> "low_memory"
        }
        // Never read description or traceInputStream: OS traces may include authenticated URLs.
        write("exit.txt", "ANDROID PREVIOUS PROCESS\ntime_ms=${exit.timestamp}\nreason=$reason\nstatus=${exit.status}\npss_kb=${exit.pss}\nrss_kb=${exit.rss}\nlast_stage=$previousStage\nStack trace unavailable for native/previous-version incidents.")
        preferences.edit().putLong("exit_at", exit.timestamp).apply()
    }
}
