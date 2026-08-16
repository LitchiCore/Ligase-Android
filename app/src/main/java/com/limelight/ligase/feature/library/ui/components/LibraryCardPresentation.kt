package com.limelight.ligase.feature.library.ui.components

import com.limelight.ligase.feature.library.domain.HostLayoutBindingState
import com.limelight.ligase.feature.library.presentation.HostLibraryAuthorityPresentation

internal enum class LibraryCardDisabledReason {
    MANUAL_EDITING,
    OPERATIONS_DISABLED,
    NOT_LAUNCHABLE,
}

internal data class LibraryCardActionPolicy(
    val launchEnabled: Boolean,
    val configureEnabled: Boolean,
    val showDragHandle: Boolean,
    val launchDisabledReason: LibraryCardDisabledReason?,
    val configureDisabledReason: LibraryCardDisabledReason?,
)

internal data class LibraryCardAuthorityMetadata(
    val steamAppId: String?,
    val layoutState: HostLayoutBindingState,
)

internal fun libraryCardAuthorityMetadata(
    authority: HostLibraryAuthorityPresentation,
): LibraryCardAuthorityMetadata {
    val steamAppId = authority.portableIdentityId?.takeIf { id ->
        authority.portableIdentityProvider == "steam" &&
            id.matches(Regex("[1-9][0-9]*"))
    }
    return LibraryCardAuthorityMetadata(
        steamAppId = steamAppId,
        layoutState = authority.layoutState,
    )
}

internal fun libraryCardActionPolicy(
    isLaunchable: Boolean,
    canOperate: Boolean,
    manualEditing: Boolean,
): LibraryCardActionPolicy {
    val sharedReason = when {
        manualEditing -> LibraryCardDisabledReason.MANUAL_EDITING
        !canOperate -> LibraryCardDisabledReason.OPERATIONS_DISABLED
        else -> null
    }
    val launchReason = sharedReason ?: if (!isLaunchable) {
        LibraryCardDisabledReason.NOT_LAUNCHABLE
    } else {
        null
    }
    return LibraryCardActionPolicy(
        launchEnabled = launchReason == null,
        configureEnabled = sharedReason == null,
        showDragHandle = manualEditing,
        launchDisabledReason = launchReason,
        configureDisabledReason = sharedReason,
    )
}
