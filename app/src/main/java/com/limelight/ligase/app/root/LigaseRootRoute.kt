package com.limelight.ligase.app.root

import com.limelight.ligase.feature.library.domain.LibraryLayoutMode

internal enum class LigaseLayoutRoute {
    MAIN,
    HALL,
    EDITOR,
}

internal data class LibraryGridRetentionKey(
    val hostUuid: String?,
    val layoutMode: LibraryLayoutMode,
)

internal fun libraryGridRetentionKey(
    hostUuid: String?,
    layoutMode: LibraryLayoutMode,
): LibraryGridRetentionKey = LibraryGridRetentionKey(hostUuid, layoutMode)

internal fun manualDraftRetentionKey(hostUuid: String?): String? = hostUuid

internal fun rootRouteAfterMainPageSelection(): LigaseLayoutRoute = LigaseLayoutRoute.MAIN
