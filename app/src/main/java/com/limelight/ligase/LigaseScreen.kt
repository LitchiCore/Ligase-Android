package com.limelight.ligase

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.limelight.R
import com.limelight.grid.assets.CachedAppAssetLoader
import com.limelight.ligase.library.HostSortMode
import com.limelight.ligase.library.LigaseLibraryItem
import com.limelight.ligase.library.LigaseLibraryPage
import com.limelight.ligase.library.LigaseLibraryStatus
import com.limelight.ligase.library.LigaseResolutionDto
import com.limelight.ligase.library.LibraryLayoutMode
import com.limelight.ligase.input.LigaseInputCategory
import com.limelight.ligase.input.LigaseInputDevice
import com.limelight.ligase.input.LigaseInputPage
import com.limelight.ligase.input.LigaseTouchLayout
import com.limelight.ligase.input.LigaseTouchOverlayMode
import com.limelight.ligase.pairing.AttendedPairingDialog
import com.limelight.ligase.pairing.AttendedPairingUiState
import com.limelight.nvstream.http.ComputerDetails

enum class LigasePage(
    @StringRes val label: Int,
    @DrawableRes val icon: Int,
) {
    HOME(R.string.ligase_nav_home, R.drawable.ic_computer),
    INPUT(R.string.ligase_nav_input, R.drawable.ic_ligase_gamepad),
    SETTINGS(R.string.ligase_nav_settings, R.drawable.ic_settings),
}

@Composable
internal fun ligaseNavigationContentBottomPadding(): Dp =
    if (LocalConfiguration.current.screenWidthDp < 600) 104.dp else 28.dp

