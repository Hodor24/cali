package dev.tabml.box

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        binding.switchNetwork.isChecked = Prefs.allowNetwork(this)
        binding.switchNetwork.setOnCheckedChangeListener { _, isChecked ->
            Prefs.setAllowNetwork(this, isChecked)
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
        refreshCheckpointUi()
    }

    private fun refreshCheckpointUi() {
        val has = CheckpointStore.exists(this)
        binding.btnLoadSaved.isEnabled = has
        binding.btnContinueTrain.isEnabled = has
        binding.btnDeleteCheckpoint.isEnabled = has
    }

    private fun setTrainingUiLocked(locked: Boolean) {
        binding.btnTrain.isEnabled = !locked
        binding.btnLoadSaved.isEnabled = !locked && CheckpointStore.exists(this)
        binding.btnContinueTrain.isEnabled = !locked && CheckpointStore.exists(this)
        binding.btnDeleteCheckpoint.isEnabled = !locked && CheckpointStore.exists(this)
        binding.btnTfliteXor.isEnabled = !locked
    }

    private fun saveCheckpoint(trainer: XorTrainer, finalLoss: Double) {
        val cp = trainer.toCheckpoint().copy(
            lastLoss = finalLoss,
            savedAtMs = System.currentTimeMillis(),
        )
        CheckpointStore.save(this, cp)
    }

    private fun runTraining() {
        setTrainingUiLocked(true)
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
                setTrainingUiLocked(false)
                refreshCheckpointUi()
            }
        }
    }

    private fun continueTrainingFromSaved() {
        if (!CheckpointStore.exists(this)) {
            Toast.makeText(this, R.string.continue_train_missing, Toast.LENGTH_LONG).show()
            return
        }
        setTrainingUiLocked(true)
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
                setTrainingUiLocked(false)
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
                binding.btnLoadSaved.isEnabled = true
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

    private fun runTfliteXor() {
        setTrainingUiLocked(true)
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
                setTrainingUiLocked(false)
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
