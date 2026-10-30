# AntiScroll UI redesign

Visual reference: the "AntiScroll UI redesign" design canvas (15 screens, clickable): https://claude.ai/artifact/KPJCxjy8MvsDobejCSS6SD
This file is the written spec. Where they disagree, ask before building. Implementation stays Android Views + Material 3 (see LLD.md), not Compose. UI never imports from `rules` internals; it renders UI state produced by the app layer.

## 1. Principles
1. Glanceable: Today answers "am I OK right now?" in one number.
2. Color is status, never category. Coral = over a limit / stop / the logo slash. Nothing else is coral.
3. Calm tone. No red alarms, no streak guilt, no shaming copy.
4. Silent failure is the worst bug: tracking health is surfaced on Today, not only in Settings.
5. Friction is asymmetric: closing Instagram is the big, easy button; continuing is smaller and slower.
6. Dark first (AMOLED, night use). Light theme follows system via the same tokens.

## 2. Tokens (dark)
| Token | Hex | Use |
|---|---|---|
| bg | #0D1020 | screen background, overlay background |
| surface | #161A2D | cards, nav bar |
| surface2 | #1F2440 | inset cards, tracks, selected pill |
| line | #2B3152 | dividers, outlines |
| ink | #EEF1F8 | primary text, primary button fill, under-limit bar fill |
| ink2 | #B4BAD0 | secondary text |
| ink3 | #8E96B3 | tertiary text and axis labels (min 12sp) |
| coral | #FF6B4A | over limit, errors, Fix actions. Tint: coral at 14% alpha |
| bar-past | #8A92B0 | past-day bars |
| dashed | #5A6290 | limit line |

Section colors (graphics only, always paired with a text label and value): Home feed #8C9BFF, Reels #C58BFF, Messages #5CC8FF, Stories #F2C75C, Explore #6FDCB5, Other #7A8199.
All text pairs above pass 4.5:1 on bg and surface (checked). Do not use coral text on coral fills; use bg-colored text on coral (6.7:1).

Type: Sora (display, numbers, titles: 500/600/700/800) and Inter (body, labels: 400/500/600). Bundle both as static fonts, only the weights used. Big number 56sp/800, screen title 24-28sp/700, card title 16sp/600, body 14-16sp, caption 12-13sp. Radii: cards 24dp, inset cards 16-20dp, pills and buttons fully rounded. Screen padding 16dp (overlays 24dp). Min touch target 44dp (primary buttons 52-60dp).

## 3. Navigation
Bottom bar, 3 tabs: Today, Limits, Settings (84dp, active tab has a 64x32dp pill). History is a drill-in from Today ("History >"), with a back arrow. Settings tab shows a coral dot while any tracking-health item needs fixing. First-run is a separate 3-step flow, shown once.

## 4. Screens
### Today (one screen, three states driven by TodayUiState)
Common top: logo mark (40dp) + title "Today" + date, and a status chip: "Counting only" (outlined), "Guarding" (tinted, shield icon) or "Not watching" (coral outline, warning icon). Chip taps to Limits (or Settings when not watching).
1. Baseline: slim "Baseline week, day N of 7" card with 7 segments and one line of copy. Hero card, section breakdown, 3 stat cards (opens today, average open = total / opens, longest stretch), 7-day card with the limit line and ghost stubs for future days.
2. Guarding: replaces the baseline card with a "Session running" card when a session is active (time left, "override N of 5", progress bar, End now button). Third stat becomes "overrides used, N of 5". Week card shows the last 7 days, today highlighted.
3. Needs attention: coral-bordered alert card at the top (role=alert) with a primary "Turn Accessibility back on" button and a secondary battery link. Hero shows the hatched unknown part of the bar and the line "Today's total may be low". A "Gaps in tracking" card lists periods the service was off.
Hero card: label, big total, "of 1h 30m daily limit", pill ("17 min over" coral tint, or "32 min left" neutral), then a linear gauge. Gauge scale is limit / 0.75 so the limit tick always sits at 75%; ink fill up to the limit, coral beyond it. Reels row below, same encoding with its own limit. Never use a ring.
Breakdown: one stacked bar (2dp gaps, min segment 4dp) plus a list sorted by time, descending, each row 44dp: dot, name, minutes, percent. Hide zero rows.

### History (drill-in)
7 / 30 day toggle. Daily average with delta against the baseline-week average. Minutes-per-day bars with limit line (value above each bar, coral overage). "When you scroll": 24 hourly bars starting at the day-start hour, with hours at or after 9 PM drawn in ink and the rest dimmed, plus one insight line (share after 9 PM, peak hour). Reels share: baseline versus last 7 days. No streaks. "Days under limit" is a plain count.

