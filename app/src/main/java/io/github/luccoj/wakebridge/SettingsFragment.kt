package io.github.luccoj.wakebridge

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** The Settings pane: what fits neither Day nor Night. */
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
        findPreference<Preference>(Settings.KEY_SETUP)!!.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), SetupActivity::class.java))
            true
        }
        updateSummaries()
    }

    override fun onResume() {
        super.onResume()
        settings.prefs.registerOnSharedPreferenceChangeListener(this)
        updateSummaries()
    }

    override fun onPause() {
        settings.prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        updateSummaries()
        when (key) {
            // Keep a pending phone alarm in step with the new settings
            Settings.KEY_PHONE_BACKUP, Settings.KEY_BACKUP_DELAY ->
                PhoneAlarm.sync(requireContext(), Session.current(requireContext()))
        }
    }

    private fun updateSummaries() {
        if (!::settings.isInitialized) return
        findPreference<Preference>(Settings.KEY_WATCH)?.summary = settings.watchAddress?.let { address ->
            val name = settings.watchName?.let { "$it, $address" } ?: address
            val state = when (WatchConnection.state(requireContext())) {
                WatchConnection.State.CONNECTED -> getString(R.string.connected)
                WatchConnection.State.DISCONNECTED -> getString(R.string.not_connected)
                WatchConnection.State.UNKNOWN -> null
            }
            listOfNotNull(name, state).joinToString(", ")
        } ?: getString(R.string.settings_watch_none)
        findPreference<Preference>(Settings.KEY_SETUP)?.summary = Session.lastFellAsleep(requireContext())
            ?.let { getString(R.string.last_event, Format.dateTime(requireContext(), it)) }
            ?: getString(R.string.setup_summary)
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
                if (which == devices.size) enterAddress() else settings.setWatch(devices[which].address, devices[which].name)
                updateSummaries()
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
                    updateSummaries()
                } else {
                    Toast.makeText(requireContext(), R.string.settings_watch_invalid, Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
