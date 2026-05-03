package dev.tabml.box

import android.content.Context

/** UTF-8 CSV export (comma-separated, RFC-style quoting) for spreadsheets. */
object WorkflowHubExporter {

    fun toCsv(context: Context): String = toCsv(WorkflowHubStore.load(context.applicationContext))

    fun toCsv(s: WorkflowHubSnapshot): String {
        return buildString {
            appendLine("# Tab ML Box — Operations hub export")
            appendLine("section,tasks")
            appendLine("id,title,details,due_ms,status,candidate_id,created_ms,updated_ms")
            for (t in s.tasks) {
                appendLine(
                    listOf(
                        t.id,
                        t.title,
                        t.details,
                        t.dueMs?.toString().orEmpty(),
                        t.status,
                        t.candidateId.orEmpty(),
                        t.createdMs.toString(),
                        t.updatedMs.toString(),
                    ).joinToString(",") { WorkflowHubCsv.escapeCell(it) },
                )
            }
            appendLine()
            appendLine("section,candidates")
            appendLine("id,name,org,role,stage,email,phone,notes,created_ms,updated_ms")
            for (c in s.candidates) {
                appendLine(
                    listOf(
                        c.id,
                        c.name,
                        c.org,
                        c.role,
                        c.stage,
                        c.email,
                        c.phone,
                        c.notes,
                        c.createdMs.toString(),
                        c.updatedMs.toString(),
                    ).joinToString(",") { WorkflowHubCsv.escapeCell(it) },
                )
            }
            appendLine()
            appendLine("section,routes")
            appendLine("id,name,waypoints_pipe_separated,created_ms,updated_ms")
            for (r in s.routes) {
                appendLine(
                    listOf(
                        r.id,
                        r.name,
                        r.waypoints.joinToString("|"),
                        r.createdMs.toString(),
                        r.updatedMs.toString(),
                    ).joinToString(",") { WorkflowHubCsv.escapeCell(it) },
                )
            }
        }
    }
}
