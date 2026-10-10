# YP Nuvio Content Discovery 4

Based on 1.1.0-beta.5, with existing IntroDB reporting preserved.
Compact borderless filter and sort menus, adjacent fixed-choice submenus, remembered filter focus, three-state genres, ascending/descending control at top, English language labels, Clear Logo artwork fallback and refresh, and server-side filtering for built-in Popular/New catalogs.

Content Discovery > Display settings now includes fixed information position (Top by default, Middle, Bottom, or expanding the focused image), with the original app slider for a 0–10 second expansion delay. The original TMDB integration screen is restored; previously saved credentials remain intact.

Release compilation and automated checks are run in CI. Remote navigation, anchored rows, image expansion and physical Google TV performance need device validation. Independent prerelease, debug signing. Preserve settings if Android reports an incompatible signing key. Saved catalog export still obeys source limits and requires narrower searches beyond 500 TMDB pages.
