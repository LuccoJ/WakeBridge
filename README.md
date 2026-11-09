# WakeBridge

An Android alarm that wakes you after a set amount of **actual sleep**, not after a set time.

WakeBridge works with [Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge) and a
watch or band that reports when you fall asleep and wake up (for example Amazfit/Mi Band devices,
Zepp OS and Xiaomi devices). Gadgetbridge tells WakeBridge when you fall asleep and wake up;
WakeBridge counts the time asleep and has Gadgetbridge set the alarm on the watch. Brief
awakenings pause the count.

The app is in English and Italian.

## Features

The design is recorded in [docs/redesign.md](docs/redesign.md).

- **Day and Night panes**, mirroring each other: "Next nap" / "Every nap" and "Tonight" /
  "Every night", a sleep amount slider, and **Wake me**:
  - after the amount of actual sleep (awakenings pause the count),
  - the amount after first falling asleep,
  - a plain countdown (naps) or at the night's end (nights).
- **Vertical timeline** of the day, noon to noon: drag the night's start and end (or tap them
  for exact times). They also decide when automatic naps can start. Left or right side, in
  Settings.
- **Nights** never ring later than the night's end, wait with an alarm there until sleep is
  detected, and push the alarm later by the time spent awake. Today's naps can count toward the
  night.
- **Fail-safe alarm**: naps hold the earliest possible alarm from the start, so a missed event
  can only wake you early.
- **Phone backup alarm**: the phone rings a few minutes after the watch alarm unless the watch
  reports waking up. With the watch disconnected, only the plain countdown can start, and it
  rings on the phone.
- **Quick Settings tile**: tap starts the Day pane's nap; tap during a session cancels it, with
  a few seconds to undo; long-press opens the app.
- **Setup screen**: Gadgetbridge settings step by step, phone permissions, a test alarm and an
  event log.
- English and Italian (with gendered Italian forms on Android 14+).

## How it works

| Direction | Gadgetbridge interface |
|---|---|
| Gadgetbridge → WakeBridge | Device actions "On Fall Asleep" / "On Wake Up" set to *Send Broadcast*, with WakeBridge as the broadcast package. Default messages `nodomain.freeyourgadget.gadgetbridge.FellAsleep` / `.WokeUp` |
| WakeBridge → Gadgetbridge | Intent API `nodomain.freeyourgadget.gadgetbridge.command.SET_ALARM` / `DISMISS_ALARM`, enabled per device with "Allow 3rd party apps to set alarms" |

- The alarm rings on the watch, so it goes off even if the phone kills WakeBridge.
- Gadgetbridge puts WakeBridge's alarms in a free slot (one that's off and has no name), titled
  "WakeBridge". WakeBridge only ever dismisses alarms with that title.
- Watch alarms have minute resolution, so wake-up times are rounded up to the next minute.
- A finished alarm is removed from the watch 10 minutes after its time, so snoozing still works.
- Gadgetbridge has no command to move an alarm, so a move is a dismissal followed by a new alarm.
- WakeBridge can't tell whether a watch supports sleep events: the setup screen shows the last
  ones received.

## Building

Standard Gradle build (`./gradlew assembleDebug`), Android 8.0 or newer.

To build without Google's Android SDK license, use the SDK from
[LuccoJ/Android-Development](https://github.com/LuccoJ/Android-Development):

```bash
setup-sdk.sh --api 37 --build-tools 36.0.0
```
```bash
ANDROID_HOME=~/oss-android-sdk ./gradlew assembleDebug testDebugUnitTest
```

The unit tests use Robolectric, which downloads Android framework jars from Maven Central. If
Central rate-limits you, set `robolectricRepoUrl` in `~/.gradle/gradle.properties` to a mirror,
e.g. `https://maven-central.storage-download.googleapis.com/maven2`.
