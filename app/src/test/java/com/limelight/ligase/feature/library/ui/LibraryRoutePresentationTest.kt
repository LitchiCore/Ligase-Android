package com.limelight.ligase.feature.library.ui

import com.limelight.ligase.feature.library.domain.HostLibraryKind
import com.limelight.ligase.feature.library.domain.HostSortMode
import com.limelight.ligase.feature.library.domain.LibraryItemKey
import com.limelight.ligase.feature.library.domain.LibraryLayoutMode
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.feature.library.domain.LigaseLibraryStatus
import com.limelight.ligase.library.LibraryConnectivity
import com.limelight.ligase.library.LibraryContentPresentation
import com.limelight.ligase.library.ManualLibraryOrderDraft
import com.limelight.ligase.library.ManualLibraryOrderEntry
import com.limelight.ligase.library.PreservedLibraryBanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryRoutePresentationTest {
    @Test
    fun `offline route preserves successful content and local search sort`() {
        val state = state(
            items = listOf(item("beta", "Beta"), item("alpha", "Alpha")),
            status = LigaseLibraryStatus.READY,
            connectivity = LibraryConnectivity.OFFLINE,
            sortMode = HostSortMode.NAME_ASCENDING,
        )

        val presentation = libraryRoutePresentation(state, query = "a")

        assertEquals(listOf("Alpha", "Beta"), presentation.visibleItems.map { it.name })
        assertEquals(LibraryContentPresentation.CONTENT, presentation.content)
        assertEquals(PreservedLibraryBanner.OFFLINE, presentation.preservedBanner)
        assertTrue(presentation.canManualSort)
    }

    @Test
    fun `manual draft order overrides local query and sort`() {
        val state = state(
            items = listOf(item("alpha", "Alpha"), item("beta", "Beta")),
            sortMode = HostSortMode.NAME_ASCENDING,
            manualDraft = ManualLibraryOrderDraft(
                originalEntries = listOf(
                    ManualLibraryOrderEntry("beta", "Beta", locked = false),
                    ManualLibraryOrderEntry("alpha", "Alpha", locked = false),
                ),
            ),
        )

        val presentation = libraryRoutePresentation(state, query = "missing")

        assertEquals(listOf("Beta", "Alpha"), presentation.visibleItems.map { it.name })
    }

    @Test
    fun `manual entry and last played gates remain derived from current snapshot`() {
        val noActions = libraryRoutePresentation(
            state(
                items = listOf(
                    item("one", "One", lastPlayedAt = "2026-07-25T00:00:00Z"),
                    item("two", "Two"),
                ),
                actionsEnabled = false,
            ),
            query = "",
        )
        assertTrue(noActions.canSortByLastPlayed)
        assertFalse(noActions.canManualSort)

        val oneItem = libraryRoutePresentation(
            state(items = listOf(item("one", "One"))),
            query = "",
        )
        assertFalse(oneItem.canManualSort)
    }

    private fun state(
        items: List<LigaseLibraryItem>,
        status: LigaseLibraryStatus = LigaseLibraryStatus.READY,
        connectivity: LibraryConnectivity = LibraryConnectivity.ONLINE,
        sortMode: HostSortMode = HostSortMode.NAME_ASCENDING,
        actionsEnabled: Boolean = true,
        manualDraft: ManualLibraryOrderDraft? = null,
    ): LibraryRouteUiState = LibraryRouteUiState(
        hosts = emptyList(),
        selectedHost = null,
        items = items,
        loading = false,
        refreshing = false,
        status = status,
        connectivity = connectivity,
        runningAppId = 0,
        sortMode = sortMode,
        layoutMode = LibraryLayoutMode.LIST,
        hasOperatePermission = actionsEnabled,
        actionsEnabled = actionsEnabled,
        showTopBar = true,
        manualEditor = LibraryManualEditorUiState(
            draft = manualDraft,
            saving = false,
            editingEnabled = actionsEnabled,
            errorMessage = null,
        ),
    )

    private fun item(
        uuid: String,
        name: String,
        lastPlayedAt: String? = null,
    ): LigaseLibraryItem = LigaseLibraryItem(
        key = LibraryItemKey.HostUuid(uuid),
        name = name,
        kind = HostLibraryKind.EXECUTABLE,
        hostAppUuid = uuid,
        appId = null,
        steamAppId = null,
        addedAt = null,
        updatedAt = null,
        lastPlayedAt = lastPlayedAt,
        launchApp = null,
    )
}
