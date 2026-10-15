package com.pura.autohotspot

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.reflect.Method

data class CommandResult(
    val exitCode: Int,
    val output: String,
    val error: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

object ShizukuManager {
    const val REQUEST_CODE_SHIZUKU = 1001

    private val newProcessMethod: Method? by lazy {
        try {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply {
                isAccessible = true
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    fun isShizukuAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun hasPermission(): Boolean {
        if (!isShizukuAvailable()) return false
        return try {
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Throwable) {
            false
        }
    }

    fun requestPermission(requestCode: Int = REQUEST_CODE_SHIZUKU) {
        if (!isShizukuAvailable()) return
        try {
            if (!hasPermission()) {
                Shizuku.requestPermission(requestCode)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    suspend fun executeCommand(command: String): CommandResult = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            return@withContext CommandResult(-1, "", "Shizuku не подключен или нет разрешения")
        }

        val method = newProcessMethod
            ?: return@withContext CommandResult(-1, "", "Не удалось получить метод Shizuku.newProcess")

        try {
            val process = method.invoke(
                null,
                arrayOf("sh", "-c", command),
                null,
                null
            ) as Process

            val output = BufferedReader(InputStreamReader(process.inputStream)).readText().trim()
            val error = BufferedReader(InputStreamReader(process.errorStream)).readText().trim()
            val exitCode = process.waitFor()
            CommandResult(exitCode, output, error)
        } catch (e: Throwable) {
            CommandResult(-1, "", "Исключение при выполнении команды: ${e.localizedMessage}")
        }
    }
}
