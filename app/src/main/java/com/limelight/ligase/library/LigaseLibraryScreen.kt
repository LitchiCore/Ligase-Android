package com.limelight.ligase.library

import com.limelight.ligase.feature.library.domain.HostSortMode
import com.limelight.ligase.feature.library.domain.LibraryLayoutMode
import com.limelight.ligase.feature.library.domain.LigaseLibraryAdapter
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.feature.library.domain.LigaseLibraryStatus
import com.limelight.ligase.feature.library.ui.components.LibraryBlockingState
import com.limelight.ligase.feature.library.ui.components.LibraryEmptyState
import com.limelight.ligase.feature.library.ui.components.LibraryGamePosterCard
import com.limelight.ligase.feature.library.ui.components.LibraryGameRowCard
import com.limelight.ligase.feature.library.ui.components.LibraryGamesToolbar
import com.limelight.ligase.feature.library.ui.components.LibraryHostStatus
import com.limelight.ligase.feature.library.ui.components.LibraryNoHostSelected
import com.limelight.ligase.feature.library.ui.components.LibraryPreservedContentBanner
import com.limelight.ligase.feature.library.ui.components.LibrarySearchField
import com.limelight.ligase.feature.library.ui.manual.ManualLibraryEditBar
import com.limelight.ligase.feature.library.ui.manual.manualLibraryReorderModifier
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.grid.assets.CachedAppAssetLoader
import com.limelight.ligase.ligaseNavigationContentBottomPadding
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LigaseLibraryPage(
    hosts: List<ComputerDetails>,
    selectedHost: ComputerDetails?,
    items: List<LigaseLibraryItem>,
    loading: Boolean,
    refreshing: Boolean,
    status: LigaseLibraryStatus,
    connectivity: LibraryConnectivity,
    runningAppId: Int,
    sortMode: HostSortMode,
    layoutMode: LibraryLayoutMode,
    gridState: LazyGridState,
    assetLoader: CachedAppAssetLoader?,
    hasOperatePermission: Boolean,
    actionsEnabled: Boolean,
    showTopBar: Boolean,
    onSortModeChanged: (HostSortMode) -> Unit,
    onLayoutModeChanged: (LibraryLayoutMode) -> Unit,
    onHostSelected: (ComputerDetails) -> Unit,
    onAddHost: () -> Unit,
    onRemoveHost: (ComputerDetails) -> Unit,
    onLaunch: (LigaseLibraryItem) -> Unit,
    onConfigure: (LigaseLibraryItem) -> Unit,
    onRetrySync: () -> Unit,
    manualOrderDraft: ManualLibraryOrderDraft?,
    manualSortSaving: Boolean,
    manualSortEditingEnabled: Boolean,
    manualSortErrorMessage: Int?,
    onManualSort: () -> Unit,
    onManualMove: (movingUuid: String, targetUuid: String) -> Unit,
    onManualSave: () -> Unit,
    onManualCancel: () -> Unit,
) {
    var query by remember(selectedHost?.uuid) { mutableStateOf("") }
    // SnapshotStateList keeps the same object identity when its contents change.
    // Compute this during composition so applist updates invalidate the result.
    val visibleItems = manualOrderDraft?.entries?.mapNotNull { entry ->
        items.firstOrNull { item ->
            item.hostAppUuid.equals(entry.uuid, ignoreCase = true)
        }
    } ?: LigaseLibraryAdapter.visibleItems(items, query, sortMode)
    val bottomPadding = ligaseNavigationContentBottomPadding()
    val pullToRefreshState = rememberPullToRefreshState()
    val compactPhoneLandscape =
        LocalConfiguration.current.screenWidthDp >= 600 &&
            LocalConfiguration.current.screenHeightDp < 600

    Scaffold(
        topBar = if (showTopBar) {
            {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (manualOrderDraft == null) {
                                R.string.ligase_library_title
                            } else {
                                R.string.ligase_manual_sort_title
                            },
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
            }
        } else {
            {}
        },
        bottomBar = {
            manualOrderDraft?.let { draft ->
                ManualLibraryEditBar(
                    draft = draft,
                    saving = manualSortSaving,
                    editingEnabled = manualSortEditingEnabled,
                    errorMessage = manualSortErrorMessage,
                    onSave = onManualSave,
                    onCancel = onManualCancel,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { scaffoldPadding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onRetrySync,
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(scaffoldPadding),
            indicator = {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(
                            96.dp *
                                pullToRefreshState.distanceFraction.coerceIn(0f, 1.4f),
                        )
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(
                            when {
                                refreshing -> R.string.ligase_refreshing
                                pullToRefreshState.distanceFraction >= 1f ->
                                    R.string.ligase_release_to_refresh
                                else -> R.string.ligase_pull_to_refresh
                            },
                        ),
                        color = Color.Black,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            },
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(
                    minSize = when {
                        layoutMode != LibraryLayoutMode.LIST -> 148.dp
                        compactPhoneLandscape -> 272.dp
                        else -> 320.dp
                    },
                ),
                state = gridState,
                modifier = Modifier
                    .fillMaxSize()
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (96.dp * pullToRefreshState.distanceFraction.coerceIn(0f, 1.4f))
                                .roundToPx(),
                        )
                    },
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = bottomPadding,
                ),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
            if (
                selectedHost?.pairState == PairingManager.PairState.PAIRED &&
                !hasOperatePermission &&
                connectivity == LibraryConnectivity.ONLINE
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.ligase_observe_mode_summary),
                            modifier = Modifier.padding(14.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                LibraryHostStatus(
                    hosts = hosts,
                    selectedHost = selectedHost,
                    onHostSelected = onHostSelected,
                    onAddHost = onAddHost,
                    onRemoveHost = onRemoveHost,
                    connectivity = connectivity,
                    onRetry = onRetrySync,
                )
            }

            if (selectedHost == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LibraryNoHostSelected(onAddHost)
                }
            } else {
                val contentPresentation = libraryContentPresentation(
                    status = status,
                    hasItems = items.isNotEmpty(),
                )
                if (contentPresentation == LibraryContentPresentation.CONTENT) {
                    val preservedBanner = preservedLibraryBanner(
                        status = status,
                        hasItems = items.isNotEmpty(),
                        connectivity = connectivity,
                    )
                    if (preservedBanner != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LibraryPreservedContentBanner(
                                banner = preservedBanner,
                                onRetry = onRetrySync,
                            )
                        }
                    }
                    if (manualOrderDraft == null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LibrarySearchField(
                                query = query,
                                onQueryChanged = { query = it },
                            )
                        }
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LibraryGamesToolbar(
                                count = visibleItems.size,
                                sortMode = sortMode,
                                layoutMode = layoutMode,
                                canSortByLastPlayed = items.any {
                                    !it.isSystem && !it.lastPlayedAt.isNullOrBlank()
                                },
                                canManualSort = actionsEnabled &&
                                    items.count { it.hostAppUuid != null } > 1,
                                onSortModeChanged = onSortModeChanged,
                                onLayoutModeChanged = onLayoutModeChanged,
                                onManualSort = onManualSort,
                            )
                        }
                    }
                    if (visibleItems.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LibraryEmptyState(
                                loading = false,
                                query = query,
                                onRefresh = if (query.isBlank()) onRetrySync else null,
                            )
                        }
                    } else {
                        items(
                            items = visibleItems,
                            key = { it.key.stableValue },
                        ) { item ->
                            val reorderModifier = manualLibraryReorderModifier(
                                item = item,
                                draft = manualOrderDraft,
                                enabled = manualSortEditingEnabled && !manualSortSaving,
                                onMove = onManualMove,
                            )
                            if (layoutMode == LibraryLayoutMode.LIST) {
                                LibraryGameRowCard(
                                    modifier = reorderModifier,
                                    item = item,
                                    running = item.appId != null && item.appId == runningAppId,
                                    assetLoader = assetLoader,
                                    canOperate = actionsEnabled && manualOrderDraft == null,
                                    manualEditing = manualOrderDraft != null,
                                    onClick = { onLaunch(item) },
                                    onConfigure = { onConfigure(item) },
                                )
                            } else {
                                LibraryGamePosterCard(
                                    modifier = reorderModifier,
                                    item = item,
                                    running = item.appId != null && item.appId == runningAppId,
                                    assetLoader = assetLoader,
                                    canOperate = actionsEnabled && manualOrderDraft == null,
                                    manualEditing = manualOrderDraft != null,
                                    onClick = { onLaunch(item) },
                                    onConfigure = { onConfigure(item) },
                                )
                            }
                        }
                    }
                } else {
                    when (status) {
                        LigaseLibraryStatus.INCOMPATIBLE -> {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LibraryBlockingState(
                                    title = R.string.ligase_host_incompatible_title,
                                    summary = R.string.ligase_host_incompatible_summary,
                                    onRetry = onRetrySync,
                                )
                            }
                        }
                        LigaseLibraryStatus.SYNC_ERROR -> {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LibraryBlockingState(
                                    title = R.string.ligase_sync_error_title,
                                    summary = R.string.ligase_sync_error_summary,
                                    onRetry = onRetrySync,
                                )
                            }
                        }
                        LigaseLibraryStatus.PERMISSION_ERROR -> {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LibraryBlockingState(
                                    title = R.string.ligase_sync_permission_error_title,
                                    summary = R.string.ligase_sync_permission_error_summary,
                                    onRetry = onRetrySync,
                                )
                            }
                        }
                        LigaseLibraryStatus.IDLE,
                        LigaseLibraryStatus.LOADING -> {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LibraryEmptyState(loading = true, query = query)
                            }
                        }
                        LigaseLibraryStatus.READY -> Unit
                    }
                }
            }
            }
        }
    }
}
