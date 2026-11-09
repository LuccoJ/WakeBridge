package io.github.luccoj.wakebridge

import android.Manifest
import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.bluetooth.BluetoothManager
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.LocalTime

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, SettingsFragment())
                .commit()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {
        private lateinit var settings: Settings

        private val bluetoothPermission =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) pickWatch() else enterAddress()
            }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.settings, rootKey)
            settings = Settings(requireContext())
            findPreference<Preference>(Settings.KEY_WATCH)!!.setOnPreferenceClickListener {
                chooseWatch()
                true
            }
            setUpTime(Settings.KEY_NIGHT_ARM_TIME) { settings.nightArmTime }
            setUpTime(Settings.KEY_NIGHT_LATEST_TIME) { settings.nightLatestTime }
            updateSummaries()
        }

        override fun onResume() {
            super.onResume()
            settings.prefs.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onPause() {
            settings.prefs.unregisterOnSharedPreferenceChangeListener(this)
            super.onPause()
        }

        override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
            updateSummaries()
            when (key) {
                Settings.KEY_NIGHT_ENABLED, Settings.KEY_NIGHT_ARM_TIME, Settings.KEY_AUTO_NAP_ENABLED ->
                    Scheduler.scheduleNight(requireContext())
            }
        }

        private fun setUpTime(key: String, current: () -> LocalTime) {
            findPreference<Preference>(key)!!.setOnPreferenceClickListener {
                val time = current()
                TimePickerDialog(
                    requireContext(),
                    { _, hour, minute -> settings.prefs.edit().putString(key, LocalTime.of(hour, minute).toString()).apply() },
                    time.hour, time.minute,
                    android.text.format.DateFormat.is24HourFormat(requireContext())
                ).show()
                true
            }
        }

        private fun updateSummaries() {
            val context = requireContext()
            findPreference<Preference>(Settings.KEY_WATCH)!!.summary = settings.watchAddress?.let { address ->
                settings.watchName?.let { "$it ($address)" } ?: address
            } ?: getString(R.string.settings_watch_none)
            findPreference<Preference>(Settings.KEY_NIGHT_ARM_TIME)!!.summary =
                getString(R.string.settings_night_arm_summary, Format.time(context, settings.nightArmTime))
            findPreference<Preference>(Settings.KEY_NIGHT_LATEST_TIME)!!.summary =
                getString(R.string.settings_night_latest_summary, Format.time(context, settings.nightLatestTime))
        }

        private fun chooseWatch() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                pickWatch()
            }
        }

        /** Offers the phone's paired Bluetooth devices, which include the watch Gadgetbridge uses. */
        @SuppressLint("MissingPermission")
        private fun pickWatch() {
            val adapter = requireContext().getSystemService(BluetoothManager::class.java)?.adapter
            val devices = try {
                adapter?.bondedDevices?.sortedBy { it.name ?: it.address } ?: emptyList()
            } catch (e: SecurityException) {
                emptyList()
            }
            if (devices.isEmpty()) {
                enterAddress()
                return
            }
            val labels = devices.map { "${it.name ?: "?"}\n${it.address}" } + getString(R.string.settings_watch_manual)
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_watch_title)
                .setItems(labels.toTypedArray()) { _, which ->
                    if (which == devices.size) {
                        enterAddress()
                    } else {
                        settings.setWatch(devices[which].address, devices[which].name)
                    }
                }
                .show()
        }

        private fun enterAddress() {
            val input = EditText(requireContext()).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                hint = "AA:BB:CC:DD:EE:FF"
                setText(settings.watchAddress ?: "")
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_watch_manual)
                .setMessage(R.string.settings_watch_manual_message)
                .setView(input)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val address = input.text.toString().trim().uppercase()
                    if (address.matches(Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$"))) {
                        settings.setWatch(address, null)
                    } else {
                        Toast.makeText(requireContext(), R.string.settings_watch_invalid, Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }
}
