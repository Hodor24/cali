package dev.tabml.box

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.util.Calendar
import java.util.Locale

class OpsListFragment : Fragment() {

    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private val adapter = OpsRowAdapter { pos -> onItemClick(pos) }

    private var highlightTaskId: String? = null
    private var highlightClearRunnable: Runnable? = null

    private val requestNotifAfterDueSave = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val act = activity ?: return@registerForActivityResult
        if (!granted) OpsNotifPermission.showBlockedFallbackIfNeeded(act)
        (act as? OperationsActivity)?.syncNotificationBanner()
    }

    private fun tabArg(): Int = arguments?.getInt(ARG_TAB) ?: 0

    fun showCreateDialog() {
        when (tabArg()) {
            0 -> showTaskDialog(null)
            1 -> showCandidateDialog(null)
            else -> showRouteDialog(null)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_ops_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recycler = view.findViewById(R.id.recyclerOps)
        empty = view.findViewById(R.id.textOpsEmpty)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        reload()
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    fun reload() {
        if (!isAdded) return
        val snap = WorkflowHubStore.load(requireContext())
        val rows = when (tabArg()) {
            0 -> snap.tasks.sortedByDescending { it.updatedMs }.map { OpsRow.TaskRow(it) }
            1 -> snap.candidates.sortedByDescending { it.updatedMs }.map { OpsRow.CandidateRow(it) }
            else -> snap.routes.sortedByDescending { it.updatedMs }.map { OpsRow.RouteRow(it) }
        }
        adapter.submit(rows)
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun onItemClick(position: Int) {
        when (val row = adapter.itemAt(position)) {
            is OpsRow.TaskRow -> showTaskDialog(row.t)
            is OpsRow.CandidateRow -> showCandidateDialog(row.c)
            is OpsRow.RouteRow -> showRouteDialog(row.r)
            null -> Unit
        }
    }

    fun scrollToTaskId(taskId: String) {
        if (tabArg() != 0 || !isAdded) return
        highlightClearRunnable?.let { recycler.removeCallbacks(it) }
        highlightTaskId = taskId
        val snap = WorkflowHubStore.load(requireContext())
        val rows = snap.tasks.sortedByDescending { it.updatedMs }
        val idx = rows.indexOfFirst { it.id == taskId || it.id.startsWith(taskId) }
        if (idx < 0) return
        adapter.notifyDataSetChanged()
        recycler.post {
            recycler.smoothScrollToPosition(idx)
            val clearer = Runnable {
                highlightTaskId = null
                adapter.notifyDataSetChanged()
                highlightClearRunnable = null
            }
            highlightClearRunnable = clearer
            recycler.postDelayed(clearer, 2800)
        }
    }

    private fun showTaskDialog(existing: HubTask?) {
        val ctx = requireContext()
        val v = layoutInflater.inflate(R.layout.dialog_ops_task, null, false)
        val title = v.findViewById<TextInputEditText>(R.id.editOpsTitle)
        val details = v.findViewById<TextInputEditText>(R.id.editOpsDetails)
        val dueDisplay = v.findViewById<TextInputEditText>(R.id.editOpsDueDisplay)
        val btnPickDue = v.findViewById<MaterialButton>(R.id.btnPickDue)
        val btnClearDue = v.findViewById<MaterialButton>(R.id.btnClearDue)
        val cand = v.findViewById<TextInputEditText>(R.id.editOpsCandidateRef)

        var dueMsLocal: Long? = existing?.dueMs
        fun refreshDueField() {
            dueDisplay.setText(
                dueMsLocal?.let { OpsDateTime.formatDateTime(ctx, it) }
                    ?: getString(R.string.ops_due_none),
            )
        }
        existing?.let {
            title.setText(it.title)
            details.setText(it.details)
            it.candidateId?.let { id -> cand.setText(id) }
        }
        refreshDueField()
        dueDisplay.setOnClickListener { pickDueMillis(dueMsLocal) { picked -> dueMsLocal = picked; refreshDueField() } }
        btnPickDue.setOnClickListener { pickDueMillis(dueMsLocal) { picked -> dueMsLocal = picked; refreshDueField() } }
        btnClearDue.setOnClickListener { dueMsLocal = null; refreshDueField() }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(if (existing == null) R.string.ops_add_task else R.string.ops_edit_task)
            .setView(v)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.ops_add_calendar) { _, _ ->
                val ti = title.text?.toString().orEmpty().trim()
                if (ti.isEmpty()) {
                    Toast.makeText(ctx, R.string.ops_calendar_needs_title, Toast.LENGTH_SHORT).show()
                    return@setNeutralButton
                }
                val dueMs = dueMsLocal ?: existing?.dueMs
                if (dueMs == null) {
                    Toast.makeText(ctx, R.string.ops_calendar_needs_due, Toast.LENGTH_SHORT).show()
                    return@setNeutralButton
                }
                val desc = details.text?.toString().orEmpty()
                val end = dueMs + 3_600_000L
                CalendarEventIntents.insertTaskEvent(ctx, ti, desc, dueMs, end)
            }
            .setPositiveButton(R.string.ai_save) { _, _ ->
                val ti = title.text?.toString().orEmpty().trim()
                if (ti.isEmpty()) return@setPositiveButton
                val dueMs = dueMsLocal
                val snap = WorkflowHubStore.load(ctx)
                val cidRaw = cand.text?.toString().orEmpty().trim()
                val cid = when {
                    cidRaw.isEmpty() -> null
                    cidRaw.length <= 12 -> WorkflowHubStore.resolveCandidateId(snap, cidRaw)
                        ?: WorkflowHubStore.findCandidateIdByName(snap, cidRaw)
                    else -> WorkflowHubStore.resolveCandidateId(snap, cidRaw)
                }
                if (existing == null) {
                    WorkflowHubStore.addTask(ctx, ti, details.text?.toString().orEmpty(), dueMs, cid)
                } else {
                    val now = System.currentTimeMillis()
                    WorkflowHubStore.updateTask(ctx, existing.id) { t ->
                        t.copy(
                            title = ti,
                            details = details.text?.toString().orEmpty(),
                            dueMs = dueMs,
                            candidateId = cid,
                            updatedMs = now,
                        )
                    }
                }
                parentRefresh()
                offerNotificationReminderForDueTask(dueMs)
            }
            .show()
    }

    /** On Android 13+, prompts for POST_NOTIFICATIONS after saving a task with a due time. */
    private fun offerNotificationReminderForDueTask(dueMs: Long?) {
        if (dueMs == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val ctx = requireContext()
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(R.string.ops_notif_due_title)
            .setMessage(R.string.ops_notif_due_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ops_notif_allow) { _, _ ->
                Prefs.setOpsPostNotifPromptShown(requireContext(), true)
                requestNotifAfterDueSave.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .show()
    }

    private fun showCandidateDialog(existing: HubCandidate?) {
        val ctx = requireContext()
        val v = layoutInflater.inflate(R.layout.dialog_ops_candidate, null, false)
        val name = v.findViewById<TextInputEditText>(R.id.editOpsName)
        val org = v.findViewById<TextInputEditText>(R.id.editOpsOrg)
        val role = v.findViewById<TextInputEditText>(R.id.editOpsRole)
        val stage = v.findViewById<TextInputEditText>(R.id.editOpsStage)
        val email = v.findViewById<TextInputEditText>(R.id.editOpsEmail)
        val phone = v.findViewById<TextInputEditText>(R.id.editOpsPhone)
        val notes = v.findViewById<TextInputEditText>(R.id.editOpsNotes)
        existing?.let {
            name.setText(it.name)
            org.setText(it.org)
            role.setText(it.role)
            stage.setText(it.stage)
            email.setText(it.email)
            phone.setText(it.phone)
            notes.setText(it.notes)
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(if (existing == null) R.string.ops_add_candidate else R.string.ops_edit_candidate)
            .setView(v)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ai_save) { _, _ ->
                val n = name.text?.toString().orEmpty().trim()
                if (n.isEmpty()) return@setPositiveButton
                if (existing == null) {
                    WorkflowHubStore.addCandidate(
                        ctx, n,
                        org.text?.toString().orEmpty(),
                        role.text?.toString().orEmpty(),
                        stage.text?.toString().orEmpty(),
                        email.text?.toString().orEmpty(),
                        phone.text?.toString().orEmpty(),
                        notes.text?.toString().orEmpty(),
                    )
                } else {
                    val now = System.currentTimeMillis()
                    WorkflowHubStore.updateCandidate(ctx, existing.id) { c ->
                        c.copy(
                            name = n,
                            org = org.text?.toString().orEmpty(),
                            role = role.text?.toString().orEmpty(),
                            stage = stage.text?.toString().orEmpty(),
                            email = email.text?.toString().orEmpty(),
                            phone = phone.text?.toString().orEmpty(),
                            notes = notes.text?.toString().orEmpty(),
                            updatedMs = now,
                        )
                    }
                }
                parentRefresh()
            }
            .apply {
                if (existing != null) {
                    setNeutralButton(R.string.delete_action) { _, _ ->
                        confirmDelete { WorkflowHubStore.deleteCandidate(ctx, existing.id) }
                    }
                }
            }
            .show()
    }

    private fun showRouteDialog(existing: HubRoute?) {
        val ctx = requireContext()
        val v = layoutInflater.inflate(R.layout.dialog_ops_route, null, false)
        val name = v.findViewById<TextInputEditText>(R.id.editOpsRouteName)
        val wps = v.findViewById<TextInputEditText>(R.id.editOpsWaypoints)
        existing?.let {
            name.setText(it.name)
            wps.setText(it.waypoints.joinToString("\n"))
        }
        MaterialAlertDialogBuilder(ctx)
            .setTitle(if (existing == null) R.string.ops_add_route else R.string.ops_edit_route)
            .setView(v)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.ops_open_maps) { _, _ ->
                val list = wps.text?.toString()?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                if (list.isNotEmpty()) {
                    CaliIntentActions.openMapsWaypoints(ctx, list)
                }
            }
            .setPositiveButton(R.string.ai_save) { _, _ ->
                val label = name.text?.toString().orEmpty().trim()
                val list = wps.text?.toString()?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
                if (label.isEmpty() || list.isEmpty()) return@setPositiveButton
                if (existing == null) {
                    WorkflowHubStore.addRoute(ctx, label, list)
                } else {
                    val now = System.currentTimeMillis()
                    WorkflowHubStore.upsert(ctx) { s ->
                        s.copy(
                            routes = s.routes.map { r ->
                                if (r.id == existing.id) r.copy(name = label, waypoints = list, updatedMs = now) else r
                            },
                        )
                    }
                }
                parentRefresh()
            }
            .show()
    }

    private fun confirmDelete(onDelete: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.ops_delete_confirm_title)
            .setMessage(R.string.ops_delete_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_action) { _, _ ->
                onDelete()
                parentRefresh()
            }
            .show()
    }

    private fun parentRefresh() {
        (activity as? OperationsActivity)?.refreshAllFragments()
    }

    companion object {
        private const val ARG_TAB = "tab"
        fun newInstance(tab: Int) = OpsListFragment().apply { arguments = bundleOf(ARG_TAB to tab) }
    }

    private fun pickDueMillis(initial: Long?, onPicked: (Long?) -> Unit) {
        val ctx = requireContext()
        val cal = Calendar.getInstance()
        if (initial != null) cal.timeInMillis = initial
        DatePickerDialog(
            ctx,
            { _, y, m, d ->
                cal.set(Calendar.YEAR, y)
                cal.set(Calendar.MONTH, m)
                cal.set(Calendar.DAY_OF_MONTH, d)
                TimePickerDialog(
                    ctx,
                    { _, h, min ->
                        cal.set(Calendar.HOUR_OF_DAY, h)
                        cal.set(Calendar.MINUTE, min)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        onPicked(cal.timeInMillis)
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true,
                ).show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    private sealed class OpsRow {
        data class TaskRow(val t: HubTask) : OpsRow()
        data class CandidateRow(val c: HubCandidate) : OpsRow()
        data class RouteRow(val r: HubRoute) : OpsRow()
    }

    private inner class OpsRowAdapter(
        private val onClick: (Int) -> Unit,
    ) : RecyclerView.Adapter<OpsRowAdapter.Holder>() {

        private val items = mutableListOf<OpsRow>()

        fun submit(list: List<OpsRow>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        fun itemAt(pos: Int): OpsRow? = items.getOrNull(pos)

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val row = layoutInflater.inflate(R.layout.item_ops_row, parent, false)
            return Holder(row)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val row = items[position]
            val title = holder.itemView.findViewById<TextView>(R.id.textOpsTitle)
            val sub = holder.itemView.findViewById<TextView>(R.id.textOpsSubtitle)
            val meta = holder.itemView.findViewById<TextView>(R.id.textOpsMeta)
            when (row) {
                is OpsRow.TaskRow -> {
                    title.text = row.t.title
                    sub.text = row.t.details.ifBlank { getString(R.string.ops_no_details) }
                    val ms = row.t.dueMs
                    meta.text = if (ms != null) {
                        getString(
                            R.string.ops_task_row_meta_with_due,
                            row.t.status,
                            OpsDateTime.formatDateTime(requireContext(), ms),
                            row.t.id.take(8),
                        )
                    } else {
                        String.format(Locale.UK, "%s · %s", row.t.status, row.t.id.take(8))
                    }
                }
                is OpsRow.CandidateRow -> {
                    title.text = row.c.name
                    sub.text = listOf(row.c.role, row.c.org).filter { it.isNotBlank() }.joinToString(" · ")
                    meta.text = String.format(Locale.UK, "%s · %s", row.c.stage, row.c.id.take(8))
                }
                is OpsRow.RouteRow -> {
                    title.text = row.r.name
                    sub.text = row.r.waypoints.joinToString(" → ")
                    meta.text = row.r.id.take(8)
                }
            }
            styleRowCard(holder, row)
            holder.itemView.setOnClickListener { onClick(holder.bindingAdapterPosition) }
            holder.itemView.setOnLongClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnLongClickListener false
                when (val row = items[pos]) {
                    is OpsRow.TaskRow -> confirmDelete { WorkflowHubStore.deleteTask(requireContext(), row.t.id) }
                    is OpsRow.CandidateRow -> confirmDelete { WorkflowHubStore.deleteCandidate(requireContext(), row.c.id) }
                    is OpsRow.RouteRow -> confirmDelete { WorkflowHubStore.deleteRoute(requireContext(), row.r.id) }
                }
                true
            }
        }

        inner class Holder(v: View) : RecyclerView.ViewHolder(v)
    }

    private fun OpsRowAdapter.styleRowCard(holder: OpsRowAdapter.Holder, row: OpsRow) {
        val card = holder.itemView as MaterialCardView
        val hl = row is OpsRow.TaskRow && highlightTaskId != null && row.t.id == highlightTaskId
        val d = resources.displayMetrics.density
        if (hl) {
            card.strokeWidth = (4f * d).toInt().coerceAtLeast(4)
            card.strokeColor = MaterialColors.getColor(card, com.google.android.material.R.attr.colorPrimary)
        } else {
            card.strokeWidth = (1f * d).toInt().coerceAtLeast(1)
            card.strokeColor = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOutlineVariant)
        }
    }
}
