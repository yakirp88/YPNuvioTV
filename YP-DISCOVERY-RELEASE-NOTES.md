Content Discovery revision 11

- Keep focused large posters fully visible with edge-based TV scrolling and an adaptive smaller hero.
- Rename display settings to Content & Discovery.
- Load up to 60 TMDB titles per batch with bounded parallel page requests; use source cursors for add-on batches.
- Preserve sorting, filters, metadata cache and existing integrations.
- Open text search in a centered dialog with live match/loaded counts, pause/resume and confirmation.
- Continue searching the active filtered source in background, waiting for metadata between batches.
- Clear text to stop automatic search; stop on source exhaustion or error without an unbounded request fan-out.
