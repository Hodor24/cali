package dev.tabml.box

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.text.DateFormat as JavaDateFormat
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Local-first hub for recruitment / field ops: tasks, candidates, multi-stop routes.
 * JSON file on CE storage; included in Cali system prompt and updatable via ###ACTION JSON.
 */
object WorkflowHubStore {

    private const val FILE = "workflow_hub_v1.json"
    private val lock = ReentrantLock()

    fun load(context: Context): WorkflowHubSnapshot = lock.withLock {
        val f = File(context.filesDir, FILE)
        if (!f.isFile) return WorkflowHubSnapshot.empty()
        return try {
            WorkflowHubSnapshot.fromJson(JSONObject(f.readText()))
        } catch (_: Exception) {
            WorkflowHubSnapshot.empty()
        }
    }

    fun clear(context: Context) {
        lock.withLock {
            File(context.filesDir, FILE).delete()
        }
        WorkflowDueScheduler.cancelAll(context.applicationContext)
    }

    private fun save(context: Context, snap: WorkflowHubSnapshot) {
        val f = File(context.filesDir, FILE)
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(snap.toJson().toString(2))
        if (!tmp.renameTo(f)) {
            tmp.copyTo(f, overwrite = true)
            tmp.delete()
        }
    }

    fun upsert(context: Context, transform: (WorkflowHubSnapshot) -> WorkflowHubSnapshot) {
        lock.withLock {
            val next = transform(load(context))
            save(context, next)
        }
        WorkflowDueScheduler.sync(context.applicationContext)
    }

    /** Replaces the whole hub (CSV import — replace). */
    fun replaceSnapshot(context: Context, snap: WorkflowHubSnapshot) {
        lock.withLock { save(context, snap) }
        WorkflowDueScheduler.sync(context.applicationContext)
    }

    /** Merges by id: incoming overwrites existing with same id. */
    fun mergeSnapshot(context: Context, incoming: WorkflowHubSnapshot) {
        upsert(context) { existing ->
            val tasks = LinkedHashMap<String, HubTask>()
            existing.tasks.forEach { tasks[it.id] = it }
            incoming.tasks.forEach { tasks[it.id] = it }
            val candidates = LinkedHashMap<String, HubCandidate>()
            existing.candidates.forEach { candidates[it.id] = it }
            incoming.candidates.forEach { candidates[it.id] = it }
            val routes = LinkedHashMap<String, HubRoute>()
            existing.routes.forEach { routes[it.id] = it }
            incoming.routes.forEach { routes[it.id] = it }
            WorkflowHubSnapshot(
                tasks.values.toList(),
                candidates.values.toList(),
                routes.values.toList(),
            )
        }
    }

    fun addTask(
        context: Context,
        title: String,
        details: String = "",
        dueMs: Long? = null,
        candidateId: String? = null,
        status: String = HubTaskStatus.OPEN,
    ) {
        val now = System.currentTimeMillis()
        val t = title.trim()
        if (t.isEmpty()) return
        upsert(context) { s ->
            val id = UUID.randomUUID().toString()
            s.copy(tasks = s.tasks + HubTask(id, t, details.trim(), dueMs, status, candidateId, now, now))
        }
    }

    fun updateTask(context: Context, taskId: String, mutate: (HubTask) -> HubTask?) {
        upsert(context) { s ->
            val next = s.tasks.map { if (it.id == taskId) mutate(it) ?: it else it }
            s.copy(tasks = next)
        }
    }

    fun deleteTask(context: Context, taskId: String) {
        upsert(context) { s -> s.copy(tasks = s.tasks.filter { it.id != taskId }) }
    }

    fun addCandidate(
        context: Context,
        name: String,
        org: String = "",
        role: String = "",
        stage: String = "",
        email: String = "",
        phone: String = "",
        notes: String = "",
    ) {
        val n = name.trim()
        if (n.isEmpty()) return
        val now = System.currentTimeMillis()
        upsert(context) { s ->
            val id = UUID.randomUUID().toString()
            s.copy(
                candidates = s.candidates + HubCandidate(
                    id, n, org.trim(), role.trim(), stage.trim(),
                    email.trim(), phone.trim(), notes.trim(), now, now,
                ),
            )
        }
    }

    fun updateCandidate(context: Context, candidateId: String, mutate: (HubCandidate) -> HubCandidate?) {
        upsert(context) { s ->
            s.copy(candidates = s.candidates.map { if (it.id == candidateId) mutate(it) ?: it else it })
        }
    }

