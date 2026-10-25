package com.nuvio.tv.core.intro;

/** Media handoff is distinct from actually running an audio/video analyzer. */
public enum IntroDetectionPhase {
    IDLE,
    SEARCHING_SUBTITLES,
    AWAITING_MEDIA_ANALYZER,
    ANALYZING_MEDIA,
    COMPLETE,
    FAILED;

    public boolean showsIndicator() { return this == SEARCHING_SUBTITLES; }
}
