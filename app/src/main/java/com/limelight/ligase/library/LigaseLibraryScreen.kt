package com.limelight.ligase.library

import com.limelight.ligase.feature.library.domain.HostSortMode
import com.limelight.ligase.feature.library.domain.LibraryLayoutMode
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.feature.library.domain.LigaseLibraryStatus
import com.limelight.ligase.feature.library.domain.HostLayoutBindingResolver
import com.limelight.ligase.feature.library.presentation.presentHostLibraryAuthority
import com.limelight.ligase.feature.library.ui.LibraryRouteActions
import com.limelight.ligase.feature.library.ui.LibraryRouteUiState
import com.limelight.ligase.feature.library.ui.libraryRoutePresentation
import com.limelight.ligase.feature.library.ui.components.LibraryBlockingState
import com.limelight.ligase.feature.library.ui.components.LibraryEmptyState
import com.limelight.ligase.feature.library.ui.components.LibraryGamePosterCard
import com.limelight.ligase.feature.library.ui.components.LibraryGameRowCard
import com.limelight.ligase.feature.library.ui.components.LibraryGamesToolbar
import com.limelight.ligase.feature.host.ui.LibraryHostStatus
import com.limelight.ligase.feature.host.ui.LibraryNoHostSelected
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
import com.limelight.ligase.feature.library.application.HostVerifiedCoverLoader
import com.limelight.ligase.ligaseNavigationContentBottomPadding
import com.limelight.nvstream.http.PairingManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryRoute(
    state: LibraryRouteUiState,
    actions: LibraryRouteActions,
    gridState: LazyGridState,
    assetLoader: CachedAppAssetLoader?,
    verifiedCoverLoader: HostVerifiedCoverLoader? = null,
) {
    val hosts = state.hosts
    val selectedHost = state.selectedHost
    val items = state.items
    val refreshing = state.refreshing
    val status = state.status
    val connectivity = state.connectivity
    val runningAppId = state.runningAppId
    val sortMode = state.sortMode
    val layoutMode = state.layoutMode
    val hasOperatePermission = state.hasOperatePermission
    val actionsEnabled = state.actionsEnabled
    val showTopBar = state.showTopBar
    val manualOrderDraft = state.manualEditor.draft
    val manualSortSaving = state.manualEditor.saving
    val manualSortEditingEnabled = state.manualEditor.editingEnabled
    val manualSortErrorMessage = state.manualEditor.errorMessage
    var query by remember(selectedHost?.uuid) { mutableStateOf("") }
    val presentation = libraryRoutePresentation(state, query)
    val visibleItems = presentation.visibleItems
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
                    onSave = actions.onManualSave,
                    onCancel = actions.onManualCancel,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
    ) { scaffoldPadding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = actions.onRetrySync,
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
                    onHostSelected = actions.onHostSelected,
                    onAddHost = actions.onAddHost,
                    onRemoveHost = actions.onRemoveHost,
                    connectivity = connectivity,
                    onRetry = actions.onRetrySync,
                )
            }

            if (selectedHost == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LibraryNoHostSelected(actions.onAddHost)
                }
            } else {
                val contentPresentation = presentation.content
                if (contentPresentation == LibraryContentPresentation.CONTENT) {
                    val preservedBanner = presentation.preservedBanner
                    if (preservedBanner != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LibraryPreservedContentBanner(
                                banner = preservedBanner,
                                onRetry = actions.onRetrySync,
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
                                canSortByLastPlayed = presentation.canSortByLastPlayed,
                                canManualSort = presentation.canManualSort,
                                onSortModeChanged = actions.onSortModeChanged,
                                onLayoutModeChanged = actions.onLayoutModeChanged,
                                onManualSort = actions.onManualSort,
                            )
                        }
                    }
                    if (visibleItems.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LibraryEmptyState(
                                loading = false,
                                query = query,
                                onRefresh = if (query.isBlank()) {
                                    actions.onRetrySync
                                } else {
                                    null
                                },
                            )
                        }
                    } else {
                        items(
                            items = visibleItems,
                            key = { it.key.stableValue },
                        ) { item ->
                            val authority = presentHostLibraryAuthority(
                                item = item,
                                resolution = HostLayoutBindingResolver.resolve(
                                    binding = item.layoutBinding,
                                    committed = state.committedLayouts,
                                ),
                            )
                            val reorderModifier = manualLibraryReorderModifier(
                                item = item,
                                draft = manualOrderDraft,
                                enabled = manualSortEditingEnabled && !manualSortSaving,
                                onMove = actions.onManualMove,
                            )
                            if (layoutMode == LibraryLayoutMode.LIST) {
                                LibraryGameRowCard(
                                    modifier = reorderModifier,
                                    item = item,
                                    authority = authority,
                                    running = item.appId != null && item.appId == runningAppId,
                                    assetLoader = assetLoader,
                                    verifiedCoverLoader = verifiedCoverLoader,
                                    canOperate = actionsEnabled && manualOrderDraft == null,
                                    manualEditing = manualOrderDraft != null,
                                    onClick = { actions.onLaunch(item) },
                                    onConfigure = { actions.onConfigure(item) },
                                )
                            } else {
                                LibraryGamePosterCard(
                                    modifier = reorderModifier,
                                    item = item,
                                    authority = authority,
                                    running = item.appId != null && item.appId == runningAppId,
                                    assetLoader = assetLoader,
                                    verifiedCoverLoader = verifiedCoverLoader,
                                    canOperate = actionsEnabled && manualOrderDraft == null,
                                    manualEditing = manualOrderDraft != null,
                                    onClick = { actions.onLaunch(item) },
                                    onConfigure = { actions.onConfigure(item) },
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
                                    onRetry = actions.onRetrySync,
                                )
                            }
                        }
                        LigaseLibraryStatus.SYNC_ERROR -> {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LibraryBlockingState(
                                    title = R.string.ligase_sync_error_title,
                                    summary = R.string.ligase_sync_error_summary,
                                    onRetry = actions.onRetrySync,
                                )
                            }
                        }
                        LigaseLibraryStatus.PERMISSION_ERROR -> {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LibraryBlockingState(
                                    title = R.string.ligase_sync_permission_error_title,
                                    summary = R.string.ligase_sync_permission_error_summary,
                                    onRetry = actions.onRetrySync,
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
