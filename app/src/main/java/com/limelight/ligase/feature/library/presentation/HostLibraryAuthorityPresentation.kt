package com.limelight.ligase.feature.library.presentation

import com.limelight.ligase.feature.library.domain.HostLayoutBindingResolution
import com.limelight.ligase.feature.library.domain.HostLayoutBindingState
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem

data class HostLibraryAuthorityPresentation(
    val hasPortableIdentity: Boolean,
    val hasCoverAuthority: Boolean,
    val layoutState: HostLayoutBindingState,
    val layoutReadyLocally: Boolean,
    val coverCurrent: Boolean,
)

fun presentHostLibraryAuthority(
    item: LigaseLibraryItem,
    resolution: HostLayoutBindingResolution,
    verifiedCoverCurrent: Boolean,
): HostLibraryAuthorityPresentation = HostLibraryAuthorityPresentation(
    hasPortableIdentity = item.portableIdentity != null,
    hasCoverAuthority = item.coverAuthority != null,
    layoutState = resolution.state,
    layoutReadyLocally = resolution.state == HostLayoutBindingState.RESOLVED,
    coverCurrent = item.coverAuthority != null && verifiedCoverCurrent,
)
