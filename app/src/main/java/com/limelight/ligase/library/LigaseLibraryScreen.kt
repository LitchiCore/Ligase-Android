package com.limelight.ligase.library

import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.limelight.R
import com.limelight.grid.assets.CachedAppAssetLoader
import com.limelight.ligase.LigaseSemanticTheme
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
    runningAppId: Int,
    sortMode: HostSortMode,
    layoutMode: LibraryLayoutMode,
    assetLoader: CachedAppAssetLoader?,
    onSortModeChanged: (HostSortMode) -> Unit,
    onLayoutModeChanged: (LibraryLayoutMode) -> Unit,
    onHostSelected: (ComputerDetails) -> Unit,
    onAddHost: () -> Unit,
    onRemoveHost: (ComputerDetails) -> Unit,
    onLaunch: (LigaseLibraryItem) -> Unit,
    onConfigure: (LigaseLibraryItem) -> Unit,
    onRetrySync: () -> Unit,
) {
    var query by remember(selectedHost?.uuid) { mutableStateOf("") }
    // SnapshotStateList keeps the same object identity when its contents change.
    // Compute this during composition so applist updates invalidate the result.
    val visibleItems = LigaseLibraryAdapter.visibleItems(items, query, sortMode)
    val bottomPadding = ligaseNavigationContentBottomPadding()
    val pullToRefreshState = rememberPullToRefreshState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.ligase_library_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
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
                    minSize = if (layoutMode == LibraryLayoutMode.LIST) 320.dp else 148.dp,
                ),
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                HostStrip(
                    hosts = hosts,
                    selectedHost = selectedHost,
                    onHostSelected = onHostSelected,
                    onAddHost = onAddHost,
                    onRemoveHost = onRemoveHost,
                )
            }

            if (selectedHost == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    NoHostSelected(onAddHost)
                }
            } else {
                when (status) {
                    LigaseLibraryStatus.INCOMPATIBLE -> {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LibrarySyncMessage(
                                title = stringResource(R.string.ligase_host_incompatible_title),
                                summary = stringResource(R.string.ligase_host_incompatible_summary),
                                retry = onRetrySync,
                            )
                        }
                    }
                    LigaseLibraryStatus.SYNC_ERROR -> {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LibrarySyncMessage(
                                title = stringResource(R.string.ligase_sync_error_title),
                                summary = stringResource(R.string.ligase_sync_error_summary),
                                retry = onRetrySync,
                            )
                        }
                    }
                    LigaseLibraryStatus.IDLE,
                    LigaseLibraryStatus.LOADING -> {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LibraryMessage(loading = true, query = query)
                        }
                    }
                    LigaseLibraryStatus.READY -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        placeholder = { Text(stringResource(R.string.ligase_library_search_hint)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_ligase_search),
                                contentDescription = null,
                            )
                        },
                    )
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    GamesSectionHeader(
                        count = visibleItems.size,
                        sortMode = sortMode,
                        layoutMode = layoutMode,
                        onSortModeChanged = onSortModeChanged,
                        onLayoutModeChanged = onLayoutModeChanged,
                    )
                }

                when {
                loading && items.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LibraryMessage(loading = true, query = query)
                    }
                }
                visibleItems.isEmpty() -> {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        LibraryMessage(
                            loading = false,
                            query = query,
                            onRefresh = if (query.isBlank()) onRetrySync else null,
                        )
                    }
                }
                else -> {
                    items(
                        items = visibleItems,
                        key = { it.key.stableValue },
                    ) { item ->
                        if (layoutMode == LibraryLayoutMode.LIST) {
                            LibraryRowCard(
                                item = item,
                                running = item.appId != null && item.appId == runningAppId,
                                assetLoader = assetLoader,
                                onClick = { onLaunch(item) },
                                onConfigure = { onConfigure(item) },
                            )
                        } else {
                            LibraryPosterCard(
                                item = item,
                                running = item.appId != null && item.appId == runningAppId,
                                assetLoader = assetLoader,
                                onClick = { onLaunch(item) },
                                onConfigure = { onConfigure(item) },
                            )
                        }
                    }
                }
                }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun LibrarySyncMessage(
    title: String,
    summary: String,
    retry: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_ligase_monitor),
            contentDescription = null,
            modifier = Modifier.size(52.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = summary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (retry != null) {
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.Button(onClick = retry) {
                Text(stringResource(R.string.ligase_retry))
            }
        }
    }
}

