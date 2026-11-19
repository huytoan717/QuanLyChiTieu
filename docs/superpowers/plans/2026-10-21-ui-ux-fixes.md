# UI/UX fixes implementation plan

**Goal:** Resolve the eight audited usability defects, starting with invalid saves, IME obstruction, and large text clipping.

**Architecture:** Keep Kotlin Activities and XML layouts. Add a small on-device transaction store and integer-VND validation. Share system-bar/inset setup and semantic navigation styling. Mark unfinished features and sample data honestly.

**Tech stack:** Existing Android Views, AppCompat, SQLiteOpenHelper, JUnit, Espresso. No additional production dependency.

## Constraints

- User authorized implementation of the prior audit and its proposed remedies.
- Keep dark visual identity and Vietnamese copy.
- Preserve app data; no destructive migration or external service.
- Min SDK 24, target SDK 36, compile SDK 37.
- No Git repository exists. Before-state is in `.codex/ui-ux-before`.
- Only main agent operates Gradle and the emulator; agents own disjoint source files.

## Tasks

- [x] Add regression instrumentation tests: blank/zero amount must keep form open; selected type survives Activity recreation.
- [x] Observe those failures on the current app before implementing transaction behavior.
- [x] Add pure amount parsing tests for blank, zero, negatives, malformed grouping, VND grouping and overflow.
- [x] Implement Long VND parser and SQLite transaction records (type, category, note, wallets, date, budget-exclusion).
- [x] Implement inline validation, one save at a time, explicit success after confirmed insert, failure keeps input, and state restoration.
- [x] Show saved records on the home screen after returning and after process restart; label remaining sample panels.
- [x] Rebuild transaction XML with 48dp touch targets, amount shortcuts next to the amount, adaptive categories, and source/destination wallets.
- [x] Repair other layouts for 200% font size and narrow portrait: flexible heights, non-overlapping text, adaptive grid/rows, expandable navigation.
- [x] Share dark system bars and IME-aware insets; align chart/legend color tokens and improve small-text contrast.
- [x] Clearly disable unfinished filters/settings/actions and remove false sync/security-success claims.
- [x] Build, run unit/instrumentation tests, review source, then manually inspect five screens, 360dp width, 200% font, keyboard and rotation.

## Ownership and integration

Transaction form layout agent: `activity_add_transaction.xml` and only its new supporting resource files. Keep existing IDs; add `txtAmountError`, `txtWalletName`, `txtTransactionTime`, `btnDestinationWallet`, `txtDestinationWalletName`, `categorySection`, `categoryGrid`, `txtSaveLabel`.

Adaptive layout agent: all other layout XML except `layout_recent_transactions.xml` and `layout_bottom_navigation.xml`; optional `AdaptiveLinearLayout.kt`. Sample panels must be clearly labeled. Do not edit Activities, charts or shared values/drawables.

Shared UI agent: `ui/components` except adaptive helper, `ui/account`, `ui/budget`, `ui/report`, bottom navigation XML, shared colors/drawables/themes. Expose `UiChrome.setup(activity: AppCompatActivity, rootId: Int, includeIme: Boolean = false)` for main/form Activities. Mark unfinished controls unavailable using existing IDs; coordinate sample-copy changes with layout agent.

Main agent: transaction model/store/Activity, MainActivity, saved-record layout, tests, verification and integration.

## Validation commands

Use session-only JDK: `$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'`.

`rtk proxy .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:connectedDebugAndroidTest --console=plain`

Expected final: successful build, all business and UI regression tests pass. AVD Pixel_10 runs read-only and headless. Use `.codex/ui-ux-review/inspect_ui.py` and save new evidence under `.codex/ui-ux-after`. Verify invalid values, successful record persistence, correct type after rotation, visible save button above IME, readable 200% text, correct chart keys, 48dp targets and disabled unfinished features. No physical-device or TalkBack claim without performing those checks.
