# Nuvio YP AI project conventions

- This project is the AI intro-detection fork on `yp/ai-intro` of `yakirp88/YPNuvioTV`.
- Preserve the separate native-reporting work on `dev`; do not replace its history or silently merge unrelated features.
- For each completed user-authorized change: update CHANGELOG.md, run relevant checks, commit and push to this branch, and inspect the Actions outcome.
- Update yp-version.properties before a new release series. Never replace an existing versioned release or reuse a version code for changed bytes.
- Distribute APKs through GitHub Releases, with SHA256SUMS.txt and corresponding source. Verify public release assets before giving the user a download link.
- Keep the persistent signing key private in Actions secrets; never commit or attach it, user API keys, session tokens or local properties.
- config/yp-public-client.properties is a public anonymous client configuration, not a personal key.
- Do not describe automatic AI reporting or audio analysis as implemented. Confidence calibration and device testing are still outstanding.
- Report build/publication blockers accurately; a workflow definition is not a successful build or published APK.

- Run `python3 tools/intro-evidence-check/check_android_regex.py` when changing subtitle regexes. Desktop JDK regex checks do not cover Android ICU behavior.
- Crash diagnostics must stay local and structurally allowlisted: never include exception messages, URLs, credentials, transcripts or raw system traces.
