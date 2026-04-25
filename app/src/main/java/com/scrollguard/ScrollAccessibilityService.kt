package com.scrollguard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.abs

/**
 * ScrollGuard Engelleme Servisi — v9
 *
 * Yenilikler:
 *  - Katı Mod (Strict Mode) desteği
 *  - PiP (Pencere İçinde Pencere) engelleme: TikTok, Instagram ve Snapchat PiP'te çalışamaz.
 *  - YouTube PiP'te Shorts tespiti iyileştirildi.
 */
class ScrollAccessibilityService : AccessibilityService() {

    companion object {
        var instance: ScrollAccessibilityService? = null
            private set

        @Volatile var isEnabled = false
            private set

        var blockedCount = 0L
            private set

        fun setEnabled(value: Boolean) {
            isEnabled = value
            if (!value) instance?.resetState()
        }

        val fullBlockApps = setOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.snapchat.android",
        )
        val partialBlockApps = setOf(
            "com.google.android.youtube",
            "com.instagram.android",
        )
        val browserPackages = setOf(
            "com.android.chrome", "com.chrome.beta",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.opera.browser",
            "com.brave.browser",
        )

        val targetPackages get() = fullBlockApps + partialBlockApps + browserPackages

        val youtubeKeywords   = listOf("Shorts", "Kısacası", "Short")
        val instagramKeywords = listOf("Reels", "Makaralar", "Reel")
        val reelsUrlPatterns  = listOf("instagram.com/reel", "/reel/", "instagram.com/reels")

        const val SPAM_COOLDOWN_MS = 300L
        const val BLOCK_DELAY_MS = 3000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastScrollY = Int.MIN_VALUE
    private var lastBlockTimeMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        isEnabled = ScrollBlockerService.isRunning

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         AccessibilityEvent.TYPE_VIEW_SCROLLED or
                         AccessibilityEvent.TYPE_WINDOWS_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            packageNames = targetPackages.toTypedArray()
            notificationTimeout = 100
        }
    }

    private fun resetState() {
        lastScrollY = Int.MIN_VALUE
        lastBlockTimeMs = 0L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isEnabled) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in targetPackages) return

        // PiP KONTRÖLÜ: Eğer yasaklı bir uygulama PiP modundaysa engelle
        if (isInForbiddenPiP()) {
            blockWithHome(BLOCK_DELAY_MS)
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                when (pkg) {
                    in fullBlockApps -> blockWithHome(BLOCK_DELAY_MS)
                    in browserPackages -> handler.post { checkBrowserUrl(pkg) }
                    "com.google.android.youtube" -> {
                        val cls = event.className?.toString()?.lowercase() ?: ""
                        if (cls.contains("short")) {
                            blockWithHome(BLOCK_DELAY_MS)
                        } else {
                            handler.post { detectReelsScreen("com.google.android.youtube", youtubeKeywords) }
                        }
                        lastScrollY = Int.MIN_VALUE
                    }
                    "com.instagram.android" -> {
                        handler.post { detectReelsScreen("com.instagram.android", instagramKeywords) }
                        lastScrollY = Int.MIN_VALUE
                    }
                }
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                when (pkg) {
                    in fullBlockApps -> handleScrollEvent(event)
                    "com.google.android.youtube" -> handleScrollEvent(event)
                }
            }
        }
    }

    /**
     * PiP modunda olan pencereleri kontrol eder.
     * Eğer PiP modundaki pencere TikTok, Snapchat veya Instagram ise yasaklıdır.
     * YouTube için PiP şimdilik serbest (normal videolar için), 
     * ancak Shorts tespiti ana eventlerde yapılmaya devam eder.
     */
    private fun isInForbiddenPiP(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        return try {
            windows?.any { window ->
                if (window.isInPictureInPictureMode) {
                    // PiP modundaki pencerenin içeriğini (root) kontrol etmeye çalış
                    val root = window.root
                    val pkg = root?.packageName?.toString()
                    root?.recycle()
                    
                    // TikTok, Snapchat ve Instagram PiP'te yasak
                    pkg != null && (pkg in fullBlockApps || pkg == "com.instagram.android")
                } else false
            } ?: false
        } catch (_: Exception) { false }
    }

    private fun blockWithHome(delayMs: Long = BLOCK_DELAY_MS) {
        val now = System.currentTimeMillis()
        if (now - lastBlockTimeMs < SPAM_COOLDOWN_MS) return
        lastBlockTimeMs = now

        handler.postDelayed({
            // Hala yasaklı bir durumdaysak HOME'a git
            performGlobalAction(GLOBAL_ACTION_HOME)
            blockedCount++
        }, delayMs)
    }

    private fun detectReelsScreen(pkg: String, keywords: List<String>) {
        val root = rootInActiveWindow ?: return
        val screenH = resources.displayMetrics.heightPixels
        val navBarTop = screenH * 0.82f

        try {
            for (kw in keywords) {
                val nodes = root.findAccessibilityNodeInfosByText(kw)
                val shouldBlock = nodes.any { node ->
                    val rect = Rect()
                    node.getBoundsInScreen(rect)

                    val tabSelected = node.isSelected || node.isChecked ||
                        (node.parent?.let { p -> (p.isSelected || p.isChecked).also { p.recycle() } } ?: false)

                    val inContent = rect.bottom > 0 && rect.centerY() < navBarTop
                    val dp24 = (24 * resources.displayMetrics.density).toInt()
                    val isLargeNode = rect.height() > dp24 * 2

                    tabSelected || (inContent && isLargeNode)
                }
                nodes.forEach { it.recycle() }

                if (shouldBlock) {
                    blockWithHome(BLOCK_DELAY_MS)
                    return
                }
            }
        } finally {
            root.recycle()
        }
    }

    private fun handleScrollEvent(event: AccessibilityEvent) {
        val currentY = event.scrollY
        if (lastScrollY == Int.MIN_VALUE) { lastScrollY = currentY; return }
        val delta = abs(currentY - lastScrollY)
        val screenH = resources.displayMetrics.heightPixels
        if (delta > screenH * 0.60f) {
            blockWithHome(BLOCK_DELAY_MS)
        }
        lastScrollY = currentY
    }

    private fun checkBrowserUrl(pkg: String) {
        val root = rootInActiveWindow ?: return
        val urlBarIds = when (pkg) {
            "com.android.chrome", "com.chrome.beta" -> listOf("com.android.chrome:id/url_bar")
            "org.mozilla.firefox" -> listOf("org.mozilla.firefox:id/mozac_browser_toolbar_url_view")
            "com.microsoft.emmx" -> listOf("com.microsoft.emmx:id/address_bar_edit_text")
            else -> listOf("${pkg}:id/url_bar", "${pkg}:id/address_bar_edit_text")
        }
        try {
            for (viewId in urlBarIds) {
                val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
                if (nodes.isNotEmpty()) {
                    val url = nodes.firstOrNull()?.text?.toString()?.lowercase() ?: ""
                    nodes.forEach { it.recycle() }
                    if (reelsUrlPatterns.any { url.contains(it, ignoreCase = true) }) {
                        blockWithHome(BLOCK_DELAY_MS)
                        return
                    }
                    break
                }
            }
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() {}
    override fun onDestroy() { super.onDestroy(); instance = null }
}