    fun deleteCandidate(context: Context, candidateId: String) {
        upsert(context) { s ->
            val now = System.currentTimeMillis()
            s.copy(
                candidates = s.candidates.filter { it.id != candidateId },
                tasks = s.tasks.map { t ->
                    if (t.candidateId == candidateId) t.copy(candidateId = null, updatedMs = now) else t
                },
            )
        }
    }

    fun addRoute(context: Context, name: String, waypoints: List<String>) {
        val label = name.trim()
        if (label.isEmpty()) return
        val wp = waypoints.map { it.trim() }.filter { it.isNotEmpty() }
        if (wp.isEmpty()) return
        val now = System.currentTimeMillis()
        upsert(context) { s ->
            val id = UUID.randomUUID().toString()
            s.copy(routes = s.routes + HubRoute(id, label, wp, now, now))
        }
    }

    fun deleteRoute(context: Context, routeId: String) {
        upsert(context) { s -> s.copy(routes = s.routes.filter { it.id != routeId }) }
    }

    fun findCandidateIdByName(snap: WorkflowHubSnapshot, name: String): String? {
        val q = name.trim().lowercase()
        if (q.isEmpty()) return null
        return snap.candidates.firstOrNull { it.name.lowercase() == q }?.id
            ?: snap.candidates.firstOrNull { it.name.lowercase().contains(q) }?.id
    }

    fun findTaskIdByTitleContains(snap: WorkflowHubSnapshot, q: String): String? {
        val t = q.trim().lowercase()
        if (t.isEmpty()) return null
        return snap.tasks.firstOrNull {
            it.status == HubTaskStatus.OPEN && it.title.lowercase().contains(t)
        }?.id
    }

    fun resolveTaskId(snap: WorkflowHubSnapshot, idOrPrefix: String): String? {
        val r = idOrPrefix.trim()
        if (r.isEmpty()) return null
        snap.tasks.forEach { if (it.id == r || it.id.startsWith(r)) return it.id }
        return null
    }

    fun resolveCandidateId(snap: WorkflowHubSnapshot, idOrPrefix: String): String? {
        val r = idOrPrefix.trim()
        if (r.isEmpty()) return null
        snap.candidates.forEach { if (it.id == r || it.id.startsWith(r)) return it.id }
        return null
    }

    fun resolveRouteId(snap: WorkflowHubSnapshot, idOrPrefix: String): String? {
        val r = idOrPrefix.trim()
        if (r.isEmpty()) return null
        snap.routes.forEach { if (it.id == r || it.id.startsWith(r)) return it.id }
        return null
    }

    /** Concise block for Cali system prompt (IDs included for machine updates). */
    fun buildCaliContextBlock(context: Context, maxChars: Int = 4000): String {
        val s = load(context)
        val fmt = JavaDateFormat.getDateTimeInstance(JavaDateFormat.SHORT, JavaDateFormat.SHORT)
        val sb = StringBuilder()
        sb.append("Tasks (").append(s.tasks.size).append("):\n")
        s.tasks.take(40).forEach { t ->
            val due = t.dueMs?.let { fmt.format(java.util.Date(it)) } ?: "—"
            sb.append("- [").append(t.id.take(8)).append("] ").append(t.title)
                .append(" | status=").append(t.status).append(" | due=").append(due)
            t.candidateId?.let { cid -> sb.append(" | candidateId=").append(cid.take(8)) }
            sb.append('\n')
        }
        sb.append("\nCandidates (").append(s.candidates.size).append("):\n")
        s.candidates.take(60).forEach { c ->
            sb.append("- [").append(c.id.take(8)).append("] ").append(c.name)
                .append(" | org=").append(c.org).append(" | role=").append(c.role)
                .append(" | stage=").append(c.stage)
                .append(" | ").append(c.email).append(" ").append(c.phone).append('\n')
        }
        sb.append("\nRoutes (").append(s.routes.size).append("):\n")
        s.routes.take(20).forEach { r ->
            sb.append("- [").append(r.id.take(8)).append("] ").append(r.name)
                .append(" → ").append(r.waypoints.joinToString(" → ")).append('\n')
        }
        var out = sb.toString().trimEnd()
        if (out.length > maxChars) {
            out = out.take(maxChars) + "\n…(truncated)"
        }
        return out
    }
}
