package com.limelight.ligase.feature.library.ui

import com.limelight.ligase.feature.library.domain.HostSortMode
import com.limelight.ligase.feature.library.domain.LibraryLayoutMode
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.feature.library.domain.LigaseLibraryStatus
import com.limelight.ligase.library.LibraryConnectivity
import com.limelight.ligase.library.ManualLibraryOrderDraft
import com.limelight.nvstream.http.ComputerDetails

data class LibraryManualEditorUiState(
    val draft: ManualLibraryOrderDraft?,
    val saving: Boolean,
    val editingEnabled: Boolean,
    val errorMessage: Int?,
)

data class LibraryRouteUiState(
    val hosts: List<ComputerDetails>,
    val selectedHost: ComputerDetails?,
    val items: List<LigaseLibraryItem>,
    val loading: Boolean,
    val refreshing: Boolean,
    val status: LigaseLibraryStatus,
    val connectivity: LibraryConnectivity,
    val runningAppId: Int,
    val sortMode: HostSortMode,
    val layoutMode: LibraryLayoutMode,
    val hasOperatePermission: Boolean,
    val actionsEnabled: Boolean,
    val showTopBar: Boolean,
    val manualEditor: LibraryManualEditorUiState,
)

data class LibraryRouteActions(
    val onSortModeChanged: (HostSortMode) -> Unit,
    val onLayoutModeChanged: (LibraryLayoutMode) -> Unit,
    val onHostSelected: (ComputerDetails) -> Unit,
    val onAddHost: () -> Unit,
    val onRemoveHost: (ComputerDetails) -> Unit,
    val onLaunch: (LigaseLibraryItem) -> Unit,
    val onConfigure: (LigaseLibraryItem) -> Unit,
    val onRetrySync: () -> Unit,
    val onManualSort: () -> Unit,
    val onManualMove: (movingUuid: String, targetUuid: String) -> Unit,
    val onManualSave: () -> Unit,
    val onManualCancel: () -> Unit,
)
