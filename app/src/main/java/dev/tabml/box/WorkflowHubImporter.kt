package dev.tabml.box

/**
 * Parses [WorkflowHubExporter] / [WorkflowHubCsv] format. Returns null on unrecognised or broken files.
 */
object WorkflowHubImporter {

    fun parseOrNull(text: String): WorkflowHubSnapshot? {
        val lines = WorkflowHubCsv.splitCsvPhysicalLines(text.trimStart('\uFEFF'))
        val tasks = mutableListOf<HubTask>()
        val candidates = mutableListOf<HubCandidate>()
        val routes = mutableListOf<HubRoute>()
        var i = 0
        try {
            while (i < lines.size) {
                val raw = lines[i]
                val line = raw.trim()
                i++
                if (line.isEmpty() || line.startsWith("#")) continue
                when {
                    line == "section,tasks" -> {
                        i = consumeHeaderAndRows(lines, i) { row ->
                            parseTaskRow(row)?.let { tasks.add(it) }
                        }
                    }
                    line == "section,candidates" -> {
                        i = consumeHeaderAndRows(lines, i) { row ->
                            parseCandidateRow(row)?.let { candidates.add(it) }
                        }
                    }
                    line == "section,routes" -> {
                        i = consumeHeaderAndRows(lines, i) { row ->
                            parseRouteRow(row)?.let { routes.add(it) }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            return null
        }
        if (tasks.isEmpty() && candidates.isEmpty() && routes.isEmpty()) return null
        return WorkflowHubSnapshot(tasks, candidates, routes)
    }

    private fun consumeHeaderAndRows(
        lines: List<String>,
        start: Int,
        row: (List<String>) -> Unit,
    ): Int {
        var i = start
        if (i < lines.size && lines[i].trim().startsWith("id,")) {
            i++
        }
        while (i < lines.size) {
            val t = lines[i].trim()
            if (t.isEmpty()) return i + 1
            if (t.startsWith("section,")) return i
            row(WorkflowHubCsv.parseLine(lines[i]))
            i++
        }
        return i
    }

    private fun parseTaskRow(p: List<String>): HubTask? {
        if (p.size < 8) return null
        val now = System.currentTimeMillis()
        return HubTask(
            id = p[0].ifBlank { return null },
            title = p[1],
            details = p.getOrElse(2) { "" },
            dueMs = p.getOrElse(3) { "" }.toLongOrNull(),
            status = p.getOrElse(4) { HubTaskStatus.OPEN }.ifBlank { HubTaskStatus.OPEN },
            candidateId = p.getOrElse(5) { "" }.takeIf { it.isNotBlank() },
            createdMs = p.getOrElse(6) { "" }.toLongOrNull() ?: now,
            updatedMs = p.getOrElse(7) { "" }.toLongOrNull() ?: now,
        )
    }

    private fun parseCandidateRow(p: List<String>): HubCandidate? {
        if (p.size < 10) return null
        val now = System.currentTimeMillis()
        return HubCandidate(
            id = p[0].ifBlank { return null },
            name = p[1].ifBlank { return null },
            org = p.getOrElse(2) { "" },
            role = p.getOrElse(3) { "" },
            stage = p.getOrElse(4) { "" },
            email = p.getOrElse(5) { "" },
            phone = p.getOrElse(6) { "" },
            notes = p.getOrElse(7) { "" },
            createdMs = p.getOrElse(8) { "" }.toLongOrNull() ?: now,
            updatedMs = p.getOrElse(9) { "" }.toLongOrNull() ?: now,
        )
    }

    private fun parseRouteRow(p: List<String>): HubRoute? {
        if (p.size < 5) return null
        val now = System.currentTimeMillis()
        val wps = p.getOrElse(2) { "" }.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (wps.isEmpty()) return null
        return HubRoute(
            id = p[0].ifBlank { return null },
            name = p[1].ifBlank { return null },
            waypoints = wps,
            createdMs = p.getOrElse(3) { "" }.toLongOrNull() ?: now,
            updatedMs = p.getOrElse(4) { "" }.toLongOrNull() ?: now,
        )
    }
}
