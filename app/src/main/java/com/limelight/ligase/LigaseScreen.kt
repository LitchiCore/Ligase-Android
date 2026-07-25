package com.limelight.ligase

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.limelight.R
import com.limelight.grid.assets.CachedAppAssetLoader
import com.limelight.ligase.app.root.LigaseRootContent
import com.limelight.ligase.feature.layout.domain.LayoutCatalogUiState
import com.limelight.ligase.feature.layout.domain.LayoutControlKind
import com.limelight.ligase.feature.layout.domain.LayoutEditorSessionState
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutCatalogV2UiState
import com.limelight.ligase.feature.input.layout.v2.application.LayoutV2EditorWorkspaceUiState
import com.limelight.ligase.feature.input.layout.v2.domain.ControlKind
import com.limelight.ligase.feature.input.layout.v2.domain.HorizontalAnchor
import com.limelight.ligase.feature.input.layout.v2.domain.VerticalAnchor
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditableProperties
import com.limelight.ligase.feature.library.data.dto.LigaseResolutionDto
import com.limelight.ligase.feature.library.domain.HostSortMode
import com.limelight.ligase.feature.library.domain.LibraryHdrState
import com.limelight.ligase.feature.library.domain.LibraryLayoutMode
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.feature.library.domain.LigaseLibraryStatus
import com.limelight.ligase.feature.stream.application.StreamBitrateUiState
import com.limelight.ligase.feature.stream.domain.StreamBitratePresetId
import com.limelight.ligase.input.LigaseInputCategory
import com.limelight.ligase.input.LigaseInputDevice
import com.limelight.ligase.input.LigaseTouchLayout
import com.limelight.ligase.input.LigaseTouchOverlayMode
import com.limelight.ligase.library.LibraryConnectivity
import com.limelight.ligase.library.ManualLibrarySortActionState
import com.limelight.ligase.pairing.AttendedPairingUiState
import com.limelight.nvstream.http.ComputerDetails

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
    layoutCatalogV2State: LayoutCatalogV2UiState,
    layoutEditorState: LayoutEditorSessionState,
    layoutV2EditorWorkspaceState: LayoutV2EditorWorkspaceUiState,
    pairingState: AttendedPairingUiState,
    streamBitrateState: StreamBitrateUiState,
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
    onLayoutCatalogV2Refresh: () -> Unit,
    onLayoutVariantPreferred: (String, Long, String) -> Unit,
    onLayoutVariantPreferenceCleared: (String) -> Unit,
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
    onLayoutV2CreateBlank: (String?) -> Unit,
    onLayoutV2CreateFromPackaged: (String, Long, String) -> Unit,
    onLayoutV2CreateFromLocal: (String, Long, String) -> Unit,
    onLayoutV2ResumeRecovery: (String) -> Unit,
    onLayoutV2DiscardRecovery: (String) -> Unit,
    onLayoutV2SelectElement: (String) -> Unit,
    onLayoutV2MoveElement: (String, Int, Int) -> Unit,
    onLayoutV2ResizeElement: (String, Int, Int) -> Unit,
    onLayoutV2SetAnchors: (String, HorizontalAnchor, VerticalAnchor) -> Unit,
    onLayoutV2SetZOrder: (String, Int) -> Unit,
    onLayoutV2DeleteElement: (String) -> Unit,
    onLayoutV2UpdateProperties: (String, LayoutV2EditableProperties) -> Unit,
    onLayoutV2AddElement: (ControlKind) -> Unit,
    onLayoutV2Validate: () -> Unit,
    onLayoutV2Save: () -> Unit,
    onLayoutV2Discard: () -> Unit,
    onLayoutV2Leave: () -> Unit,
    onGlobalResolutionClick: () -> Unit,
    onStreamBitratePresetSelected: (StreamBitratePresetId) -> Unit,
    onStreamBitrateCustomSubmitted: (String) -> Unit,
    onPairingCancel: () -> Unit,
    onPairingDismiss: () -> Unit,
) = LigaseRootContent(
    themeMode = themeMode,
    onboarding = onboarding,
    currentPage = currentPage,
    selectedInput = selectedInput,
    inputDevices = inputDevices,
    selectedGamepadKey = selectedGamepadKey,
    selectedKeyboardKey = selectedKeyboardKey,
    selectedMouseKey = selectedMouseKey,
    touchLayouts = touchLayouts,
    selectedTouchLayoutId = selectedTouchLayoutId,
    touchOverlayMode = touchOverlayMode,
    languageMode = languageMode,
    hosts = hosts,
    libraryHost = libraryHost,
    libraryItems = libraryItems,
    libraryLoading = libraryLoading,
    libraryRefreshing = libraryRefreshing,
    libraryStatus = libraryStatus,
    libraryConnectivity = libraryConnectivity,
    libraryRevision = libraryRevision,
    libraryGlobalResolution = libraryGlobalResolution,
    libraryHdrState = libraryHdrState,
    libraryRunningAppId = libraryRunningAppId,
    librarySortMode = librarySortMode,
    libraryLayoutMode = libraryLayoutMode,
    libraryAssetLoader = libraryAssetLoader,
    libraryCanOperate = libraryCanOperate,
    libraryCanConfigureInput = libraryCanConfigureInput,
    manualSortState = manualSortState,
    layoutCatalogState = layoutCatalogState,
    layoutCatalogV2State = layoutCatalogV2State,
    layoutEditorState = layoutEditorState,
    layoutV2EditorWorkspaceState = layoutV2EditorWorkspaceState,
    pairingState = pairingState,
    streamBitrateState = streamBitrateState,
    onPageSelected = onPageSelected,
    onInputSelected = onInputSelected,
    onInputConfirmed = onInputConfirmed,
    onInputDeviceSelected = onInputDeviceSelected,
    onTouchLayoutSelected = onTouchLayoutSelected,
    onTouchOverlayModeChanged = onTouchOverlayModeChanged,
    onThemeSelected = onThemeSelected,
    onLanguageSelected = onLanguageSelected,
    onHostClick = onHostClick,
    onRemoveHost = onRemoveHost,
    onAddHost = onAddHost,
    onAdvancedSettings = onAdvancedSettings,
    onLibrarySortModeChanged = onLibrarySortModeChanged,
    onLibraryLayoutModeChanged = onLibraryLayoutModeChanged,
    onLibraryLaunch = onLibraryLaunch,
    onLibraryConfigure = onLibraryConfigure,
    onLibraryRetrySync = onLibraryRetrySync,
    onManualOrderSubmit = onManualOrderSubmit,
    onLayoutCatalogRefresh = onLayoutCatalogRefresh,
    onLayoutCatalogV2Refresh = onLayoutCatalogV2Refresh,
    onLayoutVariantPreferred = onLayoutVariantPreferred,
    onLayoutVariantPreferenceCleared = onLayoutVariantPreferenceCleared,
    onLayoutSelect = onLayoutSelect,
    onLayoutPreview = onLayoutPreview,
    onLayoutCreateCopy = onLayoutCreateCopy,
    onLayoutOpenEditor = onLayoutOpenEditor,
    onLayoutMove = onLayoutMove,
    onLayoutResize = onLayoutResize,
    onLayoutDelete = onLayoutDelete,
    onLayoutAdd = onLayoutAdd,
    onLayoutSave = onLayoutSave,
    onLayoutDiscard = onLayoutDiscard,
    onLayoutV2CreateBlank = onLayoutV2CreateBlank,
    onLayoutV2CreateFromPackaged = onLayoutV2CreateFromPackaged,
    onLayoutV2CreateFromLocal = onLayoutV2CreateFromLocal,
    onLayoutV2ResumeRecovery = onLayoutV2ResumeRecovery,
    onLayoutV2DiscardRecovery = onLayoutV2DiscardRecovery,
    onLayoutV2SelectElement = onLayoutV2SelectElement,
    onLayoutV2MoveElement = onLayoutV2MoveElement,
    onLayoutV2ResizeElement = onLayoutV2ResizeElement,
    onLayoutV2SetAnchors = onLayoutV2SetAnchors,
    onLayoutV2SetZOrder = onLayoutV2SetZOrder,
    onLayoutV2DeleteElement = onLayoutV2DeleteElement,
    onLayoutV2UpdateProperties = onLayoutV2UpdateProperties,
    onLayoutV2AddElement = onLayoutV2AddElement,
    onLayoutV2Validate = onLayoutV2Validate,
    onLayoutV2Save = onLayoutV2Save,
    onLayoutV2Discard = onLayoutV2Discard,
    onLayoutV2Leave = onLayoutV2Leave,
    onGlobalResolutionClick = onGlobalResolutionClick,
    onStreamBitratePresetSelected = onStreamBitratePresetSelected,
    onStreamBitrateCustomSubmitted = onStreamBitrateCustomSubmitted,
    onPairingCancel = onPairingCancel,
    onPairingDismiss = onPairingDismiss,
)

@Composable
internal fun ligaseNavigationContentBottomPadding(): Dp =
    if (LocalConfiguration.current.screenWidthDp < 600) 104.dp else 28.dp

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
                                contentDescription = stringResource(R.string.ligase_back),
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
