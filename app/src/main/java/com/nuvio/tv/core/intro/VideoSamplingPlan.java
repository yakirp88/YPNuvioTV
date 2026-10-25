package com.nuvio.tv.core.intro;

import java.util.TreeSet;

public final class VideoSamplingPlan {
    private VideoSamplingPlan() {}
    public static long[] times(long durationMs) {
        return times(durationMs, true, true);
    }
    public static long[] times(long durationMs, boolean openingNeeded, boolean endingNeeded) {
        if (durationMs <= 0 || durationMs > Long.MAX_VALUE / 1000) {
            throw new IllegalArgumentException("Invalid video duration");
        }
        TreeSet<Long> times = new TreeSet<>();
        long opening = Math.min(600000, durationMs / 4);
        long tail = Math.min(900000, durationMs / 4);
        for (int index = 0; index < 12; index++) {
            if (openingNeeded) times.add(Math.min(durationMs - 1, opening * index / 11));
            if (endingNeeded) times.add(Math.min(durationMs - 1, durationMs - tail + tail * index / 11));
        }
        return times.stream().mapToLong(Long::longValue).toArray();
    }
}
