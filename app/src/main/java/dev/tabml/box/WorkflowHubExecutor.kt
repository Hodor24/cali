package dev.tabml.box

import android.content.Context
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/**
 * Applies workflow hub mutations from Cali's ###ACTION JSON tail (same object as web_search etc.).
 */
object WorkflowHubExecutor {

    fun applyFromAssistantJson(context: Context, json: JSONObject?, silent: Boolean = false): Int {
        if (json == null) return 0
        var n = 0
        json.optJSONObject("add_task")?.let { o ->
            if (applyAddTask(context, o)) n++
        }
        json.optJSONObject("add_candidate")?.let { o ->
            if (applyAddCandidate(context, o)) n++
        }
        json.optJSONObject("add_route")?.let { o ->
            if (applyAddRoute(context, o)) n++
        }
        json.optJSONObject("complete_task")?.let { o ->
            if (applyCompleteTask(context, o)) n++
        }
        json.optJSONObject("set_candidate_stage")?.let { o ->
            if (applySetCandidateStage(context, o)) n++
        }
        if (n > 0 && !silent) {
            Toast.makeText(context.applicationContext, context.getString(R.string.workflow_hub_updated_toast, n), Toast.LENGTH_SHORT).show()
        }
        return n
    }

    private fun applyAddTask(context: Context, o: JSONObject): Boolean {
        val title = o.optString("title", "").trim()
        if (title.isEmpty()) return false
        val details = o.optString("details", "")
        val dueMs = when {
            o.has("due_ms") && !o.isNull("due_ms") -> o.optLong("due_ms", 0L).takeIf { it > 0L }
            o.has("dueMs") && !o.isNull("dueMs") -> o.optLong("dueMs", 0L).takeIf { it > 0L }
            else -> null
        }
        val snap = WorkflowHubStore.load(context)
        var cid: String? = null
        val cName = o.optString("candidate_name", "").trim()
        if (cName.isNotEmpty()) {
            cid = WorkflowHubStore.findCandidateIdByName(snap, cName)
        }
        o.optString("candidate_id", "").trim().takeIf { it.isNotEmpty() }?.let {
            cid = WorkflowHubStore.resolveCandidateId(snap, it) ?: cid
        }
        WorkflowHubStore.addTask(context, title, details, dueMs, cid)
        return true
    }

    private fun applyAddCandidate(context: Context, o: JSONObject): Boolean {
        val name = o.optString("name", "").trim()
        if (name.isEmpty()) return false
        WorkflowHubStore.addCandidate(
            context,
            name,
            org = o.optString("org", ""),
            role = o.optString("role", ""),
            stage = o.optString("stage", ""),
            email = o.optString("email", ""),
            phone = o.optString("phone", ""),
            notes = o.optString("notes", ""),
        )
        return true
    }

    private fun applyAddRoute(context: Context, o: JSONObject): Boolean {
        val label = o.optString("name", "").trim()
        if (label.isEmpty()) return false
        val arr = o.optJSONArray("waypoints") ?: JSONArray()
        val wps = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val s = arr.optString(i, "").trim()
            if (s.isNotEmpty()) wps.add(s)
        }
        if (wps.isEmpty()) return false
        WorkflowHubStore.addRoute(context, label, wps)
        return true
    }

    private fun applyCompleteTask(context: Context, o: JSONObject): Boolean {
        val snap = WorkflowHubStore.load(context)
        val idRef = o.optString("task_id", o.optString("taskId", "")).trim()
        val tid = when {
            idRef.isNotEmpty() -> WorkflowHubStore.resolveTaskId(snap, idRef)
            else -> {
                val t = o.optString("title_contains", o.optString("titleContains", ""))
                WorkflowHubStore.findTaskIdByTitleContains(snap, t)
            }
        } ?: return false
        val now = System.currentTimeMillis()
        WorkflowHubStore.updateTask(context, tid) { t ->
            t.copy(status = HubTaskStatus.DONE, updatedMs = now)
        }
        return true
    }

    private fun applySetCandidateStage(context: Context, o: JSONObject): Boolean {
        val stage = o.optString("stage", "").trim()
        if (stage.isEmpty()) return false
        val snap = WorkflowHubStore.load(context)
        val idRef = o.optString("candidate_id", o.optString("candidateId", "")).trim()
        val cid = when {
            idRef.isNotEmpty() -> WorkflowHubStore.resolveCandidateId(snap, idRef)
            else -> {
                val n = o.optString("candidate_name", o.optString("candidateName", ""))
                WorkflowHubStore.findCandidateIdByName(snap, n)
            }
        } ?: return false
        val now = System.currentTimeMillis()
        WorkflowHubStore.updateCandidate(context, cid) { c -> c.copy(stage = stage, updatedMs = now) }
        return true
    }
}
