package com.pura.autohotspot

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BluetoothReceiver(
    private val onStatusUpdate: ((message: String, isHotspotOn: Boolean?) -> Unit)? = null
) : BroadcastReceiver() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var pendingDisconnectJob: Job? = null

    @SuppressLint("MissingPermission")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val prefs = PreferencesManager(context)

        val targetAddress = prefs.targetDeviceAddress
        if (targetAddress.isNullOrBlank()) {
            Log.d(TAG, "Целевое устройство не задано в настройках")
            return
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "PuraHotspot:BluetoothWakeLock"
        )
        // Keep CPU awake for 10 seconds while command executes
        wakeLock.acquire(10_000L)

        when (action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val deviceName = device?.name ?: "Неизвестно"
                val deviceAddress = device?.address ?: ""

                Log.d(TAG, "Подключено BT-устройство: $deviceName ($deviceAddress)")

                if (deviceAddress.equals(targetAddress, ignoreCase = true)) {
                    // Cancel any pending disconnect countdown (e.g. ignition restart debounce)
                    pendingDisconnectJob?.cancel()
                    pendingDisconnectJob = null

                    onStatusUpdate?.invoke("Подключен $deviceName. Включение хотспота...", null)

                    scope.launch(Dispatchers.IO) {
                        val (success, logs) = HotspotController.setHotspotEnabled(true, context)
                        val status = if (success) "Точка доступа ВКЛ ($deviceName)" else "Ошибка включения"
                        onStatusUpdate?.invoke("$status\n$logs", success)
                        if (wakeLock.isHeld) wakeLock.release()
                    }
                }
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val deviceName = device?.name ?: "Неизвестно"
                val deviceAddress = device?.address ?: ""

                Log.d(TAG, "Отключено BT-устройство: $deviceName ($deviceAddress)")

                if (deviceAddress.equals(targetAddress, ignoreCase = true)) {
                    val delaySec = prefs.disconnectDelaySeconds

                    if (delaySec > 0) {
                        onStatusUpdate?.invoke("Отключен $deviceName. Выключение через $delaySec сек...", null)
                        pendingDisconnectJob?.cancel()
                        pendingDisconnectJob = scope.launch(Dispatchers.IO) {
                            delay(delaySec * 1000L)
                            onStatusUpdate?.invoke("Задержка истекла. Выключение точки доступа...", null)
                            val (success, logs) = HotspotController.setHotspotEnabled(false, context)
                            val status = if (success) "Точка доступа ВЫКЛ" else "Ошибка выключения"
                            onStatusUpdate?.invoke("$status\n$logs", !success)
                            if (wakeLock.isHeld) wakeLock.release()
                        }
                    } else {
                        onStatusUpdate?.invoke("Отключен $deviceName. Выключение точки доступа...", null)
                        scope.launch(Dispatchers.IO) {
                            val (success, logs) = HotspotController.setHotspotEnabled(false, context)
                            val status = if (success) "Точка доступа ВЫКЛ" else "Ошибка выключения"
                            onStatusUpdate?.invoke("$status\n$logs", !success)
                            if (wakeLock.isHeld) wakeLock.release()
                        }
                    }
                }
            }

            BluetoothAdapter.ACTION_STATE_CHANGED -> {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    Log.d(TAG, "Bluetooth выключен на телефоне")
                    pendingDisconnectJob?.cancel()
                    scope.launch(Dispatchers.IO) {
                        HotspotController.setHotspotEnabled(false, context)
                        onStatusUpdate?.invoke("Bluetooth выключен. Точка доступа отключена.", false)
                        if (wakeLock.isHeld) wakeLock.release()
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "BluetoothReceiver"
    }
}
