package dev.tabml.box

import android.content.Context
import org.tensorflow.lite.Interpreter

/** TensorFlow Lite XOR runner; uses bundled asset or a user-downloaded `.tflite` from [ModelLoader]. */
class XorTfliteRunner(context: Context) : AutoCloseable {

    private val interpreter: Interpreter

    init {
        val modelBuffer = ModelLoader.loadMappedForInference(context)
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
