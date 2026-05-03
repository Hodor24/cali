package dev.tabml.box

import org.json.JSONArray
import org.json.JSONObject

/** Serializable snapshot of XOR MLP weights (your training, not a vendor checkpoint). */
data class XorCheckpoint(
    val hiddenSize: Int,
    val w1: List<List<Double>>,
    val b1: List<Double>,
    val w2: List<Double>,
    val b2: Double,
    /** Loss after the last training run that wrote this file, if known. */
    val lastLoss: Double? = null,
    /** Wall-clock time when this checkpoint was saved (System.currentTimeMillis). */
    val savedAtMs: Long? = null,
) {
    fun toJsonString(): String {
        val root = JSONObject()
        root.put("v", 1)
        root.put("hiddenSize", hiddenSize)
        val w1a = JSONArray()
        for (row in w1) {
            val r = JSONArray()
            for (x in row) r.put(x)
            w1a.put(r)
        }
        root.put("w1", w1a)
        root.put("b1", JSONArray(b1))
        root.put("w2", JSONArray(w2))
        root.put("b2", b2)
        lastLoss?.let { root.put("lastLoss", it) }
        savedAtMs?.let { root.put("savedAtMs", it) }
        return root.toString()
    }

    companion object {
        fun parseJson(json: String): XorCheckpoint {
            val o = JSONObject(json)
            require(o.getInt("v") == 1) { "unsupported checkpoint version" }
            val hidden = o.getInt("hiddenSize")
            val w1a = o.getJSONArray("w1")
            val w1 = List(2) { i ->
                val row = w1a.getJSONArray(i)
                List(hidden) { j -> row.getDouble(j) }
            }
            val b1a = o.getJSONArray("b1")
            val b1 = List(hidden) { b1a.getDouble(it) }
            val w2a = o.getJSONArray("w2")
            val w2 = List(hidden) { w2a.getDouble(it) }
            val b2 = o.getDouble("b2")
            val lastLoss = if (o.has("lastLoss")) o.getDouble("lastLoss") else null
            val savedAtMs = if (o.has("savedAtMs")) o.getLong("savedAtMs") else null
            return XorCheckpoint(hidden, w1, b1, w2, b2, lastLoss, savedAtMs)
        }
    }
}
