package dev.tabml.box

import org.json.JSONArray
import org.json.JSONObject

data class WorkflowHubSnapshot(
    val tasks: List<HubTask>,
    val candidates: List<HubCandidate>,
    val routes: List<HubRoute>,
) {
    companion object {
        const val JSON_VERSION = 1

        fun empty() = WorkflowHubSnapshot(emptyList(), emptyList(), emptyList())

        fun fromJson(root: JSONObject): WorkflowHubSnapshot {
            if (root.optInt("v", 0) < 1) return empty()
            val tasks = mutableListOf<HubTask>()
            val ta = root.optJSONArray("tasks") ?: JSONArray()
            for (i in 0 until ta.length()) {
                runCatching { tasks.add(HubTask.fromJson(ta.getJSONObject(i))) }
            }
            val candidates = mutableListOf<HubCandidate>()
            val ca = root.optJSONArray("candidates") ?: JSONArray()
            for (i in 0 until ca.length()) {
                runCatching { candidates.add(HubCandidate.fromJson(ca.getJSONObject(i))) }
            }
            val routes = mutableListOf<HubRoute>()
            val ra = root.optJSONArray("routes") ?: JSONArray()
            for (i in 0 until ra.length()) {
                runCatching { routes.add(HubRoute.fromJson(ra.getJSONObject(i))) }
            }
            return WorkflowHubSnapshot(tasks, candidates, routes)
        }
    }

    fun toJson(): JSONObject {
        val ta = JSONArray()
        tasks.forEach { ta.put(it.toJson()) }
        val ca = JSONArray()
        candidates.forEach { ca.put(it.toJson()) }
        val ra = JSONArray()
        routes.forEach { ra.put(it.toJson()) }
        return JSONObject()
            .put("v", JSON_VERSION)
            .put("tasks", ta)
            .put("candidates", ca)
            .put("routes", ra)
    }
}

data class HubTask(
    val id: String,
    val title: String,
    val details: String = "",
    val dueMs: Long? = null,
    val status: String = HubTaskStatus.OPEN,
    val candidateId: String? = null,
    val createdMs: Long,
    val updatedMs: Long,
) {
    companion object {
        fun fromJson(o: JSONObject): HubTask = HubTask(
            id = o.getString("id"),
            title = o.getString("title"),
            details = o.optString("details", ""),
            dueMs = if (o.has("dueMs") && !o.isNull("dueMs")) o.optLong("dueMs", 0L).takeIf { it > 0L } else null,
            status = o.optString("status", HubTaskStatus.OPEN),
            candidateId = o.optString("candidateId", "").takeIf { it.isNotEmpty() },
            createdMs = o.optLong("createdMs", 0L),
            updatedMs = o.optLong("updatedMs", 0L),
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("details", details)
        .put("dueMs", dueMs ?: JSONObject.NULL)
        .put("status", status)
        .put("candidateId", candidateId ?: JSONObject.NULL)
        .put("createdMs", createdMs)
        .put("updatedMs", updatedMs)
}

object HubTaskStatus {
    const val OPEN = "open"
    const val DONE = "done"
    const val CANCELLED = "cancelled"
}

data class HubCandidate(
    val id: String,
    val name: String,
    val org: String = "",
    val role: String = "",
    val stage: String = "",
    val email: String = "",
    val phone: String = "",
    val notes: String = "",
    val createdMs: Long,
    val updatedMs: Long,
) {
    companion object {
        fun fromJson(o: JSONObject): HubCandidate = HubCandidate(
            id = o.getString("id"),
            name = o.getString("name"),
            org = o.optString("org", ""),
            role = o.optString("role", ""),
            stage = o.optString("stage", ""),
            email = o.optString("email", ""),
            phone = o.optString("phone", ""),
            notes = o.optString("notes", ""),
            createdMs = o.optLong("createdMs", 0L),
            updatedMs = o.optLong("updatedMs", 0L),
        )
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("org", org)
        .put("role", role)
        .put("stage", stage)
        .put("email", email)
        .put("phone", phone)
        .put("notes", notes)
        .put("createdMs", createdMs)
        .put("updatedMs", updatedMs)
}

data class HubRoute(
    val id: String,
    val name: String,
    val waypoints: List<String>,
    val createdMs: Long,
    val updatedMs: Long,
) {
    companion object {
        fun fromJson(o: JSONObject): HubRoute {
            val wp = mutableListOf<String>()
            val arr = o.optJSONArray("waypoints") ?: JSONArray()
            for (i in 0 until arr.length()) {
                wp.add(arr.optString(i, "").trim())
            }
            return HubRoute(
                id = o.getString("id"),
                name = o.getString("name"),
                waypoints = wp.filter { it.isNotEmpty() },
                createdMs = o.optLong("createdMs", 0L),
                updatedMs = o.optLong("updatedMs", 0L),
            )
        }
    }

    fun toJson(): JSONObject {
        val arr = JSONArray()
        waypoints.forEach { arr.put(it) }
        return JSONObject()
            .put("id", id)
            .put("name", name)
            .put("waypoints", arr)
            .put("createdMs", createdMs)
            .put("updatedMs", updatedMs)
    }
}
