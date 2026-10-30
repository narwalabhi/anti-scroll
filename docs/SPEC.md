# AntiScroll: MVP spec

An Android app that adds friction to Instagram so you scroll less. Personal use first (Nothing Phone (2a) Plus), Play Store later. This is the working copy of the feature spec; update it when decisions change.

## Goal and non-goals

The MVP has one job: show whether friction measurably cuts Instagram scrolling.

In scope:
- Launch friction and an intention prompt every time Instagram opens
- A capped session timer
- The override ladder: wait timer, random typing challenge, escalation, cooldown, daily cap
- Reels and home-feed budgets, plus a daily total that counts all of Instagram (DMs included, for now)
- A hard reset at 4 AM local time
- Friction on turning the app off

Out of scope for the MVP: iOS, Play Store release, apps other than Instagram, reflection summaries, redirect suggestions, NFC unlock, AI check-ins, accountability partner, money stakes.

Success check: log one week of Instagram time with enforcement off (baseline), then compare with the first two weeks with enforcement on. The target is set after the baseline, as a percentage cut in total daily minutes.

## Platform

- Native Android, Kotlin, Android Views with Material 3 (what the project wizard generated; not Compose).
- minSdk 26, targetSdk 37. Test on the real phone (real Instagram, real account), not an emulator.
- Instagram only: package `com.instagram.android`.
- Escalation, cooldown, cap and reset rules live in the pure-Kotlin `rules` package (no Android imports) so they can be unit-tested and reused if iOS happens.

## Core flow

1. Instagram opens, the pause screen shows (short delay).
2. Intention prompt: pick a purpose and capped minutes.
3. Instagram runs; the session timer and budgets count.
4. When the time or a budget runs out, the blocking overlay appears.
5. The overlay offers an override if the daily cap and cooldown allow it. If not (or you decline), you are sent to the DM tab, or to the phone home screen when the daily total is spent.
6. An override goes through the ladder: wait timer, then a typing test. A granted session returns to step 3, with less time each override.

- The pause lasts 5 seconds. The intention prompt offers a purpose (reply to messages, post or share, look something up, just browsing) and 5, 10, 15 or 20 minutes (20 added 2026-10-30; longer only after friends' feedback), or "Not now", which goes home.
- Leaving Instagram mid-session and coming back before the time is up resumes the session without a pause. The session ends at its time in real time, so leaving never adds minutes.
- Until the override ladder exists (milestone 3), the time's-up screen offers "Leave" (home) or "New session" (back through the pause and prompt).

## Override ladder

One global counter covers all of Instagram. Values are starting values, kept in settings (see `LadderSettings`), to tune after a week of use. A ladder step is a list of challenges plus the minutes granted (see `docs/LLD.md`); the table shows the starting configuration.

| Override today | Wait | Typing challenge | Time granted |
| --- | --- | --- | --- |
| 1st | 30 sec | 1 random sentence | 10 min |
| 2nd | 1 min | 2 random sentences | 5 min |
| 3rd | 2 min | A random paragraph | 3 min |
| 4th and 5th | 5 min | A random paragraph, near-perfect accuracy | 2 min |
| 6th onward | Not available until the 4 AM reset | | |

- Wait timer restarts if you leave the screen, switch apps or the screen turns off.
- Typing challenge: new random text each time, paste disabled, at least 95% of characters right (case and punctuation count); "near-perfect" means 99%. Failing gives new text straight away and keeps the finished wait; Cancel goes back to the time's-up screen.
- Cooldown: after a granted session ends, overrides are locked for 20 minutes, plus 10 minutes for each later override.
- Daily cap: 5 overrides, then Instagram stays blocked until the reset.
- Reset: counter and cap return to zero at 4 AM local time.
- Break: when a session's time is up, reopening Instagram within 15 minutes shows the time's-up screen with the ladder instead of a new pause and prompt, so leaving and coming back doesn't skip the ladder.
- The time's-up screen says what the next override costs and grants, or why none is available (cooling down until when, or the day's 5 used).

