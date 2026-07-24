package com.limelight.ligase.library

import androidx.annotation.StringRes
import com.limelight.R
import com.limelight.ligase.feature.library.domain.LibraryHdrReason

@StringRes
internal fun LibraryHdrReason.messageResource(): Int =
    when (this) {
        LibraryHdrReason.AVAILABLE -> R.string.ligase_hdr_available
        LibraryHdrReason.USER_DISABLED -> R.string.ligase_hdr_disabled
        LibraryHdrReason.HOST_CAPABILITY_UNKNOWN,
        LibraryHdrReason.DISPLAY_CAPABILITY_UNKNOWN,
        LibraryHdrReason.DECODER_CAPABILITY_UNKNOWN -> R.string.ligase_hdr_unknown
        LibraryHdrReason.HOST_ENCODING_UNSUPPORTED -> R.string.ligase_hdr_host_unsupported
        LibraryHdrReason.DISPLAY_UNSUPPORTED,
        LibraryHdrReason.DECODER_UNSUPPORTED -> R.string.ligase_hdr_device_unsupported
    }
