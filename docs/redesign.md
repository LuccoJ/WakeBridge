# Redesign: decisions

Agreed on the mockup canvas "WakeBridge redesign mockups" (November 2026). This is the
reference for the next version of the app; the canvas also holds rejected variants.

## Layout

- Three panes in a bottom navigation bar: **Day**, **Night**, **Settings**. Settings has its
  own icon (sliders), distinct from the Day sun and the Night moon.
- Day and Night keep the header with the watch connection chip and the status card
  (armed / not armed, details).
- A **vertical timeline** runs down one side of the Day and Night panes:
  - noon at the top, noon at the bottom, so the night never wraps around midnight;
  - one rounded track with a vertical gradient: day fades into night around the night's start
    (dusk) and back around its end (dawn), each blend about two hours wide;
  - hour numbers (12, 18, 00, 06, 12) inside the track, centred, colour adapting to what's
    under them;
  - two draggable thresholds: night start and night end. These divide the day for everything,
    naps included. No text or explanations on the timeline;
  - a "now" line in the accent colour with a dot on its inner end;
  - the selected pane's sleep drawn to scale, centred in its window (a nap in the day,
    the night's sleep in the night);
  - side chosen in Settings > Appearance > Timeline side (default: right), since left and
    right are to be compared in use;
  - a custom view; tapping a threshold should also offer a time picker (accessibility,
    precision).
- Day and Night mirror each other:
  - mode: "Next nap" / "Every nap" (every nap = automatic naps) and "Tonight" / "Every night";
  - sleep amount slider, 5-minute steps; naps 5 min to 3 h; the night's maximum is the night
    window's length. A night window shorter than the sleep amount is impossible: narrowing
    the window shrinks the sleep amount with it;
  - **Wake me**, three choices:
    - after N of actual sleep (pauses during awakenings),
    - N after first falling asleep (ignores awakenings),
    - Day: N from now (plain countdown); Night: at the night's end (ordinary alarm).
  - Night only: "Include today's naps".
- A big round button centred in the free space of the pane:
  - idle: hollow, the page background in the middle, a soft halo of the accent fading
    outward; icon and label in the deeper cyan ("Start nap", Night: "Arm tonight");
  - running: filled deeper cyan, "Stop nap", rings pulsing slowly ("breathing"); the
    animation stops when the screen is off;
  - Night in "Every night" mode: the same circle as a pulsing indicator from night start.

## Night logic

- The night's end is a hard "never later than", not the expected alarm time.
- Before any sleep is detected, the watch holds an alarm at the night's end (the fail-safe
  for nights). Never an alarm in the middle of the night just because no event arrived.
- Fell asleep: alarm at fall-asleep time + remaining sleep, capped at the night's end.
- Awake in the night: the time awake pushes the alarm later (e.g. 30 min tossing and turning
  = 30 min later), capped at the night's end. The Amazfit Bip reports such awakenings.
- A missed event can only cost sleep, never leave you without an alarm.

## Alarms and Settings

- **Fail-safe alarm** (was "Alarm right away"): "Keep an alarm on the watch even before sleep
  is detected. Naps: after the nap's length. Nights: at the night's end."
- **Phone backup alarm**: "Ring the phone if the watch doesn't report you waking up a few
  minutes after its alarm", with a separate Backup delay slider. Full-screen alarm with Stop
  and Snooze.
- Cancel alarms that never rang: after 12 h by default.
- Watch disconnected: only the third "Wake me" option can start (it rings on the phone).
- Quick Settings tile: one tap starts the Day pane's nap; tap during a session cancels it with
  a few seconds of "Tap to undo" (no dialogs: they're slow and collapse the panel).

## Look

- Accent: pastel azzurro. Filled elements #9EDDEE with dark teal text #00363F; selected
  containers #CDEFF8; things that must contrast with white (switches, slider fills, values,
  headings, "now" line) #1F7A91.
- Night: deep petrol #173A4F, light tones #BCDDE8 / #E3F4F8.
- Day on the timeline: pale sky, #BFE9F3 when it's the focus, #EAF6F9 otherwise. No yellow.
- On Android 12+ the app may follow the wallpaper colours, with this palette as the fallback.

## Wording

- Waiting status: "Not asleep yet, alarm 14:20".
- Italian: gendered words use Android 14's grammatical inflection (values-it-feminine,
  values-it-masculine) with a neutral fallback using a slash: "Ancora sveglio/a".
  Never "sveglio, sveglia" or "sveglia" twice: say "suona alle 14:20".

