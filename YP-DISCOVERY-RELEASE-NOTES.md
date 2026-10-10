# YP Nuvio Content Discovery 6

Based on 1.1.0-beta.5, with existing IntroDB reporting preserved.
Modern Discovery with a top hero by default or a fixed left-side hero occupying one third of the screen. The toolbar begins with the movies/series switch at the top right. Four card styles (poster, clear logo, landscape and banner) and five sizes cycle independently. Expansion changes card width without replacing the focused row, retaining neighboring cards.

The hero displays artwork, logo, release date, runtime, genres, rating and a scrolling synopsis. IMDb scores use addon metadata; TMDB scores are identified separately. Clear-logo artwork uses original-resolution URLs. Long press opens Nuvio's native poster menu with trailer playback, similar content and cast/director navigation added. Settings reuse native Discover visibility/location controls. Decades precede year fields and fill the corresponding range. Redundant branding, the default all-content heading, old information-position choices and the settings Back row have been removed. The native TMDB API-key dialog and shared credentials remain intact.

Release compilation and automated checks are run in CI. Remote navigation, anchored rows, image expansion and physical Google TV performance need device validation. Independent prerelease, debug signing. Preserve settings if Android reports an incompatible signing key. Saved catalog export still obeys source limits and requires narrower searches beyond 500 TMDB pages.
