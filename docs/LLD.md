# AntiScroll: Low-level design

Agreed design. Read `docs/SPEC.md` for what the app does; this file says how the code is structured. Propose changes here before deviating from it.

## Design at a glance

A small pure-Kotlin rules engine wrapped in thin Android adapters. The engine decides what should happen; the adapters only report what Instagram is doing and carry out what the engine says. Everything that will vary later (other apps, unlock methods, penalties) sits behind a small interface, so a new behavior is a new class and the engine does not change.

```
Android layer:   guard            overlay            data
                   |                |                 |
                   v   (all arrows point inward)       v
rules (pure Kotlin, no android.* imports):
                 GuardEngine   Extension points   Ports
```

An `AppGraph` in the Application class (`AntiScrollApp`) creates every object and connects them by hand. No DI library, no singletons.

## Packages and classes

Root package `com.abhishek.antiscroll`.

| Package | Class | Role | Pattern |
| --- | --- | --- | --- |
| guard | `InstagramGuardService` | Composition only: creates the pieces below when the service connects, routes each accessibility event to them, tears them down | Adapter |
| guard | `ForegroundTracker` | Decides when Instagram opens and closes: window changes, the half-second open check, screen off/on, content events on only while open | None |
| guard | `SurfaceWatcher` | Debounced surface checks through the `AppProfile`, the tab-pager hint, Reels swipe detection | None |
| guard | `SessionTicker`, `Heartbeat` | The one-second tick (and usage summary) while Instagram is open; the 5-minute heartbeat | None |
| guard | `DebugCommands` | Debug builds only: the adb commands (enforcement, limits, keyboard test) | None |
| guard | `AccessibilityScreenNode` | Wraps `AccessibilityNodeInfo` as a `ScreenNode` | Adapter |
| guard | `NavigationEffectHandler` | Android's own Home and Back actions (no app-specific navigation) | Command executor |
| overlay | `OverlayController` | Adds and removes overlay windows | None |
| overlay | `ScreenEffectHandler` | Shows the pause, intention, block and challenge screens | Command executor |
| data | `DataStoreUsageStore`, `DataStoreSettingsStore` | Persist counters and settings | Repository |
| data | `JsonlEventLog` | Appends engine events to a file | Observer (`EventSink`) |
| data | `InstagramRulesLoader` | Reads `instagram_rules.json` from assets and parses it (with `org.json`) into `InstagramRules` | Adapter |
| rules.engine | `GuardEngine` | Takes a state and an event, returns the next state and effects | State, Command |
| rules.engine | `GuardState`, `GuardEvent`, `Effect` | Sealed types for what the engine knows, hears and says | State, Command |
| rules.engine | `GuardRunner` | Holds the current state and usage in memory, feeds every event to `UsageTracker` and, while enforcement is on, to the engine, hands effects to the `EffectHandler`, publishes `EngineEvent`s, saves usage through `UsageStore` | None |
| rules.usage | `DayUsage`, `UsageTracker` | The day's counters, and the pure function that folds events into them (time per surface, opens, 4 AM reset) | None |
| rules.profile | `AppProfile`, `InstagramProfile`, `InstagramRules`, `Surface` | Which app, and how to tell Reels from feed from DMs; `InstagramRules` is the parsed config as a plain data class | Strategy, Factory |
| rules.budget | `BudgetRule`, `SurfaceBudgetRule`, `DailyTotalBudgetRule` | Answer Allow or Block(reason) | Chain of Responsibility |
| rules.budget | `Budgets` | The daily total and per-surface limits (1 h 30 min, Reels 15 min) | None |
| rules.settings | `LaunchSettings` | Pause length, purposes and minute choices for the intention prompt | None |
| rules.escalation | `EscalationPolicy`, `CountLadderPolicy`, `LadderSettings` | What the nth override costs and grants | Strategy, Decorator later |
| rules.unlock | `UnlockChallenge`, `WaitChallenge`, `TypingChallenge`, `SequenceChallenge`, `ChallengeFactory` | The ways to earn an override | Strategy, Composite, Factory |
| rules.intervention | `Intervention`, `StepBackIntervention`, `GoHomeIntervention` | What Leave does on a block | Strategy, Decorator |
| rules.events | `EventSink`, `EngineEvent` | Notifications about what the engine did | Observer |
| rules.time | `Clock`, `DayClock` | Current time and the 4 AM day boundary | None |
| root | `AntiScrollApp` (the `AppGraph`) | Creates and connects everything | Composition root |

Surface detection lives in `InstagramProfile` (pure Kotlin). It reads a `ScreenNode`, so it is tested with fake screens; `guard` only adapts Android's nodes.

### Surface detection

Found on the phone in milestone 1, step 1. Instagram keeps every tab loaded at once, so a view existing in the tree does not say which surface is showing; the selected tab does. All IDs and labels below live in `instagram_rules.json`.

0. A Reels viewer (`clips_viewer_view_pager`) that is visible to the user: Reels, wherever it was opened (Reels tab, a DM, Explore, the feed, a profile). This rule comes first. Except when the item in it is a known photo post: Explore shows photo posts in the same viewer, so a visible view from `photoPostInReelsViewerIds` (a carousel: `clips_carousel_viewpager`, `clips_carousel_image_media_content`; dumped on the phone 2026-10-29) means it isn't Reels. Unknown layouts still count as Reels, so a new one can't slip past the limit; a wrongly covered photo is a config fix. A hidden tab's viewer does not count: views Instagram keeps loaded behind the current screen report `isVisibleToUser` false (confirmed on the phone in milestone 1, step 2b). Swipes are page-number changes of the visible viewer, including in a Reel opened from a DM.
1. Tab bar on screen (`feed_tab`, `clips_tab`, `direct_tab`, `search_tab`, `profile_tab`, in that order):
   - A tab is selected: that tab's surface. Match by view ID; if no ID matches, by content description ("Home", "Reels", "Message", "Search and explore", "Profile"), but only when at least 3 of the 5 labels are found: a single "Profile" or "Home" button on another screen, such as a Story, is not a tab bar.
   - No tab selected (Instagram does this on Home until a tab is tapped): the last page seen on the tab pager (`swipeable_tab_view_pager`, pages in tab-bar order), passed in as a hint by the service from its scroll events.
   - No tab selected and no page seen yet: Feed.
2. No tab bar (full-screen screens opened on top, often from a notification):
   - `message_list`: DMs (a thread).
   - `reel_viewer_root`: Stories.
   - Anything else (activity screen, comments sheet, a profile from a link): Other. It still counts toward the daily total.
3. Scroll events count only when they come from the surface that is showing, since hidden tabs also send them.
4. The service re-checks the surface, debounced, after any Instagram event, tab-pager scrolls included: swiping between tabs sends no click.

The `search_tab` maps to Explore; Search (the search field in use) is not detected yet.

"No `android.*` imports" in `rules` does not exclude `java.time` (`Instant`, `LocalDate`, `ZoneId`): it is a Java API, available from API 26.

### Foreground detection

