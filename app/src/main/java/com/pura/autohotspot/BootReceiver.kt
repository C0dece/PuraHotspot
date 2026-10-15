package com.pura.autohotspot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d("BootReceiver", "Получено событие системы: $action")

        if (Intent.ACTION_BOOT_COMPLETED == action || Intent.ACTION_MY_PACKAGE_REPLACED == action) {
            val prefs = PreferencesManager(context)
            if (prefs.isServiceEnabled && !prefs.targetDeviceAddress.isNullOrBlank()) {
                Log.d("BootReceiver", "Автозапуск HotspotService после загрузки системы")
                HotspotService.start(context)
            }
        }
    }
}