### Limits
- Mode card: "Counting only" (baseline) or "Guarding is on". Counting-only card has "Start guarding early". Guarding card has "Turn off" (see rules below).
- Daily limits: two rows (All of Instagram, Reels), each with the baseline-day value as a sub line, tapping opens the edit sheet.
- Override ladder: read-only table of the five rungs (wait, you get), plus daily cap, cooldown, typing accuracy. Values come from LadderSettings. "Edit" opens a later ladder editor (not designed yet).
- Edit sheet: stepper in 5 min steps, presets (45m, 1h, 1h 30m, 2h), context line with the user's real day, Save / Cancel. Min 5 min, max 6 h.
- Rule (decided 2026-10-30): once guarding is on, lowering a limit applies immediately. Raising a limit, removing one or turning guarding off costs a 10-minute wait and a typing paragraph (95%), then applies; not allowed once today's overrides are used up. Show this in the sheet and as a note on Limits. (The proposed "applies at 4 AM" rule was not taken.)
- Day 8: replace "Counting only" mode card with a suggestion card based on the baseline median (not designed yet).

### Settings
Tracking health card first: Accessibility service, Display over other apps, Notifications, Battery (needs "Unrestricted"), Usage access (optional backup). Each row: state (On with check, Fix in coral, or Set up). Header chip counts items to fix. Then General (Day starts at, Theme: match system / light / dark), Your data (privacy statement, Export history as JSON lines, Erase all data in coral with a confirm dialog), Developer (debug builds only: Short timers, off after every restart, version line).

### First run (3 steps, "N of 3")
1. Welcome: "Notice the scroll. Break the loop." and three promises (week one only counts, limits that push back, private by design).
2. Accessibility disclosure (Play policy requires this before sending the user to the system settings): what it uses the permission for, what it never does, the Android warning note. Buttons: continue to Android settings, Not now.
3. Ready: baseline week starts now, checklist of granted permissions, link to Tracking health. Overlay and battery steps follow the same disclosure pattern when added.

## 5. Inside Instagram (overlays)
Full-screen, opaque bg #0D1020, 24dp padding, AntiScroll mark top-left. Flow: Pause, then the intention prompt (kept, decided 2026-10-30: purpose and 5/10/15 minutes, in this style), Session pill; past a limit, (override) Wait, Typing challenge, Session pill, then Cooldown or Cap.
1. Pause (every open while guarding, 5 s, decided 2026-10-30): ring countdown, "Open #N today", "1h 47m so far, 17 min over your limit" (under limit: "58m so far, 32 min left today"). Primary 60dp "Close Instagram". Secondary outlined "Continue anyway" with "N overrides left today", disabled until the pause ends.
2. Wait (override N of 5): countdown ring with time left of total, "Waiting before you continue", primary "Never mind, close Instagram", secondary "Continue" (disabled until done) with "Next override waits X".
3. Type to continue: random passage in 22sp, typed text in ink, wrong characters coral AND underlined, current character highlighted with a coral underline, remaining text in ink3. Live accuracy chip with the required threshold, progress "37 of 57". Paste disabled. Continue button enabled only when complete and accuracy meets the threshold. Layout must survive the keyboard (adjustResize, text card stays visible).
4. Session pill: small pill at top centre of the screen over Instagram, timer icon plus m:ss. Last 60 s: coral fill with bg-colored text. Haptic at 60 s and 10 s. Time's up goes to Cooldown. Pill is a status display, not draggable.
5. Cooldown or cap: "Time's up" with cooldown timer and next-override time, or "No overrides left today" with time until the day resets. Override dots (filled = used). Only action is "Close Instagram".
If the keyboard-in-overlay prototype fails, the same typing screen runs as a full-screen activity. The visual design does not change.

## 6. Copy rules
Plain, short, no exclamation marks, no shame. "17 min over", not "limit exceeded". Numbers use "1h 47m". Say what happened and what to do next.

## 7. Performance rules
- Overlays: one flat opaque layer, vector drawables only, no blur, no gradients, no shadows, no Lottie. Show within 150 ms of the window event: pre-inflate and reuse one view. Countdown text updates once per second via one shared ticker that runs only while an overlay is visible. Ring drawn with a single arc in onDraw. Fades 150-200 ms max, none if animations are disabled.
- Detection: only handle events from com.instagram.android; classify the surface at most twice a second; cheap checks first (selected tab node) before any tree walk; always recycle nodes.
- Today: collects one StateFlow of TodayUiState built from incremental per-minute buckets. Never scan the JSONL log on screen. Show the last snapshot on first frame (no spinners, local data). Refresh on resume and once a minute while visible; stop collecting when stopped.
- Charts are custom Views (bars, stacked bar, gauge). No chart library. Lists are small and fixed, no RecyclerView needed except History if it grows.
- Cold start under 500 ms: no work in Application.onCreate beyond AppGraph wiring.
- Battery: no polling. The usage-access fallback runs only while Instagram is in the foreground and Accessibility is unavailable.

## 8. Accessibility
Touch targets at least 44dp. Every gauge, stacked bar and dot row has a spoken equivalent (example: "1 hour 47 minutes of 1 hour 30, 17 minutes over"). Status is never color alone (text, underline, icon). Layouts must hold at 200% font scale (cards grow, rows wrap). Respect reduced motion. Timer views are announced politely, not every second (announce at milestones).

## 9. Not designed yet / open
Ladder editor, day-8 suggested limits, light theme artboards, home screen widget, empty and loading states for History, error states for export/erase, 11 PM stricter ladder (deferred), uninstall protection (deferred), optional "why are you opening this?" intent chips on Pause (parked).
