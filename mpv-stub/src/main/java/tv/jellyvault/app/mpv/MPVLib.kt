package tv.jellyvault.app.mpv

/**
 * Stubs out MPVLib of libMPV
 */
object MPVLib {
    fun setPropertyString(
        key: String,
        value: String,
    ): Unit = throw MpvStubException()

    fun setPropertyInt(
        key: String,
        value: Int,
    ): Unit = throw MpvStubException()

    fun setPropertyDouble(
        key: String,
        value: Double,
    ): Unit = throw MpvStubException()

    fun setPropertyBoolean(
        key: String,
        value: Boolean,
    ): Unit = throw MpvStubException()
}
