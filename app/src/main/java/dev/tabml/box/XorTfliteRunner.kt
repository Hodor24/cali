package dev.tabml.box

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil

/** Runs the bundled `assets/models/xor_mlp.tflite` via TensorFlow Lite (inference only). */
class XorTfliteRunner(context: Context) : AutoCloseable {

    private val interpreter: Interpreter

    init {
        val modelBuffer = FileUtil.loadMappedFile(context, "models/xor_mlp.tflite")
        val opts = Interpreter.Options().apply { setNumThreads(4) }
        interpreter = Interpreter(modelBuffer, opts)
    }

    fun predict(x0: Float, x1: Float): Float {
        val input = arrayOf(floatArrayOf(x0, x1))
        val output = Array(1) { FloatArray(1) }
        interpreter.run(input, output)
        return output[0][0]
    }

    override fun close() {
        interpreter.close()
    }
}
