package dev.tabml.box

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Append-only JSON lines: each successful or failed assistant exchange, metadata only
 * (subject line, character counts, NVQ flag). No full user/body text.
 */
object SessionObservationStore {

    private const val FILE = "assistant_observations.jsonl"
    private const val MAX_READ_LINES = 500
    private const val SUBJECT_MAX = 120
    private const val PROMPT_SUMMARY_MAX_CHARS = 1200

    data class Record(
        val t: Long,
        val subj: String,
        val userChars: Int,
        val assistantChars: Int,
        val nvq: Boolean,
        val ok: Boolean,
    )

    fun append(
        context: Context,
        subject: String,
        userChars: Int,
        assistantChars: Int,
        nvqMode: Boolean,
        ok: Boolean,
    ) {
        if (!Prefs.observationLearning(context)) return
        val subj = subject.trim().replace("\n", " ").take(SUBJECT_MAX)
        val obj = JSONObject()
            .put("t", System.currentTimeMillis())
            .put("subj", subj)
            .put("uch", userChars)
            .put("ash", assistantChars)
            .put("nvq", nvqMode)
            .put("ok", ok)
        val line = obj.toString() + "\n"
        context.openFileOutput(FILE, Context.MODE_APPEND or Context.MODE_PRIVATE).use {
            it.write(line.toByteArray(Charsets.UTF_8))
        }
    }

    fun readRecords(context: Context): List<Record> {
        val f = File(context.filesDir, FILE)
        if (!f.isFile) return emptyList()
        val trimmed = f.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        val window = if (trimmed.size <= MAX_READ_LINES) {
            trimmed
        } else {
            trimmed.subList(trimmed.size - MAX_READ_LINES, trimmed.size)
        }
        return window.mapNotNull { line ->
            runCatching { parseRecord(line) }.getOrNull()
        }
    }

    private fun parseRecord(line: String): Record {
        val o = JSONObject(line)
        return Record(
            t = o.optLong("t", 0L),
            subj = o.optString("subj", ""),
            userChars = o.optInt("uch", 0),
            assistantChars = o.optInt("ash", 0),
            nvq = o.optBoolean("nvq", false),
            ok = o.optBoolean("ok", false),
        )
    }

    /** Short block for the assistant system prompt (recent window only). */
    fun buildPromptSummary(context: Context): String {
        if (!Prefs.observationLearning(context)) return ""
        val allOk = readRecords(context).filter { it.ok }
        if (allOk.isEmpty()) return ""
        val recent = allOk.takeLast(40)
        val avgUser = recent.map { it.userChars }.average().let { if (it.isNaN()) 0 else it.toInt() }
        val withAsst = recent.filter { it.assistantChars > 0 }
        val avgAsst = withAsst.map { it.assistantChars }.average().let { if (it.isNaN()) 0 else it.toInt() }
        val nvqPct = (recent.count { it.nvq } * 100 / recent.size.coerceAtLeast(1))
        val topSubjects = recent
            .filter { it.subj.isNotBlank() }
            .groupingBy { it.subj }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
        return buildString {
            append("Successful exchanges in window: ${recent.size} (of ${allOk.size} total logged).\n")
            append("Approx. typical user prompt length: ~$avgUser characters; assistant reply: ~$avgAsst characters.\n")
            append("Sessions with NVQ assessor brief enabled: ~$nvqPct%.\n")
            if (topSubjects.isNotEmpty()) {
                append("Repeating subject lines (use to streamline templates): ")
                append(topSubjects.joinToString("; ") { "${it.key} (${it.value}×)" })
                append('.')
            }
        }.take(PROMPT_SUMMARY_MAX_CHARS)
    }

    fun buildInsightsDisplay(context: Context): String {
        val lines = readRecords(context)
        if (lines.isEmpty()) {
            return context.getString(R.string.observations_insights_empty)
        }
        val ok = lines.count { it.ok }
        val fail = lines.size - ok
        val summary = buildPromptSummary(context).ifBlank { context.getString(R.string.observations_no_success_yet) }
        return buildString {
            append(context.getString(R.string.observations_insights_header, lines.size, ok, fail))
            append("\n\n")
            append(summary)
        }
    }

    fun exportText(context: Context): String {
        val f = File(context.filesDir, FILE)
        if (!f.isFile) return ""
        return f.readText()
    }

    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
    }

    fun hasFile(context: Context): Boolean = File(context.filesDir, FILE).isFile
}
