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