## Instagram rules

| Surface | Detected by | Budget | When spent |
| --- | --- | --- | --- |
| Reels | A Reels viewer on screen, wherever it was opened (Reels tab, a DM, Explore, the feed, a profile); each page change is one swipe | 15 min a day (chosen 2026-10-29, lowered from 30 the same day). Swipes are counted and shown, with no swipe limit for now | Overlay with the ladder; if declined, one screen back in Instagram (the session carries on) |
| Home feed | Feed tab selected and scroll events in the feed list | Optional: minutes and scroll count, set from the baseline | Same |
| DMs | Inbox or a thread open | None of its own by default; counts toward the daily total | Blocked only when the daily total is spent |
| Explore, Stories, Search, Profile | Their tabs or screens | Optional, like the feed; always counted toward the daily total | Same as the feed if a budget is set, otherwise the ladder once the daily total is spent |

- Budgets come in two layers. The daily total covers all of Instagram and is always on. Per-surface budgets are optional, for any surface: setting a Reels budget below the daily total blocks just Reels when it runs out while the rest of Instagram keeps working; leaving it unset means only the daily total applies, to the whole app.
- When the Reels limit is spent, any Reels viewer is covered with "Reels limit used" as soon as it appears; the rest of Instagram keeps working on the session. Leave goes one screen back, with Android's own Back, and the session carries on wherever that lands (from the Reels tab, the Home feed; from a DM Reel, the thread; from Explore, the grid). The cover stays up ("Going back…") until you're off Reels; if one step still lands on Reels, Leave steps back again. (Decided 2026-10-29: tapping Instagram's tabs could break with any Instagram update, and the phone's home screen looped.) An override grants that step's minutes, during which no budget applies, and shares the day's 5 overrides, cooldown and break.
- Every Reel counts as Reels, including one opened from a DM, and so does every swipe onward from it. Otherwise a friend's link would be a way around the Reels budget. Possible stricter rule for milestone 4: allow the one Reel opened from a DM, block the first swipe onward.
- Tab labels and view IDs live in `app/src/main/assets/instagram_rules.json`, not in code, so an Instagram update that renames them is a config fix. Fall back to visible text such as "Reels".

## Daily budget and reset

- Daily total: minutes across every Instagram surface, DMs included for now: 1 h 30 min a day (chosen 2026-10-29). When spent, the ladder applies to every surface and declining sends you to the home screen.
  - Opening Instagram with the total spent goes straight to "Today's limit is used" (no pause or prompt); if it runs out mid-session, that screen appears at once, whatever the session had left.
  - An override granted there runs past the limit for its minutes (10, 5, 3, 2, 2), sharing the day's 5 overrides, cooldown and break with session overrides. When it ends, the limit screen comes back.
  - The intention prompt shows how much of today's limit is left, and offers only lengths that fit, plus "Rest of today" when what's left is below a choice.
  - Time on AntiScroll's own screens (pause, prompt, a wait) counts toward the total, since Instagram is open underneath.
- Guarding (enforcement) is on by default from the first open (decided 2026-10-30, for testing with friends; before that it was off for a counting-only baseline week). It can still be turned off in Limits, which costs the loosening wait and typing.
- 4 AM hard reset: daily total, per-surface counters, override counter, cap and cooldown all reset. If the phone clock moves backward, the reset is ignored.
- What you read is by calendar date (decided 2026-10-30): "Today", the 7-day chart and the baseline day count start at midnight. Only the limits run from 4 AM to 4 AM, and the Limits card says "since 4 AM", so late-night scrolling can't refill them at midnight.
- Focus windows (scheduled zero-budget periods such as bedtime) come after the daily budget works.

## Self-protection

