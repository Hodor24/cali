package dev.tabml.box

/** Process-lifetime unlock after correct Cali PIN (not persisted). */
object CaliPinSession {
    @Volatile
    var unlocked: Boolean = false
}
