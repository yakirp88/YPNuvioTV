# Content & Discovery + IntroDB reporting — integration kit

This is a GPL-3.0 source integration kit for the Android/Google TV Nuvio app.
It cannot be installed as an add-on or applied to an already compiled APK.
The receiving fork's maintainer must apply the code and build/sign a new APK.

## Compatible base

The patch is generated against NuvioMedia/NuvioTV dev commit
`6377bee8cc644ecd8048a771ced61f06bcdf7946` (1.1.0-beta.5).
Its application and resulting file bytes are verified against that exact base.
Other versions/forks can need manual conflict resolution; compatibility with all
forks is not claimed. See manifest.json for the feature source commit and checks.

## Maintainer steps

1. Commit/back up your existing changes and create a branch in your own fork.
2. Unzip this kit outside your repository. Keep `features.patch` and this guide.
3. From the repository root, check and apply:

```sh
git switch -c feature/discovery-introdb
git apply --check /path/to/YP-Nuvio-Discovery-IntroDB/features.patch
git apply /path/to/YP-Nuvio-Discovery-IntroDB/features.patch
```

If the check fails, do not overwrite whole files from source-reference. Port the
specific hunks into your existing classes. With the recorded upstream base in
your local Git object database, `git apply --3way features.patch` can help merge;
review conflicts and preserve the fork's own features before committing.

4. Keep your fork's `applicationId`, branding, signing key and update mechanism.
The kit deliberately excludes AndroidManifest.xml and app/build.gradle.kts.
Increment your own versionCode before distributing the update. An APK signed
with another key cannot replace an installed official/fork APK in place.
5. Preserve existing service configuration. For public IntroDB reads configure
`INTRODB_API_URL=https://api.introdb.app/` in the appropriate local/dev properties
if your fork has no IntroDB URL configured. The report POST client uses the
public HTTPS service separately. Every user enters their own reporting API key
in Playback → Skip segments; it is stored with Android Keystore.
6. TMDB key entry is wired into Integrations → TMDB. If your fork already has
personal TMDB key storage, reuse it and adapt `TmdbService.configuredApiKey()`
and `DiscoveryViewModel.apiKey()` rather than creating a second conflicting
settings entry. No private keys or signing materials are supplied in this kit.
7. Use the project's JDK 17, Android SDK/NDK and Gradle wrapper. Run:

```sh
./gradlew :app:testFullDebugUnitTest --tests 'com.nuvio.tv.ui.screens.discovery.DiscoveryPolicyTest' --tests 'com.nuvio.tv.data.introdb.*' --tests 'com.nuvio.tv.data.local.DiscoveryPreferencesTest' --tests 'com.nuvio.tv.data.local.DiscoveryMetadataCacheTest' --tests 'com.nuvio.tv.core.tmdb.TmdbCollectionSourceResolverTest' --tests 'com.nuvio.tv.data.local.CollectionsDataStoreSourceMigrationTest'
./gradlew :app:assembleFullRelease
```

Windows: use `gradlew.bat`. Configure release signing using your existing setup.
FullRelease includes the existing native decoder/MPV requirements; do not remove
them merely to hide a compile or dependency error.

## Integration points to review

- DiscoverScreen delegates to ContentDiscoveryScreen while retaining its saved
  catalog management route. Four card styles, five sizes, top/side hero,
  compact filters/sort, catalog snapshots and metadata-only cache are included.
- Layout/settings host the collapsible discovery options and cache controls.
- TmdbApi adds discovery, configuration, people and company endpoints.
  TmdbService/metadata resolvers use the configured personal key.
- Collections use SavedDiscoveryItem snapshots for saved filtered catalogs.
  Preserve collection migrations and profile isolation in the receiving fork.
- PlayerScreen, PlayerViewModel and PlayerUiState connect the report coordinator
  and overlays; frame capture supports ExoPlayer/MPV. Runtime controllers suspend
  automatic skip/post-play/watch-progress writes during report precision seeks.
- PosterOptionsDialog adds slots used by discovery actions. Keep the native menu
  design and the receiving fork's other actions.

## Behavior and device checks

Text search opens a centered dialog; substring matching is case-insensitive
across Hebrew/English/original titles. Match and loaded-title counts update live.
It advances through the selected filtered source after each metadata batch,
even after Confirm closes the dialog. Pause/resume and clearing are supported.
Blank text, errors and exhausted source cursors stop automatic advances.
TMDB discovery exposes at most 500 pages per query; completion refers to the
available source, not an exhaustive global title search. Narrow filters for
large searches. Batch loading uses up to 60 TMDB titles, with four concurrent
metadata requests. Cached metadata contains descriptors, not image binaries.

Test on a Google TV device: dialog/D-pad/keyboard focus and Back; pause/clear;
offline retry; sort/filter changes during search; type/profile changes; all
card sizes/layouts; catalog persistence; report start/finish/cancel, corrected
thumbnails, key entry, existing-report blocking and a real authorized submission.
Automated patch validation is not a device test. Check manifest.json for the
current build validation state before releasing your fork.

Include the GPL-3.0 license and corresponding source when distributing the APK.
This kit is independent of the official Nuvio release and carries no official
signing key or promise of compatibility with future upstream changes.
