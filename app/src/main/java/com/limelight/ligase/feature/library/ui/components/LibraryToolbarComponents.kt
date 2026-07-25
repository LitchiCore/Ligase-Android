package com.limelight.ligase.feature.library.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.ligase.feature.library.domain.HostSortMode
import com.limelight.ligase.feature.library.domain.LibraryLayoutMode

internal data class LibrarySortOption(
    val mode: HostSortMode,
    val enabled: Boolean,
)

internal fun librarySortOptions(
    canSortByLastPlayed: Boolean,
    canManualSort: Boolean,
): List<LibrarySortOption> =
    HostSortMode.entries.map { mode ->
        LibrarySortOption(
            mode = mode,
            enabled = when (mode) {
                HostSortMode.LAST_PLAYED_NEWEST -> canSortByLastPlayed
                HostSortMode.MANUAL -> canManualSort
                else -> true
            },
        )
    }

@Composable
fun LibrarySearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        placeholder = {
            Text(stringResource(R.string.ligase_library_search_hint))
        },
        leadingIcon = {
            Icon(
                painter = painterResource(R.drawable.ic_ligase_search),
                contentDescription = null,
            )
        },
    )
}

@Composable
fun LibraryGamesToolbar(
    count: Int,
    sortMode: HostSortMode,
    layoutMode: LibraryLayoutMode,
    canSortByLastPlayed: Boolean,
    canManualSort: Boolean,
    onSortModeChanged: (HostSortMode) -> Unit,
    onLayoutModeChanged: (LibraryLayoutMode) -> Unit,
    onManualSort: () -> Unit,
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
                        text = sortModeLabel(sortMode, canSortByLastPlayed),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                librarySortOptions(canSortByLastPlayed, canManualSort)
                    .filterNot { it.mode == HostSortMode.MANUAL }
                    .forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(sortModeLabel(option.mode, canSortByLastPlayed))
                            },
                            enabled = option.enabled,
                            onClick = {
                                expanded = false
                                onSortModeChanged(option.mode)
                            },
                        )
                    }
                HorizontalDivider()
                val manualOption = librarySortOptions(
                    canSortByLastPlayed,
                    canManualSort,
                ).first { it.mode == HostSortMode.MANUAL }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ligase_manual_sort)) },
                    enabled = manualOption.enabled,
                    onClick = {
                        expanded = false
                        onManualSort()
                    },
                )
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
private fun sortModeLabel(
    mode: HostSortMode,
    canSortByLastPlayed: Boolean,
): String = when (mode) {
    HostSortMode.NAME_ASCENDING -> stringResource(R.string.ligase_sort_name_ascending)
    HostSortMode.NAME_DESCENDING -> stringResource(R.string.ligase_sort_name_descending)
    HostSortMode.ADDED_NEWEST -> stringResource(R.string.ligase_sort_added_newest)
    HostSortMode.ADDED_OLDEST -> stringResource(R.string.ligase_sort_added_oldest)
    HostSortMode.LAST_PLAYED_NEWEST -> stringResource(
        if (canSortByLastPlayed) {
            R.string.ligase_sort_last_played_newest
        } else {
            R.string.ligase_sort_last_played_unavailable
        },
    )
    HostSortMode.MANUAL -> stringResource(R.string.ligase_manual_sort)
}
