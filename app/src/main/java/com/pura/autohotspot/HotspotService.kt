package com.pura.autohotspot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class HotspotService : Service() {

    private var bluetoothReceiver: BluetoothReceiver? = null
    private lateinit var prefs: PreferencesManager

    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesManager(this)

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Служба запущена", "Ожидание событий Bluetooth"))

        registerBluetoothReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val targetName = prefs.targetDeviceName ?: "не выбрано"
        updateNotification("Мониторинг активен", "Целевое устройство: $targetName")
        return START_STICKY
    }

    private fun registerBluetoothReceiver() {
        if (bluetoothReceiver == null) {
            bluetoothReceiver = BluetoothReceiver { message, isHotspotOn ->
                val targetName = prefs.targetDeviceName ?: "устройство"
                val subtitle = when (isHotspotOn) {
                    true -> "Точка доступа: ВКЛ ($targetName)"
                    false -> "Точка доступа: ВЫКЛ"
                    null -> message
                }
                updateNotification("Мониторинг активен", subtitle)

                // Send broadcast to UI if open
                val broadcastIntent = Intent(ACTION_LOG_UPDATE).apply {
                    putExtra(EXTRA_LOG_MESSAGE, message)
                    setPackage(packageName)
                }
                sendBroadcast(broadcastIntent)
            }

            val filter = IntentFilter().apply {
                addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            }
            registerReceiver(bluetoothReceiver, filter)
        }
    }

    private fun updateNotification(title: String, content: String) {
        val notification = buildNotification(title, content)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Фоновый мониторинг хотспота",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Постоянное фоновое отслеживание подключения к Bluetooth для автоматического хотспота"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            bluetoothReceiver = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "pura_hotspot_channel"
        const val NOTIFICATION_ID = 101

        const val ACTION_LOG_UPDATE = "com.pura.autohotspot.ACTION_LOG_UPDATE"
        const val EXTRA_LOG_MESSAGE = "extra_log_message"

        fun start(context: Context) {
            val intent = Intent(context, HotspotService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, HotspotService::class.java)
            context.stopService(intent)
        }
    }
}
