package dev.tabml.box

import android.view.accessibility.AccessibilityNodeInfo

internal object NvqAccessibilityTreeText {

    private const val MAX_DEPTH = 14
    private const val MAX_CHARS = 14_000

    /** Collects visible text from [root]; recycles [root] in a finally block. */
    fun collectAndRecycleRoot(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        return try {
            val sb = StringBuilder()
            walk(root, sb, 0)
            sb.toString()
        } finally {
            root.recycle()
        }
    }

    private fun walk(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        if (sb.length >= MAX_CHARS || depth > MAX_DEPTH) return
        node.text?.let { append(sb, it) }
        node.contentDescription?.let { append(sb, it) }
        val n = node.childCount
        for (i in 0 until n) {
            val child = node.getChild(i) ?: continue
            try {
                walk(child, sb, depth + 1)
            } finally {
                child.recycle()
            }
        }
    }

    private fun append(sb: StringBuilder, cs: CharSequence) {
        if (sb.isNotEmpty()) sb.append('\n')
        val room = MAX_CHARS - sb.length
        if (room <= 0) return
        val end = cs.length.coerceAtMost(room)
        sb.append(cs, 0, end)
    }
}
