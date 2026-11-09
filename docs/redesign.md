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
