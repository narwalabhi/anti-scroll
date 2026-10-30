# AntiScroll

Android app (Kotlin, Android Views + Material 3) that adds friction to Instagram so the owner scrolls less. Personal use first, Play Store later. Owner: Abhishek.

@docs/SPEC.md
@docs/LLD.md

## Project state

Built and checked on the phone (2026-10-29): milestone 1 (surface detection, counting, status screen), 2 (pause, intention prompt, session timer), 3 (override ladder, cooldown, cap, break, session survives restarts), the daily total of milestone 5, and milestone 4 (Reels limit, swipe counting). Refactored 2026-10-29 for extensibility (docs/LLD.md, "Refactor for extensibility"): Leave is an `Intervention` (one screen back from a Reels block, with Android's Back; home from the others), surface limits are a map in settings, and the accessibility service is split into single-purpose classes. Leave's one screen back and photo posts in Explore's viewer (not counted as Reels) were checked on the phone. Enforcement (guarding) is a saved setting, on by default since 2026-10-30. Milestone 6 is built (2026-10-30): the fallback watcher (checked on the phone), and the Limits tab, where loosening costs the wait and typing (`ui/LoosenActivity`). The UI was restyled to docs/UI.md, trimmed: History, first run, theme, export and erase are not built (functionality first, UI later). Focus windows aren't built. Budgets: 1 h 30 min a day, Reels 15 min. Package `com.abhishek.antiscroll`, minSdk 26, targetSdk 37. AGP 9 uses built-in Kotlin: there is no separate Kotlin plugin in the Gradle files, so do not add one.

Phone checks: the phone's Logcat buffer is small, so capture live to a file (`adb logcat -T 1 -s AntiScroll:V > file`) before a walkthrough. Debug builds take adb commands (see `guard/DebugCommands.kt`): `am broadcast -a com.abhishek.antiscroll.debug.ENFORCEMENT --ez on false`, and `...debug.LIMITS --ei daily 90 --es surfaces REELS=15` to set limits for testing (skips the loosening friction; put them back afterwards), `...debug.CLEAR_TIMERS` to end a cooldown and break, `...debug.DUMP` to log the screen's view IDs. Read the event log with `adb shell run-as com.abhishek.antiscroll cat files/events.jsonl`. Instagram's view IDs live in `app/src/main/assets/instagram_rules.json`; in debug builds a screen detected as Other is dumped to Logcat (view IDs only) to find new ones.

## Commands

- Build: `./gradlew assembleDebug`
- Unit tests: `./gradlew test`
- Install on the phone: `./gradlew installDebug` (USB debugging on; adb is in `~/Library/Android/sdk/platform-tools`). The debug app is `com.abhishek.antiscroll.debug` and sits next to the release app the owner uses day to day; turn on only one Accessibility service at a time.
- Firebase (the developer's cloud copy): put the project's `google-services.json` in `app/`; the build reads it (no Google Services plugin). Rules are in `firestore.rules`, pasted into the console.
- Release APK for sharing: `./gradlew assembleRelease` (signed with `~/.antiscroll/release.jks`; bump `versionCode` for each update)
- Logcat: `adb logcat -s AntiScroll`

## Working agreements

- Agree the design before writing code. Follow `docs/LLD.md`; if the code needs to deviate, propose the change to the LLD first.
- Test on the real phone (Nothing Phone (2a) Plus). Accessibility and Instagram behavior cannot be tested on an emulator.
- Rules logic (ladder, budgets, cooldown, cap, 4 AM reset) lives in a pure-Kotlin package with no `android.*` imports, and is unit-tested.
- Store only counts and timestamps, never screen text.
