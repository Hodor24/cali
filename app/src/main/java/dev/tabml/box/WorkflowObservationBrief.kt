package dev.tabml.box

import android.content.Context

object WorkflowObservationBrief {
    fun load(context: Context): String =
        context.resources.openRawResource(R.raw.workflow_observation_brief).bufferedReader(Charsets.UTF_8).use {
            it.readText()
        }
}
