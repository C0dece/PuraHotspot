package com.pura.autohotspot

import android.util.Log

object HotspotController {
    private const val TAG = "HotspotController"

    // Primary and fallback commands for Android 11, 12, 13, 14 (EMUI 14)
    private val ENABLE_COMMANDS = listOf(
        "cmd wifi start-softap",
        "cmd connectivity start-tethering wifi",
        "cmd tethering start-tethering wifi"
    )

    private val DISABLE_COMMANDS = listOf(
        "cmd wifi stop-softap",
        "cmd connectivity stop-tethering wifi",
        "cmd tethering stop-tethering wifi"
    )

    suspend fun setHotspotEnabled(enabled: Boolean): Pair<Boolean, String> {
        val commands = if (enabled) ENABLE_COMMANDS else DISABLE_COMMANDS
        val actionName = if (enabled) "Включение точки доступа" else "Выключение точки доступа"

        val logBuilder = StringBuilder()
        logBuilder.append("[$actionName]\n")

        for (cmd in commands) {
            logBuilder.append("> $cmd\n")
            val result = ShizukuManager.executeCommand(cmd)
            logBuilder.append("Код: ${result.exitCode}")
            if (result.output.isNotBlank()) logBuilder.append(", вывод: ${result.output}")
            if (result.error.isNotBlank()) logBuilder.append(", ошибка: ${result.error}")
            logBuilder.append("\n")

            Log.d(TAG, "Command: '$cmd' -> exitCode=${result.exitCode}, out='${result.output}', err='${result.error}'")

            // If exit code is 0, consider command successful
            if (result.isSuccess) {
                logBuilder.append("Успешно выполнено через: $cmd\n")
                return Pair(true, logBuilder.toString())
            }
        }

        logBuilder.append("Не удалось выполнить ни одну команду для $actionName.\n")
        return Pair(false, logBuilder.toString())
    }
}