@Composable
fun LigaseRoot(
    themeMode: LigaseThemeMode,
    onboarding: Boolean,
    currentPage: LigasePage,
    selectedInput: InputDeviceMode?,
    inputDevices: List<LigaseInputDevice>,
    selectedGamepadKey: String?,
    selectedKeyboardKey: String?,
    selectedMouseKey: String?,
    touchLayouts: List<LigaseTouchLayout>,
    selectedTouchLayoutId: String?,
    touchOverlayMode: LigaseTouchOverlayMode,
    languageMode: LigaseLanguageMode,
    hosts: List<ComputerDetails>,
    libraryHost: ComputerDetails?,
    libraryItems: List<LigaseLibraryItem>,
    libraryLoading: Boolean,
    libraryRefreshing: Boolean,
    libraryStatus: LigaseLibraryStatus,
    libraryGlobalResolution: LigaseResolutionDto?,
    libraryHdrAvailable: Boolean,
    libraryRunningAppId: Int,
    librarySortMode: HostSortMode,
    libraryLayoutMode: LibraryLayoutMode,
    libraryAssetLoader: CachedAppAssetLoader?,
    libraryCanOperate: Boolean,
    libraryCanConfigureInput: Boolean,
    pairingState: AttendedPairingUiState,
    onPageSelected: (LigasePage) -> Unit,
    onInputSelected: (InputDeviceMode) -> Unit,
    onInputConfirmed: () -> Unit,
    onInputDeviceSelected: (LigaseInputCategory, String) -> Unit,
    onTouchLayoutSelected: (String) -> Unit,
    onTouchOverlayModeChanged: (LigaseTouchOverlayMode) -> Unit,
    onThemeSelected: (LigaseThemeMode) -> Unit,
    onLanguageSelected: (LigaseLanguageMode) -> Unit,
    onHostClick: (ComputerDetails) -> Unit,
    onRemoveHost: (ComputerDetails) -> Unit,
    onAddHost: () -> Unit,
    onAdvancedSettings: () -> Unit,
    onLibrarySortModeChanged: (HostSortMode) -> Unit,
    onLibraryLayoutModeChanged: (LibraryLayoutMode) -> Unit,
    onLibraryLaunch: (LigaseLibraryItem) -> Unit,
    onLibraryConfigure: (LigaseLibraryItem) -> Unit,
    onLibraryRetrySync: () -> Unit,
    onGlobalResolutionClick: () -> Unit,
    onPairingCancel: () -> Unit,
    onPairingDismiss: () -> Unit,
) {
    LigaseComposeTheme(themeMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeContent),
        ) {
            if (onboarding) {
                LigaseInputPage(
                    selectedInput = selectedInput,
                    onboarding = true,
                    devices = inputDevices,
                    selectedGamepadKey = selectedGamepadKey,
                    selectedKeyboardKey = selectedKeyboardKey,
                    selectedMouseKey = selectedMouseKey,
                    touchLayouts = touchLayouts,
                    selectedTouchLayoutId = selectedTouchLayoutId,
                    touchOverlayMode = touchOverlayMode,
                    onInputSelected = onInputSelected,
                    onInputConfirmed = onInputConfirmed,
                    onDeviceSelected = onInputDeviceSelected,
                    onTouchLayoutSelected = onTouchLayoutSelected,
                    onTouchOverlayModeChanged = onTouchOverlayModeChanged,
                )
            } else {
                val configuration = LocalConfiguration.current
                val navigationType = when (
                    ligaseNavigationPlacement(
                        configuration.screenWidthDp,
                        configuration.orientation,
                    )
                ) {
                    LigaseNavigationPlacement.BOTTOM -> NavigationSuiteType.NavigationBar
                    LigaseNavigationPlacement.SIDE -> NavigationSuiteType.NavigationRail
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
                    layoutType = navigationType,
                    navigationSuiteItems = {
                        LigasePage.entries.forEach { destination ->
                            val enabled =
                                destination != LigasePage.INPUT || libraryCanConfigureInput
                            item(
                                selected = currentPage == destination,
                                onClick = { onPageSelected(destination) },
                                enabled = enabled,
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
                    AnimatedContent(
                        targetState = currentPage,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "Ligase page",
                    ) { page ->
                        when (page) {
                            LigasePage.HOME -> LigaseLibraryPage(
                                hosts = hosts,
                                selectedHost = libraryHost,
                                items = libraryItems,
                                loading = libraryLoading,
                                refreshing = libraryRefreshing,
                                status = libraryStatus,
                                runningAppId = libraryRunningAppId,
                                sortMode = librarySortMode,
                                layoutMode = libraryLayoutMode,
                                assetLoader = libraryAssetLoader,
                                canOperate = libraryCanConfigureInput,
                                onSortModeChanged = onLibrarySortModeChanged,
                                onLayoutModeChanged = onLibraryLayoutModeChanged,
                                onHostSelected = onHostClick,
                                onAddHost = onAddHost,
                                onRemoveHost = onRemoveHost,
                                onLaunch = onLibraryLaunch,
                                onConfigure = onLibraryConfigure,
                                onRetrySync = onLibraryRetrySync,
                            )
                            LigasePage.INPUT -> LigaseInputPage(
                                selectedInput = selectedInput,
                                onboarding = false,
                                devices = inputDevices,
                                selectedGamepadKey = selectedGamepadKey,
                                selectedKeyboardKey = selectedKeyboardKey,
                                selectedMouseKey = selectedMouseKey,
                                touchLayouts = touchLayouts,
                                selectedTouchLayoutId = selectedTouchLayoutId,
                                touchOverlayMode = touchOverlayMode,
                                onInputSelected = onInputSelected,
                                onInputConfirmed = onInputConfirmed,
                                onDeviceSelected = onInputDeviceSelected,
                                onTouchLayoutSelected = onTouchLayoutSelected,
                                onTouchOverlayModeChanged = onTouchOverlayModeChanged,
                            )
                            LigasePage.SETTINGS -> SettingsPage(
                                selectedInput = selectedInput ?: InputDeviceMode.TOUCH,
                                themeMode = themeMode,
                                languageMode = languageMode,
                                globalResolution = libraryGlobalResolution,
                                hdrAvailable = libraryHdrAvailable,
                                canOperate = libraryCanOperate,
                                onOpenInput = { onPageSelected(LigasePage.INPUT) },
                                onThemeSelected = onThemeSelected,
                                onLanguageSelected = onLanguageSelected,
                                onGlobalResolutionClick = onGlobalResolutionClick,
                                onAdvancedSettings = onAdvancedSettings,
                            )
                        }
                    }
                }
            }
            AttendedPairingDialog(
                state = pairingState,
                onCancel = onPairingCancel,
                onDismiss = onPairingDismiss,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LigasePageScaffold(
    title: String,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        content(Modifier.padding(padding))
    }
}

@Composable
private fun SettingsPage(
    selectedInput: InputDeviceMode,
    themeMode: LigaseThemeMode,
    languageMode: LigaseLanguageMode,
    globalResolution: LigaseResolutionDto?,
    hdrAvailable: Boolean,
    canOperate: Boolean,
    onOpenInput: () -> Unit,
    onThemeSelected: (LigaseThemeMode) -> Unit,
    onLanguageSelected: (LigaseLanguageMode) -> Unit,
    onGlobalResolutionClick: () -> Unit,
    onAdvancedSettings: () -> Unit,
) {
    LigasePageScaffold(stringResource(R.string.ligase_settings_title)) { pageModifier ->
        val bottomPadding = ligaseNavigationContentBottomPadding()
        LazyColumn(
            modifier = pageModifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                top = 20.dp,
                end = 20.dp,
                bottom = bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SectionTitle(R.string.ligase_settings_input_title)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = canOperate, onClick = onOpenInput),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ligase_touch),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(
                                text = stringResource(inputTitle(selectedInput)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.ligase_settings_input_summary),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                SectionTitle(R.string.ligase_streaming_settings_title)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            enabled = canOperate && globalResolution != null,
                            onClick = onGlobalResolutionClick,
                        ),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = if (canOperate && globalResolution != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            LigaseSemanticTheme.colors.disabled
                        },
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ligase_monitor),
                            contentDescription = null,
                            tint = if (globalResolution != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                LigaseSemanticTheme.colors.disabled
                            },
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 16.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.ligase_global_resolution),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = globalResolution?.label
                                    ?: stringResource(R.string.ligase_sync_unavailable_short),
                                color = if (globalResolution != null) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    LigaseSemanticTheme.colors.disabled
                                },
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    if (hdrAvailable) R.string.ligase_hdr_available
                                    else R.string.ligase_hdr_unavailable,
                                ),
                                color = if (globalResolution != null) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    LigaseSemanticTheme.colors.disabled
                                },
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                SectionTitle(R.string.ligase_settings_appearance_title)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeChoice(
                        mode = LigaseThemeMode.SYSTEM,
                        selected = themeMode == LigaseThemeMode.SYSTEM,
                        label = R.string.ligase_theme_system,
                        onClick = onThemeSelected,
                    )
                    ThemeChoice(
                        mode = LigaseThemeMode.LIGHT,
                        selected = themeMode == LigaseThemeMode.LIGHT,
                        label = R.string.ligase_theme_light,
                        onClick = onThemeSelected,
                    )
                    ThemeChoice(
                        mode = LigaseThemeMode.DARK,
                        selected = themeMode == LigaseThemeMode.DARK,
                        label = R.string.ligase_theme_dark,
                        onClick = onThemeSelected,
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                SectionTitle(R.string.ligase_settings_language_title)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LanguageChoice(
                        mode = LigaseLanguageMode.SYSTEM,
                        selected = languageMode == LigaseLanguageMode.SYSTEM,
                        label = R.string.ligase_language_system,
                        onClick = onLanguageSelected,
                    )
                    LanguageChoice(
                        mode = LigaseLanguageMode.SIMPLIFIED_CHINESE,
                        selected = languageMode == LigaseLanguageMode.SIMPLIFIED_CHINESE,
                        label = R.string.ligase_language_chinese,
                        onClick = onLanguageSelected,
                    )
                    LanguageChoice(
                        mode = LigaseLanguageMode.ENGLISH,
                        selected = languageMode == LigaseLanguageMode.ENGLISH,
                        label = R.string.ligase_language_english,
                        onClick = onLanguageSelected,
                    )
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(18.dp))
                SectionTitle(R.string.ligase_advanced_title)
                Text(
                    text = stringResource(R.string.ligase_advanced_summary),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onAdvancedSettings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(stringResource(R.string.ligase_advanced_action))
                }
            }
        }
    }
}