@Composable
private fun HostStrip(
    hosts: List<ComputerDetails>,
    selectedHost: ComputerDetails?,
    onHostSelected: (ComputerDetails) -> Unit,
    onAddHost: () -> Unit,
    onRemoveHost: (ComputerDetails) -> Unit,
) {
    var managingHosts by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(R.string.ligase_current_computer),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clickable { managingHosts = true },
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ligase_monitor),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = selectedHost?.name
                            ?: stringResource(R.string.ligase_select_computer),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = selectedHost?.let { stringResource(hostStatusLabel(it)) }
                            ?: stringResource(R.string.ligase_manage_computers),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    text = stringResource(R.string.ligase_switch_computer),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    if (managingHosts) {
        Dialog(onDismissRequest = { managingHosts = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .heightIn(max = 680.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.ligase_manage_computers),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { managingHosts = false }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(android.R.string.cancel),
                            )
                        }
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        lazyListItems(hosts, key = { it.uuid ?: it.name }) { host ->
                            val selected = host.uuid.equals(
                                selectedHost?.uuid,
                                ignoreCase = true,
                            )
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        managingHosts = false
                                        onHostSelected(host)
                                    },
                                shape = RoundedCornerShape(18.dp),
                                color = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 16.dp, end = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(9.dp)
                                            .background(hostStatusColor(host), CircleShape),
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(14.dp),
                                    ) {
                                        Text(
                                            text = host.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = stringResource(hostStatusLabel(host)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    IconButton(onClick = { onRemoveHost(host) }) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_delete),
                                            contentDescription = stringResource(
                                                R.string.ligase_remove_computer_named,
                                                host.name,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.Button(
                        onClick = {
                            managingHosts = false
                            onAddHost()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = null,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ligase_add_computer))
                    }
                }
            }
        }
    }
}

@Composable
private fun NoHostSelected(onAddHost: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(88.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_ligase_monitor),
                contentDescription = null,
                modifier = Modifier.padding(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.ligase_home_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.ligase_library_select_host_hint),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(22.dp))
        androidx.compose.material3.Button(onClick = onAddHost) {
            Text(stringResource(R.string.ligase_add_computer))
        }
    }
}

private fun hostStatusLabel(host: ComputerDetails): Int = when {
    host.state == ComputerDetails.State.UNKNOWN -> R.string.ligase_host_checking
    host.state == ComputerDetails.State.OFFLINE -> R.string.ligase_host_offline
    host.pairState != PairingManager.PairState.PAIRED -> R.string.ligase_host_pair_required
    else -> R.string.ligase_host_online
}

@Composable
private fun hostStatusColor(host: ComputerDetails): Color {
    val colors = LigaseSemanticTheme.colors
    return when {
        host.state == ComputerDetails.State.UNKNOWN -> colors.disabled
        host.state == ComputerDetails.State.OFFLINE -> colors.errorDanger
        host.pairState != PairingManager.PairState.PAIRED -> colors.warning
        else -> colors.success
    }
}

