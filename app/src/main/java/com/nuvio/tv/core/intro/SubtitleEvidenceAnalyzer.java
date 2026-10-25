package com.nuvio.tv.core.intro;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Finds observations, NOT skip boundaries. No network calls and no publication side effects. */
public final class SubtitleEvidenceAnalyzer {
    private SubtitleEvidenceAnalyzer() {}

    public enum Kind { INTRO, RECAP, OUTRO, PREVIEW, MUSIC }

    public static final class Cue {
        public final long startMs;
        public final long endMs;
        public final String text;

        public Cue(long startMs, long endMs, String text) {
            this.startMs = startMs;
            this.endMs = endMs;
            this.text = text;
        }
    }

    public static final class Hint {
        public final Kind kind;
        public final long observedStartMs;
        public final long observedEndMs;
        public final int supportingCueCount;
        public final String reason;

        private Hint(Kind kind, long startMs, long endMs, int count, String reason) {
            this.kind = kind;
            this.observedStartMs = startMs;
            this.observedEndMs = endMs;
            this.supportingCueCount = count;
            this.reason = reason;
        }
    }

    private static final Pattern TAGS = Pattern.compile("<[^>]*>|\\{[^}]*}");
    private static final Pattern RECAP = Pattern.compile(
        "^(?:[\\[({]\\s*)?(?:previously on\\b|previously\\s*[:…]|last time on\\b|"
        + "בפרקים הקודמים|בפרק הקודם|עד כה ב|תקציר הפרקים הקודמים)");
    private static final Pattern THEME = Pattern.compile(
        "^(?:[\\[({]\\s*)?(?:opening (?:theme|titles|credits)|intro (?:theme|music)|"
        + "theme (?:song|music)|מנגינת הפתיחה|שיר הפתיחה|מוזיקת פתיחה)");
    private static final Pattern CREDITS = Pattern.compile(
        "^(?:[\\[({]\\s*)?(?:end credits|closing credits|closing theme|ending theme|"
        + "כתוביות סיום|קרדיטים לסיום|שיר הסיום|מוזיקת סיום)");
    private static final Pattern PREVIEW = Pattern.compile(
        "^(?:[\\[({]\\s*)?(?:next (?:time|week) on\\b|on the next episode\\b|"
        + "בפרק הבא|בפרקים הבאים)");
    private static final Pattern MUSIC = Pattern.compile(
        "^[\\[({].*(?:music|instrumental|מוזיקה|מוסיקה|מנגינה).*[\\])}]$");
    private static final Pattern TRANSLATOR = Pattern.compile(
        "subtitles? (?:by|provided)|translated by|תורגם|תרגום|סנכרון");

    public static List<Hint> analyze(List<Cue> input, long durationMs, long subtitleOffsetMs) {
        if (durationMs <= 0 || input == null) return Collections.emptyList();
        List<Cue> cues = new ArrayList<>();
        for (Cue cue : input) {
            if (cue == null || cue.text == null || cue.text.length() > 4000
                    || cue.startMs < 0 || cue.endMs <= cue.startMs) continue;
            long start;
            long end;
            try {
                start = Math.addExact(cue.startMs, subtitleOffsetMs);
                end = Math.addExact(cue.endMs, subtitleOffsetMs);
            } catch (ArithmeticException overflow) { continue; }
            if (start < 0 || end > durationMs) continue;
            String text = TAGS.matcher(cue.text).replaceAll("").toLowerCase(Locale.ROOT)
                .replaceAll("[\\u200e\\u200f\\u202a-\\u202e\\u2066-\\u2069]", "")
                .replaceAll("\\s+", " ").trim();
            if (!text.isEmpty()) cues.add(new Cue(start, end, text));
        }
        cues.sort(Comparator.comparingLong(cue -> cue.startMs));
        long openingWindow = Math.min(600_000L, durationMs / 4);
        long closingWindow = Math.max(durationMs * 3 / 4, durationMs - 900_000L);
        List<Hint> result = new ArrayList<>();
        Hint pendingMusic = null;
        for (Cue cue : cues) {
            Kind kind = null;
            String reason = "";
            if (!TRANSLATOR.matcher(cue.text).find()) {
                if (cue.startMs < openingWindow && RECAP.matcher(cue.text).find()) {
                    kind = Kind.RECAP; reason = "explicit_recap_caption";
                } else if (cue.startMs < openingWindow && THEME.matcher(cue.text).find()) {
                    kind = Kind.INTRO; reason = "explicit_opening_caption";
                } else if (cue.startMs >= closingWindow && CREDITS.matcher(cue.text).find()) {
                    kind = Kind.OUTRO; reason = "explicit_closing_caption";
                } else if (cue.startMs >= closingWindow && PREVIEW.matcher(cue.text).find()) {
                    kind = Kind.PREVIEW; reason = "explicit_preview_caption";
                } else if ((cue.startMs < openingWindow || cue.startMs >= closingWindow)
                        && (cue.text.indexOf('♪') >= 0 || cue.text.indexOf('♫') >= 0
                        || MUSIC.matcher(cue.text).find())) {
                    kind = Kind.MUSIC; reason = "music_caption_only";
                }
            }
            if (kind == Kind.MUSIC) {
                if (pendingMusic != null && cue.startMs <= pendingMusic.observedEndMs + 8000L) {
                    pendingMusic = new Hint(Kind.MUSIC, pendingMusic.observedStartMs,
                        Math.max(pendingMusic.observedEndMs, cue.endMs),
                        pendingMusic.supportingCueCount + 1, reason);
                } else {
                    if (pendingMusic != null) result.add(pendingMusic);
                    pendingMusic = new Hint(kind, cue.startMs, cue.endMs, 1, reason);
                }
            } else {
                // A dialog cue between music captions prevents accidental merging across plot.
                if (pendingMusic != null) result.add(pendingMusic);
                pendingMusic = null;
                if (kind != null) result.add(new Hint(kind, cue.startMs, cue.endMs, 1, reason));
            }
        }
        if (pendingMusic != null) result.add(pendingMusic);
        return Collections.unmodifiableList(result);
    }
}
