package com.limelight.ligase.app.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.limelight.ligase.LigaseSemanticTheme

@Composable
fun currentLigaseNavigationPlacement(): LigaseNavigationPlacement {
    val configuration = LocalConfiguration.current
    return ligaseNavigationPlacement(
        screenWidthDp = configuration.screenWidthDp,
        orientation = configuration.orientation,
    )
}

@Composable
fun LigaseNavigationShell(
    placement: LigaseNavigationPlacement,
    currentPage: LigasePage,
    inputEnabled: Boolean,
    onPageSelected: (LigasePage) -> Unit,
    content: @Composable () -> Unit,
) {
    if (placement == LigaseNavigationPlacement.SIDE) {
        Row(modifier = Modifier.fillMaxSize()) {
            LigaseLandscapeSidebar(
                currentPage = currentPage,
                inputEnabled = inputEnabled,
                onPageSelected = onPageSelected,
            )
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
        }
        return
    }

    val semanticColors = LigaseSemanticTheme.colors
    val navigationItemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = semanticColors.textPrimary,
            selectedTextColor = semanticColors.textPrimary,
            indicatorColor = semanticColors.selected,
            unselectedIconColor = semanticColors.textSecondary,
            unselectedTextColor = semanticColors.textSecondary,
            disabledIconColor = semanticColors.disabled,
            disabledTextColor = semanticColors.disabled,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = semanticColors.textPrimary,
            selectedTextColor = semanticColors.textPrimary,
            indicatorColor = semanticColors.selected,
            unselectedIconColor = semanticColors.textSecondary,
            unselectedTextColor = semanticColors.textSecondary,
            disabledIconColor = semanticColors.disabled,
            disabledTextColor = semanticColors.disabled,
        ),
    )
    NavigationSuiteScaffold(
        layoutType = NavigationSuiteType.NavigationBar,
        navigationSuiteItems = {
            LigasePage.entries.forEach { destination ->
                item(
                    selected = currentPage == destination,
                    onClick = { onPageSelected(destination) },
                    enabled = ligaseNavigationDestinationEnabled(destination, inputEnabled),
                    colors = navigationItemColors,
                    icon = {
                        Icon(
                            painter = painterResource(destination.icon),
                            contentDescription = stringResource(destination.label),
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    label = { Text(stringResource(destination.label)) },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        content()
    }
}

@Composable
private fun LigaseLandscapeSidebar(
    currentPage: LigasePage,
    inputEnabled: Boolean,
    onPageSelected: (LigasePage) -> Unit,
) {
    val compactPhoneLandscape = LocalConfiguration.current.screenHeightDp < 600
    Surface(
        modifier = Modifier
            .width(if (compactPhoneLandscape) 156.dp else 184.dp)
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeContent),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(ligaseNavigationHeader(currentPage)),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = if (compactPhoneLandscape) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.headlineMedium
                },
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))
            LigasePage.entries.forEach { destination ->
                val enabled = ligaseNavigationDestinationEnabled(destination, inputEnabled)
                val selected = currentPage == destination
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) {
                        LigaseSemanticTheme.colors.selected
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    contentColor = if (enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        LigaseSemanticTheme.colors.disabled
                    },
                    onClick = { onPageSelected(destination) },
                    enabled = enabled,
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = 14.dp,
                            vertical = if (compactPhoneLandscape) 10.dp else 13.dp,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            painter = painterResource(destination.icon),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = stringResource(destination.label),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (selected) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    }
                }
            }
        }
    }
}
