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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager

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
    languageMode: LigaseLanguageMode,
    hosts: List<ComputerDetails>,
    libraryHost: ComputerDetails?,
    libraryItems: List<LigaseLibraryItem>,
    libraryLoading: Boolean,
    libraryStatus: LigaseLibraryStatus,
    libraryGlobalResolution: LigaseResolutionDto?,
    libraryHdrAvailable: Boolean,
    libraryRunningAppId: Int,
    librarySortMode: HostSortMode,
    libraryLayoutMode: LibraryLayoutMode,
    libraryAssetLoader: CachedAppAssetLoader?,
    onPageSelected: (LigasePage) -> Unit,
    onInputSelected: (InputDeviceMode) -> Unit,
    onInputConfirmed: () -> Unit,
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
) {
    LigaseComposeTheme(themeMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeContent),
        ) {
            if (onboarding) {
                InputPage(
                    selectedInput = selectedInput,
                    onboarding = true,
                    onInputSelected = onInputSelected,
                    onConfirm = onInputConfirmed,
                )
            } else {
                NavigationSuiteScaffold(
                    navigationSuiteItems = {
                        LigasePage.entries.forEach { destination ->
                            item(
                                selected = currentPage == destination,
                                onClick = { onPageSelected(destination) },
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
                    containerColor = MaterialTheme.colorScheme.surface,
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
                                status = libraryStatus,
                                runningAppId = libraryRunningAppId,
                                sortMode = librarySortMode,
                                layoutMode = libraryLayoutMode,
                                assetLoader = libraryAssetLoader,
                                onSortModeChanged = onLibrarySortModeChanged,
                                onLayoutModeChanged = onLibraryLayoutModeChanged,
                                onHostSelected = onHostClick,
                                onAddHost = onAddHost,
                                onRemoveHost = onRemoveHost,
                                onLaunch = onLibraryLaunch,
                                onConfigure = onLibraryConfigure,
                                onRetrySync = onLibraryRetrySync,
                            )
                            LigasePage.INPUT -> InputPage(
                                selectedInput = selectedInput,
                                onboarding = false,
                                onInputSelected = onInputSelected,
                                onConfirm = onInputConfirmed,
                            )
                            LigasePage.SETTINGS -> SettingsPage(
                                selectedInput = selectedInput ?: InputDeviceMode.TOUCH,
                                themeMode = themeMode,
                                languageMode = languageMode,
                                globalResolution = libraryGlobalResolution,
                                hdrAvailable = libraryHdrAvailable,
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
        }
    }
}

@Composable
private fun LigaseComposeTheme(themeMode: LigaseThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        LigaseThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
        LigaseThemeMode.LIGHT -> false
        LigaseThemeMode.DARK -> true
    }
    val scheme = if (dark) {
        androidx.compose.material3.darkColorScheme(
            primary = Color(0xFFB7C4FF),
            onPrimary = Color(0xFF16275F),
            primaryContainer = Color(0xFF303F78),
            surface = Color(0xFF111318),
            surfaceContainer = Color(0xFF1D1F25),
            surfaceContainerHigh = Color(0xFF282A30),
            onSurface = Color(0xFFE3E2E9),
            onSurfaceVariant = Color(0xFFC6C5D0),
        )
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF455DCC),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDDE1FF),
            surface = Color(0xFFFAF8FF),
            surfaceContainer = Color(0xFFF0EFF7),
            surfaceContainerHigh = Color(0xFFE9E7F0),
            onSurface = Color(0xFF1A1B20),
            onSurfaceVariant = Color(0xFF45464F),
        )
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LigasePageScaffold(
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
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        content(Modifier.padding(padding))
    }
}

@Composable
private fun HomePage(
    hosts: List<ComputerDetails>,
    onHostClick: (ComputerDetails) -> Unit,
    onHostLongClick: (ComputerDetails) -> Unit,
    onAddHost: () -> Unit,
) {
    LigasePageScaffold(stringResource(R.string.ligase_brand)) { pageModifier ->
        if (hosts.isEmpty()) {
            Column(
                modifier = pageModifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Surface(
                    modifier = Modifier.size(88.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_computer),
                        contentDescription = null,
                        modifier = Modifier.padding(24.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.ligase_home_empty_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.ligase_home_empty_summary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(26.dp))
                Button(onClick = onAddHost) {
                    Text(stringResource(R.string.ligase_add_computer))
                }
            }
        } else {
            val bottomPadding = ligaseNavigationContentBottomPadding()
            LazyColumn(
                modifier = pageModifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    top = 12.dp,
                    end = 20.dp,
                    bottom = bottomPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.ligase_home_subtitle),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Button(onClick = onAddHost) {
                            Text(stringResource(R.string.ligase_add_computer))
                        }
                    }
                }
                items(hosts, key = { it.uuid ?: it.name }) { host ->
                    HostCard(host, onHostClick, onHostLongClick)
                }
            }
        }
    }
}

