# YP Nuvio Content Discovery 3

Based on 1.1.0-beta.5 with the existing IntroDB reporting changes preserved.
Optimized release build (R8), immediate discovery results, background metadata with bundled TMDB requests, penultimate-row prefetch, themed compact filter/sort menus, centered single-line catalog picker, shared API key under Integrations > TMDB, and nested Content Discovery display settings. Vote count and runtime filters removed.

Export collects a fixed snapshot, stores it in native Collections and pins it to the top. Cancel or a source failure leaves no partial catalog. TMDB searches with more than 500 pages must be narrowed before export. Some addon catalogs expose only a finite set of results. Physical Google TV performance and remote navigation remain to be verified. This independent prerelease uses debug signing; preserve settings if Android reports an incompatible signing key.