@Composable
private fun GamesSectionHeader(
    count: Int,
    sortMode: HostSortMode,
    layoutMode: LibraryLayoutMode,
    onSortModeChanged: (HostSortMode) -> Unit,
    onLayoutModeChanged: (LibraryLayoutMode) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.ligase_library_games),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.ligase_library_visible_count, count),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Box {
            Surface(
                modifier = Modifier.clickable { expanded = true },
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(14.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ligase_sort),
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = sortModeLabel(sortMode),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                HostSortMode.entries.forEach { mode ->
                    DropdownMenuItem(
                        text = { Text(sortModeLabel(mode)) },
                        onClick = {
                            expanded = false
                            onSortModeChanged(mode)
                        },
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(14.dp),
        ) {
            IconButton(
                onClick = {
                    onLayoutModeChanged(
                        if (layoutMode == LibraryLayoutMode.LIST) {
                            LibraryLayoutMode.POSTER
                        } else {
                            LibraryLayoutMode.LIST
                        },
                    )
                },
            ) {
                Icon(
                    painter = painterResource(
                        if (layoutMode == LibraryLayoutMode.LIST) {
                            R.drawable.ic_ligase_grid
                        } else {
                            R.drawable.ic_ligase_list
                        },
                    ),
                    contentDescription = stringResource(
                        if (layoutMode == LibraryLayoutMode.LIST) {
                            R.string.ligase_library_switch_to_poster
                        } else {
                            R.string.ligase_library_switch_to_list
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun LibraryMessage(
    loading: Boolean,
    query: String,
    onRefresh: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(14.dp))
        }
        if (!loading && query.isBlank()) {
            Text(
                text = stringResource(R.string.ligase_library_empty),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.ligase_library_empty_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (onRefresh != null) {
                Spacer(Modifier.height(20.dp))
                androidx.compose.material3.Button(onClick = onRefresh) {
                    Text(stringResource(R.string.ligase_refresh))
                }
            }
        } else {
            Text(
                text = if (loading) {
                    stringResource(R.string.ligase_library_loading)
                } else {
                    stringResource(R.string.ligase_library_no_results)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LibraryRowCard(
    item: LigaseLibraryItem,
    running: Boolean,
    assetLoader: CachedAppAssetLoader?,
    onClick: () -> Unit,
    onConfigure: () -> Unit,
) {
    val semanticColors = LigaseSemanticTheme.colors
    val accent = cardAccent(item)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(118.dp)
            .clickable(enabled = item.isLaunchable, onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = if (item.isLaunchable) {
                MaterialTheme.colorScheme.onSurface
            } else {
                semanticColors.disabled
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .fillMaxHeight()
                    .background(accent.container),
                contentAlignment = Alignment.Center,
            ) {
                if (assetLoader != null && item.launchApp != null) {
                    LibraryArtwork(
                        item = item,
                        assetLoader = assetLoader,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (item.isSystem) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ligase_monitor),
                        contentDescription = null,
                        tint = accent.content,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp, end = 54.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    item.kind?.let { LibraryChip(kindLabel(it)) }
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
        }
            IconButton(
                onClick = onConfigure,
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(
                        R.string.ligase_library_configure_named,
                        item.name,
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun LibraryPosterCard(
    item: LigaseLibraryItem,
    running: Boolean,
    assetLoader: CachedAppAssetLoader?,
    onClick: () -> Unit,
    onConfigure: () -> Unit,
) {
    val semanticColors = LigaseSemanticTheme.colors
    val accent = cardAccent(item)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.74f)
            .clickable(enabled = item.isLaunchable, onClick = onClick),
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
                IconButton(onClick = onConfigure) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = stringResource(
                            R.string.ligase_library_configure_named,
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

@Composable
private fun sortModeLabel(mode: HostSortMode): String = when (mode) {
    HostSortMode.NAME_ASCENDING -> stringResource(R.string.ligase_sort_name_ascending)
    HostSortMode.NAME_DESCENDING -> stringResource(R.string.ligase_sort_name_descending)
    HostSortMode.ADDED_NEWEST -> stringResource(R.string.ligase_sort_added_newest)
    HostSortMode.ADDED_OLDEST -> stringResource(R.string.ligase_sort_added_oldest)
    HostSortMode.LAST_PLAYED_NEWEST ->
        stringResource(R.string.ligase_sort_last_played_newest)
}
