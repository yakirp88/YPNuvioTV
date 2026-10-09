package com.nuvio.tv.ui.screens.player
import com.nuvio.tv.data.introdb.*
import com.nuvio.tv.domain.repository.MetaRepository
import kotlinx.coroutines.*

fun main() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val player = PlayerRuntimeController()
    val repo = IntroDbReportRepository()
    val report = IntroDbReportCoordinator(player, repo, MetaRepository(), scope)
    repo.existingTypes = setOf(ReportSegment.INTRO)
    player._uiState.value = FakeUiState(postPlayMode = "existing-card")
    report.load()
    check(player._uiState.value.postPlayMode == "existing-card")
    check(report.state.value.available == setOf(ReportSegment.RECAP, ReportSegment.OUTRO))
    report.begin()
    check(report.state.value.startMs == 72123L) // Exact click time, not the time of choosing.
    check(player._uiState.value.introDbReportingActive)
    player.position = 75123L
    report.choose(ReportSegment.INTRO)
    check(report.state.value.stage == ReportStage.CHOOSE) // Existing kinds cannot be chosen.
    report.choose(ReportSegment.RECAP)
    check(report.state.value.allowsPlayerControls && player.playing)
    report.showPlayerControls()
    check(report.state.value.allowsPlayerControls && player._uiState.value.showControls)
    player.seekPlaybackTo(120000L)
    report.focusReportControls()
    check(report.state.value.stage == ReportStage.RECORDING && !report.state.value.allowsPlayerControls)
    check(report.state.value.startMs == 72123L && player.position == 120000L)
    report.showPlayerControls() // Dismissing Finish/Cancel resumes normal controls without losing the report.
    check(report.state.value.stage == ReportStage.RECORDING && report.state.value.allowsPlayerControls)
    player.position = 70000L
    report.finish()
    check(report.state.value.stage == ReportStage.RECORDING && player.playing)
    player.position = 150000L
    report.finish()
    check(!player.playing)
    check(report.state.value.endMs == 150000L)
    check(!report.state.value.allowsPlayerControls)
    report.showPlayerControls()
    check(!report.state.value.allowsPlayerControls) // Review cannot accidentally hand control back.
    check(report.state.value.startImage?.atMs == 72123L)
    check(report.state.value.endImage?.atMs == 150000L)
    report.adjust(true, -500)
    report.adjust(false, 1000)
    check(report.state.value.startMs == 71623L && report.state.value.startImage?.atMs == 71623L)
    check(report.state.value.endMs == 151000L && report.state.value.endImage?.atMs == 151000L)
    report.send()
    check(repo.sends == 1 && repo.sentStart == 71623L && repo.sentEnd == 151000L)
    check(report.state.value.available == setOf(ReportSegment.OUTRO))
    check(player.position == 151000L && player.playing) // Return to Finish position, not preview position.
    report.begin(); report.choose(ReportSegment.OUTRO); player.position = 170000L; report.finish()
    repo.existingTypes += ReportSegment.OUTRO // Another contributor publishes during review.
    report.send()
    check(repo.sends == 1 && report.state.value.available.isEmpty())
    check(report.state.value.message == "exists")
    report.begin()
    check(report.state.value.stage == ReportStage.IDLE)

    val offlineRepo = IntroDbReportRepository().apply { failRead = true }
    val offline = IntroDbReportCoordinator(PlayerRuntimeController(), offlineRepo, MetaRepository(), scope)
    offline.load()
    check(!offline.state.value.canStart) // Failed lookup does not imply missing data.
    check(offline.state.value.message == "check_failed")

    val draftPlayer = PlayerRuntimeController()
    val draftRepo = IntroDbReportRepository().apply { failSend = true }
    val draft = IntroDbReportCoordinator(draftPlayer, draftRepo, MetaRepository(), scope)
    draft.load(); draft.begin(); draft.choose(ReportSegment.INTRO); draftPlayer.position = 155000; draft.finish(); draft.send()
    check(draft.state.value.stage == ReportStage.REVIEW && !draft.state.value.sending)
    check(draftRepo.draft(ReportMedia("tt0903747", 1, 1)) != null)
    draft.dispose() // Leaving preserves a failed-send draft.
    val reopened = IntroDbReportCoordinator(draftPlayer, draftRepo, MetaRepository(), scope)
    reopened.load(); draftPlayer.position = 200000; reopened.begin()
    check(reopened.state.value.stage == ReportStage.CHOOSE && reopened.state.value.startMs == 200000L)
    reopened.resumeDraft()
    check(reopened.state.value.stage == ReportStage.REVIEW && reopened.state.value.startMs == 72123L)
    reopened.cancel()
    check(draftRepo.draft(ReportMedia("tt0903747", 1, 1)) == null)
    check(draftPlayer.position == 155000L && draftPlayer.playing)
    val disabledRepo = IntroDbReportRepository().apply { enabled = false }
    val disabled = IntroDbReportCoordinator(PlayerRuntimeController(), disabledRepo, MetaRepository(), scope)
    disabled.load()
    check(!disabled.state.value.canStart && disabledRepo.reads == 0)
    val calibratePlayer = PlayerRuntimeController()
    val calibrateRepo = IntroDbReportRepository()
    val calibrate = IntroDbReportCoordinator(calibratePlayer, calibrateRepo, MetaRepository(), scope)
    calibrate.load(); calibrate.begin(); calibrate.choose(ReportSegment.INTRO)
    calibratePlayer.position = 200000; calibrate.finish()
    calibrate.selectBoundary(true)
    check(calibrate.state.value.calibratingStart == true && calibratePlayer.position == 72123L && !calibratePlayer.playing)
    calibratePlayer.captureDelayMs = 80
    calibrate.adjust(true, 500)
    calibrate.adjust(true, 2000)
    calibrate.adjust(true, 10000)
    calibrate.send()
    check(calibrateRepo.sends == 0) // Pending frame calibration cannot be submitted.
    delay(250)
    check(calibrate.state.value.startMs == 84623L && calibrate.state.value.startImage?.atMs == 84623L)
    check(calibratePlayer.position == 84623L && !calibratePlayer.playing)
    calibrate.endCalibration()
    check(calibrate.state.value.calibratingStart == null && !calibratePlayer.playing)
    calibrate.selectBoundary(false); delay(100)
    calibrate.adjust(false, 500); delay(250)
    check(calibrate.state.value.endMs == 200500L && calibratePlayer.position == 200500L)
    calibrate.selectBoundary(true); delay(100)
    calibrate.adjust(true, -500)
    calibrate.cancel() // Cancellation during frame work restores the corrected end, never the preview.
    delay(250)
    check(calibratePlayer.position == 200500L && calibratePlayer.playing)
    check(calibrate.state.value.stage == ReportStage.IDLE && calibrateRepo.sends == 0)
    scope.cancel()
    println("Reporting lifecycle checks passed: capture, preview correction, send, race blocking, offline lockout, draft restore, cancel")
}
