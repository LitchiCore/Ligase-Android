package com.limelight.ligase.app.root

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.limelight.R
import com.limelight.grid.assets.CachedAppAssetLoader
import com.limelight.ligase.feature.library.application.HostVerifiedCoverLoader
import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.LigaseComposeTheme
import com.limelight.ligase.LigaseLanguageMode
import com.limelight.ligase.LigasePage
import com.limelight.ligase.LigaseThemeMode
import com.limelight.ligase.feature.library.data.dto.LigaseResolutionDto
import com.limelight.ligase.feature.library.domain.HostSortMode
import com.limelight.ligase.feature.library.domain.LibraryHdrState
import com.limelight.ligase.feature.library.domain.LibraryLayoutMode
import com.limelight.ligase.feature.library.domain.LigaseLibraryStatus
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.app.navigation.LigaseNavigationPlacement
import com.limelight.ligase.app.navigation.LigaseNavigationShell
import com.limelight.ligase.app.navigation.currentLigaseNavigationPlacement
import com.limelight.ligase.feature.input.layout.v3.ui.LayoutV3HallScreen
import com.limelight.ligase.feature.input.layout.v3.application.LayoutV3EditorWorkspaceUiState
import com.limelight.ligase.feature.library.ui.LibraryManualEditorUiState
import com.limelight.ligase.feature.library.ui.LibraryRouteActions
import com.limelight.ligase.feature.library.ui.LibraryRouteUiState
import com.limelight.ligase.feature.settings.presentation.SettingsUiState
import com.limelight.ligase.feature.settings.ui.SettingsScreen
import com.limelight.ligase.feature.settings.ui.rememberStreamBitrateDialogState
import com.limelight.ligase.feature.stream.application.StreamBitrateUiState
import com.limelight.ligase.feature.stream.domain.StreamBitratePresetId
import com.limelight.ligase.library.LibraryRoute
import com.limelight.ligase.library.LibraryConnectivity
import com.limelight.ligase.library.ManualLibraryOrderDraft
import com.limelight.ligase.library.ManualLibrarySortActionState
import com.limelight.ligase.library.ManualLibrarySortError
import com.limelight.ligase.input.LigaseInputCategory
import com.limelight.ligase.input.LigaseInputDevice
import com.limelight.ligase.input.LigaseInputPage
import com.limelight.ligase.input.LigaseTouchOverlayMode
import com.limelight.ligase.input.LigaseCloudTouchMode
import com.limelight.ligase.input.EffectiveStreamingTouchMode
import com.limelight.ligase.feature.pairing.ui.AttendedPairingDialog
import com.limelight.ligase.pairing.AttendedPairingUiState
import com.limelight.nvstream.http.ComputerDetails

