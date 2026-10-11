# YP Content Discovery revision 9

- Removes focused-card expansion and its settings.
- Adaptive, smaller top information bar; complete rows preserve card aspect ratios at all five sizes.
- Fill card image defaults on; Clear Logo retains transparent artwork and fit scaling.
- Discovery display settings now expand under Display. Content & Discovery contains metadata cache controls.
- Native slider: 0–1000 MB in 25 MB steps, default 50 MB. Actual on-disk usage and reset action.
- Bounded metadata-only JSON cache for exact TMDB query pages, details and image descriptors. No image binaries or API keys are stored. LRU eviction, 15-minute query freshness and 24-hour details freshness. Reset prevents in-flight requests from repopulating the cleared cache.
- Actor filters are sent to TMDB even when include-missing is enabled; changing sort retains the full query. Local catalog candidates wait for required metadata before appearing.
- Already released and minimum 100 TMDB votes default on and reset to on, without active-filter chips. Already released excludes future or unknown release dates; series use first air date.
- Original TMDB integration and IntroDB reporting are unchanged.

Prerelease for TV verification; automated checks do not replace remote-control testing on a device.