It is friction, not a lock; you own the phone.
- Loosening settings (raising a budget, lowering the cap, editing the ladder) needs a 10-minute wait plus the typing challenge. Tightening applies instantly. No loosening during a focus window or after the cap is hit.
  - Planned 2026-10-29 for milestone 6: the settings you can change are Enforcement on/off, the daily total and the Reels limit; the ladder, pause and session lengths stay fixed for now. Loosening (turning Enforcement off, raising either limit, or any change that loosens one thing) costs a 10-minute wait, restarted if you leave the screen, then a 4-sentence paragraph at 95%. It's refused once today's 5 overrides are used, until 4 AM. Limits: daily total 5 to 600 minutes, Reels 0 to 600 (0 means no Reels).
- If the accessibility service is turned off, a fallback watcher (usage access) detects Instagram opening and covers it with an overlay until the service is back on. This fallback is the only thing that needs the "display over other apps" permission. Included in milestone 6 (decided 2026-10-29); it runs as a foreground service with a small notification, only while the accessibility service is off.
- Ask for a battery-optimization exemption so Nothing OS does not kill the service. This comes in milestone 1, with a heartbeat log, so the baseline week is not cut short by a killed service.
- Device administrator uninstall protection comes later.

## Sharing with friends (decided 2026-10-30)

A few friends get the app as a signed APK, before any Play release. No server and no automatic upload: data leaves a phone only when its owner shares it.
- Export: Settings, "Share my data", opens Android's share sheet with one JSON file: every calendar day since install (minutes and swipes per surface, opens, longest stretch, overrides used, whether guarding was on that day), the current limits, tracking gaps, the app version, the phone's maker and model, and Instagram's version. Counts and timestamps only.
- Reminders: one notification when the baseline week is done (day 8), and one 14 days after guarding first ran that day. Tapping opens the share sheet. Each shows once.
- Before sending anyone to Android's Accessibility settings, the app shows a disclosure screen: what the permission is used for, what it never does.
- Each friend starts with guarding on (the default since 2026-10-30).
- A copy that survives uninstalling (decided 2026-10-30): in Settings, "Keep a copy" asks once for a folder on the phone (such as Documents/AntiScroll). The app keeps one backup file there, rewritten when the folder is chosen and then at most every 10 minutes, when Instagram closes, the screen turns off or the app opens. After a reinstall, "Restore from a copy" (pick the same folder) brings back every day's counters, the limits, the guarding switch and the tracking gaps, adding to anything counted since the reinstall. Nothing leaves the phone.

## Cloud copy for the developer (decided 2026-10-30)

Each install sends its daily numbers to the developer's Firebase project (Cloud Firestore), so the developer can follow how friends use the app without asking for exports.
- No sign-in screen: Firebase's anonymous sign-in gives each install a random ID in the background. Data can't be restored from the cloud after a reinstall (a new install gets a new ID); the backup folder covers reinstalls. Google sign-in can be added later by linking the anonymous account, keeping its data.
- Google sign-in (decided 2026-10-30): optional, from a sign-in screen shown once after the disclosure and from Settings. Signing in links the install's anonymous account to the Google account, keeping its ID and data. Signing in on a reinstall or a new phone switches to that account's existing ID and restores its numbers (merged with anything counted since), the limits, the guarding switch and the tracking gaps.
- Sent: the same as the export file, per calendar day (time and swipes per surface, opens, longest stretch, overrides, guarding on or off), plus the install's limits, guarding switch, tracking gaps, app version, phone maker and model, Android and Instagram versions. Counts and timestamps only, never screen text or the purposes picked.
- At once when Instagram closes or the screen turns off (the numbers have just stopped changing), and at most every 5 minutes when the app opens or from the heartbeat (decided 2026-10-30), so an uninstall loses at most what an unsent offline write held. Firestore queues writes while offline.
- Every change to the limits or the guarding switch is recorded (decided 2026-10-30): sent at once as its own entry (when, before, after, and whether the user, a backup restore or a debug command made it), queued on the phone until sent, and logged locally too.
- Each install can write only its own data and read nothing; the developer reads everything in the Firebase console.
- The disclosure screen says the daily counts are sent to the app's developer. "Nothing leaves your phone" no longer holds; the promise is "counts only, never what's on screen".

