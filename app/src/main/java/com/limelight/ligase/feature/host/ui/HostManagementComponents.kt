package com.limelight.ligase.feature.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.limelight.R
import com.limelight.ligase.LigaseSemanticTheme
import com.limelight.ligase.feature.host.presentation.HostManagementStatus
import com.limelight.ligase.feature.host.presentation.hostManagementPresentation
import com.limelight.ligase.feature.host.presentation.hostManagementStatus
import com.limelight.ligase.feature.host.presentation.lanPairingCandidates
import com.limelight.ligase.library.LibraryConnectivity
import com.limelight.nvstream.http.ComputerDetails

@Composable
fun LibraryHostStatus(
    hosts: List<ComputerDetails>,
    selectedHost: ComputerDetails?,
    onHostSelected: (ComputerDetails) -> Unit,
    onAddHost: () -> Unit,
    onRemoveHost: (ComputerDetails) -> Unit,
    connectivity: LibraryConnectivity,
    onRetry: () -> Unit,
) {
    var managingHosts by remember { mutableStateOf(false) }
    val presentation = hostManagementPresentation(hosts, selectedHost?.uuid)
    val pairingCandidates = lanPairingCandidates(hosts)
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
                        text = selectedHost?.let {
                            stringResource(
                                if (connectivity == LibraryConnectivity.OFFLINE) {
                                    R.string.ligase_host_offline_explicit
                                } else {
                                    hostStatusLabel(hostManagementStatus(it))
                                },
                            )
                        } ?: stringResource(R.string.ligase_manage_computers),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selectedHost != null && connectivity == LibraryConnectivity.OFFLINE) {
                    TextButton(onClick = onRetry) {
                        Text(stringResource(R.string.ligase_retry))
                    }
                }
                Text(
                    text = stringResource(R.string.ligase_switch_computer),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        pairingCandidates.forEach { candidate ->
            val host = hosts[candidate.sourceIndex]
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onHostSelected(host) },
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Row(
                    modifier = Modifier.padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_ligase_monitor),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp),
                    ) {
                        Text(
                            text = candidate.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.ligase_lan_host_pairing_summary),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        text = stringResource(R.string.ligase_pair_computer),
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
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
                        items(
                            items = presentation.rows,
                            key = { it.stableKey.revealForComposeKey() },
                        ) { row ->
                            val host = hosts[row.sourceIndex]
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        managingHosts = false
                                        onHostSelected(host)
                                    },
                                shape = RoundedCornerShape(18.dp),
                                color = if (row.selected) {
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
                                            .background(hostStatusColor(row.status), CircleShape),
                                    )
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(14.dp),
                                    ) {
                                        Text(
                                            text = row.name,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = stringResource(hostStatusLabel(row.status)),
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
fun LibraryNoHostSelected(onAddHost: () -> Unit) {
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

private fun hostStatusLabel(status: HostManagementStatus): Int = when (status) {
    HostManagementStatus.CHECKING -> R.string.ligase_host_checking
    HostManagementStatus.OFFLINE -> R.string.ligase_host_offline
    HostManagementStatus.PAIR_REQUIRED -> R.string.ligase_host_pair_required
    HostManagementStatus.ONLINE -> R.string.ligase_host_online
}

@Composable
private fun hostStatusColor(status: HostManagementStatus): Color {
    val colors = LigaseSemanticTheme.colors
    return when (status) {
        HostManagementStatus.CHECKING -> colors.disabled
        HostManagementStatus.OFFLINE -> colors.errorDanger
        HostManagementStatus.PAIR_REQUIRED -> colors.warning
        HostManagementStatus.ONLINE -> colors.success
    }
}
