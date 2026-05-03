package dev.tabml.box

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicLong

/**
 * When enabled in Android Accessibility settings + app opt-in, reacts to window changes
 * and prompts (notification + in-app flag) if visible text may relate to construction NVQs.
 * Does not store screen contents — only checks keywords locally.
 */
class NvqWatchAccessibilityService : AccessibilityService() {

    private val lastWindowProbeMs = AtomicLong(0L)

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!Prefs.nvqWatchAppOptIn(this)) return
        if (Prefs.nvqWatchNeverSuggest(this)) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val now = System.currentTimeMillis()
        if (now - lastWindowProbeMs.get() < DEBOUNCE_MS) return
        lastWindowProbeMs.set(now)

        val sb = StringBuilder()
        event.text?.forEach { chunk -> sb.append(chunk).append('\n') }
        if (event.contentDescription != null) {
            sb.append(event.contentDescription).append('\n')
        }
        val root = rootInActiveWindow
        if (root != null) {
            sb.append(NvqAccessibilityTreeText.collectAndRecycleRoot(root))
        }
        val blob = sb.toString()
        if (!NvqScreenKeywordMatcher.matches(blob)) return

        NvqWatchNotifier.maybePromptUser(this, getString(R.string.nvq_watch_reason_keyword_match))
    }

    override fun onInterrupt() {}

    companion object {
        private const val DEBOUNCE_MS = 2500L
    }
}
