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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.limelight.R
import com.limelight.grid.assets.CachedAppAssetLoader
import com.limelight.ligase.feature.library.data.dto.LigaseResolutionDto
import com.limelight.ligase.feature.library.domain.HostSortMode
import com.limelight.ligase.feature.library.domain.LibraryHdrState
import com.limelight.ligase.feature.library.domain.LibraryLayoutMode
import com.limelight.ligase.feature.library.domain.LigaseLibraryStatus
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.feature.layout.domain.LayoutCatalogUiState
import com.limelight.ligase.feature.layout.domain.LayoutControlKind
import com.limelight.ligase.feature.layout.domain.LayoutEditorSessionState
import com.limelight.ligase.feature.layout.presentation.LayoutSaveNavigation
import com.limelight.ligase.feature.layout.presentation.layoutSaveNavigation
import com.limelight.ligase.feature.layout.ui.LayoutEditorScreen
import com.limelight.ligase.feature.layout.ui.LayoutHallScreen
import com.limelight.ligase.feature.settings.presentation.SettingsUiState
import com.limelight.ligase.feature.settings.ui.SettingsScreen
import com.limelight.ligase.library.LigaseLibraryPage
import com.limelight.ligase.library.LibraryConnectivity
import com.limelight.ligase.library.ManualLibraryOrderDraft
import com.limelight.ligase.library.ManualLibrarySortActionState
import com.limelight.ligase.library.ManualLibrarySortError
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

