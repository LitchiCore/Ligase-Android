package com.limelight.ligase.library

import com.limelight.ligase.feature.library.domain.HostLibraryKind
import com.limelight.ligase.feature.library.domain.HostSortMode
import com.limelight.ligase.feature.library.domain.LibraryLayoutMode
import com.limelight.ligase.feature.library.domain.LigaseLibraryAdapter
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.feature.library.domain.LigaseLibraryStatus
import com.limelight.ligase.feature.library.ui.components.LibraryBlockingState
import com.limelight.ligase.feature.library.ui.components.LibraryEmptyState
import com.limelight.ligase.feature.library.ui.components.LibraryGamesToolbar
import com.limelight.ligase.feature.library.ui.components.LibraryHostStatus
import com.limelight.ligase.feature.library.ui.components.LibraryNoHostSelected
import com.limelight.ligase.feature.library.ui.components.LibraryPreservedContentBanner
import com.limelight.ligase.feature.library.ui.components.LibrarySearchField
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.limelight.R
import com.limelight.grid.assets.CachedAppAssetLoader
import com.limelight.ligase.LigaseSemanticTheme
import com.limelight.ligase.ligaseNavigationContentBottomPadding
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager
import kotlin.math.abs

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
                ManualSortEditBar(
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
                            val reorderModifier = manualReorderModifier(
                                item = item,
                                draft = manualOrderDraft,
                                enabled = manualSortEditingEnabled && !manualSortSaving,
                                onMove = onManualMove,
                            )
                            if (layoutMode == LibraryLayoutMode.LIST) {
                                LibraryRowCard(
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
                                LibraryPosterCard(
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

@Composable
private fun ManualSortEditBar(
    draft: ManualLibraryOrderDraft,
    saving: Boolean,
    editingEnabled: Boolean,
    errorMessage: Int?,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val compactLandscape = LocalConfiguration.current.screenWidthDp >= 600 &&
        LocalConfiguration.current.screenHeightDp < 600
    val statusText = stringResource(
        if (draft.isDirty) {
            R.string.ligase_manual_sort_unsaved
        } else {
            R.string.ligase_manual_sort_summary
        },
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
    ) {
        if (compactLandscape && errorMessage == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = statusText,
                    modifier = Modifier.weight(1f),
                    color = if (draft.isDirty) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (draft.isDirty) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                androidx.compose.material3.OutlinedButton(
                    onClick = onCancel,
                    enabled = !saving,
                ) {
                    Text(stringResource(R.string.ligase_manual_sort_cancel))
                }
                androidx.compose.material3.Button(
                    onClick = onSave,
                    enabled = draft.isDirty && editingEnabled && !saving,
                ) {
                    Text(
                        stringResource(
                            if (saving) {
                                R.string.ligase_manual_sort_saving
                            } else {
                                R.string.ligase_manual_sort_save
                            },
                        ),
                        maxLines = 1,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
            Text(
                text = statusText,
                color = if (draft.isDirty) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (draft.isDirty) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (errorMessage != null) {
                Text(
                    text = stringResource(errorMessage),
                    modifier = Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.material3.OutlinedButton(
                    onClick = onCancel,
                    enabled = !saving,
                    modifier = Modifier.weight(0.8f),
                ) {
                    Text(stringResource(R.string.ligase_manual_sort_cancel))
                }
                androidx.compose.material3.Button(
                    onClick = onSave,
                    enabled = draft.isDirty && editingEnabled && !saving,
                    modifier = Modifier.weight(1.4f),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        stringResource(
                            if (saving) {
                                R.string.ligase_manual_sort_saving
                            } else {
                                R.string.ligase_manual_sort_save
                            },
                        ),
                        maxLines = 1,
                    )
                }
            }
            }
        }
    }
}

@Composable
private fun manualReorderModifier(
    item: LigaseLibraryItem,
    draft: ManualLibraryOrderDraft?,
    enabled: Boolean,
    onMove: (movingUuid: String, targetUuid: String) -> Unit,
): Modifier {
    val uuid = item.hostAppUuid ?: return Modifier
    if (draft == null) return Modifier
    var dragOffset by remember(uuid) { mutableFloatStateOf(0f) }
    val moveThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val previousUuid = draft.adjacentMovableUuid(uuid, -1)
    val nextUuid = draft.adjacentMovableUuid(uuid, 1)
    val moveUpLabel = stringResource(R.string.ligase_manual_sort_move_up)
    val moveDownLabel = stringResource(R.string.ligase_manual_sort_move_down)

    return Modifier
        .graphicsLayer { translationY = dragOffset }
        .then(
            if (dragOffset != 0f) {
                Modifier.shadow(8.dp, RoundedCornerShape(22.dp))
            } else {
                Modifier
            },
        )
        .semantics {
            customActions = buildList {
                if (enabled && previousUuid != null) {
                    add(
                        CustomAccessibilityAction(moveUpLabel) {
                            onMove(uuid, previousUuid)
                            true
                        },
                    )
                }
                if (enabled && nextUuid != null) {
                    add(
                        CustomAccessibilityAction(moveDownLabel) {
                            onMove(uuid, nextUuid)
                            true
                        },
                    )
                }
            }
        }
        .then(
            if (!enabled) {
                Modifier
            } else {
                Modifier.pointerInput(uuid, draft.entries.map(ManualLibraryOrderEntry::uuid)) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dragOffset = 0f },
                        onDragCancel = { dragOffset = 0f },
                        onDragEnd = { dragOffset = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            if (abs(dragOffset) >= moveThreshold) {
                                val direction = if (dragOffset > 0f) 1 else -1
                                draft.adjacentMovableUuid(uuid, direction)?.let { targetUuid ->
                                    onMove(uuid, targetUuid)
                                }
                                dragOffset = 0f
                            }
                        },
                    )
                }
            },
        )
}

@Composable
private fun LibraryRowCard(
    modifier: Modifier = Modifier,
    item: LigaseLibraryItem,
    running: Boolean,
    assetLoader: CachedAppAssetLoader?,
    canOperate: Boolean,
    manualEditing: Boolean,
    onClick: () -> Unit,
    onConfigure: () -> Unit,
) {
    val semanticColors = LigaseSemanticTheme.colors
    val accent = cardAccent(item)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(108.dp)
            .clickable(
                enabled = item.isLaunchable && canOperate && !manualEditing,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = if (item.isLaunchable) {
                MaterialTheme.colorScheme.onSurface
            } else {
                semanticColors.disabled
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(88.dp),
                shape = RoundedCornerShape(16.dp),
                color = accent.container,
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (assetLoader != null && item.launchApp != null) {
                        LibraryArtwork(
                            item = item,
                            assetLoader = assetLoader,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                if (item.isSystem) {
                                    R.drawable.ic_ligase_monitor
                                } else {
                                    R.drawable.ic_computer
                                },
                            ),
                            contentDescription = null,
                            tint = accent.content,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item.kind?.let { kind ->
                        Text(
                            text = kindLabel(kind),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (running) {
                        LibraryChip(stringResource(R.string.ligase_library_running))
                    }
                    if (!item.isLaunchable) {
                        Text(
                            text = stringResource(R.string.ligase_library_sync_missing),
                            color = semanticColors.errorDanger,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            IconButton(
                onClick = onConfigure,
                enabled = canOperate && !manualEditing,
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    painter = painterResource(
                        if (manualEditing) {
                            R.drawable.ic_ligase_drag_handle
                        } else {
                            R.drawable.ic_settings
                        },
                    ),
                    contentDescription = stringResource(
                        if (manualEditing) {
                            R.string.ligase_manual_sort_drag
                        } else {
                            R.string.ligase_library_configure_named
                        },
                        item.name,
                    ),
                    tint = if (manualEditing || canOperate) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        semanticColors.disabled
                    },
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun LibraryPosterCard(
    modifier: Modifier = Modifier,
    item: LigaseLibraryItem,
    running: Boolean,
    assetLoader: CachedAppAssetLoader?,
    canOperate: Boolean,
    manualEditing: Boolean,
    onClick: () -> Unit,
    onConfigure: () -> Unit,
) {
    val semanticColors = LigaseSemanticTheme.colors
    val accent = cardAccent(item)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.74f)
            .clickable(
                enabled = item.isLaunchable && canOperate && !manualEditing,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = accent.container,
            contentColor = if (item.isLaunchable) {
                accent.content
            } else {
                semanticColors.disabled
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (assetLoader != null && item.launchApp != null) {
                LibraryArtwork(
                    item = item,
                    assetLoader = assetLoader,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // This is an artwork legibility overlay, not an app-surface color.
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.78f),
                            ),
                        ),
                    ),
            )
            if (item.isSystem) {
                Icon(
                    painter = painterResource(R.drawable.ic_ligase_monitor),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(15.dp)
                        .size(36.dp),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 14.dp, top = 14.dp, end = 54.dp, bottom = 14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item.kind?.let { PosterChip(kindLabel(it)) }
                    if (running) {
                        PosterChip(stringResource(R.string.ligase_library_running))
                    }
                    if (!item.isLaunchable) {
                        PosterChip(stringResource(R.string.ligase_library_sync_missing))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.46f),
            ) {
                IconButton(
                    onClick = onConfigure,
                    enabled = canOperate && !manualEditing,
                ) {
                    Icon(
                        painter = painterResource(
                            if (manualEditing) {
                                R.drawable.ic_ligase_drag_handle
                            } else {
                                R.drawable.ic_settings
                            },
                        ),
                        contentDescription = stringResource(
                            if (manualEditing) {
                                R.string.ligase_manual_sort_drag
                            } else {
                                R.string.ligase_library_configure_named
                            },
                            item.name,
                        ),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun PosterChip(text: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.38f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun LibraryArtwork(
    item: LigaseLibraryItem,
    assetLoader: CachedAppAssetLoader,
    modifier: Modifier,
) {
    val app = item.launchApp ?: return
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).apply {
                val image = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
                val fallbackText = TextView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(1, 1)
                }
                addView(image)
                addView(fallbackText)
            }
        },
        update = { container ->
            assetLoader.populateImageView(
                app,
                container.getChildAt(0) as ImageView,
                container.getChildAt(1) as TextView,
            )
        },
    )
}

@Composable
private fun LibraryChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(50),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

private data class CardAccent(
    val container: Color,
    val content: Color,
)

@Composable
private fun cardAccent(item: LigaseLibraryItem): CardAccent {
    val colors = LigaseSemanticTheme.colors
    return when (item.kind) {
        HostLibraryKind.DESKTOP ->
            CardAccent(colors.brandPrimary, colors.surface)
        HostLibraryKind.VIRTUAL_DESKTOP, HostLibraryKind.STEAM ->
            CardAccent(colors.brandSecondary, colors.surface)
        HostLibraryKind.EXECUTABLE, null ->
            CardAccent(colors.surfaceVariant, colors.textPrimary)
    }
}

@Composable
private fun kindLabel(kind: HostLibraryKind): String = when (kind) {
    HostLibraryKind.DESKTOP -> stringResource(R.string.ligase_library_kind_desktop)
    HostLibraryKind.VIRTUAL_DESKTOP ->
        stringResource(R.string.ligase_library_kind_virtual_desktop)
    HostLibraryKind.STEAM -> stringResource(R.string.ligase_library_kind_steam)
    HostLibraryKind.EXECUTABLE -> stringResource(R.string.ligase_library_kind_executable)
}
