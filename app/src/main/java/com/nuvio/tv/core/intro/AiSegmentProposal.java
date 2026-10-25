package com.nuvio.tv.core.intro;

/** A model suggestion, never proof of a boundary and never eligible for auto-publication. */
public final class AiSegmentProposal {
    public final String type;
    public final long startMs;
    public final long endMs;

    public AiSegmentProposal(String type, long startMs, long endMs, long durationMs) {
        long maxLength;
        if ("intro".equals(type)) maxLength = 300000;
        else if ("recap".equals(type)) maxLength = 480000;
        else if ("outro".equals(type)) maxLength = 900000;
        else if ("post-credits".equals(type)) maxLength = 600000;
        else throw new IllegalArgumentException("Unsupported segment type");
        if (durationMs <= 0 || startMs < 0 || endMs <= startMs || endMs > durationMs
            || endMs - startMs < 5000 || endMs - startMs > maxLength) {
            throw new IllegalArgumentException("Invalid suggested segment bounds");
        }
        this.type = type;
        this.startMs = startMs;
        this.endMs = endMs;
    }
}