private enum class LigaseLayoutRoute {
    MAIN,
    HALL,
    EDITOR,
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
    libraryConnectivity: LibraryConnectivity,
    libraryRevision: Long?,
    libraryGlobalResolution: LigaseResolutionDto?,
    libraryHdrState: LibraryHdrState,
    libraryRunningAppId: Int,
    librarySortMode: HostSortMode,
    libraryLayoutMode: LibraryLayoutMode,
    libraryAssetLoader: CachedAppAssetLoader?,
    libraryCanOperate: Boolean,
    libraryCanConfigureInput: Boolean,
    manualSortState: ManualLibrarySortActionState,
    layoutCatalogState: LayoutCatalogUiState,
    layoutEditorState: LayoutEditorSessionState,
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
    onManualOrderSubmit: (List<String>) -> Unit,
    onLayoutCatalogRefresh: () -> Unit,
    onLayoutSelect: (String) -> Unit,
    onLayoutPreview: (String) -> Unit,
    onLayoutCreateCopy: (String) -> Unit,
    onLayoutOpenEditor: (String) -> Unit,
    onLayoutMove: (String, Float, Float) -> Unit,
    onLayoutResize: (String, Float, Float) -> Unit,
    onLayoutDelete: (String) -> Unit,
    onLayoutAdd: (LayoutControlKind) -> Unit,
    onLayoutSave: () -> Unit,
    onLayoutDiscard: () -> Unit,
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
                val libraryOnline = libraryConnectivity == LibraryConnectivity.ONLINE
                var layoutRoute by rememberSaveable {
                    mutableStateOf(LigaseLayoutRoute.MAIN)
                }
                var pendingEditorOpen by rememberSaveable { mutableStateOf(false) }
                var pendingLayoutSave by rememberSaveable { mutableStateOf(false) }
                LaunchedEffect(
                    layoutEditorState.draftId,
                    layoutEditorState.error,
                    pendingEditorOpen,
                ) {
                    if (pendingEditorOpen && layoutEditorState.draftId != null) {
                        layoutRoute = LigaseLayoutRoute.EDITOR
                        pendingEditorOpen = false
                    } else if (
                        pendingEditorOpen &&
                        layoutEditorState.error != null
                    ) {
                        layoutRoute = LigaseLayoutRoute.HALL
                        pendingEditorOpen = false
                    }
                }
                LaunchedEffect(
                    pendingLayoutSave,
                    layoutEditorState.saving,
                    layoutEditorState.dirty,
                    layoutEditorState.error,
                ) {
                    when (
                        layoutSaveNavigation(
                            pendingSave = pendingLayoutSave,
                            saving = layoutEditorState.saving,
                            dirty = layoutEditorState.dirty,
                            hasError = layoutEditorState.error != null,
                        )
                    ) {
                        LayoutSaveNavigation.HALL -> {
                            pendingLayoutSave = false
                            layoutRoute = LigaseLayoutRoute.HALL
                        }
                        LayoutSaveNavigation.STAY_EDITOR -> {
                            pendingLayoutSave = false
                        }
                        LayoutSaveNavigation.WAIT -> Unit
                    }
                }
                var manualOrderDraft by remember(libraryHost?.uuid) {
                    mutableStateOf<ManualLibraryOrderDraft?>(null)
                }
                var manualConflictRevision by remember(libraryHost?.uuid) {
                    mutableStateOf<Long?>(null)
                }
                var manualConflictPending by remember(libraryHost?.uuid) {
                    mutableStateOf(false)
                }
                LaunchedEffect(libraryCanOperate) {
                    if (!libraryCanOperate) {
                        manualOrderDraft = null
                        manualConflictRevision = null
                        manualConflictPending = false
                    }
                }
                LaunchedEffect(manualSortState.appliedRevision) {
                    if (manualSortState.appliedRevision != null) {
                        manualOrderDraft = null
                        manualConflictRevision = null
                        manualConflictPending = false
                    }
                }
                LaunchedEffect(
                    manualSortState.error,
                    libraryRevision,
                    libraryItems.mapNotNull(LigaseLibraryItem::hostAppUuid),
                ) {
                    if (manualSortState.error == ManualLibrarySortError.REVISION_CONFLICT) {
                        if (!manualConflictPending) {
                            manualConflictRevision = libraryRevision
                            manualConflictPending = true
                        } else if (libraryRevision != manualConflictRevision) {
                            manualOrderDraft =
                                ManualLibraryOrderDraft.fromLibraryItems(libraryItems)
                            manualConflictRevision = null
                            manualConflictPending = false
                        }
                    }
                }
                val libraryGridState = rememberSaveable(
                    libraryHost?.uuid,
                    libraryLayoutMode,
                    saver = LazyGridState.Saver,
                ) {
                    LazyGridState()
                }
                val inputListState = rememberSaveable(
                    saver = LazyListState.Saver,
                ) {
                    LazyListState()
                }
                val navigateToMainPage: (LigasePage) -> Unit = { page ->
                    pendingEditorOpen = false
                    layoutRoute = LigaseLayoutRoute.MAIN
                    onPageSelected(page)
                }
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
                val mainPageContent: @Composable () -> Unit = {
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
                                connectivity = libraryConnectivity,
                                runningAppId = libraryRunningAppId,
                                sortMode = librarySortMode,
                                layoutMode = libraryLayoutMode,
                                gridState = libraryGridState,
                                assetLoader = libraryAssetLoader,
                                hasOperatePermission = libraryCanConfigureInput,
                                actionsEnabled = libraryCanConfigureInput && libraryOnline,
                                showTopBar =
                                    navigationType != NavigationSuiteType.NavigationRail,
                                onSortModeChanged = onLibrarySortModeChanged,
                                onLayoutModeChanged = onLibraryLayoutModeChanged,
                                onHostSelected = onHostClick,
                                onAddHost = onAddHost,
                                onRemoveHost = onRemoveHost,
                                onLaunch = onLibraryLaunch,
                                onConfigure = onLibraryConfigure,
                                onRetrySync = onLibraryRetrySync,
                                manualOrderDraft = manualOrderDraft,
                                manualSortSaving = manualSortState.saving,
                                manualSortEditingEnabled = libraryCanOperate &&
                                    libraryOnline &&
                                    !(
                                        manualSortState.error ==
                                            ManualLibrarySortError.REVISION_CONFLICT &&
                                            manualConflictPending
                                    ),
                                manualSortErrorMessage =
                                    manualSortErrorMessage(manualSortState.error),
                                onManualSort = {
                                    if (libraryCanOperate && libraryOnline) {
                                        manualOrderDraft =
                                            ManualLibraryOrderDraft.fromLibraryItems(libraryItems)
                                        manualConflictRevision = null
                                        manualConflictPending = false
                                    }
                                },
                                onManualMove = { movingUuid, targetUuid ->
                                    manualOrderDraft = manualOrderDraft?.move(
                                        movingUuid,
                                        targetUuid,
                                    )
                                },
                                onManualSave = {
                                    manualOrderDraft?.let { draft ->
                                        onManualOrderSubmit(draft.orderedPublishedUuids)
                                    }
                                },
                                onManualCancel = {
                                    manualOrderDraft = null
                                    manualConflictRevision = null
                                    manualConflictPending = false
                                },
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
                                listState = inputListState,
                                selectedTouchLayoutEditable = layoutCatalogState.items
                                    .firstOrNull {
                                        it.layoutId == selectedTouchLayoutId
                                    }
                                    ?.editable == true,
                                onBrowseLayouts = {
                                    onLayoutCatalogRefresh()
                                    layoutRoute = LigaseLayoutRoute.HALL
                                },
                                onEditTouchLayout = {
                                    selectedTouchLayoutId?.let { layoutId ->
                                        val editable = layoutCatalogState.items
                                            .firstOrNull { it.layoutId == layoutId }
                                            ?.editable == true
                                        if (editable) {
                                            onLayoutOpenEditor(layoutId)
                                        } else {
                                            onLayoutCreateCopy(layoutId)
                                        }
                                        pendingEditorOpen = true
                                        layoutRoute = LigaseLayoutRoute.HALL
                                    }
                                },
                            )
                            LigasePage.SETTINGS -> SettingsScreen(
                                state = SettingsUiState(
                                    selectedInput = selectedInput ?: InputDeviceMode.TOUCH,
                                    themeMode = themeMode,
                                    languageMode = languageMode,
                                    globalResolution = libraryGlobalResolution,
                                    hdrState = libraryHdrState,
                                    canOperate = libraryCanOperate && libraryOnline,
                                ),
                                onOpenInput = {
                                    navigateToMainPage(LigasePage.INPUT)
                                },
                                onThemeSelected = onThemeSelected,
                                onLanguageSelected = onLanguageSelected,
                                onGlobalResolutionClick = onGlobalResolutionClick,
                                onAdvancedSettings = onAdvancedSettings,
                            )
                        }
                    }
                }
                val pageContent: @Composable () -> Unit = {
                    when (layoutRoute) {
                        LigaseLayoutRoute.MAIN -> mainPageContent()
                        LigaseLayoutRoute.HALL -> LayoutHallScreen(
                            state = layoutCatalogState,
                            actionError = layoutEditorState.error,
                            onBack = { layoutRoute = LigaseLayoutRoute.MAIN },
                            onRefresh = onLayoutCatalogRefresh,
                            onSelect = onLayoutSelect,
                            onPreview = onLayoutPreview,
                            onEdit = { layoutId ->
                                onLayoutOpenEditor(layoutId)
                                pendingEditorOpen = true
                            },
                            onCreateCopy = { layoutId ->
                                onLayoutCreateCopy(layoutId)
                                pendingEditorOpen = true
                            },
                        )
                        LigaseLayoutRoute.EDITOR -> LayoutEditorScreen(
                            state = layoutEditorState,
                            onBack = { layoutRoute = LigaseLayoutRoute.HALL },
                            onMove = onLayoutMove,
                            onResize = onLayoutResize,
                            onDelete = onLayoutDelete,
                            onAdd = onLayoutAdd,
                            onSave = {
                                pendingLayoutSave = true
                                onLayoutSave()
                            },
                            onDiscard = onLayoutDiscard,
                        )
                    }
                }
                if (navigationType == NavigationSuiteType.NavigationRail) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        LigaseLandscapeSidebar(
                            currentPage = currentPage,
                            inputEnabled = libraryCanConfigureInput && libraryOnline,
                            onPageSelected = navigateToMainPage,
                        )
                        Box(modifier = Modifier.weight(1f)) {
                            pageContent()
                        }
                    }
                } else {
                    NavigationSuiteScaffold(
                        layoutType = navigationType,
                        navigationSuiteItems = {
                            LigasePage.entries.forEach { destination ->
                                val enabled =
                                    destination != LigasePage.INPUT ||
                                        libraryCanConfigureInput && libraryOnline
                                item(
                                    selected = currentPage == destination,
                                    onClick = { navigateToMainPage(destination) },
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
                        pageContent()
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

@Composable
private fun LigaseLandscapeSidebar(
    currentPage: LigasePage,
    inputEnabled: Boolean,
    onPageSelected: (LigasePage) -> Unit,
) {
    val compactPhoneLandscape =
        LocalConfiguration.current.screenHeightDp < 600
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
                text = stringResource(
                    if (currentPage == LigasePage.HOME) {
                        R.string.ligase_library_title
                    } else {
                        currentPage.label
                    },
                ),
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
                val enabled = destination != LigasePage.INPUT || inputEnabled
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

@StringRes
private fun manualSortErrorMessage(error: ManualLibrarySortError?): Int? =
    when (error) {
        null -> null
        ManualLibrarySortError.REVISION_CONFLICT -> R.string.ligase_manual_sort_conflict
        ManualLibrarySortError.PERMISSION_DENIED ->
            R.string.ligase_manual_sort_permission_denied
        ManualLibrarySortError.INVALID_ORDER,
        ManualLibrarySortError.FAILED -> R.string.ligase_manual_sort_failed
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LigasePageScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ligase_back),
                                contentDescription = stringResource(
                                    R.string.ligase_back,
                                ),
                            )
                        }
                    }
                },
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

@StringRes
internal fun inputTitle(mode: InputDeviceMode): Int = when (mode) {
    InputDeviceMode.GAMEPAD -> R.string.ligase_input_gamepad
    InputDeviceMode.KEYBOARD_MOUSE -> R.string.ligase_input_keyboard_mouse
    InputDeviceMode.TOUCH -> R.string.ligase_input_touch
}
