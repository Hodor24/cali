package dev.tabml.box

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dev.tabml.box.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    }

    private fun runTraining() {
        binding.btnTrain.isEnabled = false
        binding.logView.text = getString(R.string.status_training)
        log.clear()
        appendLog(getString(R.string.log_start))

        lifecycleScope.launch(Dispatchers.Default) {
            val trainer = XorTrainer(
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

            withContext(Dispatchers.Main) {
                synchronized(lines) {
                    lines.forEach { appendLog(it) }
                }
                appendLog(String.format(Locale.US, "final loss: %.6f", finalLoss))
                checkLines.forEach { appendLog(it) }
                appendLog(getString(R.string.log_done))
                binding.btnTrain.isEnabled = true
            }
        }
    }

    private fun appendLog(line: String) {
        if (log.isNotEmpty()) log.append('\n')
        log.append(line)
        binding.logView.text = log.toString()
    }
}
