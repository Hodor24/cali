package dev.tabml.box

import java.security.MessageDigest

object CaliPinHasher {
    private const val SALT = "dev.tabml.box.cali.pin.v1"

    fun sha256Hex(pin: String): String {
        val d = MessageDigest.getInstance("SHA-256").digest((SALT + pin).toByteArray(Charsets.UTF_8))
        return d.joinToString("") { b -> "%02x".format(b) }
    }
}
