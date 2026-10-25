import com.nuvio.tv.core.intro.SubtitleEvidenceAnalyzer;
import com.nuvio.tv.core.intro.SubtitleEvidenceAnalyzer.Cue;
import com.nuvio.tv.core.intro.SubtitleEvidenceAnalyzer.Kind;
import com.nuvio.tv.core.intro.IntroSubmissionMode;
import com.nuvio.tv.core.intro.AiSegmentProposal;
import com.nuvio.tv.core.intro.IntroDetectionPhase;
import com.nuvio.tv.core.intro.VideoSamplingPlan;
import com.nuvio.tv.core.intro.SegmentReviewRange;
import java.util.Arrays;
import java.util.Collections;

/** Runs with a stock JDK: checks the production analyzer without Android/Gradle dependencies. */
public class SubtitleEvidenceCheck {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(SubtitleEvidenceAnalyzer.analyze(Collections.emptyList(), 0, 0).isEmpty(), "unknown duration");
        check(SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(1000, 3000, "Hello")), 2400000, 0).isEmpty(), "dialog is not an intro");
        check(SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(1000, 3000, "[music]")), 2400000, 0).get(0).kind == Kind.MUSIC, "music must not imply intro");
        check(SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(1000, 3000, "<i>[Theme song]</i>")), 2400000, 0).get(0).kind == Kind.INTRO, "opening caption");
        check(SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(1000, 3000, "Previously on Example")), 2400000, 0).get(0).kind == Kind.RECAP, "English recap");
        check(SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(1000, 3000, "\u200fבפרקים הקודמים")), 2400000, 0).get(0).kind == Kind.RECAP, "Hebrew recap + RTL");
        check(SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(2200000, 2203000, "[End credits]")), 2400000, 0).get(0).kind == Kind.OUTRO, "credits caption");
        check(SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(2300000, 2303000, "בפרק הבא")), 2400000, 0).get(0).kind == Kind.PREVIEW, "preview is distinct from outro");
        check(SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(50000, 53000, "[End credits]")), 2400000, 0).isEmpty(), "wrong episode region");
        check(SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(1100000, 1103000, "Previously on Example")), 2400000, 0).isEmpty(), "recap phrase in mid-plot");
        check(SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(2200000, 2203000, "End credits: subtitles by Someone")), 2400000, 0).isEmpty(), "translator credits");
        check(SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(-1, 3000, "[music]"), new Cue(5000, 4000, "[music]")), 2400000, 0).isEmpty(), "invalid cue ranges");
        check(SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(1000, 3000, "[music]")), 2400000, -2000).isEmpty(), "negative shifted time");
        check(SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(1000, 3000, "[music]")), 2400000, 500).get(0).observedStartMs == 1500, "subtitle offset");
        check(SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(1000, 3000, "[music]")), 2400000, Long.MAX_VALUE).isEmpty(), "offset overflow");
        var merged = SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(8000, 10000, "♪ second ♪"), new Cue(1000, 3000, "♪ first ♪")), 2400000, 0);
        check(merged.size() == 1 && merged.get(0).supportingCueCount == 2 && merged.get(0).observedEndMs == 10000, "sort and merge music");
        var split = SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(1000, 3000, "♪ one ♪"), new Cue(4000, 5000, "Plot dialog"), new Cue(6000, 8000, "♪ two ♪")), 2400000, 0);
        check(split.size() == 2, "dialog splits music evidence");
        check(SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(1000, 3000, "We were previously on the train")), 2400000, 0).isEmpty(), "phrase must be anchored");
        check(SubtitleEvidenceAnalyzer.analyze(Arrays.asList(new Cue(2399000, 2401000, "[End credits]")), 2400000, 0).isEmpty(), "cue exceeds video");
        check(IntroSubmissionMode.fromStoredValue(null) == IntroSubmissionMode.REVIEW_AT_EPISODE_END, "new profile defaults to review");
        check(IntroSubmissionMode.fromStoredValue("UNKNOWN") == IntroSubmissionMode.REVIEW_AT_EPISODE_END, "unknown mode defaults to review");
        check(IntroSubmissionMode.fromStoredValue("AUTOMATIC") == IntroSubmissionMode.AUTOMATIC, "automatic mode round trip");
        check(!IntroSubmissionMode.REVIEW_AT_EPISODE_END.shouldShowReview(false, 2), "no popup during playback");
        check(!IntroSubmissionMode.REVIEW_AT_EPISODE_END.shouldShowReview(true, 0), "no empty popup");
        check(IntroSubmissionMode.REVIEW_AT_EPISODE_END.shouldShowReview(true, 2), "review verified segments at natural end");
        check(!IntroSubmissionMode.AUTOMATIC.shouldShowReview(true, 2), "auto mode has no review popup");
        check(!IntroSubmissionMode.REVIEW_AT_EPISODE_END.mayAutoSubmit(true, true, false), "review mode cannot auto-submit");
        check(!IntroSubmissionMode.AUTOMATIC.mayAutoSubmit(false, true, false), "uncalibrated detection cannot auto-submit");
        check(!IntroSubmissionMode.AUTOMATIC.mayAutoSubmit(true, false, false), "unknown boundaries cannot auto-submit");
        check(!IntroSubmissionMode.AUTOMATIC.mayAutoSubmit(true, true, true), "conflicting timing cannot auto-submit");
        check(IntroSubmissionMode.AUTOMATIC.mayAutoSubmit(true, true, false), "verified calibrated results are eligible");
        var range = new SegmentReviewRange(10000, 70000, 2400000);
        check(range.adjusted(true, -1000).startMs == 9000, "adjust start by one second");
        check(range.adjusted(false, 1000).endMs == 71000, "adjust end by one second");
        check(range.adjusted(true, -20000).startMs == 0, "start does not precede video");
        check(range.adjusted(true, 100000).startMs == 69999, "start cannot cross end");
        check(range.adjusted(false, -100000).endMs == 10001, "end cannot cross start");
        check(range.adjusted(false, 3000000).endMs == 2400000, "end cannot exceed video");
        check(range.adjusted(true, Long.MAX_VALUE) == range, "overflow is rejected");
        boolean invalidRangeRejected = false;
        try { new SegmentReviewRange(10000, 10000, 2400000); }
        catch (IllegalArgumentException expected) { invalidRangeRejected = true; }
        check(invalidRangeRejected, "empty ranges rejected");
        check(new AiSegmentProposal("intro", 10000, 70000, 2400000).endMs == 70000, "valid model suggestion");
        String[] kinds = {"unknown", "intro", "intro", "outro", "recap", "post-credits", "intro"};
        long[] starts = {0, -1, 10000, 10000, 0, 0, 0};
        long[] ends = {10000, 10000, 9000, 2500000, 481000, 601000, 4999};
        for (int i = 0; i < kinds.length; i++) {
            boolean rejected = false;
            try { new AiSegmentProposal(kinds[i], starts[i], ends[i], 2400000); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid model suggestion " + i);
        }
        for (IntroDetectionPhase phase : IntroDetectionPhase.values()) {
            check(phase.showsIndicator() == (phase == IntroDetectionPhase.SEARCHING_SUBTITLES), "indicator visibility " + phase);
        }
        long[] plan = VideoSamplingPlan.times(2400000);
        check(plan.length == 24, "bounded coarse video samples");
        check(plan[0] == 0 && plan[plan.length - 1] == 2399999, "video sampling edges");
        boolean sorted = true;
        for (int i = 1; i < plan.length; i++) sorted &= plan[i] > plan[i - 1];
        check(sorted, "sampling strictly ordered");
        check(VideoSamplingPlan.times(1).length == 1, "tiny duration samples deduplicated");
        check(VideoSamplingPlan.times(2400000, false, true)[0] >= 1800000, "outro-only sampling");
        check(VideoSamplingPlan.times(2400000, true, false).length == 12, "opening-only sampling");
        check(VideoSamplingPlan.times(2400000, false, false).length == 0, "no missing types means no samples");
        System.out.println("Passed " + checks + " production analyzer/review checks.");
    }
}
