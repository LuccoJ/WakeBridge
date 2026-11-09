package io.github.luccoj.wakebridge

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

/** Day, Night and Settings panes. Also opened by long-pressing the Quick Settings tile. */
class MainActivity : AppCompatActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.setOnItemSelectedListener { item ->
            show(item.itemId)
            true
        }
        if (savedInstanceState == null) {
            nav.selectedItemId = when {
                intent.getBooleanExtra(EXTRA_SETTINGS, false) -> R.id.tab_settings
                Session.current(this)?.kind == Session.Kind.NIGHT -> R.id.tab_night
                else -> R.id.tab_day
            }
        }
        askForNotifications()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_SETTINGS, false)) {
            findViewById<BottomNavigationView>(R.id.bottom_nav).selectedItemId = R.id.tab_settings
        }
    }

    private fun show(id: Int) {
        val tag = "tab$id"
        val fragments = supportFragmentManager
        val transaction = fragments.beginTransaction()
        fragments.fragments.forEach { if (it.tag != tag) transaction.hide(it) }
        val existing = fragments.findFragmentByTag(tag)
        if (existing != null) {
            transaction.show(existing)
        } else {
            val fragment: Fragment = when (id) {
                R.id.tab_night -> PaneFragment.create(night = true)
                R.id.tab_settings -> SettingsFragment()
                else -> PaneFragment.create(night = false)
            }
            transaction.add(R.id.content, fragment, tag)
        }
        transaction.commit()
    }

    /** The phone backup alarm rings through a notification (Android 13+ asks first). */
    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (!Settings(this).phoneBackup) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return
        val prefs = getSharedPreferences("asked", MODE_PRIVATE)
        if (prefs.getBoolean("notifications", false)) return
        prefs.edit().putBoolean("notifications", true).apply()
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_SETTINGS = "settings"
    }
}
