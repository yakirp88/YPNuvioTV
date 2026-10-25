package com.nuvio.tv.core.intro;

/** Episode-relative boundary editing without overflow or crossing the other boundary. */
public final class SegmentReviewRange {
    public final long startMs;
    public final long endMs;
    public final long durationMs;

    public SegmentReviewRange(long startMs, long endMs, long durationMs) {
        if (durationMs <= 0 || startMs < 0 || endMs <= startMs || endMs > durationMs) {
            throw new IllegalArgumentException("Invalid segment review range");
        }
        this.startMs = startMs;
        this.endMs = endMs;
        this.durationMs = durationMs;
    }

    public SegmentReviewRange adjusted(boolean start, long deltaMs) {
        final long shifted;
        try {
            shifted = Math.addExact(start ? startMs : endMs, deltaMs);
        } catch (ArithmeticException overflow) {
            return this;
        }
        return start
            ? new SegmentReviewRange(Math.max(0, Math.min(shifted, endMs - 1)), endMs, durationMs)
            : new SegmentReviewRange(startMs, Math.max(startMs + 1, Math.min(shifted, durationMs)), durationMs);
    }
}
