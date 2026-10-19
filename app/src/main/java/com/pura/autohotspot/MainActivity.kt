package com.pura.autohotspot

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pura.autohotspot.databinding.ActivityMainBinding
import rikka.shizuku.Shizuku
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PreferencesManager
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private var pairedDevicesList = mutableListOf<BluetoothDevice>()
    private val delayOptions = listOf(0, 15, 30, 60)
    private val delayLabels = listOf("Без задержки (0 сек)", "15 секунд (рекомендуется)", "30 секунд", "1 минута")

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        updateShizukuStatus()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        updateShizukuStatus()
    }
    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _: Int, grantResult: Int ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                appendLog("Разрешение Shizuku получено!")
            } else {
                appendLog("В разрешении Shizuku отказано.")
            }
            updateShizukuStatus()
        }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val message = intent?.getStringExtra(HotspotService.EXTRA_LOG_MESSAGE)
            if (!message.isNullOrBlank()) {
                appendLog(message)
            }
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val btGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            true
        }

        if (btGranted) {
            loadPairedDevices()
        } else {
            appendLog("Требуется разрешение Bluetooth для поиска сопряженных устройств.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferencesManager(this)

        initShizukuListeners()
        setupDelaySpinner()
        setupListeners()
        checkAndRequestAndroidPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
        updateShizukuStatus()
        loadPairedDevices()
        syncUiState()

        val filter = IntentFilter(HotspotService.ACTION_LOG_UPDATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(logReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            unregisterReceiver(logReceiver)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
    }

    private fun initShizukuListeners() {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        updateAccessibilityStatus()
        updateShizukuStatus()
    }

    private fun updateAccessibilityStatus() {
        val isAccRunning = PuraAccessibilityService.isRunning()
        if (isAccRunning) {
            binding.tvAccessibilityStatus.text = "🟢 Служба активна (Работает в кармане без ПК)"
            binding.tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            binding.btnEnableAccessibility.visibility = View.GONE
        } else {
            binding.tvAccessibilityStatus.text = "🔴 Служба не включена в системе"
            binding.tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.status_red))
            binding.btnEnableAccessibility.visibility = View.VISIBLE
        }
    }

    private fun updateShizukuStatus() {
        if (!ShizukuManager.isShizukuAvailable()) {
            binding.tvShizukuStatus.text = "⚪ Резервный Shizuku (ADB): не запущен"
        } else if (!ShizukuManager.hasPermission()) {
            binding.tvShizukuStatus.text = "🟡 Резервный Shizuku: ожидает разрешения"
        } else {
            binding.tvShizukuStatus.text = "🟢 Резервный Shizuku: подключен (UID 2000)"
        }
    }

    private fun setupDelaySpinner() {
        val adapter = ArrayAdapter(this, R.layout.spinner_item, delayLabels).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        binding.spDelay.adapter = adapter

        val currentDelay = prefs.disconnectDelaySeconds
        val selectedIndex = delayOptions.indexOf(currentDelay).let { if (it >= 0) it else 1 }
        binding.spDelay.setSelection(selectedIndex)

        binding.spDelay.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.disconnectDelaySeconds = delayOptions[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupListeners() {
        binding.btnEnableAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "Найдите «Pura Hotspot» в списке и включите", Toast.LENGTH_LONG).show()
        }

        binding.btnRefreshDevices.setOnClickListener {
            checkAndRequestAndroidPermissions()
            loadPairedDevices()
        }

        binding.switchService.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (prefs.targetDeviceAddress.isNullOrBlank()) {
                    Toast.makeText(this, "Сначала выберите целевое устройство!", Toast.LENGTH_SHORT).show()
                    binding.switchService.isChecked = false
                    return@setOnCheckedChangeListener
                }
                if (!PuraAccessibilityService.isRunning() && !ShizukuManager.hasPermission()) {
                    Toast.makeText(this, "Включите службу Pura Hotspot в Спец. возможностях!", Toast.LENGTH_LONG).show()
                    binding.switchService.isChecked = false
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                    return@setOnCheckedChangeListener
                }

                prefs.isServiceEnabled = true
                HotspotService.start(this)
                appendLog("Служба мониторинга включена для: ${prefs.targetDeviceName}")
            } else {
                prefs.isServiceEnabled = false
                HotspotService.stop(this)
                appendLog("Служба мониторинга остановлена")
            }
        }

        binding.btnBatteryOptimization.setOnClickListener {
            openBatterySettings()
        }

        binding.btnTestStartHotspot.setOnClickListener {
            scope.launch {
                appendLog("Ручной тест: включение точки доступа...")
                val (success, logs) = HotspotController.setHotspotEnabled(true, this@MainActivity)
                appendLog(logs)
            }
        }

        binding.btnTestStopHotspot.setOnClickListener {
            scope.launch {
                appendLog("Ручной тест: выключение точки доступа...")
                val (success, logs) = HotspotController.setHotspotEnabled(false, this@MainActivity)
                appendLog(logs)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadPairedDevices() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()

        if (adapter == null) {
            appendLog("Bluetooth адаптер недоступен на устройстве")
            return
        }

        val bonded = adapter.bondedDevices ?: emptySet()
        pairedDevicesList.clear()
        pairedDevicesList.addAll(bonded)

        val deviceNames = pairedDevicesList.map { "${it.name ?: "Неизвестно"} (${it.address})" }.toMutableList()
        if (deviceNames.isEmpty()) {
            deviceNames.add("Нет сопряженных устройств")
        }

        val spinnerAdapter = ArrayAdapter(this, R.layout.spinner_item, deviceNames).apply {
            setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        binding.spDevices.adapter = spinnerAdapter

        val savedAddress = prefs.targetDeviceAddress
        val selectedIndex = pairedDevicesList.indexOfFirst { it.address == savedAddress }
        if (selectedIndex >= 0) {
            binding.spDevices.setSelection(selectedIndex)
        }

        binding.spDevices.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position < pairedDevicesList.size) {
                    val device = pairedDevicesList[position]
                    prefs.targetDeviceAddress = device.address
                    prefs.targetDeviceName = device.name ?: device.address
                    appendLog("Выбрано устройство: ${device.name} [${device.address}]")
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun syncUiState() {
        binding.switchService.isChecked = prefs.isServiceEnabled
    }

    private fun checkAndRequestAndroidPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    @SuppressLint("BatteryLife")
    private fun openBatterySettings() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } else {
                // Open App Info to allow configuring "Launch" / "Запуск приложений" on EMUI
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        } catch (e: Throwable) {
            Toast.makeText(this, "Откройте Настройки -> Батарея -> Запуск приложений", Toast.LENGTH_LONG).show()
        }
    }

    private fun appendLog(text: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val currentLogs = binding.tvLogs.text.toString()
        val newLogs = "[$time] $text\n" + currentLogs.take(1500)
        binding.tvLogs.text = newLogs
    }
}