## Round 2: feedback on 0.3.0 (to do)

Status: done in 0.3.1, except "Nothing should move when tapped", which waits for a choice
between the interactive mockups "Stable layout A" (round button anchored at the bottom) and
"Stable layout B" (round button at the top, status under it).

### Timeline
- Default side: **left** (keep the Settings option).
- The thresholds can't be dragged so far that the window gets shorter than the sleep amount
  (no more silently shrinking the sleep). Trying shows a toast: "Decrease the sleep amount to
  shorten the night". Conversely, trying to slide the sleep amount past the window shows
  "Widen the night window to sleep longer".
- Hour labels follow the system 12/24-hour setting. In 12-hour mode, AM/PM goes on a separate
  line under the number, to keep the track narrow.
- More anchors: every 3 hours (12, 15, 18, 21, 00, 03, 06, 09, 12).
- While dragging a threshold, a tooltip beside the finger shows the time it's being moved to
  (the Day tab otherwise never shows the night's times).

### Nothing should move when tapped
- Switching "Next nap" / "Every nap" adds lines to the status card, pushing everything down,
  including the buttons just tapped.
- The round button sits at a different height in Day and Night.
- "Arm tonight" adds a status line, the view no longer fits, and the button moves down.
→ Fixed-height status card and a fixed position for the round button, the same in both tabs.
  Mockups to decide where the varying text goes.

### Names and navigation
- Rename the tabs "Day naps" / "Night sleep" (also ties in with the tile). If too long for the
  bottom bar, "Daytime" / "Nighttime".
- Long-pressing the tile opens the Day or Night tab, whichever applies (a running night: Night;
  otherwise Day), never the last tab used (it came back on Settings). Starting sleep from there
  must be one obvious tap: the round button.

### Calmer updates
- While dragging the sleep slider, only the big number next to it changes; the "Wake me"
  options update when the slider is released.
- "Armed for tonight from 22:45" → "Armed for tonight": the window is shown right below.
  (No reason to repeat it.)

### Round button
- Running: a pulsing gradient moving **inward**, the mirror image of the idle halo (which fades
  outward), so the two states look like one design.

### Status card text (agreed)
- Fixed height (two lines, sized in sp so it follows the system font size, never the content);
  each line single-line, cut off with "…"; the title auto-sizes from 20 down to about 16 sp first.
- The title states only the state, in two or three words; the second line carries the numbers,
  alarm time first:
  - "Not asleep yet" / "Alarm 14:20, nap of 20 min"
  - "Asleep" / "Alarm 14:25, slept 12 of 20 min"
  - "Armed for tonight" / "Night 22:45 to 08:00, 7 h 30 min"
  - Italian: "Ancora sveglio/a" / "Suona alle 14:20, pisolino di 20 min", "Sonno in corso" /
    "Suona alle 14:25, dormito 12 min su 20", "Attiva per stanotte" / "Notte dalle 22:45 alle
    08:00, 7 h 30 min".
- The one-line caption under the Once/Every toggle explains the selected mode (agreed).
- Connection chip: Bluetooth icon + watch name; green when connected, red with a crossed-out
  Bluetooth icon when not; "connected" only in the accessibility label.
- Round button (agreed): the darkest ring stays at the same radius in both states. Disarmed: glow
  fading outward from it. Armed: glow fading inward from it, its inner edge slowly breathing
  deeper and back; nothing scales.

### Pane layout (round 2 decision)
Mockup canvas: "WakeBridge redesign mockups" (claude.ai artifact F9mU4TVnatMgD7FwGxk2tv);
copies of the final candidates and their generator are in docs/mockups/.
B (round button at the top) is ruled out. **A is preferred, C stays an option** (possibly both
as a Settings choice, mindful of option creep):
- **A**: status card on top; Once/Every toggle + caption; sleep amount; Wake me; extra row
  (Night: Include today's naps, Day: Napped today); the round button centred in the space left
  below them (not at the extreme bottom), so it's within thumb reach.
- **C**: Once/Every toggle + caption; sleep amount; the round button centred; Wake me and the
  extra row right above the status card, which sits at the bottom just above the tab bar
  (where the eye is when switching tabs).
- In both: every block has a fixed height and Day/Night have the same blocks, so the round
  button is in the same place in both tabs and nothing moves when tapping.
- **The panes must never scroll.** On short screens or large fonts, the free space around the
  round button shrinks first, then the button itself (down to ~120 dp), then the spacing.
- Timeline landmarks (agreed): a soft warm sun glow (~30 dp, little blur) behind both 12s and a
  pale blue crescent (~28 dp, 80% opacity) behind 00; numbers stay on top. The top sun also
  softens the timeline's top edge against the status card.

## Round 3: feedback on 0.3.1 (layout A chosen for the app)
Status: done in 0.4.0. The slot problem is also fixed on the Gadgetbridge fork's branch
fix-third-party-alarms: SET_ALARM prefers unused slots, DISMISS_ALARM marks them unused again.
- Build layout A (status card two fixed lines, mode caption, same rows in both tabs, round
  button centred in the space left, no scrolling); chip = Bluetooth icon + name.
- Per-app language: switchable in Android's "App languages" (Android 13+).
- Tapping the greyed round button explains why (toast: watch not connected, or a night running).
- Connection chip updates live (Bluetooth connect/disconnect broadcasts), not only on pane switch.
- Gadgetbridge's SET_ALARM treats any alarm that is off and has no name as free, so it can
  overwrite the user's own switched-off, untitled alarms. WakeBridge only ever dismisses alarms
  titled "WakeBridge", but can't choose the slot. Setup screen: tell users to name their own
  switched-off alarms to protect them (Gadgetbridge's documented convention).
- Settings: subtle icons beside each entry, like Android's own settings.

## Round 4: feedback on 0.4.0
- The mode toggle reads "Manual" / "Automatic" ("Manuale" / "Automatico") in both tabs: in both,
  Automatic means it starts without a tap (Day: on falling asleep; Night: at the night's start),
  and both count sleep from actually falling asleep. "Prossimo pisolino" didn't fit, "Ogni
  pisolino" was unclear.
- Automatic: the round button is greyed ("Detecting" / "In ascolto"; Night: "From 22:45") and a
  tap explains; it pulses once a nap or the night actually starts. At night it then offers
  "Skip tonight" ("Salta stanotte"), which re-arms the next night.
- A switch instead of the toggle is mocked up for comparison (canvas: ModeToggle, ModeSwitch;
  generator docs/mockups/mode_mockups.py).
- Manual Day status: "Naps don't start by themselves" instead of an empty second line.
- Italian: "Includi i pisolini di oggi".
- Timeline suns: sized as in the mockup (CSS radial-gradient(circle) reaches the square's corner,
  so the glow is ~30 dp across), sticking out of the track level with the status card.

## Round 5: the timeline explains itself (mockup: TimelineLegend, TimelineShift)
- Testers found the timeline non-obvious. On first visit, and whenever the timeline is touched
  or dragged, a legend appears: labelled arrows for now, the nap, night start, the night's
  sleep and night end (durations as brackets to scale). Both sleeps show in both tabs while
  the legend is up; outside it each tab shows only its own.
- Backdrop: two mockups, a tint blending into the pane (TimelineLegend) and a raised panel
  (TimelineLegendPanel). **The raised panel was chosen.** "Now" is small, grey and drawn under
  the other labels; labels sit on the panel colour so they stay readable over it.
- No "now" line on the timeline outside the legend.
- Night start/end can be dragged by the handle or by their label and arrow; while dragging the
  label shows the live time and the other labels dim. This replaces the drag tooltip.
- Shift work: no panning and no "now at the top" (TimelineNowTop, rejected: the scale would move
  on its own and a night viewed at night is cut in two). The axis is noon to noon, or midnight to
  midnight when the night crosses noon, chosen automatically. Dragging stops at the track's ends
  and the axis is frozen during a drag; crossing noon is done with the time picker (tap a handle
  or its label). An "overshoot to wrap" drag gesture was considered and rejected.
- Built in 0.5.0 with the raised-panel backdrop (TimelineLegendPanel): times follow the system
  12/24-hour setting; the nap goes in the longer part of the day the track shows; the "tap to
  close" line goes to whichever end of the panel is free. ScreenshotTest renders the panes with
  `-Pscreenshots=DIR`.

## Round 6: after the first night with 0.5.0 (built in 0.5.1)
- Neither the WakeBridge alarm nor the owner's own Gadgetbridge alarm rang. The own alarm had
  been changed on the watch, and was rewritten with Gadgetbridge's stale copy: Gadgetbridge sends
  every alarm whenever one changes (its Intent API code has a TODO about exactly this).
- New setting Alarms > "Ring only on the phone" (default off): no alarm goes to the watch, the
  phone rings at the wake time with no delay; the watch still reports sleep, so sleep-counting
  modes still need it connected. Greys out the phone backup alarm. Applies from the next session.
- Setup and info: "Simulate falling asleep" / "Simulate waking up" buttons under the test alarm
  ones; they pass the Gadgetbridge broadcast through the same receiver and log that it was
  simulated.
- Tapping Night starts / Night ends while the legend is closed only opens the legend; the time
  picker opens on a tap when the legend is already showing. Dragging works straight away either
  way.
- CI uploads the APK unzipped (upload-artifact v7, `archive: false`) as WakeBridge-VERSION-debug.apk.

## Status and how to continue (end of 9 November 2026)
- The mode control stays a toggle (Manual / Automatic); the switch mockup (ModeSwitch) was not
  chosen.
- 0.5.0 is the build being tested in real sleep. Open issue seen in screenshots: a night handle
  exactly on an hour mark (the default 21:00) hides that hour's number.
- The Settings > Appearance timeline side mirrors everything, legend included.
- Mockups: canvas "WakeBridge redesign mockups" (claude.ai artifact F9mU4TVnatMgD7FwGxk2tv);
  copies and their generators in docs/mockups/ (stable_mockups.py, mode_mockups.py imports it,
  legend_mockups.py imports mode_mockups.py; each takes the output folder as its argument).

### Building
- No Google SDK licence is accepted anywhere. setup-sdk.sh from LuccoJ/android-development
  (private; branch claude/trusting-bell-usyza9) assembles an SDK from Robolectric's android-all
  (Apache-2.0, stubbed to the public API), the JDK's java.* classes and Debian/Ubuntu build
  tools: `setup-sdk.sh --sdk DIR --api 37 --build-tools 36.0.0`, then
  `ANDROID_HOME=DIR ./gradlew assembleDebug testDebugUnitTest lintDebug`. AGP prints "License
  for package Android SDK Platform-Tools not accepted": it offered a download, nothing was
  accepted or fetched.
- GitHub Actions (.github/workflows/build.yml) does the same on ubuntu-24.04 after deleting the
  runner's preinstalled Google SDK, and uploads the APK. It works since LuccoJ/android-development
  was made public (11 November). Actions' Node 20 deprecation warnings are harmless for now.
- Signing: the debug key that signed every APK so far is kept by the owner; stored base64 as the
  repository secret DEBUG_KEYSTORE, CI builds install over local ones. Never commit it; publishing
  would need a real release key.
- ScreenshotTest renders the panes with `-Pscreenshots=DIR` (Robolectric native graphics),
  for checking layouts without a phone.

### Gadgetbridge
- WakeBridge needs, per watch: Device actions On Fall Asleep / On Wake Up → Send Broadcast to
  package io.github.luccoj.wakebridge, and Developer settings > Intent API > Allow 3rd party apps
  to set alarms.
- Stock Gadgetbridge puts a third-party alarm in the first alarm that is off and untitled (it can
  be one of the user's own: naming them protects them) and, on Huami watches, may send it
  disabled because the slot is still marked unused.
- The owner's fork (github.com/LuccoJ/Gadgetbridge, branch fix-third-party-alarms, synced with
  upstream) fixes both: SET_ALARM clears "unused" when enabling and prefers unused slots;
  DISMISS_ALARM marks the slot unused again. Not yet built or tested; no upstream request for now.
  Commits there carry an "AI-assisted" note, per Gadgetbridge's policy.
- Fork, second commit: on pre-Zepp OS Huami watches (HuamiCoordinator but not ZeppOsCoordinator;
  all use HuamiSupport.onSetAlarms, which writes one message per given alarm to its slot) the
  Intent API sends only the alarms it changed, so alarms changed on the watch aren't overwritten.
  Other devices still get the full list, since their protocols may take it as the complete set.
  Reading alarms back first isn't enough on the Bip: Gadgetbridge already asks the watch at
  connect and when the watch reports a change, but older Huami firmware answers only which slots
  are on and in use, not their times. Alarms edited in Gadgetbridge's own alarm list are still
  all sent, as upstream does. Commit b3bd0945d5: compiles, not yet tried on the watch.
- Compiling the fork with the open SDK needs a stub of javax.annotation.processing.Generated
  (Dagger's generated code uses it; the stubbed android.jar lacks it) added to the JavaCompile
  classpath through a Gradle init script.
