package io.github.luccoj.wakebridge

import android.app.Application
import android.content.Intent
import androidx.preference.PreferenceManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionTest {
    private lateinit var app: Application
    private val watch = "AA:BB:CC:DD:EE:FF"

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        WatchConnection.override = WatchConnection.State.CONNECTED
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putString(Settings.KEY_WATCH_ADDRESS, watch)
            .putString(Settings.KEY_NIGHT_START, "21:00")
            .putString(Settings.KEY_NIGHT_END, "08:00")
            // Most tests cover the plain behaviour; the fail-safe has its own tests
            .putBoolean(Settings.KEY_FAIL_SAFE, false)
            .putBoolean(Settings.KEY_PHONE_BACKUP, false)
            .commit()
        at("2026-11-09T14:00:00")
    }

    @After
    fun tearDown() {
        WatchConnection.override = null
    }

    private fun at(time: String) {
        Session.clock = Clock.fixed(Instant.parse("${time}Z"), ZoneOffset.UTC)
    }

    private fun setting(key: String, value: Any) {
        val editor = PreferenceManager.getDefaultSharedPreferences(app).edit()
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            else -> editor.putString(key, value.toString())
        }
        editor.commit()
    }

    /** The commands sent to Gadgetbridge since the last call, as "SET hh:mm" / "DISMISS title". */
    private fun commands(): List<String> {
        val shadow = shadowOf(app)
        val sent = shadow.broadcastIntents
            .filter { it.action?.startsWith("nodomain.freeyourgadget.gadgetbridge.command.") == true }
            .map(::describe)
        shadow.clearBroadcastIntents()
        return sent
    }

    private fun describe(intent: Intent): String {
        assertEquals(watch, intent.getStringExtra("device"))
        return if (intent.action!!.endsWith("SET_ALARM")) {
            "SET %02d:%02d".format(intent.getIntExtra("hour", -1), intent.getIntExtra("minutes", -1))
        } else {
            "DISMISS ${intent.getStringExtra("title")}"
        }
    }

    private fun nap(minutes: Long, mode: WakeMode = WakeMode.SLEEP_ONLY) =
        Session.start(app, Session.Kind.NAP, Duration.ofMinutes(minutes), mode)

    private fun everyNight(minutes: Int = 480, mode: WakeMode = WakeMode.SLEEP_ONLY) {
        setting(Settings.KEY_NIGHT_EVERY, true)
        setting(Settings.KEY_NIGHT_MINUTES, minutes)
        setting(Settings.KEY_NIGHT_WAKE_MODE, mode.name)
    }

    // Naps

    @Test
    fun countsOnlySleepAndPausesWhileAwake() {
        nap(20)
        assertEquals(emptyList<String>(), commands())

        at("2026-11-09T14:05:00")
        Session.onFellAsleep(app)
        assertEquals(listOf("SET 14:25"), commands())

        at("2026-11-09T14:10:00") // 5 minutes slept
        Session.onWokeUp(app)
        assertEquals(listOf("DISMISS WakeBridge"), commands())
        assertEquals(Duration.ofMinutes(5), Session.current(app)!!.slept)

        at("2026-11-09T14:12:30") // 15 minutes left: 14:27:30, rounded up
        Session.onFellAsleep(app)
        assertEquals(listOf("SET 14:28"), commands())

        at("2026-11-09T14:28:00")
        assertNull(Session.checkTimers(app))
        assertEquals("no removal while the alarm may still be snoozing", emptyList<String>(), commands())

        at("2026-11-09T14:38:00")
        Session.checkTimers(app)
        assertEquals(listOf("DISMISS WakeBridge"), commands())
    }

    @Test
    fun repeatedEventsAreIgnored() {
        nap(20)
        Session.onWokeUp(app)
        at("2026-11-09T14:05:00")
        Session.onFellAsleep(app)
        at("2026-11-09T14:06:00")
        Session.onFellAsleep(app)
        assertEquals(listOf("SET 14:25"), commands())
    }

    @Test
    fun plainCountdownSetsTheAlarmRightAway() {
        at("2026-11-09T14:00:30")
        nap(20, WakeMode.FIXED)
        assertEquals(listOf("SET 14:21"), commands())
        Session.onFellAsleep(app)
        Session.onWokeUp(app)
        assertEquals(emptyList<String>(), commands())
    }

    @Test
    fun fromFirstSleepIgnoresLaterAwakenings() {
        nap(20, WakeMode.FROM_FIRST_SLEEP)
        assertEquals(emptyList<String>(), commands())
        at("2026-11-09T14:05:00")
        Session.onFellAsleep(app)
        assertEquals(listOf("SET 14:25"), commands())
        at("2026-11-09T14:10:00")
        Session.onWokeUp(app)
        at("2026-11-09T14:12:00")
        Session.onFellAsleep(app)
        assertEquals("the alarm stays put", emptyList<String>(), commands())
    }

    @Test
    fun withoutAWatchNothingStarts() {
        setting(Settings.KEY_WATCH_ADDRESS, "")
        assertEquals(Session.StartResult.NO_WATCH, nap(20))
        assertNull(Session.current(app))
    }

    @Test
    fun disconnectedWatchAllowsOnlyThePlainCountdown() {
        WatchConnection.override = WatchConnection.State.DISCONNECTED
        assertEquals(Session.StartResult.DISCONNECTED, nap(20))
        assertEquals(Session.StartResult.DISCONNECTED, nap(20, WakeMode.FROM_FIRST_SLEEP))
        assertNull(Session.current(app))

        assertEquals(Session.StartResult.STARTED, nap(20, WakeMode.FIXED))
        assertEquals("nothing can reach the watch", emptyList<String>(), commands())
        assertEquals(true, Session.current(app)!!.phoneOnly)
        assertEquals("the phone rings at the alarm time itself", Instant.parse("2026-11-09T14:20:00Z"), PhoneAlarm.pendingAt(app))
    }

    @Test
    fun expiresWhenNoAlarmWithinTheLimit() {
        setting(Settings.KEY_EXPIRY_HOURS, 2)
        nap(20)
        at("2026-11-09T15:59:00")
        assertNotNull(Session.checkTimers(app))
        at("2026-11-09T16:00:00")
        assertNull(Session.checkTimers(app))
    }

    @Test
    fun cancelWithdrawsTheAlarm() {
        nap(20, WakeMode.FIXED)
        Session.cancel(app, "test")
        assertEquals(listOf("SET 14:20", "DISMISS WakeBridge"), commands())
    }

    @Test
    fun undoRestoresACancelledNap() {
        nap(20, WakeMode.FIXED)
        Session.cancel(app, "test", undoable = true)
        commands()
        at("2026-11-09T14:00:05")
        assertEquals(true, Session.undo(app))
        assertEquals(listOf("SET 14:20"), commands())
        assertNotNull(Session.current(app))
    }

    @Test
    fun undoExpires() {
        nap(20, WakeMode.FIXED)
        Session.cancel(app, "test", undoable = true)
        at("2026-11-09T14:00:30")
        assertEquals(false, Session.undo(app))
        assertNull(Session.current(app))
    }

    @Test
    fun everyNapOutsideNightTimeOnly() {
        setting(Settings.KEY_DAY_EVERY, true)
        setting(Settings.KEY_NAP_MINUTES, 25)

        at("2026-11-09T15:00:00")
        Session.onFellAsleep(app)
        assertEquals(listOf("SET 15:25"), commands())
        Session.cancel(app, "test")
        commands()

        at("2026-11-09T23:00:00")
        Session.onFellAsleep(app)
        assertNull(Session.current(app))
        assertEquals(emptyList<String>(), commands())
    }

    // Fail-safe

    @Test
    fun failSafeNapMovesOnlyLater() {
        setting(Settings.KEY_FAIL_SAFE, true)
        nap(20)
        assertEquals(listOf("SET 14:20"), commands())

        at("2026-11-09T14:05:00")
        Session.onFellAsleep(app)
        assertEquals(listOf("DISMISS WakeBridge", "SET 14:25"), commands())

        at("2026-11-09T14:10:00") // 15 left: earliest is 14:25 again
        Session.onWokeUp(app)
        assertEquals(emptyList<String>(), commands())

        at("2026-11-09T14:12:00")
        Session.onFellAsleep(app)
        assertEquals(listOf("DISMISS WakeBridge", "SET 14:27"), commands())
    }

    @Test
    fun failSafeNapRingsEvenWithoutSleep() {
        setting(Settings.KEY_FAIL_SAFE, true)
        nap(20)
        at("2026-11-09T14:20:00")
        assertNull(Session.checkTimers(app))
    }

    // Nights

    @Test
    fun nightWaitsWithAnAlarmAtItsEnd() {
        everyNight()
        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        assertEquals(listOf("SET 08:00"), commands())
    }

    @Test
    fun awakeTimeInTheNightPushesTheAlarm() {
        setting(Settings.KEY_FAIL_SAFE, true)
        everyNight(480)
        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        at("2026-11-09T23:00:00")
        Session.onFellAsleep(app)
        assertEquals(listOf("SET 08:00", "DISMISS WakeBridge", "SET 07:00"), commands())

        at("2026-11-10T03:00:00") // 4 h slept, 4 h left: still 07:00 at the earliest
        Session.onWokeUp(app)
        assertEquals(emptyList<String>(), commands())

        at("2026-11-10T03:30:00") // 30 min awake: 30 min later
        Session.onFellAsleep(app)
        assertEquals(listOf("DISMISS WakeBridge", "SET 07:30"), commands())

        at("2026-11-10T04:30:00")
        Session.onWokeUp(app)
        at("2026-11-10T05:00:00") // would be 08:00 + 30 min: capped at the night's end
        Session.onFellAsleep(app)
        assertEquals(listOf("DISMISS WakeBridge", "SET 08:00"), commands())
    }

    @Test
    fun withoutFailSafeAnAwakeNightFallsBackToItsEnd() {
        everyNight(480)
        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        at("2026-11-09T23:00:00")
        Session.onFellAsleep(app)
        at("2026-11-10T03:00:00")
        Session.onWokeUp(app)
        assertEquals(listOf("SET 08:00", "DISMISS WakeBridge", "SET 07:00", "DISMISS WakeBridge", "SET 08:00"), commands())
    }

    @Test
    fun fixedNightIsAnOrdinaryAlarm() {
        everyNight(480, WakeMode.FIXED)
        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        at("2026-11-09T23:00:00")
        Session.onFellAsleep(app)
        assertEquals(listOf("SET 08:00"), commands())
    }

    @Test
    fun disconnectedAtNightStartTheNightStillRingsOnThePhone() {
        everyNight()
        WatchConnection.override = WatchConnection.State.DISCONNECTED
        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        val state = Session.current(app)!!
        assertEquals(WakeMode.FIXED, state.mode)
        assertEquals(emptyList<String>(), commands())
        assertEquals(Instant.parse("2026-11-10T08:00:00Z"), PhoneAlarm.pendingAt(app))
    }

    @Test
    fun nightTakesOverASleepingNap() {
        everyNight(450)
        at("2026-11-09T20:40:00")
        nap(30)
        at("2026-11-09T20:50:00")
        Session.onFellAsleep(app)
        assertEquals(listOf("SET 21:20"), commands())

        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        // The nap's alarm goes, the night waits at its end, then counts from now: 21:00 + 7 h 30 min
        assertEquals(listOf("DISMISS WakeBridge", "SET 08:00", "DISMISS WakeBridge", "SET 04:30"), commands())
        assertEquals(Session.Kind.NIGHT, Session.current(app)!!.kind)
    }

    @Test
    fun napIsCancelledAtNightWhenNoNightIsArmed() {
        setting(Settings.KEY_DAY_EVERY, true)
        setting(Settings.KEY_NAP_MINUTES, 20)
        at("2026-11-09T20:50:00")
        Session.onFellAsleep(app)
        assertEquals(listOf("SET 21:10"), commands())
        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        assertEquals(listOf("DISMISS WakeBridge"), commands())
        assertNull(Session.current(app))
    }

    @Test
    fun tonightArmsOnce() {
        setting(Settings.KEY_NIGHT_MINUTES, 480)
        at("2026-11-09T15:00:00")
        Scheduler.armTonight(app)
        assertNull("not night yet", Session.current(app))
        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        assertNotNull(Session.current(app))
        Session.cancel(app, "test")
        at("2026-11-10T21:00:00")
        Scheduler.armNight(app)
        assertNull("only tonight", Session.current(app))
    }

    @Test
    fun armTonightDuringTheNightArmsAtOnce() {
        at("2026-11-09T22:00:00")
        Scheduler.armTonight(app)
        assertEquals(Session.Kind.NIGHT, Session.current(app)!!.kind)
    }

    @Test
    fun todaysNapsCountTowardTheNight() {
        everyNight(480)
        setting(Settings.KEY_NIGHT_INCLUDE_NAPS, true)
        nap(30, WakeMode.FROM_FIRST_SLEEP)
        at("2026-11-09T14:05:00")
        Session.onFellAsleep(app)
        at("2026-11-09T14:35:00")
        assertNull(Session.checkTimers(app)) // 30 min of nap done
        at("2026-11-09T15:00:00")
        Session.checkTimers(app) // the nap's alarm is cleaned up, as the app's timer would
        commands()

        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        at("2026-11-09T23:00:00")
        Session.onFellAsleep(app)
        assertEquals("8 h minus the 30 min nap", listOf("SET 08:00", "DISMISS WakeBridge", "SET 06:30"), commands())
    }

    @Test
    fun theNightEndsAtItsEndWhenAwake() {
        everyNight(480)
        setting(Settings.KEY_DAY_EVERY, true)
        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        at("2026-11-09T23:00:00")
        Session.onFellAsleep(app)
        at("2026-11-10T03:00:00")
        Session.onWokeUp(app)
        commands()
        at("2026-11-10T14:00:00") // an afternoon doze must not resume last night's alarm
        Session.onFellAsleep(app)
        assertEquals(Session.Kind.NAP, Session.current(app)!!.kind)
    }

    @Test
    fun anActiveNightIsNotReplaced() {
        everyNight()
        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        val first = Session.current(app)!!.startedAt
        at("2026-11-09T21:30:00")
        Scheduler.armNight(app)
        assertEquals(first, Session.current(app)!!.startedAt)
    }

    // Phone backup alarm

    @Test
    fun phoneBackupIsCancelledByWakingUp() {
        setting(Settings.KEY_PHONE_BACKUP, true)
        setting(Settings.KEY_BACKUP_DELAY, 10)
        nap(20, WakeMode.FIXED)
        assertEquals(Instant.parse("2026-11-09T14:30:00Z"), PhoneAlarm.pendingAt(app))
        at("2026-11-09T14:22:00") // after the watch alarm
        Session.onWokeUp(app)
        assertNull(PhoneAlarm.pendingAt(app))
    }

    @Test
    fun phoneBackupFollowsTheWatchAlarm() {
        setting(Settings.KEY_PHONE_BACKUP, true)
        setting(Settings.KEY_BACKUP_DELAY, 5)
        nap(20)
        assertNull("no watch alarm yet", PhoneAlarm.pendingAt(app))
        at("2026-11-09T14:05:00")
        Session.onFellAsleep(app)
        assertEquals(Instant.parse("2026-11-09T14:30:00Z"), PhoneAlarm.pendingAt(app))
        Session.cancel(app, "test")
        assertNull(PhoneAlarm.pendingAt(app))
    }

    // Time helpers

    @Test
    fun nightTimeWindowWrapsAroundMidnight() {
        val settings = Settings(app)
        assertEquals(true, settings.isNightTime(LocalTime.of(21, 0)))
        assertEquals(true, settings.isNightTime(LocalTime.of(3, 0)))
        assertEquals(false, settings.isNightTime(LocalTime.of(8, 0)))
        assertEquals(false, settings.isNightTime(LocalTime.of(14, 0)))
        assertEquals(Duration.ofHours(11), settings.nightWindow)
    }

    @Test
    fun theNightsSleepNeverExceedsTheWindow() {
        setting(Settings.KEY_NIGHT_MINUTES, 600)
        setting(Settings.KEY_NIGHT_START, "23:00")
        setting(Settings.KEY_NIGHT_END, "07:00")
        assertEquals(480, Settings(app).nightMinutes)
    }

    @Test
    fun occurrences() {
        at("2026-11-09T22:00:00")
        assertEquals(Instant.parse("2026-11-10T08:00:00Z"), Scheduler.nextOccurrence(LocalTime.of(8, 0)))
        assertEquals(Instant.parse("2026-11-09T23:00:00Z"), Scheduler.nextOccurrence(LocalTime.of(23, 0)))
        assertEquals(Instant.parse("2026-11-09T08:00:00Z"), Scheduler.previousOccurrence(LocalTime.of(8, 0)))
        assertEquals(Instant.parse("2026-11-08T23:00:00Z"), Scheduler.previousOccurrence(LocalTime.of(23, 0)))
    }
}