@Composable
private fun HostCard(
    host: ComputerDetails,
    onClick: (ComputerDetails) -> Unit,
    onLongClick: (ComputerDetails) -> Unit,
) {
    val status = when {
        host.state == ComputerDetails.State.UNKNOWN -> R.string.ligase_host_checking
        host.state == ComputerDetails.State.OFFLINE -> R.string.ligase_host_offline
        host.pairState != PairingManager.PairState.PAIRED -> R.string.ligase_host_pair_required
        else -> R.string.ligase_host_online
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = host.name }
            .clickable(onClick = { onClick(host) }),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_computer),
                    contentDescription = null,
                    modifier = Modifier.padding(14.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(
                    text = host.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(status),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InputPage(
    selectedInput: InputDeviceMode?,
    onboarding: Boolean,
    onInputSelected: (InputDeviceMode) -> Unit,
    onConfirm: () -> Unit,
) {
    LigasePageScaffold(
        if (onboarding) stringResource(R.string.ligase_brand)
        else stringResource(R.string.ligase_nav_input),
    ) { pageModifier ->
        val bottomPadding =
            if (onboarding) 28.dp else ligaseNavigationContentBottomPadding()
        LazyColumn(
            modifier = pageModifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                top = 12.dp,
                end = 20.dp,
                bottom = bottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.ligase_input_setup_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ligase_input_setup_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }
            item {
                InputChoice(
                    mode = InputDeviceMode.GAMEPAD,
                    selected = selectedInput == InputDeviceMode.GAMEPAD,
                    icon = R.drawable.ic_ligase_gamepad,
                    title = R.string.ligase_input_gamepad,
                    summary = R.string.ligase_input_gamepad_summary,
                    onClick = onInputSelected,
                )
            }
            item {
                InputChoice(
                    mode = InputDeviceMode.KEYBOARD_MOUSE,
                    selected = selectedInput == InputDeviceMode.KEYBOARD_MOUSE,
                    icon = R.drawable.ic_ligase_keyboard_mouse,
                    title = R.string.ligase_input_keyboard_mouse,
                    summary = R.string.ligase_input_keyboard_mouse_summary,
                    onClick = onInputSelected,
                )
            }
            item {
                InputChoice(
                    mode = InputDeviceMode.TOUCH,
                    selected = selectedInput == InputDeviceMode.TOUCH,
                    icon = R.drawable.ic_ligase_touch,
                    title = R.string.ligase_input_touch,
                    summary = R.string.ligase_input_touch_summary,
                    onClick = onInputSelected,
                )
            }
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onConfirm,
                    enabled = selectedInput != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text(stringResource(R.string.ligase_continue))
                }
            }
        }
    }
}

@Composable
private fun InputChoice(
    mode: InputDeviceMode,
    selected: Boolean,
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes summary: Int,
    onClick: (InputDeviceMode) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(mode) },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RadioButton(
                selected = selected,
                onClick = { onClick(mode) },
            )
        }
    }
}

@Composable
private fun SettingsPage(
    selectedInput: InputDeviceMode,
    themeMode: LigaseThemeMode,
    languageMode: LigaseLanguageMode,
    globalResolution: LigaseResolutionDto?,
    hdrAvailable: Boolean,
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
                        .clickable(onClick = onOpenInput),
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
                            enabled = globalResolution != null,
                            onClick = onGlobalResolutionClick,
                        ),
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
                            painter = painterResource(R.drawable.ic_ligase_monitor),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    if (hdrAvailable) R.string.ligase_hdr_available
                                    else R.string.ligase_hdr_unavailable,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun inputTitle(mode: InputDeviceMode): Int = when (mode) {
    InputDeviceMode.GAMEPAD -> R.string.ligase_input_gamepad
    InputDeviceMode.KEYBOARD_MOUSE -> R.string.ligase_input_keyboard_mouse
    InputDeviceMode.TOUCH -> R.string.ligase_input_touch
}