The service hears `TYPE_WINDOW_STATE_CHANGED` from every package, so it can tell when Instagram leaves the screen. For a non-Instagram window it reads only the package name; content events and node reads happen only for `com.instagram.android`.

- Instagram is the active window: `InstagramOpened` (if not already open). An Instagram event alone is not enough: for a moment after another app comes up, Instagram keeps sending events and is still reported as the active window. So only Instagram's window coming to front (`TYPE_WINDOW_STATE_CHANGED`) or a tap proposes an open while closed; its redraws during the Home animation (`TYPE_WINDOW_CONTENT_CHANGED`) did, and caused extra opens. The open is confirmed if Instagram is still the active window 500 ms later, and dated back to the proposal. A glance shorter than that is not counted.
- Another app's window comes to front: `InstagramClosed`.
- Ignored, so they do not close Instagram: our own package, the current input method, `com.android.systemui`.
- Screen off (`ACTION_SCREEN_OFF`): `InstagramClosed`, and the counters are saved. Screen on or unlock (`ACTION_SCREEN_ON`, `ACTION_USER_PRESENT`) with Instagram as the active window: `InstagramOpened`, a new open, so locking the phone is no way around launch friction.
- Closing forgets the surface, so each session reports its surface again (time before it is known counts as Other).
- `Tick` runs on a main-thread `Handler`, once a second, only while Instagram is open.
- The service also listens for `TYPE_WINDOW_CONTENT_CHANGED`. Android refreshes a service's cached screen tree only from the events it receives; without content changes, a tapped tab switch left the old tab "selected" and the hidden Reels viewer "visible" in the cache (found on the phone in milestone 1, step 3). Content changes also trigger the surface re-check once the screen settles. To keep other apps' content changes from costing anything, the service turns this event type on only while Instagram is in front (milestone 1, step 5).

## Usage counting

`UsageTracker` is a pure function `(UsageTracker.State, GuardEvent) -> UsageTracker.State`. The state is the day's `DayUsage` plus what counting the next tick needs (Instagram open or not, current surface, last tick time); only `DayUsage` is saved. Milestone 1 uses it on its own; from milestone 2, `GuardEngine` calls it before checking budgets. It reads the time zone from `Clock` but never the time: events carry it.

```kotlin
enum class Surface { REELS, FEED, DMS, EXPLORE, STORIES, SEARCH, PROFILE, OTHER }

data class DayUsage(
    val day: LocalDate,                                  // the 4 AM day these counters belong to
    val opens: Int = 0,
    val millisBySurface: Map<Surface, Long> = emptyMap(),   // milliseconds, so uneven ticks lose nothing
    val swipesBySurface: Map<Surface, Int> = emptyMap(),
    val overridesUsed: Int = 0,
    val cooldownUntil: Instant? = null,
    val breakUntil: Instant? = null,                        // milestone 3: reopening before this shows the ladder
    val session: Session? = null,                           // milestone 3: the session in progress, so a restart doesn't lose it
)
```

- `InstagramOpened` adds one open (not again while already open). `SurfaceChanged` sets the current surface. `InstagramClosed` stops counting.
- Each `Tick` adds the time since the previous tick to the current surface (Other until a surface is known), capped at 5 seconds so a stalled handler or a sleep does not overcount, and never negative.
- When `DayClock` says an event is in a later day than `usage.day`, the tracker starts a fresh `DayUsage` first and the runner publishes `DayReset`. A session that spans the reset counts as one open in the new day, and the tick interval that crosses the reset counts in the new day.
- The day only moves forward: a clock moved back into the previous day keeps the current counters. This replaces a separate `lastSeen` field.
- A `Tick` while Instagram is closed counts nothing but still starts a new day at the reset hour. The status screen sends one every second (`GuardRunner.catchUp()`, with or without enforcement), so the morning's status screen shows the new day before Instagram is opened (found 2026-10-30: it showed the previous day until an Instagram event).

### Calendar stats and the limit window (2026-10-30)

Two sets of counters, each with its own day boundary:

- `DayUsage` is the limit window, 4 AM to 4 AM: the budgets, overrides, cooldown, break and saved session. Unchanged.
- `DayStats(date, opens, millisBySurface, swipesBySurface)` is per calendar date, midnight to midnight: what the status screen shows as "Today", the 7-day chart, the baseline day count.
- `UsageTracker` adds each tick, open and swipe to both and rolls each over on its own `DayClock` (reset hour 4 and 0). A session open across midnight counts one open in the new date, as at 4 AM.
- A separate `StatsStore` port (`load(date)`, `save(stats)`, `firstDay()`), with its own DataStore file, saved on the same schedule as `UsageStore`. Days from before calendar stats existed are shown from `DayUsage`.

## The engine

A pure function of a state, the day's counters and an event: it returns the next state, a list of effects and the counters, and does nothing else. It never calls a port or reads a clock; events carry the time. `GuardRunner` runs `UsageTracker` first (counting is separate from deciding), then the engine, then hands the effects to the `EffectHandler` and publishes `EngineEvent`s. From milestone 3 the engine also receives the day's `DayUsage` (override count, cooldown, break; from milestone 4 also the budget checks) and returns it changed when an override is granted or a session ends; the runner applies that to the tracker's state and saves it.

```kotlin
class GuardEngine(
    launch: LaunchSettings, ladder: LadderSettings, policy: EscalationPolicy, challenges: ChallengeFactory,
    budgets: Budgets, budgetRules: List<BudgetRule>,
) {
    data class Transition(val state: GuardState, val effects: List<Effect>, val usage: DayUsage)
    fun next(state: GuardState, usage: DayUsage, surface: Surface?, event: GuardEvent, now: Instant): Transition  // surface and now from the runner
}
```

Screen effects carry what the screen needs (`ShowPause(seconds)`, `ShowIntention(purposes, minuteChoices)`), so the overlay only draws. `IntentionChosen` carries the time it was chosen, so the session's end comes from the event, not a clock.

