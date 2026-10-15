package com.pura.autohotspot

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("pura_hotspot_prefs", Context.MODE_PRIVATE)

    var targetDeviceAddress: String?
        get() = prefs.getString(KEY_DEVICE_ADDRESS, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_ADDRESS, value).apply()

    var targetDeviceName: String?
        get() = prefs.getString(KEY_DEVICE_NAME, null)
        set(value) = prefs.edit().putString(KEY_DEVICE_NAME, value).apply()

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SERVICE_ENABLED, value).apply()

    var disconnectDelaySeconds: Int
        get() = prefs.getInt(KEY_DISCONNECT_DELAY, 15) // default 15 sec debounce
        set(value) = prefs.edit().putInt(KEY_DISCONNECT_DELAY, value).apply()

    companion object {
        private const val KEY_DEVICE_ADDRESS = "target_device_address"
        private const val KEY_DEVICE_NAME = "target_device_name"
        private const val KEY_SERVICE_ENABLED = "service_enabled"
        private const val KEY_DISCONNECT_DELAY = "disconnect_delay"
    }
}
