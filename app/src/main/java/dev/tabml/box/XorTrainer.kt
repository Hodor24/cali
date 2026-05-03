package dev.tabml.box

import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Tiny 2 → H → 1 MLP on XOR: sigmoid activations, MSE loss, SGD.
 * Weights start random — no external checkpoints.
 */
class XorTrainer(
    private val hiddenSize: Int = 16,
    private val learningRate: Double = 0.5,
    rng: Random = Random.Default,
) {
    private val w1: Array<DoubleArray> = Array(2) { DoubleArray(hiddenSize) }
    private val b1 = DoubleArray(hiddenSize)
    private val w2 = DoubleArray(hiddenSize)
    private var b2 = 0.0

    init {
        fun hiInit() = (rng.nextDouble() - 0.5) * 2.0 * sqrt(2.0 / (2 + hiddenSize))
        for (i in 0 until 2) {
            for (j in 0 until hiddenSize) {
                w1[i][j] = hiInit()
            }
        }
        for (j in 0 until hiddenSize) {
            w2[j] = (rng.nextDouble() - 0.5) * 0.5
        }
    }

    data class EpochLog(val epoch: Int, val loss: Double)

    private fun sigmoid(x: Double): Double = 1.0 / (1.0 + exp(-x))

    /** y = sigmoid(z); derivative w.r.t. z given y */
    private fun sigmoidPrimeFromY(y: Double): Double = y * (1.0 - y)

    fun train(
        epochs: Int,
        logEvery: Int,
        onEpoch: (EpochLog) -> Unit,
    ): Double {
        val xs = arrayOf(
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(0.0, 1.0),
            doubleArrayOf(1.0, 0.0),
            doubleArrayOf(1.0, 1.0),
        )
        val ys = doubleArrayOf(0.0, 1.0, 1.0, 0.0)

        var lastLoss = 0.0
        val h = DoubleArray(hiddenSize)
        val z1 = DoubleArray(hiddenSize)
        val dW2 = DoubleArray(hiddenSize)
        val dH = DoubleArray(hiddenSize)
        val dW1 = Array(2) { DoubleArray(hiddenSize) }

        for (e in 0 until epochs) {
            var sumLoss = 0.0
            for (n in xs.indices) {
                val x0 = xs[n][0]
                val x1 = xs[n][1]
                val target = ys[n]

                for (j in 0 until hiddenSize) {
                    z1[j] = w1[0][j] * x0 + w1[1][j] * x1 + b1[j]
                    h[j] = sigmoid(z1[j])
                }
                var z2 = b2
                for (j in 0 until hiddenSize) {
                    z2 += w2[j] * h[j]
                }
                val out = sigmoid(z2)
                val err = out - target
                sumLoss += 0.5 * err * err

                val dOut = err * sigmoidPrimeFromY(out)
                for (j in 0 until hiddenSize) {
                    dW2[j] = dOut * h[j]
                    dH[j] = dOut * w2[j] * sigmoidPrimeFromY(h[j])
                }

                for (j in 0 until hiddenSize) {
                    dW1[0][j] = dH[j] * x0
                    dW1[1][j] = dH[j] * x1
                }

                for (j in 0 until hiddenSize) {
                    w2[j] -= learningRate * dW2[j]
                }
                b2 -= learningRate * dOut
                for (j in 0 until hiddenSize) {
                    b1[j] -= learningRate * dH[j]
                    w1[0][j] -= learningRate * dW1[0][j]
                    w1[1][j] -= learningRate * dW1[1][j]
                }
            }
            lastLoss = sumLoss / xs.size
            if (e % logEvery == 0 || e == epochs - 1) {
                onEpoch(EpochLog(e, lastLoss))
            }
        }
        return lastLoss
    }

    fun predict(x0: Double, x1: Double): Double {
        val h = DoubleArray(hiddenSize)
        for (j in 0 until hiddenSize) {
            val z = w1[0][j] * x0 + w1[1][j] * x1 + b1[j]
            h[j] = sigmoid(z)
        }
        var z2 = b2
        for (j in 0 until hiddenSize) {
            z2 += w2[j] * h[j]
        }
        return sigmoid(z2)
    }
}
