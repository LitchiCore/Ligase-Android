package com.limelight.ligase.feature.library.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.ligase.library.PreservedLibraryBanner

@Composable
fun LibraryBlockingState(
    @StringRes title: Int,
    @StringRes summary: Int,
    onRetry: (() -> Unit)?,
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
            text = stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(summary),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.Button(onClick = onRetry) {
                Text(stringResource(R.string.ligase_retry))
            }
        }
    }
}

@Composable
fun LibraryEmptyState(
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
internal fun LibraryPreservedContentBanner(
    banner: PreservedLibraryBanner,
    onRetry: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (
                banner == PreservedLibraryBanner.CHECKING ||
                banner == PreservedLibraryBanner.LOADING
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(
                text = stringResource(
                    when (banner) {
                        PreservedLibraryBanner.CHECKING ->
                            R.string.ligase_library_preserving_content_checking
                        PreservedLibraryBanner.OFFLINE ->
                            R.string.ligase_library_preserving_content_offline
                        PreservedLibraryBanner.LOADING ->
                            R.string.ligase_library_preserving_content_loading
                        PreservedLibraryBanner.ERROR ->
                            R.string.ligase_library_preserving_content_error
                    },
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (
                            banner == PreservedLibraryBanner.CHECKING ||
                            banner == PreservedLibraryBanner.LOADING
                        ) {
                            12.dp
                        } else {
                            0.dp
                        },
                    ),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            if (
                banner == PreservedLibraryBanner.ERROR ||
                banner == PreservedLibraryBanner.OFFLINE
            ) {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.ligase_retry))
                }
            }
        }
    }
}
