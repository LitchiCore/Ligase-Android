package com.limelight.ligase.feature.library.domain

enum class LibraryHdrReason {
    AVAILABLE,
    USER_DISABLED,
    HOST_CAPABILITY_UNKNOWN,
    HOST_ENCODING_UNSUPPORTED,
    DISPLAY_CAPABILITY_UNKNOWN,
    DISPLAY_UNSUPPORTED,
    DECODER_CAPABILITY_UNKNOWN,
    DECODER_UNSUPPORTED,
}

data class LibraryHdrState(
    val available: Boolean,
    val reason: LibraryHdrReason,
) {
    companion object {
        val UNKNOWN = LibraryHdrState(
            available = false,
            reason = LibraryHdrReason.HOST_CAPABILITY_UNKNOWN,
        )
    }
}

object LibraryHdrStateResolver {
    fun resolve(
        hostEncodingSupported: Boolean?,
        displaySupported: Boolean?,
        decoderSupported: Boolean?,
        userEnabled: Boolean?,
    ): LibraryHdrState {
        val reason = when {
            userEnabled == false -> LibraryHdrReason.USER_DISABLED
            hostEncodingSupported == null -> LibraryHdrReason.HOST_CAPABILITY_UNKNOWN
            !hostEncodingSupported -> LibraryHdrReason.HOST_ENCODING_UNSUPPORTED
            displaySupported == null -> LibraryHdrReason.DISPLAY_CAPABILITY_UNKNOWN
            !displaySupported -> LibraryHdrReason.DISPLAY_UNSUPPORTED
            decoderSupported == null -> LibraryHdrReason.DECODER_CAPABILITY_UNKNOWN
            !decoderSupported -> LibraryHdrReason.DECODER_UNSUPPORTED
            else -> LibraryHdrReason.AVAILABLE
        }
        return LibraryHdrState(
            available = reason == LibraryHdrReason.AVAILABLE,
            reason = reason,
        )
    }
}
