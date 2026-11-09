package io.github.luccoj.wakebridge

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders the panes to PNGs for a look; only when -Dscreenshots=DIR is given. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenshotTest {
    private fun shoot(name: String, before: (Settings) -> Unit = {}, prepare: (MainActivity) -> Unit) {
        val dir = System.getProperty("screenshots")
        assumeTrue(dir != null)
        before(Settings(org.robolectric.RuntimeEnvironment.getApplication()))
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        prepare(activity)
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        val root = activity.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(822, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1782, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 822, 1782)
        val bitmap = Bitmap.createBitmap(822, 1782, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File(dir!!).mkdirs()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun dayLegend() = shoot("day-legend") {}

    @Test
    fun dayClosed() = shoot("day-closed", { it.setLegendSeen(false); it.setLegendSeen(true) }) {}

    @Test
    @Config(qualifiers = "it-w411dp-h891dp-xhdpi")
    fun shiftRight() = shoot("shift-right-it", {
        it.nightStart = java.time.LocalTime.of(7, 30)
        it.nightEnd = java.time.LocalTime.of(15, 30)
        it.prefs.edit().putString("timeline_side", "right").commit()
        android.provider.Settings.System.putString(
            org.robolectric.RuntimeEnvironment.getApplication().contentResolver,
            android.provider.Settings.System.TIME_12_24, "24"
        )
    }) {
        it.findViewById<View>(R.id.tab_night).performClick()
    }

    @Test
    fun nightLegend() = shoot("night-legend") {
        it.findViewById<View>(R.id.tab_night).performClick()
    }
}