```kotlin
sealed interface GuardEvent {
    data class InstagramOpened(val at: Instant) : GuardEvent
    data object InstagramClosed : GuardEvent
    data class SurfaceChanged(val surface: Surface) : GuardEvent
    data class Swiped(val surface: Surface) : GuardEvent
    data class Tick(val at: Instant) : GuardEvent        // once a second while Instagram is in front
    data object PauseFinished : GuardEvent
    data class IntentionChosen(val purpose: String, val minutes: Int, val at: Instant) : GuardEvent
    data class OverrideRequested(val at: Instant) : GuardEvent
    data object LeaveChosen : GuardEvent
    data class ChallengeSubmitted(val answer: ChallengeAnswer, val at: Instant) : GuardEvent  // judged in rules, not by the screen
    data class ChallengeCancelled(val at: Instant) : GuardEvent
}

sealed interface GuardState {
    data object Idle : GuardState
    data object Pause : GuardState
    data object Intention : GuardState
    data class Running(val session: Session) : GuardState
    data class Away(val session: Session) : GuardState  // left mid-session; back before allowedUntil resumes it
    data class Blocked(val reason: BlockReason, val session: Session?) : GuardState
    data class Challenging(val reason: BlockReason, val session: Session?, val run: ChallengeRun, val number: Int, val step: LadderStep) : GuardState
}

sealed interface Effect {
    data class ShowPause(val seconds: Int) : Effect
    data class ShowIntention(val purposes: List<String>, val minuteChoices: List<Int>, val restOfToday: Int?, val minutesLeftToday: Int) : Effect
    data class ShowBlock(val reason: BlockReason, val override: OverrideAvailability) : Effect
    data class ShowChallenge(val prompt: ChallengePrompt) : Effect
    data object Dismiss : Effect
    data object GoBack : Effect                         // Android's Back, once
    data object ShowLeaving : Effect                    // the block says "Going back…"; the cover stays
    data object GoHome : Effect
}

data class Session(val purpose: String, val allowedUntil: Instant, val byOverride: Boolean = false)

sealed interface OverrideAvailability {                 // what the block screen offers
    data class Available(val number: Int, val step: LadderStep) : OverrideAvailability  // the nth override today and its cost
    data class CoolingDown(val until: Instant) : OverrideAvailability
    data object CapReached : OverrideAvailability
}

sealed interface BlockReason {
    data object SessionTimeUp : BlockReason
    data class SurfaceBudget(val surface: Surface) : BlockReason
    data object DailyTotal : BlockReason
}
```

Instagram is usable only in the Running state. Every other state shows an overlay or nothing.

| State | Event | Next state | Effects |
| --- | --- | --- | --- |
| Idle | `InstagramOpened` | Pause; Blocked(SessionTimeUp) during the break after a session (milestone 3); Blocked(DailyTotal) when the day's total is already spent (milestone 5) | `ShowPause`, or `ShowBlock` |
| Pause | `PauseFinished` | Intention | `ShowIntention` |
| Pause, Intention | `InstagramClosed` | Idle | `Dismiss` |
| Intention | `IntentionChosen` | Running, with the minutes capped | `Dismiss` |
| Intention | `LeaveChosen` ("Not now") | Idle | `Dismiss`, `GoHome` |
| Running | `Tick`, `Swiped`, `SurfaceChanged` | Running, or Blocked when a `BudgetRule` says Block or the session time is up. Time up starts the break, and after an override session also the cooldown, both saved in the returned usage | none, or `ShowBlock` |
| Running | `InstagramClosed` | Away, keeping the session | none |
| Away | `InstagramOpened` | Running if the session has time left (real time: being away never adds minutes); otherwise the break (and cooldown) start from when the session ended, then as from Idle | none, or as from Idle |
| Away | `Tick` after the session's end (the status screen's catch-up) | Idle; the break (and cooldown) start from when the session ended | none |
| Blocked | `OverrideRequested` | Challenging, only if a ladder step is left and the cooldown is over; otherwise unchanged | `ShowChallenge` with the first part of the step |
| Running | `SurfaceChanged` to a surface whose limit is spent (not in an override session) | Blocked(SurfaceBudget), keeping the session | `ShowBlock` |
| Blocked | `LeaveChosen` | as the `Intervention` plans: stays Blocked, keeping the session (a surface block: one screen back); or Idle (the other blocks) | `ShowLeaving`, `GoBack`; or `Dismiss`, `GoHome` |
| Blocked | `SurfaceChanged` to a surface the reason `liftsOn` (for a surface block: any other surface) | Running, the block lifts | `Dismiss` |
| Blocked(SurfaceBudget), Challenging from it | `InstagramClosed` | Away, keeping the session | `Dismiss` |
| Challenging | `ChallengeSubmitted`, run in progress | Challenging, the next part of the step (the wait done, now the typing) | `ShowChallenge` |
| Challenging | `ChallengeSubmitted`, typing failed | Challenging, new random text; the finished wait is kept | `ShowChallenge` |
| Challenging | `ChallengeSubmitted`, passed | Running, a new session of the minutes that step grants (`byOverride`); the override count goes up in the returned usage | `Dismiss` |
| Challenging | `ChallengeCancelled` | Blocked, same reason | `ShowBlock` |
| Blocked, Challenging | `InstagramClosed` | Idle; the wait timer and typing progress are lost | `Dismiss` |
| Any | Any event after 4 AM passes | The same transition, after the day's counters reset | none; a `DayReset` engine event is published |

### Milestone 2: launch friction

Agreed 2026-10-29. Milestone 2 builds the Idle, Pause, Intention, Running, Away and Blocked(SessionTimeUp) rows; the ladder rows come in milestone 3, budget blocks in milestone 4.

