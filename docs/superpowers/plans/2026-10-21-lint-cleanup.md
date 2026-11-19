# Android Lint Cleanup Implementation Plan

**Goal:** Resolve the 341 current warnings, prioritizing accessible input, readable UI and layout performance.

**Architecture:** Keep the existing Kotlin/Views design and persisted transaction format. Extract UI copy into Android resources, simplify redundant views and use AndroidX accessibility compatibility APIs. Validate tooling upgrades separately.

**Tech stack:** Android Views, Kotlin, SQLite, Android Lint, existing JUnit/instrumentation suites.

## Constraints

- User authorized fixes, UI/UX warnings first.
- Preserve Vietnamese copy, visible behavior, IDs used by Activities/tests and saved transaction semantics.
- No blanket lint suppression or lint baseline.
- No Git checkout exists; preserve a source/configuration snapshot before editing.
- Root alone runs Gradle and the emulator. Independent agents own disjoint files.
- Do not add tests that merely mirror reversible resource edits; use Lint, resource parity checks and visual inspection.

## Tasks

- [x] Snapshot source/configuration and the current Lint report; record issue counts.
- [x] Extract all literal layout text/hints/descriptions into strings_layout.xml and verify text parity.
- [x] Simplify the transaction categories and selectors; remove duplicate field-label sources, explicitly opt transaction inputs out of autofill, use compatible accessibility headings.
- [x] Move dynamic Activity text into formatted resources; preserve storage labels and transaction regression behavior.
- [x] Fix AdaptiveLinearLayout allocations during measure and use the scoped AndroidX styled-attribute helper.
- [x] Remove redundant nav containers and simple image/text wrappers; disable unneeded baseline measurement in weighted icon rows.
- [x] Reuse or remove truly unreferenced resources after reference inspection.
- [x] Verify official AGP/SDK compatibility; upgrade only with successful build and runtime checks.
- [x] Run fresh debug/release Lint, debug APK/unit/instrumentation checks and inspect affected screens at ordinary and 200% text.
- [x] Record final warning counts and remaining limitations.

## Validation

Use session-only JAVA_HOME=C:\Program Files\Android\Android Studio\jbr.

Run `rtk proxy .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug :app:lintRelease --console=plain`.

On the task-owned Pixel_10 emulator, run all instrumentation tests; inspect form labels/category selection, keyboard CTA, nav and modified compound-drawable rows at 360dp/200% font. Recheck income/expense/transfer labels after resource extraction. Preserve evidence under .codex/lint-cleanup.

## Verified result (2026-10-21)

- Android Lint: **341 warnings -> 0 errors / 0 warnings**, both debug and release. No lint baseline or ignored issue was added.
- 5/5 unit tests and 12/12 instrumentation tests passed on API 37. Four new tests cover adaptive width/font behavior, parameter reuse, RTL margin restoration and dynamic children.
- Vietnamese copy parity: all 252 retained extracted layout attributes match the snapshot; four obsolete placeholder/decorative entries were removed. Persisted transaction category/wallet labels remain unchanged.
- Transaction layout: 87 -> 72 views, all 43 prior IDs preserved. Category buttons expose one text label and selected state. Inputs have permanent labels and explicit autofill policy; the empty amount field has a visible boundary.
- Visual inspection: all five screens at default width/font and 360dp/200%; category selection, keyboard/save action, date/time buttons, budget advice, account settings; form/account at 600dp. Manual expense, income and transfer saves display the expected labels and VNĐ amounts on Home.
- Build: AGP 9.4.1, Gradle 9.8.2 (official distribution SHA-256 pinned), Kotlin 2.4.21, target SDK 37. Updating Kotlin fixes the pre-existing mismatch with Firebase 35 metadata. Firebase dependency versions are unchanged and now referenced through the version catalog.
- Evidence and source/configuration snapshot: `.codex/lint-cleanup/`. See `verification.json`, `final-build.log`, `instrumentation-final.log`, `source-changes.diff` and `final-*.png/xml`.

### Scope of verification

No physical-device or interactive TalkBack gesture test was performed; this pass ran on API 37. A separate Gradle deprecation diagnostic remains inside `com.android.internal.application`: `Configuration.setVisible(boolean)`. It needs an upstream AGP update and was not suppressed. The build succeeds; both Lint reports are empty. Gradle also retains the Firebase datastore native library unstripped, as supplied by the dependency.

### Tooling references

- [AGP 9.4 compatibility (API 37; minimum Gradle 9.6)](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
- [Official override for AGP built-in Kotlin](https://developer.android.com/build/releases/agp-9-0-0-release-notes#upgrade_to_a_higher_kgp_version)
- [Target API 37 behavior changes reviewed](https://developer.android.com/about/versions/17/behavior-changes-17)
