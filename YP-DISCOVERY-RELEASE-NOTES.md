# YP Nuvio Content Discovery 7

Based on 1.1.0-beta.5, with existing IntroDB reporting preserved.
Modern Discovery with a top hero by default or a fixed left-side hero occupying one third of the screen. Hero artwork now reaches the physical top and left edges behind the toolbar and uses higher-resolution TMDB backgrounds. Four card styles and five sizes remain available. Expansion grows a horizontally scrollable row, pushing adjacent cards without shrinking their width.

The hero prefers clear-logo artwork over a text title. Appended external IDs no longer require a nested TMDB ID; regression coverage verifies that this response shape preserves images and other metadata. IMDb-based titles can fall back to transparent Metahub logos when TMDB or addon artwork is absent. A real Dune logo was fetched and verified as an 800x310 transparent PNG. Existing native actions, settings and shared TMDB credentials are preserved.

Release compilation and automated checks are run in CI. Remote navigation, anchored rows, image expansion and physical Google TV performance need device validation. Independent prerelease, debug signing. Preserve settings if Android reports an incompatible signing key. Saved catalog export still obeys source limits and requires narrower searches beyond 500 TMDB pages.
