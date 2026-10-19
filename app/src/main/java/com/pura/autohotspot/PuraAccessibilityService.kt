package com.pura.autohotspot

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class PuraAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "PuraAccessibilityService подключена и готова")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events are handled on demand during toggleHotspot
    }

    override fun onInterrupt() {
        Log.d(TAG, "PuraAccessibilityService прервана")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.d(TAG, "PuraAccessibilityService уничтожена")
    }

    companion object {
        private const val TAG = "PuraAccessibility"
        var instance: PuraAccessibilityService? = null

        fun isRunning(): Boolean = instance != null

        @SuppressLint("MissingPermission")
        suspend fun toggleHotspot(targetState: Boolean, context: Context): Pair<Boolean, String> = withContext(Dispatchers.Main) {
            val service = instance
                ?: return@withContext Pair(false, "Служба доступности не включена пользователем")

            val log = StringBuilder()
            log.append("[Шторка переключателей]\n")

            // 1. Wake screen if it's off (so quick settings can be opened on lock screen)
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isScreenOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                powerManager.isInteractive
            } else {
                @Suppress("DEPRECATION")
                powerManager.isScreenOn
            }

            var wakeLock: PowerManager.WakeLock? = null
            if (!isScreenOn) {
                @Suppress("DEPRECATION")
                wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "PuraHotspot:WakeScreenForToggle"
                )
                wakeLock.acquire(3000L)
                log.append("Экран был выключен -> пробуждение экрана блокировки\n")
                delay(200) // Give screen time to turn on
            }

            // 2. Open Quick Settings shade
            val opened = service.performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            if (!opened) {
                wakeLock?.let { if (it.isHeld) it.release() }
                return@withContext Pair(false, "Не удалось открыть панель управления/шторку")
            }
            log.append("Открытие панели управления...\n")
            delay(350) // Wait for shade to animate open

            // 3. Find Hotspot tile
            val rootNode = service.rootInActiveWindow
            var tileNode: AccessibilityNodeInfo? = null

            if (rootNode != null) {
                tileNode = findHotspotTileNode(rootNode)
            }

            // If not found in active window, search all windows
            if (tileNode == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (window in service.windows) {
                    val root = window.root ?: continue
                    val node = findHotspotTileNode(root)
                    if (node != null) {
                        tileNode = node
                        break
                    }
                }
            }

            if (tileNode == null) {
                log.append("Значок «Точка доступа» не найден в шторке. Убедитесь, что он добавлен в панель управления.\n")
                // Close shade
                service.performGlobalAction(GLOBAL_ACTION_BACK)
                wakeLock?.let { if (it.isHeld) it.release() }
                return@withContext Pair(false, log.toString())
            }

            // 4. Inspect current tile state if available
            val stateDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                tileNode.stateDescription?.toString()?.lowercase() ?: ""
            } else {
                ""
            }
            val contentDesc = tileNode.contentDescription?.toString()?.lowercase() ?: ""
            val fullText = (stateDesc + " " + contentDesc).trim()

            val isCurrentlyOn = fullText.contains("вкл") || fullText.contains("on") || tileNode.isSelected || tileNode.isChecked
            val isCurrentlyOff = fullText.contains("выкл") || fullText.contains("off")

            log.append("Плитка найдена: '${tileNode.text ?: contentDesc}' (текущее: ${if (isCurrentlyOn) "ВКЛ" else if (isCurrentlyOff) "ВЫКЛ" else "неопределено"})\n")

            var clicked = false
            if (targetState && isCurrentlyOn) {
                log.append("Точка доступа уже включена, повторный клик не требуется.\n")
                clicked = true
            } else if (!targetState && isCurrentlyOff) {
                log.append("Точка доступа уже выключена, повторный клик не требуется.\n")
                clicked = true
            } else {
                // Click the tile
                val clickableNode = findClickableNode(tileNode) ?: tileNode
                clicked = clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                log.append("Клик по плитке точки доступа: ${if (clicked) "УСПЕШНО" else "ОШИБКА"}\n")
            }

            delay(150)

            // 5. Close quick settings shade
            service.performGlobalAction(GLOBAL_ACTION_BACK)
            log.append("Панель управления закрыта.\n")

            // 6. If screen was originally off in pocket, turn it back off!
            if (!isScreenOn) {
                delay(100)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    service.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                    log.append("Экран заблокирован и погашен.\n")
                }
                wakeLock?.let { if (it.isHeld) it.release() }
            }

            Pair(clicked, log.toString())
        }

        private fun findHotspotTileNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""

            val matchText = text.contains("точка доступа") || text.contains("личная точка") || text.contains("hotspot")
            val matchDesc = desc.contains("точка доступа") || desc.contains("личная точка") || desc.contains("hotspot")

            if (matchText || matchDesc) {
                return node
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                val found = findHotspotTileNode(child)
                if (found != null) return found
            }
            return null
        }

        private fun findClickableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
            var curr = node
            while (curr != null) {
                if (curr.isClickable) return curr
                curr = curr.parent
            }
            return null
        }
    }
}
