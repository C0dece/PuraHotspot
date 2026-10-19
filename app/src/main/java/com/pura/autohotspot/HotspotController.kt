package com.pura.autohotspot

import android.content.Context
import android.util.Log

object HotspotController {
    private const val TAG = "HotspotController"

    // Fallback commands if Shizuku is active
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

    suspend fun setHotspotEnabled(enabled: Boolean, context: Context): Pair<Boolean, String> {
        val actionName = if (enabled) "Включение точки доступа" else "Выключение точки доступа"
        val logBuilder = StringBuilder()
        logBuilder.append("[$actionName]\n")

        // Priority 1: Check Shizuku (Pure programmatic ADB shell UID 2000, 100% silent, works locked, zero UI)
        if (ShizukuManager.hasPermission()) {
            logBuilder.append("Движок: Shizuku (ADB Shell - программный режим)\n")
            val commands = if (enabled) ENABLE_COMMANDS else DISABLE_COMMANDS
            for (cmd in commands) {
                logBuilder.append("> $cmd\n")
                val result = ShizukuManager.executeCommand(cmd)
                logBuilder.append("Код: ${result.exitCode}")
                if (result.output.isNotBlank()) logBuilder.append(", вывод: ${result.output}")
                if (result.error.isNotBlank()) logBuilder.append(", ошибка: ${result.error}")
                logBuilder.append("\n")

                if (result.isSuccess) {
                    logBuilder.append("Успешно выполнено программно через Shizuku: $cmd\n")
                    return Pair(true, logBuilder.toString())
                }
            }
            logBuilder.append("Shizuku не смог выполнить команду, пробуем альтернативы...\n")
        }

        // Priority 2: Fallback to Accessibility Service (Quick Settings shade)
        if (PuraAccessibilityService.isRunning()) {
            logBuilder.append("Движок: Служба доступности (Шторка переключателей)\n")
            val (success, accLogs) = PuraAccessibilityService.toggleHotspot(enabled, context)
            logBuilder.append(accLogs)
            if (success) {
                return Pair(true, logBuilder.toString())
            }
            logBuilder.append("Служба доступности не смогла переключить.\n")
        }

        // Neither engine is ready
        if (!PuraAccessibilityService.isRunning() && !ShizukuManager.hasPermission()) {
            logBuilder.append("Ошибка: ни один движок не активен!\n")
            logBuilder.append("Включите службу «Pura Hotspot» в «Специальные возможности» телефона.\n")
            return Pair(false, logBuilder.toString())
        }

        logBuilder.append("Не удалось выполнить $actionName ни одним методом.\n")
        return Pair(false, logBuilder.toString())
    }
}
