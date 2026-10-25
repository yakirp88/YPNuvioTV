package com.nuvio.tv.core.intro;

/** Stable preference values; unknown/old values default to reviewing at episode end. */
public enum IntroSubmissionMode {
    REVIEW_AT_EPISODE_END,
    AUTOMATIC;

    public static IntroSubmissionMode fromStoredValue(String value) {
        return "AUTOMATIC".equals(value) ? AUTOMATIC : REVIEW_AT_EPISODE_END;
    }

    public boolean shouldShowReview(boolean naturalEpisodeEnd, int verifiedSegmentCount) {
        return this == REVIEW_AT_EPISODE_END && naturalEpisodeEnd && verifiedSegmentCount > 0;
    }

    public boolean mayAutoSubmit(boolean calibrated, boolean boundariesVerified, boolean hasConflict) {
        return this == AUTOMATIC && calibrated && boundariesVerified && !hasConflict;
    }
}
