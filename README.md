# WakeBridge

An Android alarm that wakes you after a set amount of **actual sleep**, not after a set time.

WakeBridge works with [Gadgetbridge](https://codeberg.org/Freeyourgadget/Gadgetbridge) and a
watch or band that reports when you fall asleep and wake up (for example Amazfit/Mi Band devices,
Zepp OS and Xiaomi devices). Gadgetbridge tells WakeBridge when you fall asleep and wake up;
WakeBridge counts the time asleep and has Gadgetbridge set the alarm on the watch. Brief
awakenings pause the count.

The app is in English and Italian.

## Features

- **Naps**: pick a length (5 min to 3 h) and start; counting begins when you fall asleep.
  Turn off "Count only time asleep" for a plain countdown that starts right away.
- **Alarm right away** (on by default): when counting only sleep, the watch always holds an
  alarm for the earliest time the sleep could be complete, moved later as sleep is detected.
  A missed event or a disconnection can only wake you early, never not at all.
- **Quick Settings tile**: tap for a dialog over the panel to start a nap (or, with "One tap
  starts the nap", start right away); tap during a session to confirm cancelling it;
  long-press to open the app.
- **Automatic naps**: falling asleep outside night-time starts a nap of a preset length.
- **Nightly alarm**: every night at a set time, arm an alarm for a full night's sleep
  (5 to 10 hours of actual sleep), with an optional "never later than" wake-up time.
  A nap still running when night starts hands over to the night alarm.
- **Auto-cancel**: an alarm that hasn't gone off within a set number of hours
  (12 by default) is cancelled.
- **Setup screen**: step-by-step Gadgetbridge settings, a test alarm, and a log of the events
  received, to check everything gets through.

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
