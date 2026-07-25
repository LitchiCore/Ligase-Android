package com.limelight.ligase.feature.library.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.grid.assets.CachedAppAssetLoader
import com.limelight.ligase.LigaseSemanticTheme
import com.limelight.ligase.feature.library.domain.HostLibraryKind
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem

@Composable
fun LibraryGameRowCard(
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
    val actions = libraryCardActionPolicy(
        isLaunchable = item.isLaunchable,
        canOperate = canOperate,
        manualEditing = manualEditing,
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(108.dp)
            .clickable(
                enabled = actions.launchEnabled,
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
                        LibraryArtworkHost(
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
                enabled = actions.configureEnabled,
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    painter = painterResource(
                        if (actions.showDragHandle) {
                            R.drawable.ic_ligase_drag_handle
                        } else {
                            R.drawable.ic_settings
                        },
                    ),
                    contentDescription = stringResource(
                        if (actions.showDragHandle) {
                            R.string.ligase_manual_sort_drag
                        } else {
                            R.string.ligase_library_configure_named
                        },
                        item.name,
                    ),
                    tint = if (actions.showDragHandle || canOperate) {
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
fun LibraryGamePosterCard(
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
    val actions = libraryCardActionPolicy(
        isLaunchable = item.isLaunchable,
        canOperate = canOperate,
        manualEditing = manualEditing,
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.74f)
            .clickable(
                enabled = actions.launchEnabled,
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
                LibraryArtworkHost(
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
                    enabled = actions.configureEnabled,
                ) {
                    Icon(
                        painter = painterResource(
                            if (actions.showDragHandle) {
                                R.drawable.ic_ligase_drag_handle
                            } else {
                                R.drawable.ic_settings
                            },
                        ),
                        contentDescription = stringResource(
                            if (actions.showDragHandle) {
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
