package dev.tabml.box

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import dev.tabml.box.databinding.ActivityAiChatBinding
import dev.tabml.box.databinding.DialogAiSettingsBinding
import dev.tabml.box.databinding.DialogCaliPinBinding
import dev.tabml.box.databinding.DialogCaliOptionsBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import android.os.SystemClock

class AiChatActivity : AppCompatActivity() {

    private val onDeviceProfileKeyOrder = listOf("LLAMA_3_2", "GEMMA", "QWEN_2_5", "SMOLLM2")

    private lateinit var binding: ActivityAiChatBinding
    private lateinit var caliVoice: CaliVoice
    private val transcript = mutableListOf<ChatMessage>()
    private val adapter = ChatAdapter()
    private var pendingTfliteUrl: String? = null
    private var voiceAutoSend = false
    /** Blocks listen-after → STT → auto-send loops on the same phrase (echo / noise). */
    private var lastVoiceAutoSendElapsedMs = 0L
    private var lastVoiceAutoSendText: String? = null
    /** Active send/generate job (on-device or remote); used to cancel on-device generation. */
    private var sendJob: Job? = null

    private val importGgufLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch(Dispatchers.IO) {
            val r = runCatching {
                val dest = TabletGgufStore.modelFile(this@AiChatActivity)
                dest.parentFile?.mkdirs()
                contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { out -> input.copyTo(out) }
                } ?: throw IOException("openInputStream")
                val len = dest.length()
                if (len < 1_000_000L) {
                    dest.delete()
                    error("file too small")
                }
                OnDeviceLlm.closeAll()
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@AiChatActivity,
                    if (r.isSuccess) R.string.ai_import_gguf_ok else R.string.ai_import_gguf_fail,
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startVoiceCapture(autoSend = voiceAutoSend)
        else Toast.makeText(this, R.string.cali_need_mic_permission, Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener(this::onToolbarMenu)

        if (needsPinUnlock()) {
            showPinUnlockDialog { initChatAfterPin() }
        } else {
            initChatAfterPin()
        }
    }

    private fun needsPinUnlock(): Boolean =
        Prefs.caliPinEnabled(this) &&
            AiSecurePrefs.caliPinHash(this).isNotBlank() &&
            !CaliPinSession.unlocked

    private fun showPinUnlockDialog(onUnlocked: () -> Unit) {
        val d = DialogCaliPinBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cali_pin_title)
            .setView(d.root)
            .setCancelable(false)
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = d.editCaliPin.text?.toString().orEmpty()
                if (CaliPinHasher.sha256Hex(pin) != AiSecurePrefs.caliPinHash(this@AiChatActivity)) {
                    Toast.makeText(this@AiChatActivity, R.string.cali_pin_wrong, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                CaliPinSession.unlocked = true
                dialog.dismiss()
                onUnlocked()
            }
        }
        dialog.show()
    }

    private fun initChatAfterPin() {
        caliVoice = CaliVoice(
            activity = this,
            onListenResult = listen@{ text ->
                binding.inputMessage.setText(text)
                binding.inputMessage.setSelection(text.length)
                if (voiceAutoSend && text.isNotBlank()) {
                    val now = SystemClock.elapsedRealtime()
                    val tnorm = text.trim()
                    if (tnorm == lastVoiceAutoSendText?.trim() &&
                        now - lastVoiceAutoSendElapsedMs < 4_000L
                    ) {
                        voiceAutoSend = false
                        return@listen
                    }
                    lastVoiceAutoSendText = tnorm
                    lastVoiceAutoSendElapsedMs = now
                    voiceAutoSend = false
                    sendMessage()
                }
            },
            onListenError = {
                Toast.makeText(this@AiChatActivity, R.string.cali_voice_unavailable, Toast.LENGTH_SHORT).show()
            },
            onSpeakDone = { scheduleListenAfterIfNeeded(delayMs = 1_400L) },
            onSpeakingChanged = { refreshStopSpeakingMenuItem() },
        )
        caliVoice.init()

        binding.chatList.layoutManager = LinearLayoutManager(this)
        binding.chatList.adapter = adapter
        adapter.onUserRowLongPress = { pos, msg -> showChatRowMenu(pos, msg) }
        adapter.onAssistantRowLongPress = { pos, msg -> showChatRowMenu(pos, msg) }

        val saved = ChatTranscriptStore.load(this)
        if (saved.isNotEmpty()) {
            transcript.clear()
            transcript.addAll(saved)
            adapter.submitAll(transcript.toList())
            binding.chatList.post {
                if (adapter.itemCount > 0) {
                    binding.chatList.scrollToPosition(adapter.itemCount - 1)
                }
            }
        }

        binding.chipQuickSummarize.setOnClickListener { applyQuickPrompt(R.string.cali_chip_prompt_summarize) }
        binding.chipQuickSteps.setOnClickListener { applyQuickPrompt(R.string.cali_chip_prompt_steps) }
        binding.chipQuickExample.setOnClickListener { applyQuickPrompt(R.string.cali_chip_prompt_example) }
        binding.chipQuickChecklist.setOnClickListener { applyQuickPrompt(R.string.cali_chip_prompt_checklist) }

        binding.btnSend.setOnClickListener { sendMessage() }
        binding.inputMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        binding.btnMic.setOnClickListener {
            voiceAutoSend = false
            ensureMicThenListen(autoSend = false)
        }

        binding.btnInstallSuggested.setOnClickListener { installSuggestedTflite() }

        if (intent.getBooleanExtra(EXTRA_NVQ_ASSESSOR, false)) {
            Prefs.setNvqAssessorMode(this, true)
        }

        binding.switchSpeakReplies.isChecked = Prefs.caliSpeakReplies(this)
        binding.switchSpeakReplies.setOnCheckedChangeListener { _, checked ->
            Prefs.setCaliSpeakReplies(this, checked)
        }
        binding.switchListenAfter.isChecked = Prefs.caliListenAfter(this)
        binding.switchListenAfter.setOnCheckedChangeListener { _, checked ->
            Prefs.setCaliListenAfter(this, checked)
        }

        if (!Prefs.assistantOnDeviceLlm(this) && !Prefs.allowNetwork(this)) {
            Toast.makeText(this, R.string.ai_need_network, Toast.LENGTH_LONG).show()
        }

        handleWakeWordIntent(intent)
        binding.root.post { refreshStopSpeakingMenuItem() }
        refreshRetryShareMenu(sending = false)
    }

