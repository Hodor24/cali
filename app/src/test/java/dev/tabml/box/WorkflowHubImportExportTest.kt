package dev.tabml.box

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkflowHubCsvTest {

    @Test
    fun escapeCell_quotesCommasAndNewlines() {
        assertEquals("a", WorkflowHubCsv.escapeCell("a"))
        assertEquals("\"a,b\"", WorkflowHubCsv.escapeCell("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", WorkflowHubCsv.escapeCell("say \"hi\""))
        assertEquals("\"line1\nline2\"", WorkflowHubCsv.escapeCell("line1\nline2"))
    }

    @Test
    fun parseLine_roundTripsQuotes() {
        val line = listOf("a", "b,c", "d\"e").joinToString(",") { WorkflowHubCsv.escapeCell(it) }
        assertEquals(listOf("a", "b,c", "d\"e"), WorkflowHubCsv.parseLine(line))
    }

    @Test
    fun parseLine_emptyFields() {
        assertEquals(listOf("", "", ""), WorkflowHubCsv.parseLine(",,"))
    }

    @Test
    fun parseLine_commasInsideQuotes() {
        val line = "task-1,\"Hello, world\",\"Line1\nLine2\",1700000000000,open,,100,200"
        val p = WorkflowHubCsv.parseLine(line)
        assertEquals(8, p.size)
        assertEquals("task-1", p[0])
        assertEquals("Hello, world", p[1])
        assertEquals("Line1\nLine2", p[2])
    }

    @Test
    fun splitCsvPhysicalLines_keepsNewlinesInsideQuotes() {
        val csv = "a,\"b\nc\",d\nx,y"
        val lines = WorkflowHubCsv.splitCsvPhysicalLines(csv)
        assertEquals(2, lines.size)
        assertEquals("a,\"b\nc\",d", lines[0])
        assertEquals("x,y", lines[1])
    }
}

class WorkflowHubImportExportTest {

    @Test
    fun exportImport_roundTrip_preservesData() {
        val t = HubTask(
            id = "task-1",
            title = "Hello, world",
            details = "Line1\nLine2",
            dueMs = 1_700_000_000_000L,
            status = HubTaskStatus.OPEN,
            candidateId = null,
            createdMs = 100L,
            updatedMs = 200L,
        )
        val c = HubCandidate(
            id = "cand-1",
            name = "Ada",
            org = "Org, Ltd",
            role = "Welder",
            stage = "Interview",
            email = "a@b.co",
            phone = "07123",
            notes = "Note",
            createdMs = 300L,
            updatedMs = 400L,
        )
        val r = HubRoute(
            id = "route-1",
            name = "Day run",
            waypoints = listOf("Stop A", "Stop,B"),
            createdMs = 500L,
            updatedMs = 600L,
        )
        val snap = WorkflowHubSnapshot(listOf(t), listOf(c), listOf(r))
        val csv = WorkflowHubExporter.toCsv(snap)
        val parsed = WorkflowHubImporter.parseOrNull(csv)!!
        assertEquals(1, parsed.tasks.size)
        assertEquals(t.id, parsed.tasks[0].id)
        assertEquals(t.title, parsed.tasks[0].title)
        assertEquals(t.details, parsed.tasks[0].details)
        assertEquals(t.dueMs, parsed.tasks[0].dueMs)
        assertEquals(t.status, parsed.tasks[0].status)
        assertEquals(1, parsed.candidates.size)
        assertEquals(c.name, parsed.candidates[0].name)
        assertEquals(c.org, parsed.candidates[0].org)
        assertEquals(1, parsed.routes.size)
        assertEquals(r.waypoints, parsed.routes[0].waypoints)
    }

    @Test
    fun parseOrNull_rejectsEmpty() {
        assertNull(WorkflowHubImporter.parseOrNull("   \n  "))
    }
}

class WorkflowDueSchedulerEarlyTest {

    @Test
    fun earlyReminderDelay_nullWhenLeadAlreadyPassed() {
        val due = 1_000_000L
        val now = due - WorkflowDueScheduler.EARLY_LEAD_MS + 1
        assertNull(WorkflowDueScheduler.earlyReminderDelayMsOrNull(now, due))
    }

    @Test
    fun earlyReminderDelay_positiveWhenInFuture() {
        val now = 1_000_000L
        val due = now + WorkflowDueScheduler.EARLY_LEAD_MS + 60_000L
        val d = WorkflowDueScheduler.earlyReminderDelayMsOrNull(now, due)
        assertEquals(60_000L, d)
    }
}