## Architecture

- `InstagramGuardService` (AccessibilityService) reports events to the rules engine. It hears window changes from every app, so it knows when Instagram leaves the screen, but for any other app it reads only the package name, never the content. Screen content is read only inside Instagram. The keyboard, System UI (such as the notification shade) and our own overlay do not count as leaving Instagram; the screen turning off does.
- Rules engine (pure Kotlin): ladder, budgets, cooldown, cap, 4 AM reset. It returns decisions: allow, show pause, block, go to the DM tab.
- Overlay screens: pause, intention prompt, blocking overlay, wait and typing challenge. Drawn with the accessibility service's own overlay window, which needs no extra permission. Prototype the typing keyboard first; if it does not work in an overlay, the challenge opens as a full-screen activity of our own. See `docs/LLD.md`.
- Local storage: counters, logs (time, opens, swipes, overrides), settings. Candidates: Room or DataStore.
- Rules config (JSON): Instagram tab labels and view IDs.
- Fallback watcher: usage access, used when the accessibility service is off.
- Counts stay on the phone, except the daily numbers sent to the developer's Firebase project (see Cloud copy for the developer). The service sees all screen content in Instagram, so store and send only counts and timestamps, never screen text.

## Risks

| Risk | Mitigation |
| --- | --- |
| Instagram UI update breaks detection | IDs and labels in config; fall back to visible text; check after each update |
| Nothing OS kills the service | Battery exemption, foreground notification, heartbeat log so gaps are visible |
| Missed or double-counted swipes | Count page changes and also time in the Reels tab |
| Too much friction, so you switch it off | Start gentle, tune from the baseline, keep early overrides cheap |
| Sensitive screen content | Read content only in the Instagram package (other apps: package name only), process on-device, store counts and timestamps only |
| Play Store review (later) | Declare accessibility as core functionality, clear in-app disclosure, data stays on device |

## Build order

| # | Milestone | Includes | Difficulty |
| --- | --- | --- | --- |
| 1 | Skeleton and logging | Accessibility service (content read in Instagram only), permission flows, battery exemption, heartbeat log, time and opens per day, read-only log of which tab (Reels, feed, DMs) is on screen | Medium |
| 2 | Launch friction | Overlay on every Instagram open, intention prompt, capped session timer | Easy to Medium |
| 3 | Override ladder | Wait timer, random typing challenge, escalation, cooldown, daily cap, 15-minute break after a session, 4 AM reset (values as constants until settings are editable in milestone 6) | Easy to Medium |
| 4 | Reels and feed enforcement | Swipe counting, per-surface budgets, blocking overlay, redirect to the DM tab | Hard |
| 5 | Daily budget and focus windows | Daily total across surfaces (planned 2026-10-29, built first); scheduled zero-budget windows later | Easy to Medium |
| 6 | Self-protection | Delayed loosening of settings, fallback watcher | Medium |
| Later | Extras | 11 PM higher ladder start, device administrator uninstall protection, grayscale escalation (ADB, personal build only), reflection, redirect suggestions, AI check-in, NFC unlock, iOS app, Play Store release | Easy to Hard |

Run the baseline week right after milestone 1, with enforcement off, while building milestones 2 and 3.

## Open items

- Starting the ladder one level higher after 11 PM: deferred.
- Success target: budgets were chosen up front (1 h 30 min total, 15 min Reels); after the baseline week, check them against the baseline numbers and adjust if needed. The status screen shows progress against them during the baseline week, by choice, so the baseline may already be lower than fully unaware use.
- Revisit DMs in the daily total after the first week; a long chat could use up the budget.