- Enforcement switch: `enforcementEnabled`, on by default since 2026-10-30 (`AppSettings`; it was off for the baseline week). Counting always runs; with the switch off, `GuardRunner` does not run the engine, so no screen is ever shown. During the baseline week the switch is on only while testing; test minutes count toward the baseline. Since 2026-10-29 it is a saved setting in all builds (`SettingsStore`, see Ports), shown on the status screen, so a restart keeps it; turning it off is instant until milestone 6 makes loosening cost a wait and a typing challenge.
- `LaunchSettings` (pure Kotlin, set in `AppGraph` like `Budgets` until settings can be edited, milestone 6): pause of 5 seconds; purposes "Reply to messages", "Post or share", "Look something up", "Just browsing"; minute choices 5, 10, 15 and 20 (20 added 2026-10-30), so 20 is the most one session can ask for.
- Time's up, before the ladder existed: "Leave" went home; "New session" went through the pause and intention prompt again. Milestone 3 replaced "New session" with the override ladder.
- Coming back mid-session resumes it without a pause; the session ends at `allowedUntil` in real time, wherever you are. Every return still counts as an open.
- `GuardRunner` passes each event to `UsageTracker` and then to `GuardEngine`, and hands the effects to an `EffectHandler`. A dispatcher in `AppGraph` sends screen effects to `ScreenEffectHandler` (overlay) and `GoHome` to `NavigationEffectHandler` (the service's global Home action).
- Overlay: `OverlayController` adds one full-screen window of type `TYPE_ACCESSIBILITY_OVERLAY` through the service (no extra permission), touchable so Instagram underneath can't be used. Screens are Views, inflated with the app's Material theme.
- Time on our screens counts as Instagram time, since Instagram is in front underneath.
- Keyboard spike: while the overlay is built, try a text field in it. The milestone 3 typing challenge depends on the keyboard working there; if it doesn't, the challenge uses a full-screen activity of our own (see Decisions).

### Milestone 3: override ladder

Agreed 2026-10-29.

- Ladder (`LadderSettings`, the SPEC's starting values): 1st override 30 s wait + 1 sentence for 10 min; 2nd 1 min + 2 sentences for 5 min; 3rd 2 min + a paragraph for 3 min; 4th and 5th 5 min + a paragraph at near-perfect accuracy for 2 min; none after the 5th until 4 AM.
- Cooldown: when an override session ends, overrides are locked for 20 minutes plus 10 for each earlier override that day (`CountLadderPolicy.cooldownMinutes`). Saved as `DayUsage.cooldownUntil`.
- Break: when any session ends, reopening Instagram within 15 minutes shows the time's-up screen with the ladder instead of the pause and prompt, so leaving and coming back is no way around the ladder. Saved as a new `DayUsage.breakUntil`, so killing the app doesn't end it. The 4 AM reset clears it, with the override count and cooldown.
- Challenges are judged in `rules`: the overlay reports what happened (`ChallengeSubmitted` with `ChallengeAnswer.WaitDone` or `Typed(text)`) and the `ChallengeRun` decides. The wait screen counts down, but the run checks the time itself, so a submitted wait is accepted only once its seconds have passed since it began.
- Leaving during a challenge (another app, Home, screen off) loses the progress, wait included: Instagram closing returns the engine to Idle.
- Typing: new random text each time, from a list of plain Kotlin sentences; a paragraph is 4 of them. Accuracy is the share of characters right, by edit distance against the target after collapsing runs of spaces; case and punctuation count. At least 95%, or 99% for "near-perfect". A failed attempt gets new text right away and keeps the finished wait; Cancel returns to the time's-up screen.
- Paste is blocked: no long-press menu or text selection, a keyboard mode without suggestions or clipboard (`textVisiblePassword`), and any single change that adds more than 3 characters at once is undone.
- Settings: `LadderSettings`, the break and the budgets stay constants in `AppGraph` until milestone 6 makes them editable (where loosening costs a wait and a typing challenge). The settings store exists already, for the enforcement switch only.
- Debug builds get a second switch, "Short timers for testing" (`LadderSettings.shortForTesting()`, `LaunchSettings.shortForTesting()`): 5 s waits, sessions of 1 or 2 minutes, 1-minute grants, cooldowns and break; the typing stays as it is. Off by default and after a restart.
- New log events: `OverrideGranted(number, minutes)` and `TypingFailed`.
- The session survives restarts (step 7): `Session` lives in `rules.usage` and is saved as `DayUsage.session` when it starts (intention chosen or override passed) and cleared when it ends. When the app starts, `GuardEngine.restore` turns a saved session into `Away`, so reopening resumes it or, if it ran out meanwhile, starts its break and cooldown from its end time. `GuardRunner.catchUp()` (called by the status screen each second) ends a session that ran out while Instagram was closed, so the break and cooldown show without reopening Instagram. The 4 AM reset keeps a session in progress; turning enforcement off forgets it.

### Milestone 5: daily total

Agreed 2026-10-29. Focus windows come later.

- `rules.budget`: `BudgetRule.check(usage, session, now)` answers `BudgetVerdict.Allow` or `Block(reason)`; the engine runs a list of rules, first Block wins. `DailyTotalBudgetRule(budgets)` blocks with `BlockReason.DailyTotal` once `usage.totalMillis` reaches `budgets.dailyTotal`. Milestone 4's `SurfaceBudgetRule` joins the same list.
- When the rules run: on opening (Idle, or Away coming back), before the pause or a resume; and on every `Tick` while Running. An override session (`byOverride`) is exempt: its minutes are the override. When it ends and the total is still spent, the block reason is `DailyTotal`, not `SessionTimeUp`.
- Blocked(DailyTotal): the same ladder as time's up (shared count, cooldown, break). `LeaveChosen` goes home. Reopening while the total is spent shows the block again, whatever the break says.
- The intention prompt: `ShowIntention` also carries `minutesLeftToday`; the minute choices are those that fit what's left, plus a "Rest of today" choice of the remaining minutes (rounded up) when that is below the largest choice. `IntentionChosen` minutes are clamped to the largest standard choice but not to what's left: the daily-total check ends the session when the total runs out (clamping to what's left at choose time once turned "Rest of today (2 min)" into 1 minute on the phone).
- Short timers: the daily total becomes 5 minutes, so the block can be tried on any day.
- `GuardEngine` gets `Budgets` and the rule list from `AppGraph` (constants until milestone 6).

### Milestone 4: Reels enforcement

Agreed 2026-10-29.

- `BudgetRule.check` also gets the surface on screen (null until known), so a per-surface rule can tell whether the user is on it. `SurfaceBudgetRule(budgets)` blocks with `BlockReason.SurfaceBudget(surface)` when the current surface has a limit in `Budgets.perSurface` and today's time on it has reached it. The rule list is `[DailyTotalBudgetRule, SurfaceBudgetRule]`: a spent daily total wins.
- `GuardEngine.next` gets the current surface from the runner (the tracker knows it). Rules run on every `Tick` while Running (as for the daily total) and also on `SurfaceChanged`, so a Reel is covered as soon as it's detected rather than up to a second later. Override sessions stay exempt from all budgets.
- Blocked(SurfaceBudget) keeps the session. Leave is one screen back with Android's Back, the cover kept until the user lands off Reels (see "Refactor for extensibility"). Reaching the limited surface again blocks again. Override from it works as from the other blocks (shared count, cooldown, break).
- History (2026-10-29): Leave first went to the DM tab by tapping Instagram's tabs after Back presses (spiked: 0 to 3 Backs), then to the phone's home screen, then to the Home feed tab. Tapping tabs depends on Instagram's layout and the home screen looped (Instagram reopens on the Reel), so all were replaced by one screen back.
- Swipes: the service sends `Swiped(surface)` when the Reels pager's page changes while the surface is Reels; the tracker counts them in `DayUsage.swipesBySurface`. No swipe limit for now; the status screen shows the count.
- Short timers: the Reels limit becomes 1 minute.
- Built 2026-10-29: the engine takes the surface and the event's time from the runner (`next(state, usage, surface, event, now)`). A session that ends while on Reels is time's up or the daily limit, never a surface block, so Leave can't resume an ended session into a loop. Moving off Reels underneath the block lifts it; closing Instagram on it keeps the session. Leave to messages is logged as `LeftByChoice`, and the block as `SurfaceLimitReached`. The first page of a newly opened Reels viewer isn't a swipe.

### Milestone 6: self-protection

Agreed 2026-10-29. The pure rules come first; the screens follow the UI redesign.

- `AppSettings(enforcementEnabled, dailyTotalMinutes = 90, surfaceMinutes = {REELS: 15})` in the `SettingsStore`; `AppGraph` builds `Budgets` from it (no more constants for the limits) and rebuilds the engine when it changes. Ranges: daily total 5 to 600, each surface 0 to 600 (see "Refactor for extensibility" for the map).
- `SettingsChanger` (pure, `rules.settings`): `request(current, wanted, usage, now)` answers `Apply(wanted)` for a tightening, `Refused(reason)` when loosening isn't allowed now (today's override cap used; focus windows later), or `Challenge(run)` for a loosening: `Wait(600 s)` then `Typing(paragraph, 0.95)`, built by the same `ChallengeFactory` as the ladder. A change that loosens anything counts as loosening. The settings screen drives the run (as the overlay drives the ladder's) and applies `wanted` once it passes; leaving the screen discards the run, so the wait starts over.
- Short timers: the loosening wait becomes 5 seconds.
- Fallback watcher: see "Fallback watcher (2026-10-30)".

### Refactor for extensibility (2026-10-29)

Agreed after the Leave-to-a-tab approach proved fragile. Nothing here changes what the user sees except Leave on a surface block.

- `Intervention` (as planned in Extension points, now built): `onLeave(reason, session): LeavePlan`, where a `LeavePlan` is `EndVisit(effects)` (Idle, the cover goes, then the effects) or `StayInInstagram(effects)` (the cover stays, the session is kept, then the effects). `GoHomeIntervention` ends the visit with `GoHome`. `StepBackIntervention(fallback)` answers `StayInInstagram(GoBack)` for a block that keeps the session (a surface block) and asks `fallback` otherwise. `AppGraph` wires `StepBackIntervention(GoHomeIntervention())`; a different Leave is a different class there, not an engine change.
- Leave on a surface block: one press of Android's Back (`Effect.GoBack`, the system action, no Instagram IDs) with the cover kept ("Going back…", `Effect.ShowLeaving`). The cover lifts on the first `SurfaceChanged` to anything but the blocked surface. If one Back still lands on it (a Reel opened from a Reel), the block stays and Leave steps back again; if Back closes Instagram, that's leaving, with the session kept. The tab-tapping navigation and `GoToDmTab`/`GoToTab` are removed.
- `BlockReason` carries its own behaviour instead of the engine asking `is SurfaceBudget`: `keepsSession` (true for a surface block: the rest of Instagram still works) and `liftsOn(surface)` (a surface block lifts on any other surface; the others never lift by moving).
- Settings: `AppSettings.surfaceMinutes: Map<Surface, Int>` replaces `reelsMinutes` (default `{REELS: 15}`, lowered from 30 on 2026-10-29), so a Feed or Explore limit is a setting, not code. `SettingsChanger` treats raising any surface limit, or removing one, as loosening; adding one tightens. Saved as `surface_minutes` (`REELS=30,FEED=20`); an old `reels_minutes` is read once and carried over.
- `InstagramGuardService` is split into the guard classes in the package table; it keeps only composition.

### Fallback watcher (2026-10-30)

Replaces milestone 6's first sketch. Covers Instagram while the accessibility service is off, and records when tracking had gaps, so a silent failure shows on Today (docs/UI.md, principle 4).

- `rules.health` (pure): `TrackingHealth` folds the service's life into a `HealthRecord(running, lastAliveAt, gaps)`: `started(at)`, `alive(at)` (the heartbeat), `stopped(at)` (`onDestroy`, i.e. turned off). A start after a stop closes the open gap; a start with `running` still true means the process was killed, so it adds a closed gap from `lastAliveAt`, cause `STOPPED_BY_SYSTEM` (gaps under a minute are dropped). Gaps older than 7 days are pruned. `HealthStore` port, `DataStoreHealthStore` (own file, JSON). Counts and timestamps only.
- `rules.health.ForegroundApp` (pure): folds usage events (`RESUMED`/`PAUSED` of a package) into the app in front, so the watcher's logic is unit-tested.
- `guard.FallbackWatcherService`: a foreground service (type `specialUse`) with a low-importance notification, "AntiScroll isn't watching Instagram", that opens Accessibility settings. It runs only while the accessibility service is off and Usage access is granted. While the screen is on it reads `UsageStatsManager.queryEvents` every 1.5 seconds (incremental, from the last read); with the screen off it stops reading. There is no event for "an app came to front" without accessibility, so this is the cheapest way to see it; UI.md's "no polling" holds for everything else. A `ContentObserver` on the enabled-services setting stops it as soon as the service is back.
- When Instagram is in front, the watcher adds a full-screen `TYPE_APPLICATION_OVERLAY` cover (needs Display over other apps; without it, only the notification): "AntiScroll isn't watching", with "Turn Accessibility back on" (opens the settings) and "Close Instagram" (home). With enforcement off (the baseline), a third, quiet button, "Use Instagram uncounted", hides the cover until Instagram leaves the front, so counting-only never blocks. Nothing is counted while the service is off; the gap records it.
- Starting it: `FallbackStarter.sync(context)` starts or stops the watcher from the current state. Called from the service's `onUnbind` (turned off; still bound, so a background start is allowed), from the app's screens on resume, and from a `BootReceiver` (`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`). A start Android refuses is logged, not retried. The service's `onServiceConnected` stops the watcher.
- Permissions: `PACKAGE_USAGE_STATS`, `SYSTEM_ALERT_WINDOW`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`. All optional: Settings' Tracking health lists each.

### UI redesign (2026-10-30)

Built 2026-10-30, trimmed to what's needed (decided with the owner: functionality before UI): the tab shell (Today, Limits, Settings with Tracking health and the debug switch), the edit sheet, `LoosenActivity` and the restyled overlays. Not built, and not to be built until asked: History, first run, the theme setting, "Day starts at", export and erase. Added for the screens and kept: `DayStats.longestVisitMillis` and `millisByHour`, `Session.startedAt`, `GuardEvent.EndSessionRequested` ("End now"), Pause + `LeaveChosen` (Close Instagram on the pause), and the fallback cover's "Use Instagram uncounted" (baseline only). Behaviour changes from the restyle: after the pause, "Open Instagram" is tapped (no longer automatic); after a wait, "Continue" is tapped.

Builds docs/UI.md, with three decisions taken on 2026-10-30: the intention prompt stays after the pause (restyled; the session pill shows for every session), loosening still costs the 10-minute wait and a typing paragraph (not UI.md's "apply at 4 AM"), and the pause stays 5 seconds.

Layers. Views never read `rules` types; a pure presenter in the app layer turns rules state into a UI state, and the view draws it:

| Package | What | Android? |
| --- | --- | --- |
| `present` | `TodayPresenter` → `TodayUiState`, `HistoryPresenter` → `HistoryUiState`, `LimitsPresenter` → `LimitsUiState`, `HealthPresenter` → `HealthUiState`, `OverlayStatus` (numbers the overlay screens show), `Durations` ("1h 47m") | No: plain JUnit tests |
| `ui` | `MainActivity` (bottom bar: Today, Limits, Settings), `TodayScreen`, `LimitsScreen`, `SettingsScreen`, `HistoryActivity`, `FirstRunActivity`, `EditLimitSheet`, `LoosenActivity` (the settings challenge), `ThemePrefs` | Yes |
| `ui.widget` | Custom views, each one `onDraw`, no chart library: `GaugeView`, `StackedBarView`, `DayBarsView`, `HourBarsView`, `RingView`, `SegmentsView`, `DotsView` | Yes |
| `overlay` | `ScreenEffectHandler` restyled; `WaitPanel` and `TypingPanel` bind the wait and typing layouts and are shared with `LoosenActivity`, so both look the same; `SessionPill` | Yes |
| `system` | `SystemChecks`: Accessibility, overlay, notifications, battery and Usage access granted or not | Yes |

Data the design needs that the counters lacked:
- `DayStats` gains `longestVisitMillis` (the longest single open, for "longest stretch") and `millisByHour` (24 local-hour buckets, for "When you scroll"). `UsageTracker` fills both on each tick (it has the zone). Old saved days read as 0.
- `Session` gains `startedAt` (nullable, for old saved sessions), so the session card can show progress.
- `GuardEvent.EndSessionRequested(at)`: Today's "End now". Running or Away: the session ends now (break and, after an override, cooldown from now), Idle. Elsewhere ignored.
- Gaps come from `HealthStore` (above). Today never reads the JSONL log.

Screens, as UI.md says, with these specifics:
- Today: `TodayPresenter.present(...)` takes the calendar stats, the limit window's usage, the past 6 days, the budgets, the guard state, health, the baseline day and now; one `StateFlow<TodayUiState>` in `MainActivity`, rebuilt on resume, every second while a session runs and every minute otherwise; the week is loaded once per resume on a background thread. The three states (baseline, guarding, needs attention) are one screen with sections shown or hidden.
- Limits: the mode card switches enforcement through `SettingsChanger` (on is instant; off is loosening). The edit sheet saves through `SettingsChanger`: `Apply` saves, `Refused` says why, `Challenge` opens `LoosenActivity`, which runs the wait and typing and applies the change when it passes; leaving it discards the run. The ladder table reads `LadderSettings`.
- Settings: Tracking health from `SystemChecks`; the Settings tab shows a coral dot while anything needs fixing. "Day starts at" shows 4:00 AM, not yet editable (changing it mid-day could hand out a fresh day). Theme (system, light, dark) is a UI preference in `ThemePrefs` (SharedPreferences, read before the first frame), not an `AppSettings` field. Export writes each calendar day's stats and then the event log as JSON lines through the system's file picker. Erase all data clears the counters, stats, gaps and log (settings stay); with guarding on, erasing would refill today's allowance, so it costs the same challenge as loosening.
- First run: shown once, when not yet finished and the accessibility service is off (an existing install with the service on skips it).
- Overlays: always dark (a night-mode configuration), the tokens from UI.md. Pause: ring countdown, "Open #N today" and today's time against the limit; "Close Instagram" (Leave) is the big button; "Open Instagram" enables when the pause ends and sends `PauseFinished`, then the restyled intention prompt. A block with an override available uses the pause layout ("Continue anyway", overrides left); without one, the cooldown or cap layout counts down to the cooldown's end (then offers "Continue anyway") or to the day's reset. Wait: ring, "Continue" enables at zero and submits the wait. Typing: the passage itself shows progress (typed in ink, wrong characters coral and underlined, the next one underlined), over a transparent paste-blocked field; live accuracy and "37 of 57"; Continue enables when complete and accurate enough; the engine still judges. The window resizes for the keyboard. Context numbers come from an `OverlayStatus` provider in `AppGraph`, so effects stay decisions only.
- Session pill: a second, untouchable overlay window at the top centre while the engine is Running, updated by `SessionTicker`; coral in the last minute, a short vibration at 60 and 10 seconds; hidden when Instagram closes.
- Fonts: Sora (600, 700, 800) and Inter (400, 500, 600), static TTFs in `res/font`, OFL.

### Sharing with friends (2026-10-30)

See SPEC, "Sharing with friends".
- `DayStats.guarded`: true once any tick that calendar day was counted with enforcement on. `GuardRunner` sets it (it knows the switch), so the export can tell baseline days from guarding days.
- `rules.export.ExportReminders` (pure): `due(firstDay, firstGuardedDay, today, shown)` answers which reminder is due, if any: `BASELINE_DONE` from `firstDay + 7`, `GUARDING_TWO_WEEKS` from `firstGuardedDay + 14`. `guard.Heartbeat` asks at most once per calendar day and posts the notification (`ui.Reminders`); shown kinds are kept in SharedPreferences.
- `data.HistoryExport` builds the JSON (org.json) from the stores: the dates from the first day to today, each from `StatsStore` (or `DayStats.from` the usage store for older dates), with that day's overrides from `UsageStore`. `ui.ExportSharer` writes it to the cache and shares it through a `FileProvider`. Instagram's version needs a `<queries>` entry for its package.
- `ui.DisclosureActivity`: every in-app path to Accessibility settings goes through it; it opens by itself once on first launch while the service is off.
- Backup folder (2026-10-30): `data.BackupFile` encodes and decodes the whole restorable state (`AppSettings`, every `DayUsage` and `DayStats`, the `HealthRecord`) as one JSON file, reusing the existing JSON codecs. `ui.BackupFolder` keeps the folder the user picked (the picker opens in Documents; unless the picked folder is called AntiScroll, an `AntiScroll` folder is made inside it, so nobody types a name) (a persisted Storage Access Framework tree URI, in SharedPreferences) and writes `antiscroll-backup.json` there: when the folder is picked, then at most every 10 minutes when Instagram closes, the screen turns off, the app opens or the heartbeat beats (the heartbeat alone pauses while the phone sleeps). The cloud sync is asked at the same moments, at most every 5 minutes. Restore merges, so time counted since a reinstall isn't lost: `DayUsage.plus` and `DayStats.plus` (pure, `rules.usage`) add opens, time and swipes, keep the larger override count, longest visit and later cooldown, break and session, and OR `guarded`. Days only in the backup are saved as they are. Today's merged counters go into the running `GuardRunner` (`replaceToday`), the settings through `AppGraph.applySettings` (a restore isn't a loosening: it brings back what the user had), the health record through `HealthRecorder.replace`.
- Release build: signed with a keystore kept outside the project (`~/.antiscroll/`), read from `keystore.properties` when present; R8 on. Debug builds use the app ID `com.abhishek.antiscroll.debug` (from 2026-10-30), so they install next to the release app; turn on only one of the two Accessibility services at a time, or Instagram is counted twice. The owner moved to the release build on 2026-10-30, starting a fresh baseline with the friends.

### Cloud copy (2026-10-30)

See SPEC, "Cloud copy for the developer".
- Port `rules.sync.CloudStore`: `signIn(done)`, `sendInstall(fields, done)`, `sendDay(date, fields, done)`; plain maps, so `rules` never sees Firebase. `data.FirestoreCloudStore` implements it with Firebase Auth (anonymous) and Firestore: `installs/{uid}` and `installs/{uid}/days/{yyyy-mm-dd}`, each written whole with `set`.
- `rules.sync.SyncPlanner` (pure): which dates to send, given the days available and a fingerprint of what was last sent for each; today is always sent while it changes. `data.CloudDocuments` (pure) turns `DayStats`, the day's `DayUsage` and the install's facts into the field maps, the same fields as the export file.
- `ui.CloudSync` runs it: at once when Instagram closes or the screen turns off (`syncOnLeave`), and at most every 5 minutes when the app opens or from the heartbeat (`syncIfDue`); each run logs what it sent or why it skipped, on a background thread; the fingerprints of sent days are kept in SharedPreferences. Firestore's offline cache holds unsent writes.
- Firebase is set up without the Google Services Gradle plugin (the AGP 9 rule): the build reads `app/google-services.json` if present into `BuildConfig` fields, and `FirebaseApp.initializeApp` gets them in code, lazily on the first sync, not in `Application.onCreate`. Without the file, sync does nothing.
- Settings history: `AppGraph.applySettings(new, source)` publishes `EngineEvent.SettingsChanged(at, from, to, source)` (`ChangeSource`: USER, RESTORE, DEBUG) and calls `CloudSync.settingsChanged`, which queues the change in SharedPreferences, sends it to `installs/{uid}/settingsChanges/{epoch millis}` (`CloudStore.sendSettingsChange`; the ID makes a resend overwrite) and re-sends the install document. Anything still queued is sent whenever a sync is asked for (Instagram closing, screen off, the app opening, the heartbeat), without waiting for the 30-minute cycle.
- Google sign-in (2026-10-30): `CloudStore` gains `signInWithGoogle(idToken, done: (SignInResult) -> Unit)` and `fetchBackup(done: (String?) -> Unit)`. `FirestoreCloudStore` links the anonymous user to the Google credential (`linkWithCredential`: same UID, `LINKED`); if that Google account already belongs to another UID (`credential-already-in-use`), it signs in with it instead (`SWITCHED`). The install document carries a `backup` field: the same `BackupFile` JSON the folder holds, sent with each sync, so a cloud restore is the folder restore (`BackupFolder.mergePast` and `finish`, unchanged). On `SWITCHED`, `ui.AccountSignIn` (app-level scope, so closing the sign-in screen can't cut it short) fetches that backup, merges it in unless this install wrote it (`installId`), and syncs at once. Until that restore succeeds (or finds nothing), a `restore_pending` flag stops every upload, so a fresh install can't overwrite the account's stored backup; the app retries the restore each time it opens. Nothing syncs before the sign-in screen is answered (signed in, "Not now" or Back: `AccountSignIn.decided`), so a fresh install doesn't upload anonymously behind the screen and leave a stray install when the user then signs in. The Google ID token comes from Credential Manager (`androidx.credentials` with `googleid`, the web client ID read from `google-services.json` into `BuildConfig` like the other Firebase fields). Release builds only: Google sign-in is tied to the release key's fingerprint.
- `firestore.rules` (project root): a signed-in install may write `installs/{its uid}`, its `days` and its `settingsChanges`, and read only its own install document (for the restore).

## Extension points

Six small interfaces cover everything that will vary. Each already has a second implementation on the roadmap.

| Interface | Question it answers | In the MVP | Slots in later | Pattern |
| --- | --- | --- | --- | --- |
| `AppProfile` | Which app, and which surface is on screen? | `InstagramProfile` | YouTube Shorts, TikTok | Strategy, registry keyed by package |
| `BudgetRule` | Should this activity be blocked? | `SurfaceBudgetRule`, `DailyTotalBudgetRule` | `FocusWindowRule` | Chain of Responsibility, first Block wins |
| `EscalationPolicy` | What does the nth override cost and grant? | `CountLadderPolicy` | `TimeOfDayPolicy` wrapping another policy, gradual decay | Strategy, Decorator |
| `UnlockChallenge` | How does the user earn an override? | `WaitChallenge`, `TypingChallenge`, `SequenceChallenge` | NFC tag, steps, partner approval, money stake | Strategy, Composite, Factory |
| `Intervention` | What does Leave do on a block? | `StepBackIntervention` (surface blocks: one screen back) wrapping `GoHomeIntervention` (the rest) | Grayscale, slow-down, redirect suggestions | Strategy, Decorator |
| `EventSink` | Who needs to know what the engine did? | `JsonlEventLog` | Reflection summary, AI check-in | Observer |

```kotlin
interface AppProfile {
    val packageName: String
    fun surfaceOf(root: ScreenNode, tabPage: Int? = null): Surface   // tabPage: last tab-pager page seen, if any
}

interface ScreenNode {                                 // no text property on purpose: screen text never enters rules
    val viewId: String?
    val contentDescription: String?
    val isSelected: Boolean
    val children: List<ScreenNode>
}

sealed interface BudgetVerdict {
    data object Allow : BudgetVerdict
    data class Block(val reason: BlockReason) : BudgetVerdict
}

interface BudgetRule {
    fun check(usage: DayUsage, surface: Surface?, session: Session?, now: Instant): BudgetVerdict  // surface: what's on screen, if known
}

interface EscalationPolicy {
    fun stepFor(overridesToday: Int): LadderStep?   // null once the daily cap is reached
    fun cooldownMinutes(overridesToday: Int): Int
}

interface UnlockChallenge {
    fun begin(now: Instant): ChallengeRun
}

interface ChallengeRun {                            // immutable: submit returns the next run, so the engine stays pure
    val prompt: ChallengePrompt                     // what the overlay should show
    fun submit(answer: ChallengeAnswer, now: Instant): ChallengeStep
}

data class ChallengeStep(val run: ChallengeRun, val status: ChallengeStatus)

sealed interface ChallengePrompt {
    data class Wait(val seconds: Int) : ChallengePrompt
    data class Typing(val text: String, val minAccuracy: Double) : ChallengePrompt
}

sealed interface ChallengeAnswer {
    data object WaitDone : ChallengeAnswer
    data class Typed(val text: String) : ChallengeAnswer
}

sealed interface ChallengeStatus {
    data object InProgress : ChallengeStatus        // a later part follows, or a failed typing attempt gets new text
    data object Passed : ChallengeStatus
}

interface Intervention {
    fun onLeave(reason: BlockReason, session: Session?): LeavePlan
}

sealed interface LeavePlan {
    data class EndVisit(val effects: List<Effect>) : LeavePlan          // Idle; the cover goes, then the effects
    data class StayInInstagram(val effects: List<Effect>) : LeavePlan   // the cover stays, the session is kept
}

interface EventSink {
    fun onEvent(event: EngineEvent)
}

sealed interface EngineEvent {                         // counts, timestamps and our own labels, never Instagram's screen text
    val at: Instant
    data class Opened(override val at: Instant) : EngineEvent
    data class Closed(override val at: Instant) : EngineEvent
    data class SurfaceEntered(override val at: Instant, val surface: Surface) : EngineEvent
    data class DayReset(override val at: Instant, val newDay: LocalDate) : EngineEvent
    data class SessionStarted(override val at: Instant, val purpose: String, val minutes: Int) : EngineEvent
    data class SessionTimeUp(override val at: Instant) : EngineEvent
    data class DailyLimitReached(override val at: Instant) : EngineEvent   // when the total blocks a session
    data class SurfaceLimitReached(override val at: Instant, val surface: Surface) : EngineEvent  // when a surface limit blocks
    data class LeftByChoice(override val at: Instant) : EngineEvent    // "Not now" or "Leave"
    data class OverrideGranted(override val at: Instant, val number: Int, val minutes: Int) : EngineEvent
    data class TypingFailed(override val at: Instant) : EngineEvent
    data class Heartbeat(override val at: Instant) : EngineEvent       // every 5 min while the service lives
    data class ServiceStarted(override val at: Instant) : EngineEvent
    data class ServiceStopped(override val at: Instant) : EngineEvent
}
```

Later milestones add cases (blocks, overrides, challenge results). `Heartbeat`, `ServiceStarted` and `ServiceStopped` come from the service through `GuardRunner`, not from the engine; gaps between heartbeats show when the OS killed the service.

- A `SequenceChallenge` holds a list of challenges and behaves like one, so adding NFC to a step is one more entry in the list.
- A ladder step in `LadderSettings` is a list of challenge specs plus the minutes granted. `ChallengeFactory` turns the specs into challenges. The spec's table shows the starting configuration, not a fixed shape.
- Behaviors are open interfaces; events, states, effects and prompts are `sealed`, so the compiler flags every place that must handle a new one.

## Ports

Interfaces the engine depends on but never implements.

| Port | What it does | Implemented by | Test double |
| --- | --- | --- | --- |
| `Clock` | Current time and time zone | Thin wrapper over the system clock, created in `AppGraph` | `FakeClock` you can move forward |
| `UsageStore` | Reads and writes the day's counters: time per surface, opens, swipes, overrides used, cooldown end | `data` (DataStore) | In-memory map |
| `SettingsStore` | Loads and saves `AppSettings`. Now only the enforcement switch; `LadderSettings`, budgets and the reset hour join it in milestone 6, when they become editable (until then they are constants in `AppGraph`) | `DataStoreSettingsStore` (its own DataStore file) | Fixed settings |
| `EffectHandler` | Carries out an `Effect` | `overlay` for screens, `guard` for navigation; a dispatcher in `AppGraph` routes each effect | Records the effects it received |
| `ScreenNode` | Read-only view of Instagram's on-screen tree | `guard`, wrapping `AccessibilityNodeInfo` | Hand-built fake screens |

`UsageStore` and `SettingsStore` are separate on purpose, so budget checks never get write access to settings.

`GuardRunner` keeps `DayUsage` in memory and saves it through `UsageStore` every 30 seconds, and at once on `InstagramClosed`, screen off, a day reset and service destroy. At most 30 seconds are lost if the process is killed. Ticks are never written one by one.

## One session end to end

1. Instagram opens. `InstagramGuardService` sends `InstagramOpened`. The engine moves Idle to Pause and returns `ShowPause`.
2. `ScreenEffectHandler` draws the pause screen. When the delay ends the overlay sends `PauseFinished`, and the engine returns `ShowIntention`.
3. The user picks a purpose and minutes. `IntentionChosen` moves the engine to Running and dismisses the overlay.
4. While scrolling, the service sends `SurfaceChanged` and `Swiped`, and a one-second `Tick` keeps time. On every event the engine asks the `BudgetRule` chain whether this is still allowed.
5. A rule says Block, or the session time is up. The engine moves to Blocked and returns `ShowBlock`, saying whether an override is available (and its cost), cooling down, or used up for the day.
6. The user presses override. `ChallengeFactory` builds the challenges for the current step. The engine moves to Challenging and returns `ShowChallenge`.
7. The user passes: the run accepts the last `ChallengeSubmitted`. The engine returns to Running with the minutes that step grants, and a usage with the new override count, which `GuardRunner` saves through `UsageStore`.
8. Or the user leaves. `LeaveChosen` hands control to the `Intervention`: one screen back (Android's Back) from a Reels block, keeping the session; home from the others.
9. Every step also publishes an `EngineEvent` to the `EventSink`s.

## SOLID and patterns

| Principle | How it shows up |
| --- | --- |
| Single responsibility | `InstagramProfile` knows only Instagram's screens, `CountLadderPolicy` only the cost schedule, `OverlayController` only windows |
| Open/closed | A new app, rule, challenge, penalty or log is a new class registered in `AppGraph`; `GuardEngine` does not change |
| Liskov substitution | Any `UnlockChallenge` can replace another, including a `SequenceChallenge`; the engine reads only `ChallengeStatus` |
| Interface segregation | Five small ports, and `UsageStore` is separate from `SettingsStore` |
| Dependency inversion | `rules` defines every interface; `guard`, `overlay` and `data` implement them; `AppGraph` connects them |

| Pattern | Where |
| --- | --- |
| Strategy | `AppProfile`, `EscalationPolicy`, `UnlockChallenge`, `Intervention` |
| State | `GuardState` and the engine's transitions |
| Command | `Effect` objects, run by an `EffectHandler` |
| Chain of Responsibility | The `BudgetRule` list, first Block wins |
| Composite | `SequenceChallenge` |
| Decorator | `TimeOfDayPolicy` wrapping another `EscalationPolicy`, later |
| Factory | `ChallengeFactory` and the `AppProfile` registry |
| Observer | `EventSink` |
| Adapter | `AccessibilityScreenNode`, event mapping in `InstagramGuardService` |
| Repository | `UsageStore`, `SettingsStore` |

Deliberately not used: Singleton (the `AppGraph` replaces it), a DI library (extra build plugins that could clash with the new Gradle and Android plugin; hand wiring is enough), abstract factory, visitor, plugin frameworks. Add an interface only when a second implementation is already planned.

## Testing

- `rules`: plain JUnit with `FakeClock`, in-memory stores, fake `ScreenNode` trees, `InstagramRules` built in code (not parsed: `org.json` is a stub in plain JUnit) and a recording `EffectHandler`. Each row of the transition table gets at least one test; the day boundary, cooldown, daily cap and `UsageTracker` each get their own. Run with `./gradlew test`.
- `guard`: thin adapters. The debug node dump in Logcat confirms what the service sees in the real Instagram app.
- `overlay` and `data`: checked by hand on the phone first, instrumented tests for the stores later.
- Riskiest assumptions, tested on the phone first: what Instagram's accessibility events and view IDs look like, and whether the typing keyboard works in an overlay.

## Decisions

- Overlay window type: use the accessibility overlay (no extra permission). The keyboard works in it: tested on the phone on 2026-10-29 (milestone 2, step 4), visible and typing into a focusable overlay window. So the milestone 3 typing challenge draws in the overlay too; the full-screen activity fallback is not needed.
- Storage: DataStore plus a JSONL log file now. Move to Room later only if reflection needs real queries.
- Typing-challenge text: plain Kotlin sentences at first. Add a `TextSource` port only when editable text is wanted.
- Ladder step: a list of challenge specs plus a grant.
- Surface detection lives in `InstagramProfile`, not in `guard`.
- The service hears window changes from every package to detect leaving Instagram, but reads content only in Instagram (see Foreground detection).
- The engine is pure and returns the updated `DayUsage`; `GuardRunner` persists it, batched every 30 seconds.
- `instagram_rules.json` is parsed in `data` with `org.json` into the plain `InstagramRules` class; no kotlinx-serialization plugin.
- Battery-optimization exemption and the heartbeat log are in milestone 1, so a killed service cannot spoil the baseline week.
