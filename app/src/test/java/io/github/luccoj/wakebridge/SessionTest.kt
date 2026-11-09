package io.github.luccoj.wakebridge

import android.app.Application
import android.content.Intent
import androidx.preference.PreferenceManager
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
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SessionTest {
    private lateinit var app: Application
    private val watch = "AA:BB:CC:DD:EE:FF"

    @Before
    fun setUp() {
        app = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(app).edit()
            .putString(Settings.KEY_WATCH_ADDRESS, watch)
            .putString(Settings.KEY_NIGHT_ARM_TIME, "21:00")
            .putString(Settings.KEY_NIGHT_LATEST_TIME, "08:00")
            // Most tests cover the classic mode; the early alarm has its own tests below
            .putBoolean(Settings.KEY_EARLY_ALARM, false)
            .commit()
        at("2026-11-09T14:00:00")
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

    private fun nap(minutes: Long, sleepOnly: Boolean = true) =
        Session.start(app, Session.Kind.NAP, Duration.ofMinutes(minutes), sleepOnly)

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
        nap(20, sleepOnly = false)
        assertEquals(listOf("SET 14:21"), commands())
        Session.onFellAsleep(app)
        Session.onWokeUp(app)
        assertEquals(emptyList<String>(), commands())
    }

    @Test
    fun withoutAWatchNothingStarts() {
        setting(Settings.KEY_WATCH_ADDRESS, "")
        assertEquals(false, nap(20))
        assertNull(Session.current(app))
    }

    @Test
    fun expiresWhenNoAlarmWithinTheLimit() {
        setting(Settings.KEY_EXPIRY_HOURS, 2)
        nap(20)
        at("2026-11-09T15:59:00")
        assertNotNull(Session.checkTimers(app))
        at("2026-11-09T16:00:00")
        assertNull(Session.checkTimers(app))
        at("2026-11-09T16:05:00")
        Session.onFellAsleep(app)
        assertEquals(emptyList<String>(), commands())
    }

    @Test
    fun expiryWithdrawsASetAlarm() {
        setting(Settings.KEY_EXPIRY_HOURS, 1)
        nap(90)
        at("2026-11-09T14:10:00")
        Session.onFellAsleep(app)
        at("2026-11-09T15:00:00")
        Session.checkTimers(app)
        assertEquals(listOf("SET 15:40", "DISMISS WakeBridge"), commands())
        assertNull(Session.current(app))
    }

    @Test
    fun cancelWithdrawsTheAlarm() {
        nap(20, sleepOnly = false)
        Session.cancel(app, "test")
        assertEquals(listOf("SET 14:20", "DISMISS WakeBridge"), commands())
    }

    @Test
    fun automaticNapOutsideNightTimeOnly() {
        setting(Settings.KEY_AUTO_NAP_ENABLED, true)
        setting(Settings.KEY_AUTO_NAP_MINUTES, "25")

        at("2026-11-09T15:00:00")
        Session.onFellAsleep(app)
        assertEquals(listOf("SET 15:25"), commands())
        Session.cancel(app, "test")
        commands()

        at("2026-11-09T23:00:00")
        Session.onFellAsleep(app)
        assertNull(Session.current(app))
        at("2026-11-10T07:59:00")
        Session.onFellAsleep(app)
        assertNull(Session.current(app))
        assertEquals(emptyList<String>(), commands())
    }

    @Test
    fun noAutomaticNapWhenDisabled() {
        Session.onFellAsleep(app)
        assertNull(Session.current(app))
    }

    @Test
    fun nightTakesOverANapRunningIntoTheNight() {
        setting(Settings.KEY_NIGHT_ENABLED, true)
        setting(Settings.KEY_NIGHT_MINUTES, "450")
        at("2026-11-09T20:40:00")
        nap(30)
        at("2026-11-09T20:50:00")
        Session.onFellAsleep(app)
        assertEquals(listOf("SET 21:20"), commands())

        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        // The nap's alarm goes, the night counts from now, asleep: 21:00 + 7 h 30 min
        assertEquals(listOf("DISMISS WakeBridge", "SET 04:30"), commands())
        assertEquals(Session.Kind.NIGHT, Session.current(app)!!.kind)
    }

    @Test
    fun nightTakesOverAnAwakeNapWaitingForSleep() {
        setting(Settings.KEY_NIGHT_ENABLED, true)
        at("2026-11-09T20:40:00")
        nap(30)
        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        assertEquals(emptyList<String>(), commands())
        assertEquals(Session.Kind.NIGHT, Session.current(app)!!.kind)
    }

    @Test
    fun napIsCancelledAtNightWhenNightlyAlarmIsOff() {
        setting(Settings.KEY_AUTO_NAP_ENABLED, true)
        at("2026-11-09T20:50:00")
        Session.onFellAsleep(app)
        assertEquals(listOf("SET 21:10"), commands())
        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        assertEquals(listOf("DISMISS WakeBridge"), commands())
        assertNull(Session.current(app))
    }

    @Test
    fun nightAlarmNoLaterThanNightsEnd() {
        setting(Settings.KEY_NIGHT_ENABLED, true)
        setting(Settings.KEY_NIGHT_MINUTES, "450")
        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        at("2026-11-10T01:00:00") // 7 h 30 min would end at 08:30
        Session.onFellAsleep(app)
        assertEquals(listOf("SET 08:00"), commands())
    }

    @Test
    fun nightEndsAtItsLatestTimeWhenAwake() {
        setting(Settings.KEY_NIGHT_ENABLED, true)
        setting(Settings.KEY_AUTO_NAP_ENABLED, true)
        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        at("2026-11-09T23:00:00")
        Session.onFellAsleep(app)
        at("2026-11-10T03:00:00") // woke up early: alarm withdrawn, night still waiting
        Session.onWokeUp(app)
        assertEquals(listOf("SET 06:30", "DISMISS WakeBridge"), commands())

        at("2026-11-10T14:00:00") // an afternoon doze must not resume last night's alarm
        Session.onFellAsleep(app)
        assertEquals(listOf("SET 14:20"), commands())
        assertEquals(Session.Kind.NAP, Session.current(app)!!.kind)
    }

    @Test
    fun nightWithoutLatestLimit() {
        setting(Settings.KEY_NIGHT_ENABLED, true)
        setting(Settings.KEY_NIGHT_LATEST_ENABLED, false)
        setting(Settings.KEY_NIGHT_MINUTES, "450")
        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        at("2026-11-10T01:00:00")
        Session.onFellAsleep(app)
        assertEquals(listOf("SET 08:30"), commands())
    }

    @Test
    fun anActiveNightIsNotReplaced() {
        setting(Settings.KEY_NIGHT_ENABLED, true)
        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        val first = Session.current(app)!!.startedAt
        at("2026-11-09T21:30:00")
        Scheduler.armNight(app)
        assertEquals(first, Session.current(app)!!.startedAt)
    }

    @Test
    fun nightTimeWindowWrapsAroundMidnight() {
        val settings = Settings(app)
        assertEquals(true, settings.isNightTime(java.time.LocalTime.of(21, 0)))
        assertEquals(true, settings.isNightTime(java.time.LocalTime.of(3, 0)))
        assertEquals(false, settings.isNightTime(java.time.LocalTime.of(8, 0)))
        assertEquals(false, settings.isNightTime(java.time.LocalTime.of(14, 0)))
    }

    @Test
    fun nextOccurrence() {
        at("2026-11-09T22:00:00")
        assertEquals(Instant.parse("2026-11-10T08:00:00Z"), Scheduler.nextOccurrence(java.time.LocalTime.of(8, 0)))
        assertEquals(Instant.parse("2026-11-09T23:00:00Z"), Scheduler.nextOccurrence(java.time.LocalTime.of(23, 0)))
    }

    // Early alarm ("Alarm right away"): the watch always holds the earliest possible alarm

    private fun early() = setting(Settings.KEY_EARLY_ALARM, true)

    @Test
    fun earlyAlarmMovesLaterWithSleepAndStaysWhileAwake() {
        early()
        nap(20)
        assertEquals(listOf("SET 14:20"), commands())

        at("2026-11-09T14:05:00")
        Session.onFellAsleep(app)
        assertEquals(listOf("DISMISS WakeBridge", "SET 14:25"), commands())

        at("2026-11-09T14:10:00") // 5 slept, 15 left: earliest is 14:25 again
        Session.onWokeUp(app)
        assertEquals("unchanged time, nothing sent", emptyList<String>(), commands())
        assertEquals(Instant.parse("2026-11-09T14:25:00Z"), Session.current(app)!!.wakeAt)

        at("2026-11-09T14:12:00") // still 15 left: 14:27
        Session.onFellAsleep(app)
        assertEquals(listOf("DISMISS WakeBridge", "SET 14:27"), commands())

        at("2026-11-09T14:20:00") // a second awakening: 8 + 5 = 13 slept, 7 left
        Session.onWokeUp(app)
        assertEquals("waking up never moves the alarm earlier or withdraws it", emptyList<String>(), commands())
        assertEquals(Instant.parse("2026-11-09T14:27:00Z"), Session.current(app)!!.wakeAt)
    }

    @Test
    fun earlyAlarmRingsEvenWithoutSleep() {
        early()
        nap(20)
        commands()
        at("2026-11-09T14:20:00")
        assertNull(Session.checkTimers(app))
        at("2026-11-09T14:30:00")
        Session.checkTimers(app)
        assertEquals(listOf("DISMISS WakeBridge"), commands())
    }

    @Test
    fun earlyAlarmOnlyWhenCountingSleep() {
        early()
        nap(20, sleepOnly = false)
        assertEquals(listOf("SET 14:20"), commands())
        Session.onFellAsleep(app)
        assertEquals(emptyList<String>(), commands())
    }

    @Test
    fun earlyAutomaticNapStartsAsleep() {
        early()
        setting(Settings.KEY_AUTO_NAP_ENABLED, true)
        at("2026-11-09T15:00:00")
        Session.onFellAsleep(app)
        assertEquals("set at the start, already the right time once asleep", listOf("SET 15:20"), commands())
    }

    @Test
    fun earlyNightRespectsNightsEnd() {
        early()
        setting(Settings.KEY_NIGHT_ENABLED, true)
        setting(Settings.KEY_NIGHT_MINUTES, "450")
        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        assertEquals(listOf("SET 04:30"), commands())
        at("2026-11-10T01:00:00")
        Session.onFellAsleep(app)
        assertEquals(listOf("DISMISS WakeBridge", "SET 08:00"), commands())
    }

    @Test
    fun earlyNightTakesOverASleepingNap() {
        early()
        setting(Settings.KEY_NIGHT_ENABLED, true)
        setting(Settings.KEY_NIGHT_MINUTES, "450")
        at("2026-11-09T20:40:00")
        nap(30)
        at("2026-11-09T20:50:00")
        Session.onFellAsleep(app)
        assertEquals(listOf("SET 21:10", "DISMISS WakeBridge", "SET 21:20"), commands())
        at("2026-11-09T21:00:00")
        Scheduler.armNight(app)
        assertEquals(listOf("DISMISS WakeBridge", "SET 04:30"), commands())
    }

    @Test
    fun aChangedSettingDoesNotAffectARunningSession() {
        early()
        nap(20)
        setting(Settings.KEY_EARLY_ALARM, false)
        at("2026-11-09T14:05:00")
        Session.onFellAsleep(app)
        at("2026-11-09T14:10:00")
        Session.onWokeUp(app)
        assertNotNull(Session.current(app)!!.wakeAt)
    }
}
