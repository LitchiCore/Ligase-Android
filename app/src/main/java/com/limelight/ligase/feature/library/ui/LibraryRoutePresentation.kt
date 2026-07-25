package com.limelight.ligase.feature.library.ui

import com.limelight.ligase.feature.library.domain.LigaseLibraryAdapter
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.library.LibraryContentPresentation
import com.limelight.ligase.library.PreservedLibraryBanner
import com.limelight.ligase.library.libraryContentPresentation
import com.limelight.ligase.library.preservedLibraryBanner

internal data class LibraryRoutePresentation(
    val visibleItems: List<LigaseLibraryItem>,
    val content: LibraryContentPresentation,
    val preservedBanner: PreservedLibraryBanner?,
    val canSortByLastPlayed: Boolean,
    val canManualSort: Boolean,
)

internal fun libraryRoutePresentation(
    state: LibraryRouteUiState,
    query: String,
): LibraryRoutePresentation {
    val visibleItems = state.manualEditor.draft?.entries?.mapNotNull { entry ->
        state.items.firstOrNull { item ->
            item.hostAppUuid.equals(entry.uuid, ignoreCase = true)
        }
    } ?: LigaseLibraryAdapter.visibleItems(state.items, query, state.sortMode)
    val hasItems = state.items.isNotEmpty()
    return LibraryRoutePresentation(
        visibleItems = visibleItems,
        content = libraryContentPresentation(
            status = state.status,
            hasItems = hasItems,
        ),
        preservedBanner = preservedLibraryBanner(
            status = state.status,
            hasItems = hasItems,
            connectivity = state.connectivity,
        ),
        canSortByLastPlayed = state.items.any {
            !it.isSystem && !it.lastPlayedAt.isNullOrBlank()
        },
        canManualSort = state.actionsEnabled &&
            state.items.count { it.hostAppUuid != null } > 1,
    )
}
