package dev.tabml.box

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.tabml.box.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val log = StringBuilder()

    /** True while training, TFLite demo, or HTTPS download is in progress. */
    private var workLocked = false

    private var nvqWatchOfferShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.inputHttpsUrl.setText(Prefs.lastHttpsUrl(this))

        binding.switchNetwork.isChecked = Prefs.allowNetwork(this)
        binding.switchNetwork.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setAllowNetwork(this, isChecked)
            updateNetworkUi()
            if (isChecked) {
                Toast.makeText(
                    this,
                    getString(R.string.network_toast_on),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

        binding.btnTrain.setOnClickListener { runTraining() }
        binding.btnLoadSaved.setOnClickListener { loadSavedAndTest() }
        binding.btnContinueTrain.setOnClickListener { continueTrainingFromSaved() }
        binding.btnDeleteCheckpoint.setOnClickListener { confirmDeleteCheckpoint() }
        binding.btnTfliteXor.setOnClickListener { runTfliteXor() }
        binding.btnDownloadTflite.setOnClickListener { downloadTflite() }
        binding.btnDownloadApk.setOnClickListener { downloadApk() }
        binding.btnUseBundledTflite.setOnClickListener { resetBundledTflite() }

        refreshCheckpointUi()
        updateNetworkUi()
        binding.btnOpenAssistant.setOnClickListener {
            startActivity(AiChatActivity.intent(this, nvqAssessorPreferred = false))
        }
        binding.btnOpenAssessorAssistant.setOnClickListener {
            startActivity(AiChatActivity.intent(this, nvqAssessorPreferred = true))
        }

        binding.btnOpenOperations.setOnClickListener {
            startActivity(android.content.Intent(this, OperationsActivity::class.java))
        }

        binding.btnDownloadVoskWakeModel.setOnClickListener { downloadVoskForWake() }

        NvqWatchNotifier.ensureChannel(this)
        binding.btnNvqWatchOpenA11y.setOnClickListener {
            NvqWatchAccess.openAccessibilitySettings(this)
        }

        binding.btnExportObservations.setOnClickListener { exportObservations() }
        binding.btnClearObservations.setOnClickListener { confirmClearObservations() }
        refreshNvqWatchUi()
        refreshObservationsUi()
        refreshCaliWakeSwitches()

        binding.btnCaliSetPin.setOnClickListener { showCaliPinSetupDialog() }
        binding.btnCaliClearLocalData.setOnClickListener { confirmClearCaliLocalData() }
    }

    private fun refreshCaliWakeSwitches() {
        binding.switchCaliWakeListen.setOnCheckedChangeListener(null)
        binding.switchCaliWakeListen.isChecked = Prefs.caliWakeEnabled(this)
        binding.switchCaliWakeListen.setOnCheckedChangeListener { _, on ->
            Prefs.setCaliWakeEnabled(this, on)
            if (on) {
                tryStartCaliWakeWithPermissions()
            } else {
                CaliWakeService.stop(this)
            }
            refreshCaliWakeStatusText()
        }
        binding.switchCaliWakeChargeOnly.setOnCheckedChangeListener(null)
        binding.switchCaliWakeChargeOnly.isChecked = Prefs.caliWakeChargeOnly(this)
        binding.switchCaliWakeChargeOnly.setOnCheckedChangeListener { _, on ->
            Prefs.setCaliWakeChargeOnly(this, on)
            if (Prefs.caliWakeEnabled(this)) restartCaliWakeService()
            refreshCaliWakeStatusText()
        }
        refreshCaliWakeStatusText()
    }

    private fun refreshCaliWakeStatusText() {
        binding.textCaliWakeStatus.text = when {
            !VoskModelStore.isInstalled(this) ->
                getString(R.string.cali_wake_status_need_model)
            Prefs.caliWakeEnabled(this) ->
                getString(R.string.cali_wake_status_listening)
            else ->
                getString(R.string.cali_wake_status_ready)
        }
    }

    private fun downloadVoskForWake() {
        if (!Prefs.allowNetwork(this)) {
            Toast.makeText(this, R.string.ai_need_network, Toast.LENGTH_LONG).show()
            return
        }
        binding.btnDownloadVoskWakeModel.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val result = VoskModelDownloader.downloadAndInstall(this@MainActivity)
            withContext(Dispatchers.Main) {
                binding.btnDownloadVoskWakeModel.isEnabled = true
                refreshCaliWakeStatusText()
                Toast.makeText(
                    this@MainActivity,
                    if (result.isSuccess) R.string.cali_vosk_download_ok else R.string.cali_vosk_download_fail,
                    Toast.LENGTH_LONG,
                ).show()
                if (Prefs.caliWakeEnabled(this@MainActivity)) restartCaliWakeService()
            }
        }
    }

    private fun tryStartCaliWakeWithPermissions() {
        if (!VoskModelStore.isInstalled(this)) {
            Toast.makeText(this, R.string.cali_wake_need_vosk_model, Toast.LENGTH_LONG).show()
            Prefs.setCaliWakeEnabled(this, false)
            refreshCaliWakeSwitches()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 5002)
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 5003)
            return
        }
        if (Prefs.caliWakeChargeOnly(this) && !isBatteryChargingOrFull()) {
            Toast.makeText(this, R.string.cali_wake_need_charging, Toast.LENGTH_LONG).show()
            Prefs.setCaliWakeEnabled(this, false)
            refreshCaliWakeSwitches()
            return
        }
        CaliWakeService.start(this)
        refreshCaliWakeStatusText()
    }

    private fun showCaliPinSetupDialog() {
        val v = layoutInflater.inflate(R.layout.dialog_cali_pin_setup, null, false)
        val switchReq = v.findViewById<MaterialSwitch>(R.id.switchRequirePin)
        val editPin = v.findViewById<TextInputEditText>(R.id.editCaliPinSetup)
        val editConfirm = v.findViewById<TextInputEditText>(R.id.editCaliPinConfirm)
        val hadHash = AiSecurePrefs.caliPinHash(this).isNotBlank()
        switchReq.isChecked = Prefs.caliPinEnabled(this) && hadHash

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cali_pin_set_change)
            .setView(v)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ai_save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (!switchReq.isChecked) {
                    AiSecurePrefs.clearCaliPin(this)
                    Prefs.setCaliPinEnabled(this, false)
                    CaliPinSession.unlocked = true
                    Toast.makeText(this, R.string.cali_pin_disabled, Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    return@setOnClickListener
                }
                val p = editPin.text?.toString().orEmpty()
                val c = editConfirm.text?.toString().orEmpty()
                if (p.isEmpty() && c.isEmpty()) {
                    if (hadHash) {
                        Prefs.setCaliPinEnabled(this, true)
                        Toast.makeText(this, R.string.cali_pin_saved, Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(this, R.string.cali_pin_enter_new, Toast.LENGTH_SHORT).show()
                    }
                    return@setOnClickListener
                }
                if (p.length < 4) {
                    Toast.makeText(this, R.string.cali_pin_too_short, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (p != c) {
                    Toast.makeText(this, R.string.cali_pin_mismatch, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                AiSecurePrefs.setCaliPinHash(this, CaliPinHasher.sha256Hex(p))
                Prefs.setCaliPinEnabled(this, true)
                CaliPinSession.unlocked = true
                Toast.makeText(this, R.string.cali_pin_saved, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun confirmClearCaliLocalData() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cali_clear_data_confirm_title)
            .setMessage(R.string.cali_clear_data_confirm_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_action) { _, _ ->
                CaliDataWiper.wipeTranscriptAndObservations(this)
                Toast.makeText(this, R.string.cali_data_cleared, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun restartCaliWakeService() {
        CaliWakeService.stop(this)
        if (Prefs.caliWakeEnabled(this)) {
            tryStartCaliWakeWithPermissions()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 4001 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            NvqWatchNotifier.ensureChannel(this)
        }
        if (requestCode == 5002) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (Prefs.caliWakeEnabled(this)) tryStartCaliWakeWithPermissions()
            } else {
                Toast.makeText(this, R.string.cali_need_mic_permission, Toast.LENGTH_LONG).show()
                Prefs.setCaliWakeEnabled(this, false)
                refreshCaliWakeSwitches()
            }
        }
        if (requestCode == 5003) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (Prefs.caliWakeEnabled(this)) tryStartCaliWakeWithPermissions()
            } else {
                Toast.makeText(this, R.string.cali_wake_notif_denied, Toast.LENGTH_LONG).show()
                Prefs.setCaliWakeEnabled(this, false)
                refreshCaliWakeSwitches()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (intent.getBooleanExtra(NvqWatchNotifier.EXTRA_SHOW_NVQ_PROMPT, false)) {
            intent.removeExtra(NvqWatchNotifier.EXTRA_SHOW_NVQ_PROMPT)
        }
        refreshNvqWatchUi()
        refreshObservationsUi()
        refreshCaliWakeSwitches()
        if (!Prefs.caliWakeEnabled(this)) {
            CaliWakeService.stop(this)
        }
        binding.root.post { tryShowNvqWatchOfferDialog() }
    }

    private fun refreshNvqWatchUi() {
        binding.switchNvqWatchOptIn.setOnCheckedChangeListener(null)
        binding.switchNvqWatchOptIn.isChecked = Prefs.nvqWatchAppOptIn(this)
        binding.switchNvqWatchOptIn.setOnCheckedChangeListener { _, on ->
            Prefs.setNvqWatchAppOptIn(this, on)
            if (on) {
                NvqWatchNotifier.ensureChannel(this)
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4001)
                }
            }
            refreshNvqWatchUi()
        }
        val svcOn = NvqWatchAccess.isServiceEnabled(this)
        binding.textNvqWatchStatus.text = buildString {
            append(getString(R.string.nvq_watch_status_prefix))
            append(
                if (svcOn) {
                    getString(R.string.nvq_watch_status_on)
                } else {
                    getString(R.string.nvq_watch_status_off)
                },
            )
        }
    }

    private fun tryShowNvqWatchOfferDialog() {
        if (nvqWatchOfferShowing) return
        if (!Prefs.nvqWatchPendingPrompt(this)) return
        nvqWatchOfferShowing = true
        val reason = Prefs.nvqWatchPendingReason(this).ifBlank {
            getString(R.string.nvq_watch_reason_keyword_match)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.nvq_watch_offer_title)
            .setMessage(getString(R.string.nvq_watch_offer_message, reason))
            .setCancelable(false)
            .setPositiveButton(R.string.nvq_watch_offer_enable) { _, _ ->
                Prefs.setObservationLearning(this, true)
                Prefs.setNvqAssessorMode(this, true)
                Prefs.clearNvqWatchPending(this)
                refreshObservationsUi()
            }
            .setNegativeButton(R.string.nvq_watch_offer_later) { _, _ ->
                Prefs.clearNvqWatchPending(this)
            }
            .setNeutralButton(R.string.nvq_watch_offer_never) { _, _ ->
                Prefs.setNvqWatchNeverSuggest(this, true)
                Prefs.setNvqWatchAppOptIn(this, false)
                Prefs.clearNvqWatchPending(this)
                refreshNvqWatchUi()
            }
            .setOnDismissListener { nvqWatchOfferShowing = false }
            .show()
    }

    private fun refreshObservationsUi() {
        binding.switchObservationLearningMain.setOnCheckedChangeListener(null)
        binding.switchObservationLearningMain.isChecked = Prefs.observationLearning(this)
        binding.switchObservationLearningMain.setOnCheckedChangeListener { _, checked ->
            Prefs.setObservationLearning(this, checked)
        }
        binding.textObservationsInsight.text = SessionObservationStore.buildInsightsDisplay(this)
        val has = SessionObservationStore.hasFile(this)
        binding.btnExportObservations.isEnabled = has
        binding.btnClearObservations.isEnabled = has
    }

    private fun exportObservations() {
        val text = SessionObservationStore.exportText(this).trim()
        if (text.isEmpty()) {
            Toast.makeText(this, R.string.observations_insights_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.observations_export_chooser)))
    }

    private fun confirmClearObservations() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.observations_clear_title)
            .setMessage(R.string.observations_clear_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_action) { _, _ ->
                SessionObservationStore.clear(this)
                refreshObservationsUi()
                Toast.makeText(this, R.string.observations_clear_done, Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun updateNetworkUi() {
        val on = Prefs.allowNetwork(this)
        binding.networkExtras.visibility = if (on) View.VISIBLE else View.GONE
        refreshDownloadAvailability()
    }

    private fun refreshDownloadAvailability() {
        val net = Prefs.allowNetwork(this)
        binding.btnDownloadTflite.isEnabled = !workLocked && net
        binding.btnDownloadApk.isEnabled = !workLocked && net
        binding.btnUseBundledTflite.isEnabled = !workLocked
    }

    private fun setWorkLocked(locked: Boolean) {
        workLocked = locked
        binding.btnTrain.isEnabled = !locked
        binding.btnLoadSaved.isEnabled = !locked && CheckpointStore.exists(this)
        binding.btnContinueTrain.isEnabled = !locked && CheckpointStore.exists(this)
        binding.btnDeleteCheckpoint.isEnabled = !locked && CheckpointStore.exists(this)
        binding.btnTfliteXor.isEnabled = !locked
        refreshDownloadAvailability()
    }

    private fun refreshCheckpointUi() {
        val has = CheckpointStore.exists(this)
        binding.btnLoadSaved.isEnabled = !workLocked && has
        binding.btnContinueTrain.isEnabled = !workLocked && has
        binding.btnDeleteCheckpoint.isEnabled = !workLocked && has
    }

    private fun saveCheckpoint(trainer: XorTrainer, finalLoss: Double) {
        val cp = trainer.toCheckpoint().copy(
            lastLoss = finalLoss,
            savedAtMs = System.currentTimeMillis(),
        )
        CheckpointStore.save(this, cp)
    }

    private fun runTraining() {
        setWorkLocked(true)
        binding.logView.text = getString(R.string.status_training)
        log.clear()
        appendLog(getString(R.string.log_start))

        lifecycleScope.launch(Dispatchers.Default) {
            val trainer = XorTrainer.newRandom(
                hiddenSize = 16,
                learningRate = 0.6,
            )
            val lines = mutableListOf<String>()
            val finalLoss = trainer.train(epochs = 8000, logEvery = 500) { epoch ->
                val line = String.format(
                    Locale.US,
                    "epoch %5d  loss %.6f",
                    epoch.epoch,
                    epoch.loss,
                )
                synchronized(lines) { lines.add(line) }
            }

            val checks = listOf(
                "f(0,0)" to trainer.predict(0.0, 0.0),
                "f(0,1)" to trainer.predict(0.0, 1.0),
                "f(1,0)" to trainer.predict(1.0, 0.0),
                "f(1,1)" to trainer.predict(1.0, 1.0),
            )
            val checkLines = checks.map { (name, v) ->
                String.format(Locale.US, "  %s = %.4f (target XOR)", name, v)
            }

            saveCheckpoint(trainer, finalLoss)

            withContext(Dispatchers.Main) {
                synchronized(lines) {
                    lines.forEach { appendLog(it) }
                }
                appendLog(String.format(Locale.US, "final loss: %.6f", finalLoss))
                checkLines.forEach { appendLog(it) }
                appendLog(getString(R.string.saved_checkpoint))
                appendLog(getString(R.string.log_done))
                setWorkLocked(false)
                refreshCheckpointUi()
            }
        }
    }

    private fun continueTrainingFromSaved() {
        if (!CheckpointStore.exists(this)) {
            Toast.makeText(this, R.string.continue_train_missing, Toast.LENGTH_LONG).show()
            return
        }
        setWorkLocked(true)
        binding.logView.text = getString(R.string.status_training)
        log.clear()
        appendLog(getString(R.string.continue_train_start))

        lifecycleScope.launch(Dispatchers.Default) {
            val cp = CheckpointStore.load(this@MainActivity)
            val trainer = XorTrainer.fromCheckpoint(cp, learningRate = 0.6)
            val lines = mutableListOf<String>()
            val finalLoss = trainer.train(epochs = 2000, logEvery = 400) { epoch ->
                val line = String.format(
                    Locale.US,
                    "epoch %5d  loss %.6f",
                    epoch.epoch,
                    epoch.loss,
                )
                synchronized(lines) { lines.add(line) }
            }
            val checks = listOf(
                "f(0,0)" to trainer.predict(0.0, 0.0),
                "f(0,1)" to trainer.predict(0.0, 1.0),
                "f(1,0)" to trainer.predict(1.0, 0.0),
                "f(1,1)" to trainer.predict(1.0, 1.0),
            )
            val checkLines = checks.map { (name, v) ->
                String.format(Locale.US, "  %s = %.4f (target XOR)", name, v)
            }
            saveCheckpoint(trainer, finalLoss)

            withContext(Dispatchers.Main) {
                synchronized(lines) {
                    lines.forEach { appendLog(it) }
                }
                appendLog(String.format(Locale.US, "final loss: %.6f", finalLoss))
                checkLines.forEach { appendLog(it) }
                appendLog(getString(R.string.saved_checkpoint))
                appendLog(getString(R.string.log_done))
                setWorkLocked(false)
                refreshCheckpointUi()
            }
        }
    }

    private fun loadSavedAndTest() {
        if (!CheckpointStore.exists(this)) {
            Toast.makeText(this, R.string.load_saved_missing, Toast.LENGTH_LONG).show()
            return
        }
        binding.btnLoadSaved.isEnabled = false
        log.clear()
        lifecycleScope.launch(Dispatchers.Default) {
            val cp = CheckpointStore.load(this@MainActivity)
            val metaLines = buildList {
                add(getString(R.string.load_saved_run_header))
                cp.lastLoss?.let {
                    add(String.format(Locale.US, "  last saved loss: %.6f", it))
                }
                cp.savedAtMs?.takeIf { it > 0L }?.let { ms ->
                    val fmt = DateFormat.getDateTimeInstance(
                        DateFormat.SHORT,
                        DateFormat.SHORT,
                        Locale.getDefault(),
                    )
                    add("  saved: ${fmt.format(Date(ms))}")
                }
            }
            val net = XorTrainer.fromCheckpoint(cp, learningRate = 0.6)
            val checks = listOf(
                "f(0,0)" to net.predict(0.0, 0.0),
                "f(0,1)" to net.predict(0.0, 1.0),
                "f(1,0)" to net.predict(1.0, 0.0),
                "f(1,1)" to net.predict(1.0, 1.0),
            )
            val checkLines = checks.map { (name, v) ->
                String.format(Locale.US, "  %s = %.4f", name, v)
            }
            withContext(Dispatchers.Main) {
                metaLines.forEach { appendLog(it) }
                appendLog("")
                checkLines.forEach { appendLog(it) }
                refreshCheckpointUi()
            }
        }
    }

    private fun confirmDeleteCheckpoint() {
        if (!CheckpointStore.exists(this)) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_checkpoint_title)
            .setMessage(R.string.delete_checkpoint_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.delete_action) { _, _ ->
                CheckpointStore.delete(this)
                Toast.makeText(this, R.string.delete_checkpoint_done, Toast.LENGTH_SHORT).show()
                refreshCheckpointUi()
            }
            .show()
    }

    private fun downloadTflite() {
        val url = binding.inputHttpsUrl.text?.toString().orEmpty()
        if (url.isBlank()) {
            Toast.makeText(this, R.string.url_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (!NetworkGuard.isAllowed(this)) {
            Toast.makeText(this, R.string.network_disabled_toast, Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.setLastHttpsUrl(this, url)
        setWorkLocked(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = runCatching {
                    val dest = ModelLoader.downloadedFile(this@MainActivity)
                    val n = HttpsDownload.streamToFile(this@MainActivity, url, dest)
                    require(n >= 256) { "file too small to be a useful .tflite" }
                    Prefs.setPreferDownloadedTflite(this@MainActivity, true)
                    n
                }
                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = { n ->
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.download_tflite_ok, n.toInt()),
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                        onFailure = { e ->
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.download_fail, e.message ?: e.toString()),
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                    )
                }
            } finally {
                withContext(Dispatchers.Main) {
                    setWorkLocked(false)
                }
            }
        }
    }

    private fun downloadApk() {
        val url = binding.inputHttpsUrl.text?.toString().orEmpty()
        if (url.isBlank()) {
            Toast.makeText(this, R.string.url_required, Toast.LENGTH_SHORT).show()
            return
        }
        if (!NetworkGuard.isAllowed(this)) {
            Toast.makeText(this, R.string.network_disabled_toast, Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.setLastHttpsUrl(this, url)
        setWorkLocked(true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = runCatching {
                    val dest = ModelLoader.updatesApkFile(this@MainActivity)
                    val n = HttpsDownload.streamToFile(this@MainActivity, url, dest)
                    require(n >= 50_000) {
                        "file too small to be a plausible APK"
                    }
                    dest
                }
                withContext(Dispatchers.Main) {
                    result.fold(
                        onSuccess = { apkFile ->
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.download_apk_ok, apkFile.length().toInt()),
                                Toast.LENGTH_LONG,
                            ).show()
                            ApkInstallPrompt.open(this@MainActivity, apkFile)
                        },
                        onFailure = { e ->
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.download_fail, e.message ?: e.toString()),
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                    )
                }
            } finally {
                withContext(Dispatchers.Main) {
                    setWorkLocked(false)
                }
            }
        }
    }

    private fun resetBundledTflite() {
        Prefs.setPreferDownloadedTflite(this, false)
        val f = ModelLoader.downloadedFile(this)
        if (f.exists()) f.delete()
        Toast.makeText(this, R.string.use_bundled_done, Toast.LENGTH_SHORT).show()
    }

    private fun runTfliteXor() {
        setWorkLocked(true)
        log.clear()
        lifecycleScope.launch(Dispatchers.Default) {
            val lines: List<String> = try {
                XorTfliteRunner(this@MainActivity).use { runner ->
                    buildList {
                        add(getString(R.string.tflite_header))
                        val pts = listOf(
                            0f to 0f,
                            0f to 1f,
                            1f to 0f,
                            1f to 1f,
                        )
                        for ((a, b) in pts) {
                            val y = runner.predict(a, b)
                            add(
                                String.format(
                                    Locale.US,
                                    "  f(%.0f,%.0f) = %.4f (expect XOR)",
                                    a,
                                    b,
                                    y,
                                ),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                listOf(getString(R.string.tflite_fail, e.message ?: e.toString()))
            }
            withContext(Dispatchers.Main) {
                lines.forEach { appendLog(it) }
                setWorkLocked(false)
                refreshCheckpointUi()
            }
        }
    }

    private fun appendLog(line: String) {
        if (log.isNotEmpty()) log.append('\n')
        log.append(line)
        binding.logView.text = log.toString()
    }
}