@Composable
private fun RowScope.ThemeChoice(
    mode: LigaseThemeMode,
    selected: Boolean,
    @StringRes label: Int,
    onClick: (LigaseThemeMode) -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = { onClick(mode) },
        label = {
            Text(
                text = stringResource(label),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = LigaseSemanticTheme.colors.selected,
            selectedLabelColor = LigaseSemanticTheme.colors.textPrimary,
            selectedLeadingIconColor = LigaseSemanticTheme.colors.brandPrimary,
            disabledLabelColor = LigaseSemanticTheme.colors.disabled,
        ),
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun RowScope.LanguageChoice(
    mode: LigaseLanguageMode,
    selected: Boolean,
    @StringRes label: Int,
    onClick: (LigaseLanguageMode) -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = { onClick(mode) },
        label = {
            Text(
                text = stringResource(label),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = LigaseSemanticTheme.colors.selected,
            selectedLabelColor = LigaseSemanticTheme.colors.textPrimary,
            selectedLeadingIconColor = LigaseSemanticTheme.colors.brandPrimary,
            disabledLabelColor = LigaseSemanticTheme.colors.disabled,
        ),
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun SectionTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 10.dp),
    )
}

@StringRes
internal fun inputTitle(mode: InputDeviceMode): Int = when (mode) {
    InputDeviceMode.GAMEPAD -> R.string.ligase_input_gamepad
    InputDeviceMode.KEYBOARD_MOUSE -> R.string.ligase_input_keyboard_mouse
    InputDeviceMode.TOUCH -> R.string.ligase_input_touch
}
