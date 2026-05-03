package dev.tabml.box

import android.content.Context

/** Clears on-disk Cali chat + observation metadata (not model files or API keys). */
object CaliDataWiper {
    fun wipeTranscriptAndObservations(context: Context) {
        ChatTranscriptStore.save(context, emptyList())
        SessionObservationStore.clear(context)
        WorkflowHubStore.clear(context)
    }
}
