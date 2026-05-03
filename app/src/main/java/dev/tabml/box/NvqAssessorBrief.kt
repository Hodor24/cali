package dev.tabml.box

import android.content.Context

/**
 * Loads the bundled construction NVQ assessor brief from [R.raw.nvq_assessor_knowledge].
 */
object NvqAssessorBrief {
    fun load(context: Context): String =
        context.resources.openRawResource(R.raw.nvq_assessor_knowledge).bufferedReader(Charsets.UTF_8).use {
            it.readText()
        }
}
