package dev.tabml.box

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.tabml.box.databinding.ActivityAiChatBinding
import dev.tabml.box.databinding.DialogAiSettingsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiChatBinding
    private val transcript = mutableListOf<ChatMessage>()
    private val adapter = ChatAdapter()
    private var pendingTfliteUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.toolbar.setOnMenuItemClickListener(this::onToolbarMenu)

        binding.btnAiHelp.setOnClickListener { showHelpDialog() }
        binding.btnAiApiSettings.setOnClickListener { showApiSettingsDialog() }

        binding.chatList.layoutManager = LinearLayoutManager(this)
        binding.chatList.adapter = adapter

        binding.btnSend.setOnClickListener { sendMessage() }
        binding.inputMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        binding.btnInstallSuggested.setOnClickListener { installSuggestedTflite() }

        if (!Prefs.allowNetwork(this)) {
            Toast.makeText(this, R.string.ai_need_network, Toast.LENGTH_LONG).show()
        }
    }

    private fun onToolbarMenu(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_ai_settings -> showApiSettingsDialog()
            R.id.action_ai_help -> showHelpDialog()
            else -> return false
        }
        return true
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
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ai_api_settings)
            .setView(d.root)
            .setNeutralButton(R.string.ai_help_menu) { _, _ -> showHelpDialog() }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ai_save) { _, _ ->
                val base = d.editBaseUrl.text?.toString()?.trim().orEmpty()
                if (base.isNotEmpty()) {
                    Prefs.setAiBaseUrl(this, base)
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

    private fun sendMessage() {
        if (!Prefs.allowNetwork(this)) {
            Toast.makeText(this, R.string.ai_need_network, Toast.LENGTH_LONG).show()
            return
        }
        val key = AiSecurePrefs.apiKey(this)
        if (key.isBlank()) {
            Toast.makeText(this, R.string.ai_no_key_toast, Toast.LENGTH_SHORT).show()
        }

        val subject = binding.inputSubject.text?.toString()?.trim().orEmpty()
        val body = binding.inputMessage.text?.toString()?.trim().orEmpty()
        if (body.isEmpty()) return

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

        val apiMessages = buildApiPayload()
        setSending(true)
        pendingTfliteUrl = null
        binding.btnInstallSuggested.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val client = OpenAiChatClient(
                    Prefs.aiBaseUrl(this@AiChatActivity),
                    key,
                    Prefs.aiModel(this@AiChatActivity),
                )
                client.chat(apiMessages)
            }
            withContext(Dispatchers.Main) {
                setSending(false)
                result.fold(
                    onSuccess = { reply ->
                        transcript.add(ChatMessage(ChatSpeaker.Assistant, reply))
                        adapter.append(transcript.last())
                        binding.chatList.scrollToPosition(adapter.itemCount - 1)
                        val action = AiActionParser.parse(reply)
                        pendingTfliteUrl = action?.downloadTfliteUrl
                        binding.btnInstallSuggested.visibility =
                            if (pendingTfliteUrl != null) View.VISIBLE else View.GONE
                    },
                    onFailure = { e ->
                        val err = getString(R.string.ai_error, e.message ?: e.toString())
                        transcript.add(ChatMessage(ChatSpeaker.Assistant, err))
                        adapter.append(transcript.last())
                        binding.chatList.scrollToPosition(adapter.itemCount - 1)
                    },
                )
            }
        }
    }

    private fun buildApiPayload(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        out.add("system" to AiSystemPrompt.TEXT)
        for (m in transcript) {
            when (m.speaker) {
                ChatSpeaker.User -> out.add("user" to (m.apiText ?: m.text))
                ChatSpeaker.Assistant -> out.add("assistant" to m.text)
            }
        }
        return out
    }

    private fun setSending(sending: Boolean) {
        binding.btnSend.isEnabled = !sending
        binding.inputMessage.isEnabled = !sending
        binding.inputSubject.isEnabled = !sending
        binding.toolbar.menu.findItem(R.id.action_ai_settings)?.isEnabled = !sending
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

    companion object {
        fun intent(context: Context): Intent = Intent(context, AiChatActivity::class.java)
    }
}