@Composable
internal fun LigaseRootContent(
    themeMode: LigaseThemeMode,
    onboarding: Boolean,
    currentPage: LigasePage,
    selectedInput: InputDeviceMode?,
    inputDevices: List<LigaseInputDevice>,
    selectedGamepadKey: String?,
    selectedKeyboardKey: String?,
    selectedMouseKey: String?,
    touchOverlayMode: LigaseTouchOverlayMode,
    cloudTouchMode: LigaseCloudTouchMode = LigaseCloudTouchMode.SINGLE_TOUCH,
    effectiveStreamingTouchMode: EffectiveStreamingTouchMode =
        EffectiveStreamingTouchMode.ABSOLUTE_POINTER,
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
    libraryVerifiedCoverLoader: HostVerifiedCoverLoader? = null,
    libraryCanOperate: Boolean,
    libraryCanConfigureInput: Boolean,
    manualSortState: ManualLibrarySortActionState,
    layoutV3EditorWorkspaceState: LayoutV3EditorWorkspaceUiState =
        LayoutV3EditorWorkspaceUiState(),
    pairingState: AttendedPairingUiState,
    streamBitrateState: StreamBitrateUiState,
    onPageSelected: (LigasePage) -> Unit,
    onInputSelected: (InputDeviceMode) -> Unit,
    onInputConfirmed: () -> Unit,
    onInputDeviceSelected: (LigaseInputCategory, String) -> Unit,
    onTouchOverlayModeChanged: (LigaseTouchOverlayMode) -> Unit,
    onCloudTouchModeChanged: (LigaseCloudTouchMode) -> Unit = {},
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
    onLayoutV3CreateBlank: (String?) -> Unit = {},
    onLayoutV3ResumeRecovery: (String) -> Unit = {},
    onLayoutV3DiscardRecovery: (String) -> Unit = {},
    onLayoutV3OpenCommitted: (String, Long, String) -> Unit = { _, _, _ -> },
    onGlobalResolutionClick: () -> Unit,
    onStreamBitratePresetSelected: (StreamBitratePresetId) -> Unit,
    onStreamBitrateCustomSubmitted: (String) -> Unit,
    onPairingCancel: () -> Unit,
    onPairingDismiss: () -> Unit,
) {
    val streamBitrateDialogState = rememberStreamBitrateDialogState()
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
                    touchOverlayMode = touchOverlayMode,
                    cloudTouchMode = cloudTouchMode,
                    inputSettingsWritable = true,
                    effectiveStreamingTouchMode = effectiveStreamingTouchMode,
                    onInputSelected = onInputSelected,
                    onInputConfirmed = onInputConfirmed,
                    onDeviceSelected = onInputDeviceSelected,
                    onTouchOverlayModeChanged = onTouchOverlayModeChanged,
                    onCloudTouchModeChanged = onCloudTouchModeChanged,
                )
            } else {
                val navigationPlacement = currentLigaseNavigationPlacement()
                val libraryOnline = libraryConnectivity == LibraryConnectivity.ONLINE
                var layoutRoute by rememberSaveable {
                    mutableStateOf(LigaseLayoutRoute.MAIN)
                }
                val manualRetentionKey = manualDraftRetentionKey(libraryHost?.uuid)
                var manualOrderDraft by remember(manualRetentionKey) {
                    mutableStateOf<ManualLibraryOrderDraft?>(null)
                }
                var manualConflictRevision by remember(manualRetentionKey) {
                    mutableStateOf<Long?>(null)
                }
                var manualConflictPending by remember(manualRetentionKey) {
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
                    libraryGridRetentionKey(libraryHost?.uuid, libraryLayoutMode),
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
                    layoutRoute = rootRouteAfterMainPageSelection()
                    onPageSelected(page)
                }
                val mainPageContent: @Composable () -> Unit = {
                    AnimatedContent(
                        targetState = currentPage,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "Ligase page",
                    ) { page ->
                        when (page) {
                            LigasePage.HOME -> LibraryRoute(
                                state = LibraryRouteUiState(
                                    hosts = hosts,
                                    selectedHost = libraryHost,
                                    items = libraryItems,
                                    committedLayouts =
                                        layoutV3EditorWorkspaceState.committedLayouts,
                                    loading = libraryLoading,
                                    refreshing = libraryRefreshing,
                                    status = libraryStatus,
                                    connectivity = libraryConnectivity,
                                    runningAppId = libraryRunningAppId,
                                    sortMode = librarySortMode,
                                    layoutMode = libraryLayoutMode,
                                    hasOperatePermission = libraryCanConfigureInput,
                                    actionsEnabled = libraryCanConfigureInput && libraryOnline,
                                    showTopBar =
                                        navigationPlacement != LigaseNavigationPlacement.SIDE,
                                    manualEditor = LibraryManualEditorUiState(
                                        draft = manualOrderDraft,
                                        saving = manualSortState.saving,
                                        editingEnabled = libraryCanOperate &&
                                            libraryOnline &&
                                            !(
                                                manualSortState.error ==
                                                    ManualLibrarySortError.REVISION_CONFLICT &&
                                                    manualConflictPending
                                            ),
                                        errorMessage =
                                            manualSortErrorMessage(manualSortState.error),
                                    ),
                                ),
                                actions = LibraryRouteActions(
                                    onSortModeChanged = onLibrarySortModeChanged,
                                    onLayoutModeChanged = onLibraryLayoutModeChanged,
                                    onHostSelected = onHostClick,
                                    onAddHost = onAddHost,
                                    onRemoveHost = onRemoveHost,
                                    onLaunch = onLibraryLaunch,
                                    onConfigure = onLibraryConfigure,
                                    onRetrySync = onLibraryRetrySync,
                                    onManualSort = {
                                        if (libraryCanOperate && libraryOnline) {
                                            manualOrderDraft =
                                                ManualLibraryOrderDraft.fromLibraryItems(
                                                    libraryItems,
                                                )
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
                                ),
                                gridState = libraryGridState,
                                assetLoader = libraryAssetLoader,
                                verifiedCoverLoader = libraryVerifiedCoverLoader,
                            )
                            LigasePage.INPUT -> LigaseInputPage(
                                selectedInput = selectedInput,
                                onboarding = false,
                                devices = inputDevices,
                                selectedGamepadKey = selectedGamepadKey,
                                selectedKeyboardKey = selectedKeyboardKey,
                                selectedMouseKey = selectedMouseKey,
                                touchOverlayMode = if (libraryCanConfigureInput) {
                                    touchOverlayMode
                                } else {
                                    LigaseTouchOverlayMode.HIDDEN
                                },
                                cloudTouchMode = cloudTouchMode,
                                inputSettingsWritable = libraryCanConfigureInput,
                                effectiveStreamingTouchMode = effectiveStreamingTouchMode,
                                onInputSelected = onInputSelected,
                                onInputConfirmed = onInputConfirmed,
                                onDeviceSelected = onInputDeviceSelected,
                                onTouchOverlayModeChanged = onTouchOverlayModeChanged,
                                onCloudTouchModeChanged = onCloudTouchModeChanged,
                                listState = inputListState,
                            )
                            LigasePage.SETTINGS -> SettingsScreen(
                                state = SettingsUiState(
                                    selectedInput = selectedInput ?: InputDeviceMode.TOUCH,
                                    themeMode = themeMode,
                                    languageMode = languageMode,
                                    globalResolution = libraryGlobalResolution,
                                    hdrState = libraryHdrState,
                                    canOperate = libraryCanOperate && libraryOnline,
                                    streamBitrate = streamBitrateState,
                                ),
                                streamBitrateDialogState = streamBitrateDialogState,
                                onOpenInput = {
                                    navigateToMainPage(LigasePage.INPUT)
                                },
                                onOpenLayoutHall = {
                                    layoutRoute = LigaseLayoutRoute.HALL
                                },
                                onThemeSelected = onThemeSelected,
                                onLanguageSelected = onLanguageSelected,
                                onGlobalResolutionClick = onGlobalResolutionClick,
                                onStreamBitratePresetSelected =
                                    onStreamBitratePresetSelected,
                                onStreamBitrateCustomSubmitted =
                                    onStreamBitrateCustomSubmitted,
                                onAdvancedSettings = onAdvancedSettings,
                            )
                        }
                    }
                }
                val pageContent: @Composable () -> Unit = {
                    when (layoutRoute) {
                        LigaseLayoutRoute.MAIN -> mainPageContent()
                        LigaseLayoutRoute.HALL -> LayoutV3HallScreen(
                            onBack = { layoutRoute = LigaseLayoutRoute.MAIN },
                            recoverableV3Drafts =
                                layoutV3EditorWorkspaceState.editor.recoverableDrafts,
                            committedV3Layouts =
                                layoutV3EditorWorkspaceState.committedLayouts,
                            onV3CreateBlank = onLayoutV3CreateBlank,
                            onV3ResumeRecovery = onLayoutV3ResumeRecovery,
                            onV3DiscardRecovery = onLayoutV3DiscardRecovery,
                            onV3OpenCommitted = onLayoutV3OpenCommitted,
                        )
                    }
                }
                LigaseNavigationShell(
                    placement = navigationPlacement,
                    currentPage = currentPage,
                    inputEnabled = libraryCanConfigureInput && libraryOnline,
                    onPageSelected = navigateToMainPage,
                ) {
                    pageContent()
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