    override fun onPause() {
        super.onPause()
        ChatTranscriptStore.save(this, transcript.toList())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleWakeWordIntent(intent)
    }

    private fun handleWakeWordIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_WAKE_OPEN_MIC, false) != true) return
        intent.removeExtra(EXTRA_WAKE_OPEN_MIC)
        if (Prefs.caliWakeClearSubject(this)) {
            binding.inputSubject.text?.clear()
        }
        binding.root.post {
            voiceAutoSend = true
            ensureMicThenListen(autoSend = true)
        }
    }

    private fun onToolbarMenu(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_stop_generation -> {
                stopActiveGeneration()
                return true
            }
            R.id.action_stop_speaking -> {
                caliVoice.stopSpeaking()
                return true
            }
            R.id.action_clear_chat -> {
                clearConversation()
                return true
            }
            R.id.action_retry_last -> {
                retryLastTurn()
                return true
            }
            R.id.action_share_transcript -> {
                shareTranscript()
                return true
            }
            R.id.action_copy_transcript -> {
                copyFullTranscript()
                return true
            }
            R.id.action_share_markdown -> {
                shareTranscriptMarkdown()
                return true
            }
            R.id.action_regenerate_alt -> {
                regenerateAlternativeReply()
                return true
            }
            R.id.action_cali_options -> showCaliOptionsDialog()
            R.id.action_ai_settings -> showApiSettingsDialog()
            R.id.action_ai_help -> showHelpDialog()
            else -> return false
        }
        return true
    }

    private fun applyQuickPrompt(promptRes: Int) {
        val prompt = getString(promptRes)
        binding.inputMessage.setText(prompt)
        binding.inputMessage.setSelection(prompt.length)
        binding.inputMessage.requestFocus()
    }

    private fun copyChatMessage(text: String) {
        if (text.isBlank()) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.cali_title), text))
        Toast.makeText(this, R.string.ai_message_copied, Toast.LENGTH_SHORT).show()
    }

    private fun showChatRowMenu(position: Int, msg: ChatMessage) {
        val popup = PopupMenu(this, binding.chatList.findViewHolderForAdapterPosition(position)?.itemView
            ?: return)
        popup.menuInflater.inflate(R.menu.chat_row_menu, popup.menu)
        popup.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                R.id.row_copy -> {
                    copyChatMessage(msg.text)
                    true
                }
                R.id.row_edit -> {
                    showEditMessageDialog(position, msg)
                    true
                }
                R.id.row_delete -> {
                    confirmDeleteMessage(position)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showEditMessageDialog(position: Int, msg: ChatMessage) {
        val input = TextInputEditText(this).apply {
            when (msg.speaker) {
                ChatSpeaker.User -> {
                    val (_, body) = parseUserTurnForTelemetry(msg)
                    setText(body)
                }
                ChatSpeaker.Assistant -> setText(msg.text)
            }
            setSelection(text?.length ?: 0)
            minLines = 4
        }
        val pad = (12 * resources.displayMetrics.density).toInt()
        input.setPadding(pad, pad, pad, pad)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.chat_edit_user_title)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ai_save) { _, _ ->
                val newContent = input.text?.toString()?.trim().orEmpty()
                if (newContent.isEmpty()) return@setPositiveButton
                val updated = when (msg.speaker) {
                    ChatSpeaker.User -> {
                        val (subject, _) = parseUserTurnForTelemetry(msg)
                        val display = if (subject.isNotEmpty()) {
                            getString(R.string.ai_user_display_format, subject, newContent)
                        } else {
                            newContent
                        }
                        val api = if (subject.isNotEmpty()) {
                            "Library / subject: $subject\n\n$newContent"
                        } else {
                            newContent
                        }
                        ChatMessage(ChatSpeaker.User, display, api)
                    }
                    ChatSpeaker.Assistant ->
                        ChatMessage(ChatSpeaker.Assistant, newContent)
                }
                transcript[position] = updated
                adapter.replaceAt(position, updated)
            }
            .show()
    }

    private fun confirmDeleteMessage(position: Int) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.chat_delete_confirm_title)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                transcript.removeAt(position)
                adapter.removeAt(position)
                refreshRetryShareMenu(!binding.btnSend.isEnabled)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun clearConversation() {
        transcript.clear()
        adapter.submitAll(emptyList())
        pendingTfliteUrl = null
        binding.btnInstallSuggested.visibility = View.GONE
        ChatTranscriptStore.save(this, emptyList())
        refreshRetryShareMenu(sending = false)
        clearConnectionBanner()
        if (Prefs.assistantOnDeviceLlm(this)) {
            lifecycleScope.launch(Dispatchers.IO) {
                OnDeviceLlm.resetConversation()
            }
        }
    }

    private fun parseUserTurnForTelemetry(msg: ChatMessage): Pair<String, String> {
        val text = msg.text
        val idx = text.indexOf("]\n")
        if (idx > 0 && text.startsWith("[")) {
            val subject = text.substring(1, idx)
            val body = text.substring(idx + 2)
            return subject to body
        }
        return "" to text
    }

    private fun retryLastTurn() {
        if (!binding.btnSend.isEnabled) return
        if (transcript.none { it.speaker == ChatSpeaker.User }) {
            Toast.makeText(this, R.string.ai_retry_nothing, Toast.LENGTH_SHORT).show()
            return
        }
        if (transcript.last().speaker == ChatSpeaker.Assistant) {
            transcript.removeAt(transcript.lastIndex)
            adapter.removeLast()
        }
        val lastUser = transcript.lastOrNull() ?: return
        if (lastUser.speaker != ChatSpeaker.User) {
            Toast.makeText(this, R.string.ai_retry_nothing, Toast.LENGTH_SHORT).show()
            return
        }
        val (subject, userBody) = parseUserTurnForTelemetry(lastUser)
        runAssistantAfterUserAdded(subject, userBody)
    }

    private fun shareTranscript() {
        if (transcript.isEmpty()) {
            Toast.makeText(this, R.string.ai_share_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val text = transcript.joinToString(separator = "\n\n---\n\n") { m ->
            val label = when (m.speaker) {
                ChatSpeaker.User -> getString(R.string.ai_share_label_you)
                ChatSpeaker.Assistant -> getString(R.string.ai_share_label_cali)
            }
            "$label\n${m.text}"
        }
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.ai_share_transcript_subject))
                },
                getString(R.string.ai_share_transcript_chooser),
            ),
        )
    }

    private fun copyFullTranscript() {
        if (transcript.isEmpty()) {
            Toast.makeText(this, R.string.ai_share_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val text = transcript.joinToString(separator = "\n\n---\n\n") { m ->
            val label = when (m.speaker) {
                ChatSpeaker.User -> getString(R.string.ai_share_label_you)
                ChatSpeaker.Assistant -> getString(R.string.ai_share_label_cali)
            }
            "$label\n${m.text}"
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(getString(R.string.ai_share_transcript_subject), text))
        Toast.makeText(this, R.string.ai_message_copied, Toast.LENGTH_SHORT).show()
    }

    private fun shareTranscriptMarkdown() {
        if (transcript.isEmpty()) {
            Toast.makeText(this, R.string.ai_share_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val md = transcript.joinToString(separator = "\n\n") { m ->
            when (m.speaker) {
                ChatSpeaker.User -> "## ${getString(R.string.ai_share_label_you)}\n\n${m.text}"
                ChatSpeaker.Assistant -> "## ${getString(R.string.ai_share_label_cali)}\n\n${m.text}"
            }
        }
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, md)
                    putExtra(Intent.EXTRA_SUBJECT, getString(R.string.ai_share_transcript_subject))
                },
                getString(R.string.ai_share_transcript_chooser),
            ),
        )
    }

    private fun regenerateAlternativeReply() {
        if (!binding.btnSend.isEnabled) return
        if (Prefs.assistantOnDeviceLlm(this)) {
            Toast.makeText(this, R.string.ai_regenerate_on_device_unsupported, Toast.LENGTH_LONG).show()
            return
        }
        if (!Prefs.allowNetwork(this)) {
            Toast.makeText(this, R.string.ai_need_network, Toast.LENGTH_LONG).show()
            return
        }
        val base = Prefs.aiBaseUrl(this).trim()
        if (base.isEmpty() || !AssistantUrlPolicy.isValidBaseUrl(base)) {
            Toast.makeText(this, R.string.assistant_need_endpoint, Toast.LENGTH_LONG).show()
            return
        }
        if (transcript.lastOrNull()?.speaker != ChatSpeaker.Assistant) {
            Toast.makeText(this, R.string.ai_regenerate_need_assistant, Toast.LENGTH_SHORT).show()
            return
        }
        val lastUser = transcript.lastOrNull { it.speaker == ChatSpeaker.User } ?: run {
            Toast.makeText(this, R.string.ai_regenerate_need_assistant, Toast.LENGTH_SHORT).show()
            return
        }
        val (subject, userBody) = parseUserTurnForTelemetry(lastUser)
        val snapshotBefore = transcript.toList()
        val extra = "Provide a different helpful answer to the user's last request. Vary the structure or examples."
        val apiMessages = buildApiPayloadForMessages(snapshotBefore) + ("user" to extra)

        val placeholder = getString(R.string.ai_streaming_placeholder)
        transcript.add(ChatMessage(ChatSpeaker.Assistant, placeholder))
        adapter.append(transcript.last())
        binding.chatList.scrollToPosition(adapter.itemCount - 1)

        setSending(true)
        pendingTfliteUrl = null
        binding.btnInstallSuggested.visibility = View.GONE
        caliVoice.stopSpeaking()
        clearConnectionBanner()

        val key = AiSecurePrefs.apiKey(this)
        sendJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                val raw = AssistantChatClient(
                    Prefs.aiBaseUrl(this@AiChatActivity),
                    key,
                    Prefs.aiModel(this@AiChatActivity),
                ).chatStreamWithFallback(apiMessages) { partial ->
                    runOnUiThread {
                        if (isDestroyed || isFinishing) return@runOnUiThread
                        val shown = partial.ifBlank { placeholder }
                        transcript[transcript.lastIndex] = ChatMessage(ChatSpeaker.Assistant, shown)
                        adapter.replaceLast(transcript.last())
                        binding.chatList.scrollToPosition(adapter.itemCount - 1)
                    }
                }
                withContext(Dispatchers.Main) {
                    if (isDestroyed || isFinishing) return@withContext
                    setSending(false)
                    deliverAssistantSuccess(
                        raw,
                        subject,
                        userBody,
                        replaceStreamingPlaceholder = true,
                    )
                }
            } catch (e: CancellationException) {
                withContext(Dispatchers.Main) {
                    if (!isDestroyed && !isFinishing) {
                        setSending(false)
                        deliverAssistantStopped()
                    }
                }
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (!isDestroyed && !isFinishing) {
                        setSending(false)
                        deliverAssistantFailure(
                            e,
                            subject,
                            userBody,
                            replaceStreamingPlaceholder = true,
                        )
                    }
                }
            }
        }
    }

    private fun setConnectionBanner(message: String) {
        binding.textConnectionBanner.text = message
        binding.textConnectionBanner.visibility = View.VISIBLE
    }

    private fun clearConnectionBanner() {
        binding.textConnectionBanner.visibility = View.GONE
    }

    private fun ngrokUpstreamHintIfNeeded(displayedError: String): String {
        val d = displayedError.lowercase()
        if (!d.contains("ngrok") && !d.contains("8765") && !d.contains("err_ngrok")) {
            return ""
        }
        return "\n\n" + getString(R.string.ai_error_ngrok_upstream_hint)
    }

    private fun refreshRetryShareMenu(sending: Boolean) {
        if (isDestroyed || isFinishing) return
        val hasUser = transcript.any { it.speaker == ChatSpeaker.User }
        val remote = !Prefs.assistantOnDeviceLlm(this)
        val lastAsst = transcript.lastOrNull()?.speaker == ChatSpeaker.Assistant
        binding.toolbar.menu.findItem(R.id.action_retry_last)?.isEnabled = !sending && hasUser
        binding.toolbar.menu.findItem(R.id.action_share_transcript)?.isEnabled = !sending && transcript.isNotEmpty()
        binding.toolbar.menu.findItem(R.id.action_copy_transcript)?.isEnabled = !sending && transcript.isNotEmpty()
        binding.toolbar.menu.findItem(R.id.action_share_markdown)?.isEnabled = !sending && transcript.isNotEmpty()
        binding.toolbar.menu.findItem(R.id.action_regenerate_alt)?.isEnabled = !sending && remote && lastAsst
    }

    private fun stopActiveGeneration() {
        sendJob?.cancel()
        if (Prefs.assistantOnDeviceLlm(this)) {
            lifecycleScope.launch(Dispatchers.IO) {
                OnDeviceLlm.abortGeneration()
            }
        }
    }

    private fun refreshStopSpeakingMenuItem() {
        if (isDestroyed || isFinishing) return
        binding.toolbar.menu.findItem(R.id.action_stop_speaking)?.isEnabled = caliVoice.isSpeaking()
    }

    private fun showCaliOptionsDialog() {
        val d = DialogCaliOptionsBinding.inflate(layoutInflater)
        d.switchOptNvq.isChecked = Prefs.nvqAssessorMode(this)
        d.switchOptObservation.isChecked = Prefs.observationLearning(this)
        d.switchOptOfflineStt.isChecked = Prefs.caliOfflineStt(this)
        d.switchOptLargeChat.isChecked = Prefs.caliLargeChatText(this)
        d.switchOptHighContrast.isChecked = Prefs.caliHighContrastChat(this)
        d.switchOptWakeClearSubject.isChecked = Prefs.caliWakeClearSubject(this)
        d.sliderTtsRate.value = Prefs.caliTtsRate(this)
        fun refreshVoskStatus() {
            d.textVoskModelStatus.text = if (VoskModelStore.isInstalled(this)) {
                getString(R.string.cali_vosk_status_ready)
            } else {
                getString(R.string.cali_vosk_status_missing)
            }
        }
        refreshVoskStatus()
        d.btnDownloadVoskModel.setOnClickListener {
            if (!Prefs.allowNetwork(this)) {
                Toast.makeText(this, R.string.ai_need_network, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            d.btnDownloadVoskModel.isEnabled = false
            lifecycleScope.launch {
                val result = VoskModelDownloader.downloadAndInstall(this@AiChatActivity)
                withContext(Dispatchers.Main) {
                    d.btnDownloadVoskModel.isEnabled = true
                    refreshVoskStatus()
                    Toast.makeText(
                        this@AiChatActivity,
                        if (result.isSuccess) R.string.cali_vosk_download_ok else R.string.cali_vosk_download_fail,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.cali_options_menu)
            .setView(d.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                Prefs.setNvqAssessorMode(this, d.switchOptNvq.isChecked)
                Prefs.setObservationLearning(this, d.switchOptObservation.isChecked)
                Prefs.setCaliOfflineStt(this, d.switchOptOfflineStt.isChecked)
                Prefs.setCaliLargeChatText(this, d.switchOptLargeChat.isChecked)
                Prefs.setCaliHighContrastChat(this, d.switchOptHighContrast.isChecked)
                Prefs.setCaliWakeClearSubject(this, d.switchOptWakeClearSubject.isChecked)
                Prefs.setCaliTtsRate(this, d.sliderTtsRate.value)
                caliVoice.applySpeechRateFromPrefs()
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showHelpDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ai_help_title)
            .setMessage(R.string.ai_help_body)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showApiSettingsDialog() {
        val d = DialogAiSettingsBinding.inflate(layoutInflater)
        d.editBaseUrl.setText(Prefs.aiBaseUrl(this))
        d.editModel.setText(Prefs.aiModel(this))
        d.editApiKey.setText(AiSecurePrefs.apiKey(this))
        d.switchOnDeviceLlm.isChecked = Prefs.assistantOnDeviceLlm(this)

        ArrayAdapter.createFromResource(
            this,
            R.array.on_device_profile_labels,
            android.R.layout.simple_spinner_item,
        ).also { ad ->
            ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            d.spinnerOnDeviceProfile.adapter = ad
        }
        val selIdx = onDeviceProfileKeyOrder.indexOf(Prefs.onDeviceModelProfileKey(this)).let { if (it >= 0) it else 0 }
        d.spinnerOnDeviceProfile.setSelection(selIdx)

        fun refreshGgufStatus() {
            d.textGgufStatus.text = if (TabletGgufStore.isModelPresent(this)) {
                getString(R.string.ai_gguf_status_ready)
            } else {
                getString(R.string.ai_gguf_status_missing)
            }
        }
        refreshGgufStatus()

        d.btnDownloadGguf.setOnClickListener {
            if (!Prefs.allowNetwork(this)) {
                Toast.makeText(this, R.string.ai_need_network, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            d.btnDownloadGguf.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                val r = TabletGgufDownloader.downloadDefaultModel(this@AiChatActivity)
                withContext(Dispatchers.Main) {
                    d.btnDownloadGguf.isEnabled = true
                    refreshGgufStatus()
                    Toast.makeText(
                        this@AiChatActivity,
                        if (r.isSuccess) R.string.ai_gguf_download_ok else R.string.ai_gguf_download_fail,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }

        d.btnImportGguf.setOnClickListener {
            importGgufLauncher.launch(arrayOf("*/*"))
        }

        d.btnRemoveGguf.setOnClickListener {
            if (!TabletGgufStore.isModelPresent(this@AiChatActivity)) {
                Toast.makeText(this@AiChatActivity, R.string.ai_gguf_status_missing, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            MaterialAlertDialogBuilder(this@AiChatActivity)
                .setTitle(R.string.ai_remove_model_confirm_title)
                .setMessage(R.string.ai_remove_model_confirm_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        OnDeviceLlm.closeAll()
                        TabletGgufStore.deleteModelFile(this@AiChatActivity)
                        withContext(Dispatchers.Main) {
                            refreshGgufStatus()
                            Toast.makeText(
                                this@AiChatActivity,
                                R.string.ai_remove_model_done,
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
                .show()
        }

        d.btnFetchModels.setOnClickListener {
            if (!Prefs.allowNetwork(this)) {
                Toast.makeText(this, R.string.ai_need_network, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val base = d.editBaseUrl.text?.toString()?.trim().orEmpty()
            if (base.isEmpty() || !AssistantUrlPolicy.isValidBaseUrl(base)) {
                Toast.makeText(this, R.string.assistant_need_endpoint, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            d.btnFetchModels.isEnabled = false
            lifecycleScope.launch(Dispatchers.IO) {
                val r = runCatching {
                    AssistantChatClient(
                        base,
                        d.editApiKey.text?.toString().orEmpty(),
                        Prefs.aiModel(this@AiChatActivity),
                    ).fetchModelIds()
                }
                withContext(Dispatchers.Main) {
                    d.btnFetchModels.isEnabled = true
                    r.fold(
                        onSuccess = { ids ->
                            if (ids.isEmpty()) {
                                Toast.makeText(this@AiChatActivity, R.string.ai_models_none, Toast.LENGTH_LONG).show()
                            } else {
                                MaterialAlertDialogBuilder(this@AiChatActivity)
                                    .setTitle(R.string.ai_models_pick_title)
                                    .setItems(ids.toTypedArray()) { _, which ->
                                        d.editModel.setText(ids[which])
                                    }
                                    .setNegativeButton(android.R.string.cancel, null)
                                    .show()
                            }
                        },
                        onFailure = { e ->
                            Toast.makeText(
                                this@AiChatActivity,
                                getString(R.string.ai_error, e.message ?: e.toString()),
                                Toast.LENGTH_LONG,
                            ).show()
                        },
                    )
                }
            }
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ai_api_settings)
            .setView(d.root)
            .setNeutralButton(R.string.ai_help_menu) { _, _ -> showHelpDialog() }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ai_save) { _, _ ->
                val idx = d.spinnerOnDeviceProfile.selectedItemPosition
                    .coerceIn(0, onDeviceProfileKeyOrder.lastIndex)
                Prefs.setOnDeviceModelProfileKey(this, onDeviceProfileKeyOrder[idx])
                Prefs.setAssistantOnDeviceLlm(this, d.switchOnDeviceLlm.isChecked)

                val base = d.editBaseUrl.text?.toString()?.trim().orEmpty()
                if (base.isNotEmpty()) {
                    if (!AssistantUrlPolicy.isValidBaseUrl(base)) {
                        Toast.makeText(this, R.string.assistant_invalid_base_url, Toast.LENGTH_LONG).show()
                    } else {
                        Prefs.setAiBaseUrl(this, base)
                    }
                }
                val model = d.editModel.text?.toString()?.trim().orEmpty()
                if (model.isNotEmpty()) {
                    Prefs.setAiModel(this, model)
                }
                AiSecurePrefs.setApiKey(this, d.editApiKey.text?.toString().orEmpty())
            }
            .show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.inputMessage.windowToken, 0)
    }

    private fun ensureMicThenListen(autoSend: Boolean) {
        voiceAutoSend = autoSend
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED -> startVoiceCapture(autoSend = autoSend)

            else -> requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startVoiceCapture(autoSend: Boolean) {
        voiceAutoSend = autoSend
        Toast.makeText(this, R.string.cali_listening, Toast.LENGTH_SHORT).show()
        val useVosk = Prefs.caliOfflineStt(this) && VoskModelStore.isInstalled(this)
        if (!useVosk) {
            caliVoice.startListening()
            return
        }
        lifecycleScope.launch {
            val path = VoskModelStore.modelPath(this@AiChatActivity)
            val loaded = withContext(Dispatchers.IO) {
                caliVoice.ensureVoskModelLoaded(path)
            }
            if (!loaded) {
                Toast.makeText(this@AiChatActivity, R.string.cali_vosk_model_load_error, Toast.LENGTH_LONG).show()
                caliVoice.startListening()
                return@launch
            }
            caliVoice.startVoskListening()
        }
    }

    private fun scheduleListenAfterIfNeeded(delayMs: Long = 550L) {
        if (!Prefs.caliListenAfter(this)) return
        binding.root.postDelayed(
            {
                if (isDestroyed || isFinishing) return@postDelayed
                if (!binding.btnSend.isEnabled) return@postDelayed
                if (caliVoice.isSpeaking()) return@postDelayed
                ensureMicThenListen(autoSend = true)
            },
            delayMs,
        )
    }

    private fun sendMessage() {
        val subject = binding.inputSubject.text?.toString()?.trim().orEmpty()
        val body = binding.inputMessage.text?.toString()?.trim().orEmpty()
        if (body.isEmpty()) return

        val onDevice = Prefs.assistantOnDeviceLlm(this)
        if (onDevice) {
            if (!TabletGgufStore.isModelPresent(this)) {
                Toast.makeText(this, R.string.on_device_llm_need_model, Toast.LENGTH_LONG).show()
                return
            }
        } else {
            if (!Prefs.allowNetwork(this)) {
                Toast.makeText(this, R.string.ai_need_network, Toast.LENGTH_LONG).show()
                return
            }
            val base = Prefs.aiBaseUrl(this).trim()
            if (base.isEmpty() || !AssistantUrlPolicy.isValidBaseUrl(base)) {
                Toast.makeText(this, R.string.assistant_need_endpoint, Toast.LENGTH_LONG).show()
                return
            }
        }

        val display = if (subject.isNotEmpty()) {
            getString(R.string.ai_user_display_format, subject, body)
        } else {
            body
        }
        val apiPayload = if (subject.isNotEmpty()) {
            "Library / subject: $subject\n\n$body"
        } else {
            body
        }

        transcript.add(ChatMessage(ChatSpeaker.User, display, apiPayload))
        adapter.append(transcript.last())
        binding.inputMessage.text?.clear()
        hideKeyboard()
        binding.chatList.scrollToPosition(adapter.itemCount - 1)

        runAssistantAfterUserAdded(subject, body)
    }

    private fun runAssistantAfterUserAdded(subject: String, userBody: String) {
        setSending(true)
        pendingTfliteUrl = null
        binding.btnInstallSuggested.visibility = View.GONE
        caliVoice.stopSpeaking()
        clearConnectionBanner()

        val onDevice = Prefs.assistantOnDeviceLlm(this)
        if (onDevice) {
            val placeholder = getString(R.string.ai_streaming_placeholder)
            transcript.add(ChatMessage(ChatSpeaker.Assistant, placeholder))
            adapter.append(transcript.last())
            binding.chatList.scrollToPosition(adapter.itemCount - 1)

            sendJob = lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val profileKey = Prefs.onDeviceModelProfileKey(this@AiChatActivity)
                    val profile = OnDeviceLlm.profileForPreferenceKey(profileKey)
                    val forModel = transcript.subList(0, transcript.lastIndex).toList()
                    val result = OnDeviceLlm.generateReply(
                        this@AiChatActivity,
                        profileKey,
                        profile,
                        buildSystemPrompt(),
                        forModel,
                        onPartial = { partial ->
                            withContext(Dispatchers.Main) {
                                if (isDestroyed || isFinishing) return@withContext
                                val shown = partial.ifBlank { placeholder }
                                transcript[transcript.lastIndex] = ChatMessage(ChatSpeaker.Assistant, shown)
                                adapter.replaceLast(transcript.last())
                                binding.chatList.scrollToPosition(adapter.itemCount - 1)
                            }
                        },
                    )
                    withContext(Dispatchers.Main) {
                        if (isDestroyed || isFinishing) return@withContext
                        setSending(false)
                        result.fold(
                            onSuccess = { raw ->
                                deliverAssistantSuccess(
                                    raw,
                                    subject,
                                    userBody,
                                    replaceStreamingPlaceholder = true,
                                )
                            },
                            onFailure = { e ->
                                deliverAssistantFailure(
                                    e,
                                    subject,
                                    userBody,
                                    replaceStreamingPlaceholder = true,
                                )
                            },
                        )
                    }
                } catch (e: CancellationException) {
                    withContext(Dispatchers.Main) {
                        if (!isDestroyed && !isFinishing) {
                            setSending(false)
                            deliverAssistantStopped()
                        }
                    }
                    throw e
                }
            }
        } else {
            val placeholder = getString(R.string.ai_streaming_placeholder)
            transcript.add(ChatMessage(ChatSpeaker.Assistant, placeholder))
            adapter.append(transcript.last())
            binding.chatList.scrollToPosition(adapter.itemCount - 1)

            val key = AiSecurePrefs.apiKey(this)
            val apiMessages = buildApiPayload()
            sendJob = lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val raw = AssistantChatClient(
                        Prefs.aiBaseUrl(this@AiChatActivity),
                        key,
                        Prefs.aiModel(this@AiChatActivity),
                    ).chatStreamWithFallback(apiMessages) { partial ->
                        runOnUiThread {
                            if (isDestroyed || isFinishing) return@runOnUiThread
                            val shown = partial.ifBlank { placeholder }
                            transcript[transcript.lastIndex] = ChatMessage(ChatSpeaker.Assistant, shown)
                            adapter.replaceLast(transcript.last())
                            binding.chatList.scrollToPosition(adapter.itemCount - 1)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        if (isDestroyed || isFinishing) return@withContext
                        setSending(false)
                        deliverAssistantSuccess(
                            raw,
                            subject,
                            userBody,
                            replaceStreamingPlaceholder = true,
                        )
                    }
                } catch (e: CancellationException) {
                    withContext(Dispatchers.Main) {
                        if (!isDestroyed && !isFinishing) {
                            setSending(false)
                            deliverAssistantStopped()
                        }
                    }
                    throw e
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        if (!isDestroyed && !isFinishing) {
                            setSending(false)
                            deliverAssistantFailure(
                                e,
                                subject,
                                userBody,
                                replaceStreamingPlaceholder = true,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun deliverAssistantStopped() {
        caliVoice.stopSpeaking()
        if (transcript.isNotEmpty() &&
            transcript.last().speaker == ChatSpeaker.Assistant
        ) {
            transcript[transcript.lastIndex] =
                ChatMessage(ChatSpeaker.Assistant, getString(R.string.ai_generation_stopped))
            adapter.replaceLast(transcript.last())
        } else {
            transcript.add(ChatMessage(ChatSpeaker.Assistant, getString(R.string.ai_generation_stopped)))
            adapter.append(transcript.last())
        }
        binding.chatList.scrollToPosition(adapter.itemCount - 1)
        scheduleListenAfterIfNeeded()
    }

    private fun deliverAssistantSuccess(
        rawReply: String,
        subject: String,
        userBody: String,
        replaceStreamingPlaceholder: Boolean = false,
    ) {
        clearConnectionBanner()
        SessionObservationStore.append(
            this,
            subject = subject,
            userChars = userBody.length,
            assistantChars = rawReply.length,
            nvqMode = Prefs.nvqAssessorMode(this),
            ok = true,
        )
        val action = AiActionParser.parse(rawReply)
        applyAssistantActions(action)
        val jsonTail = AiActionParser.parseJsonTail(rawReply)
        if (jsonTail != null) {
            WorkflowHubExecutor.applyFromAssistantJson(this, jsonTail)
            applyOpenRouteFromJson(jsonTail)
        }
        val displayText = AiActionParser.stripActionBlock(rawReply)
        if (replaceStreamingPlaceholder &&
            transcript.isNotEmpty() &&
            transcript.last().speaker == ChatSpeaker.Assistant
        ) {
            transcript[transcript.lastIndex] = ChatMessage(ChatSpeaker.Assistant, displayText)
            adapter.replaceLast(transcript.last())
        } else {
            transcript.add(ChatMessage(ChatSpeaker.Assistant, displayText))
            adapter.append(transcript.last())
        }
        binding.chatList.scrollToPosition(adapter.itemCount - 1)
        pendingTfliteUrl = action?.downloadTfliteUrl
        binding.btnInstallSuggested.visibility =
            if (pendingTfliteUrl != null) View.VISIBLE else View.GONE

        if (Prefs.caliSpeakReplies(this)) {
            caliVoice.speak(displayText)
        } else {
            scheduleListenAfterIfNeeded()
        }
    }

    private fun deliverAssistantFailure(
        e: Throwable,
        subject: String,
        userBody: String,
        replaceStreamingPlaceholder: Boolean = false,
    ) {
        SessionObservationStore.append(
            this,
            subject = subject,
            userChars = userBody.length,
            assistantChars = 0,
            nvqMode = Prefs.nvqAssessorMode(this),
            ok = false,
        )
        val baseErr = getString(R.string.ai_error, e.message ?: e.toString())
        val err = baseErr + ngrokUpstreamHintIfNeeded(baseErr)
        if (replaceStreamingPlaceholder &&
            transcript.isNotEmpty() &&
            transcript.last().speaker == ChatSpeaker.Assistant
        ) {
            transcript[transcript.lastIndex] = ChatMessage(ChatSpeaker.Assistant, err)
            adapter.replaceLast(transcript.last())
        } else {
            transcript.add(ChatMessage(ChatSpeaker.Assistant, err))
            adapter.append(transcript.last())
        }
        binding.chatList.scrollToPosition(adapter.itemCount - 1)
        if (!Prefs.assistantOnDeviceLlm(this)) {
            setConnectionBanner(err)
        }
    }

    private fun applyAssistantActions(action: AiAction?) {
        if (action == null) return
        action.webSearchQuery?.let { q ->
            Toast.makeText(this, R.string.cali_opened_search, Toast.LENGTH_SHORT).show()
            CaliIntentActions.openWebSearch(this, q)
        }
        val waypoints = action.navigateWaypoints
        if (!waypoints.isNullOrEmpty()) {
            Toast.makeText(this, R.string.cali_opened_nav, Toast.LENGTH_SHORT).show()
            CaliIntentActions.openMapsWaypoints(this, waypoints)
        } else {
            action.navigateQuery?.let { q ->
                Toast.makeText(this, R.string.cali_opened_nav, Toast.LENGTH_SHORT).show()
                CaliIntentActions.openNavigation(this, q)
            }
        }
        action.openHttpsUrl?.let { u ->
            Toast.makeText(this, R.string.cali_opened_link, Toast.LENGTH_SHORT).show()
            CaliIntentActions.openHttps(this, u)
        }
    }

    private fun applyOpenRouteFromJson(json: JSONObject) {
        val id = json.optString("open_route_id", json.optString("openRouteId", "")).trim()
        if (id.isEmpty()) return
        val snap = WorkflowHubStore.load(this)
        val rid = WorkflowHubStore.resolveRouteId(snap, id) ?: return
        val route = snap.routes.find { it.id == rid } ?: return
        Toast.makeText(this, R.string.cali_opened_nav, Toast.LENGTH_SHORT).show()
        CaliIntentActions.openMapsWaypoints(this, route.waypoints)
    }

    private fun buildApiPayload(): List<Pair<String, String>> =
        buildApiPayloadForMessages(transcript)

    private fun buildApiPayloadForMessages(messages: List<ChatMessage>): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        out.add("system" to buildSystemPrompt())
        for (m in messages) {
            when (m.speaker) {
                ChatSpeaker.User -> out.add("user" to (m.apiText ?: m.text))
                ChatSpeaker.Assistant -> out.add("assistant" to m.text)
            }
        }
        return out
    }

    private fun buildSystemPrompt(): String {
        val ctx = this@AiChatActivity
        return buildString {
            append(AiSystemPrompt.TEXT)
            if (Prefs.nvqAssessorMode(ctx)) {
                append("\n\n--- NVQ ASSESSOR BRIEF (bundled in app; verify against current AO / centre rules) ---\n")
                append(NvqAssessorBrief.load(ctx))
            }
            if (Prefs.observationLearning(ctx)) {
                append("\n\n--- WORKFLOW LEARNING (bundled guidance: observation → streamlined workflows) ---\n")
                append(WorkflowObservationBrief.load(ctx))
                val obs = SessionObservationStore.buildPromptSummary(ctx)
                if (obs.isNotBlank()) {
                    append("\n--- SESSION OBSERVATION SUMMARY (this device; subject + size metadata only) ---\n")
                    append(obs)
                }
            }
            append("\n\n--- OPERATIONS HUB (this device; tasks, candidates, route plans) ---\n")
            append(WorkflowHubStore.buildCaliContextBlock(ctx))
        }
    }

    private fun setSending(sending: Boolean) {
        binding.btnSend.isEnabled = !sending
        binding.inputMessage.isEnabled = !sending
        binding.inputSubject.isEnabled = !sending
        binding.btnMic.isEnabled = !sending
        binding.switchSpeakReplies.isEnabled = !sending
        binding.switchListenAfter.isEnabled = !sending
        binding.chipQuickSummarize.isEnabled = !sending
        binding.chipQuickSteps.isEnabled = !sending
        binding.chipQuickExample.isEnabled = !sending
        binding.chipQuickChecklist.isEnabled = !sending
        binding.toolbar.menu.findItem(R.id.action_stop_generation)?.apply {
            isVisible = sending
            isEnabled = sending
        }
        binding.toolbar.menu.findItem(R.id.action_clear_chat)?.isEnabled = !sending
        binding.toolbar.menu.findItem(R.id.action_cali_options)?.isEnabled = !sending
        binding.toolbar.menu.findItem(R.id.action_ai_settings)?.isEnabled = !sending
        binding.toolbar.menu.findItem(R.id.action_ai_help)?.isEnabled = !sending
        binding.toolbar.menu.findItem(R.id.action_stop_speaking)?.isEnabled = caliVoice.isSpeaking()
        refreshRetryShareMenu(sending)
    }

    private fun installSuggestedTflite() {
        val url = pendingTfliteUrl
        if (url.isNullOrBlank()) {
            Toast.makeText(this, R.string.ai_no_suggested_url, Toast.LENGTH_SHORT).show()
            return
        }
        if (!Prefs.allowNetwork(this)) {
            Toast.makeText(this, R.string.ai_need_network, Toast.LENGTH_LONG).show()
            return
        }
        setSending(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val res = runCatching {
                TfliteRemoteInstaller.installBlocking(this@AiChatActivity, url)
            }
            withContext(Dispatchers.Main) {
                setSending(false)
                res.fold(
                    onSuccess = { n ->
                        Toast.makeText(
                            this@AiChatActivity,
                            getString(R.string.ai_install_done, n.toInt()),
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                    onFailure = { e ->
                        Toast.makeText(
                            this@AiChatActivity,
                            getString(R.string.download_fail, e.message ?: e.toString()),
                            Toast.LENGTH_LONG,
                        ).show()
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        caliVoice.shutdown()
        runBlocking {
            OnDeviceLlm.closeAll()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_NVQ_ASSESSOR = "extra_nvq_assessor"
        const val EXTRA_WAKE_OPEN_MIC = "extra_wake_open_mic"

        fun intent(context: Context, nvqAssessorPreferred: Boolean = false): Intent =
            Intent(context, AiChatActivity::class.java).apply {
                putExtra(EXTRA_NVQ_ASSESSOR, nvqAssessorPreferred)
            }
    }
}
