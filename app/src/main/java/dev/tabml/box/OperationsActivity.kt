package dev.tabml.box

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import dev.tabml.box.databinding.ActivityOperationsBinding

/** Tasks, candidates, and route plans for field / recruitment workflows. */
class OperationsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_INITIAL_TAB = "extra_ops_initial_tab"
        const val EXTRA_FOCUS_TASK_ID = "extra_ops_focus_task_id"
    }

    private lateinit var binding: ActivityOperationsBinding
    private var pendingImportMerge = false

    private val requestNotifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        refreshNotifBanner()
        if (!granted) OpsNotifPermission.showBlockedFallbackIfNeeded(this)
    }

    private val importPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val text = input.bufferedReader().readText()
                val snap = WorkflowHubImporter.parseOrNull(text)
                if (snap == null) {
                    Toast.makeText(this, R.string.ops_import_failed, Toast.LENGTH_LONG).show()
                    return@use
                }
                if (pendingImportMerge) WorkflowHubStore.mergeSnapshot(this, snap)
                else WorkflowHubStore.replaceSnapshot(this, snap)
                refreshAllFragments()
                Toast.makeText(this, R.string.ops_import_ok, Toast.LENGTH_SHORT).show()
            } ?: Toast.makeText(this, R.string.ops_import_failed, Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.ops_import_failed, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOperationsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.viewPager.adapter = OpsPagerAdapter(this)
        TabLayoutMediator(binding.tabs, binding.viewPager) { tab, pos ->
            tab.text = when (pos) {
                0 -> getString(R.string.ops_tab_tasks)
                1 -> getString(R.string.ops_tab_candidates)
                else -> getString(R.string.ops_tab_routes)
            }
        }.attach()

        binding.fabAdd.setOnClickListener {
            val tab = binding.viewPager.currentItem
            opsFragmentAt(tab)?.showCreateDialog()
        }

        binding.btnNotifAllow.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                Prefs.setOpsPostNotifPromptShown(this, true)
                requestNotifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        binding.btnNotifSettings.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                },
            )
        }

        applyLaunchIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        refreshNotifBanner()
    }

    private fun applyLaunchIntent(intent: Intent?) {
        if (intent == null) return
        val tab = intent.getIntExtra(EXTRA_INITIAL_TAB, -1)
        if (tab in 0..2) binding.viewPager.setCurrentItem(tab, false)
        val focus = intent.getStringExtra(EXTRA_FOCUS_TASK_ID)
        if (!focus.isNullOrEmpty()) scheduleScrollToTask(focus)
    }

    private fun scheduleScrollToTask(taskId: String) {
        fun attempt(): Boolean {
            supportFragmentManager.executePendingTransactions()
            val frag = opsFragmentAt(0) ?: return false
            if (!frag.isResumed) return false
            frag.scrollToTaskId(taskId)
            return true
        }
        binding.viewPager.post {
            if (!attempt()) binding.viewPager.postDelayed({ attempt() }, 250)
        }
    }

    private fun opsFragmentAt(position: Int): OpsListFragment? {
        val tag = "f${binding.viewPager.id}:$position"
        return supportFragmentManager.findFragmentByTag(tag) as? OpsListFragment
    }

    private fun refreshNotifBanner() {
        val show = shouldShowNotifBanner()
        binding.cardNotifBanner.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        val needRuntime = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (!needRuntime) {
            binding.btnNotifAllow.visibility = View.GONE
            binding.textNotifBanner.setText(R.string.ops_notif_banner_body)
            return
        }
        val showAllow = shouldShowNotifAllowButton()
        binding.btnNotifAllow.visibility = if (showAllow) View.VISIBLE else View.GONE
        binding.textNotifBanner.setText(
            if (showAllow) R.string.ops_notif_banner_body else R.string.ops_notif_banner_need_settings,
        )
    }

    private fun shouldShowNotifAllowButton(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        if (!Prefs.opsPostNotifPromptShown(this)) return true
        return shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun shouldShowNotifBanner(): Boolean {
        val snap = WorkflowHubStore.load(this)
        val hasDueOpen = snap.tasks.any { it.status == HubTaskStatus.OPEN && it.dueMs != null }
        if (!hasDueOpen) return false
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return true
        }
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.operations_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_export_hub -> {
                shareHubExport()
                return true
            }
            R.id.action_import_hub -> {
                showImportChoiceDialog()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun showImportChoiceDialog() {
        val choices = arrayOf(getString(R.string.ops_import_replace), getString(R.string.ops_import_merge))
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ops_import_title)
            .setItems(choices) { _, which ->
                pendingImportMerge = (which == 1)
                importPicker.launch("*/*")
            }
            .show()
    }

    private fun shareHubExport() {
        val text = WorkflowHubExporter.toCsv(this)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.ops_export_subject))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.ops_export_chooser)))
    }

    fun syncNotificationBanner() {
        refreshNotifBanner()
    }

    fun refreshAllFragments() {
        for (f in supportFragmentManager.fragments) {
            (f as? OpsListFragment)?.reload()
        }
        refreshNotifBanner()
    }

    private class OpsPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = 3
        override fun createFragment(position: Int) = OpsListFragment.newInstance(position)
    }
}
