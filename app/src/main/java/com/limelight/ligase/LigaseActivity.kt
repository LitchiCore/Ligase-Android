package com.limelight.ligase

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.limelight.BuildConfig
import com.limelight.LimeLog
import com.limelight.R
import com.limelight.TouchKitLayoutPreviewActivity
import com.limelight.binding.PlatformBinding
import com.limelight.computers.ComputerManagerService
import com.limelight.grid.assets.CachedAppAssetLoader
import com.limelight.ligase.feature.host.application.HostAddResult
import com.limelight.ligase.feature.host.application.HostClickAction
import com.limelight.ligase.feature.host.application.HostEndpointCoordinator
import com.limelight.ligase.feature.host.application.HostWakeResult
import com.limelight.ligase.feature.host.infrastructure.LegacyComputerRegistryTransport
import com.limelight.ligase.feature.input.application.InputSelectionCoordinator
import com.limelight.ligase.feature.input.application.InputSelectionState
import com.limelight.ligase.feature.input.layout.v2.application.LayoutCatalogV2Catalog
import com.limelight.ligase.feature.input.layout.v2.application.LayoutV2CatalogRegistrationAttachment
import com.limelight.ligase.feature.input.layout.v2.application.LayoutV2EditorWorkspaceViewModel
import com.limelight.ligase.feature.input.layout.v2.application.LayoutCatalogV2SourceRegistry
import com.limelight.ligase.feature.input.layout.v2.data.LayoutCatalogV2LocalRepository
import com.limelight.ligase.feature.input.layout.v2.data.LayoutCatalogV2PackagedSource
import com.limelight.ligase.feature.input.layout.v2.data.LayoutPreferredVariantV2Repository
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutCatalogV2UiState
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorHandoffIssue
import com.limelight.ligase.feature.input.layout.v2.editor.LayoutV2EditorHandoffResult
import com.limelight.ligase.feature.input.layout.v2.ui.blackeditor.LayoutV2BlackEditorActivity
import com.limelight.ligase.feature.library.application.LibraryHostCoordinator
import com.limelight.ligase.feature.library.application.LibraryManualSortCoordinator
import com.limelight.ligase.feature.library.application.LibraryStreamingSettingsCoordinator
import com.limelight.ligase.feature.library.application.LibraryStreamingSettingsResult
import com.limelight.ligase.feature.library.data.repository.LigaseSyncRepository
import com.limelight.ligase.feature.library.domain.HostSortMode
import com.limelight.ligase.feature.library.domain.LibraryHdrState
import com.limelight.ligase.feature.library.domain.LibraryHdrStateResolver
import com.limelight.ligase.feature.library.domain.LibraryLayoutMode
import com.limelight.ligase.feature.library.domain.LibrarySyncAutoLoadPolicy
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.feature.library.infrastructure.LegacyGameStreamLibraryTransport
import com.limelight.ligase.feature.library.infrastructure.AndroidHdrCapabilities
import com.limelight.ligase.feature.library.infrastructure.AndroidHdrCapabilityProbe
import com.limelight.ligase.feature.library.ui.settings.StreamingResolutionDialogFragment
import com.limelight.ligase.feature.library.ui.settings.StreamingResolutionEditResult
import com.limelight.ligase.feature.library.ui.settings.StreamingResolutionEditorRequest
import com.limelight.ligase.feature.library.ui.settings.StreamingResolutionSubmissionDecision
import com.limelight.ligase.feature.library.ui.settings.StreamingResolutionTarget
import com.limelight.ligase.feature.library.ui.settings.streamingResolutionSubmissionDecision
import com.limelight.ligase.feature.layout.editor.LayoutWorkspaceViewModel
import com.limelight.ligase.feature.pairing.application.HostPairingCoordinator
import com.limelight.ligase.feature.pairing.application.HostPairingMode
import com.limelight.ligase.feature.pairing.infrastructure.LegacyPairingResult
import com.limelight.ligase.feature.pairing.infrastructure.LegacyPairingTransport
import com.limelight.ligase.feature.stream.application.StreamLaunchBlockReason
import com.limelight.ligase.feature.stream.application.StreamLaunchConfirmation
import com.limelight.ligase.feature.stream.application.StreamLaunchCoordinator
import com.limelight.ligase.feature.stream.application.StreamLaunchExecutionResult
import com.limelight.ligase.feature.stream.application.StreamLaunchPlanningResult
import com.limelight.ligase.feature.stream.application.StreamLaunchRequest
import com.limelight.ligase.feature.stream.application.StreamBitrateState
import com.limelight.ligase.feature.stream.application.StreamBitrateUiState
import com.limelight.ligase.feature.stream.domain.StreamBitratePresetId
import com.limelight.ligase.feature.stream.infrastructure.LegacyGameStreamLauncher
import com.limelight.ligase.feature.stream.infrastructure.StreamBitratePreferences
import com.limelight.ligase.library.LibraryConnectivity
import com.limelight.ligase.library.LibrarySessionError
import com.limelight.ligase.library.LibrarySessionViewModel
import com.limelight.ligase.library.LibraryOperationGate
import com.limelight.ligase.library.ManualLibrarySortAction
import com.limelight.ligase.endpoint.LigaseAddHostDialog
import com.limelight.ligase.endpoint.LigaseEndpoint
import com.limelight.ligase.input.LigaseInputCategory
import com.limelight.ligase.input.LigaseTouchOverlayMode
import com.limelight.ligase.pairing.AttendedPairingViewModel
import com.limelight.ligase.pairing.LigaseAccessUiPolicy
import com.limelight.ligase.pairing.LigaseClientAccessMode
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager
import com.limelight.nvstream.http.PairingManager.PairState
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.preferences.StreamSettings
import com.limelight.utils.UiHelper
import java.io.IOException
import java.util.Locale

class LigaseActivity : AppCompatActivity() {
    private var currentPage by mutableStateOf(LigasePage.HOME)
    private var inputSelectionState by mutableStateOf<InputSelectionState?>(null)
    private var themeMode by mutableStateOf(LigaseThemeMode.SYSTEM)
    private var languageMode by mutableStateOf(LigaseLanguageMode.SYSTEM)
    private val hosts = mutableStateListOf<ComputerDetails>()
    private var libraryHost by mutableStateOf<ComputerDetails?>(null)
    private var libraryAccessMode by mutableStateOf<String?>(null)
    private var localHdrCapabilities = AndroidHdrCapabilities(null, null, null)
    private var libraryRunningAppId by mutableStateOf(0)
    private var librarySortMode by mutableStateOf(HostSortMode.NAME_ASCENDING)
    private var libraryLayoutMode by mutableStateOf(LibraryLayoutMode.LIST)
    private var libraryAssetLoader by mutableStateOf<CachedAppAssetLoader?>(null)
    private var pendingLibraryHostUuid: String? = null
    private val syncRepository = LigaseSyncRepository()
    private lateinit var inputSelectionCoordinator: InputSelectionCoordinator
    private lateinit var pairingViewModel: AttendedPairingViewModel
    private lateinit var hostPairingCoordinator: HostPairingCoordinator
    private lateinit var hostEndpointCoordinator: HostEndpointCoordinator
    private lateinit var streamLaunchCoordinator: StreamLaunchCoordinator
    private lateinit var streamBitrateState: StreamBitrateState
    private var streamBitrateUiState by mutableStateOf<StreamBitrateUiState?>(null)
    private lateinit var hdrCapabilityProbe: AndroidHdrCapabilityProbe
    private lateinit var librarySessionViewModel: LibrarySessionViewModel
    private lateinit var libraryHostCoordinator: LibraryHostCoordinator
    private lateinit var libraryManualSortCoordinator: LibraryManualSortCoordinator
    private lateinit var libraryStreamingSettingsCoordinator:
        LibraryStreamingSettingsCoordinator
    private lateinit var layoutWorkspaceViewModel: LayoutWorkspaceViewModel
    private lateinit var layoutCatalogV2Catalog: LayoutCatalogV2Catalog
    private lateinit var layoutCatalogV2SourceRegistry: LayoutCatalogV2SourceRegistry
    private lateinit var layoutV2EditorWorkspaceViewModel: LayoutV2EditorWorkspaceViewModel
    private var layoutV2CatalogRegistrationAttachment:
        LayoutV2CatalogRegistrationAttachment? = null
    private var layoutCatalogV2State by mutableStateOf(LayoutCatalogV2UiState())
    private var layoutV2EditorLaunchInFlight = false

    private var managerBinder: ComputerManagerService.ComputerManagerBinder? = null
    private var serviceBound = false
    private var foreground = false
    private var lastBackPressedAt = 0L

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? ComputerManagerService.ComputerManagerBinder ?: return
            Thread {
                binder.waitForReady()
                managerBinder = binder
                val restoredHost = pendingLibraryHostUuid?.let(binder::getComputer)
                runOnUiThread {
                    hostEndpointCoordinator.onTransportAvailable(::handleHostUpdate)
                    if (currentPage == LigasePage.HOME && restoredHost != null) {
                        openLibrary(restoredHost)
                    }
                }
                PlatformBinding.getCryptoProvider(this@LigaseActivity).clientCertificate
            }.start()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            managerBinder = null
            hostEndpointCoordinator.onTransportUnavailable()
            if (::libraryHostCoordinator.isInitialized) {
                libraryHostCoordinator.stopAppListUpdates()
            }
            libraryHost?.let {
                librarySessionViewModel.updateConnectivity(
                    it.uuid,
                    LibraryConnectivity.UNKNOWN,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiHelper.setLocale(this)
        enableEdgeToEdge()
        hdrCapabilityProbe = AndroidHdrCapabilityProbe(this)
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        localHdrCapabilities = hdrCapabilityProbe.probe()
        streamBitrateState = StreamBitrateState(
            preferences = StreamBitratePreferences(
                PreferenceManager.getDefaultSharedPreferences(this),
            ),
            defaultKbps = PreferenceConfiguration.getDefaultBitrate(this),
            onStateChanged = { state -> streamBitrateUiState = state },
        )

        inputSelectionCoordinator = InputSelectionCoordinator(this) { state ->
            inputSelectionState = state
        }
        val initialInputState = checkNotNull(inputSelectionState)
        themeMode = LigasePreferences.getThemeMode(this)
        languageMode = LigasePreferences.getLanguageMode(this)
        libraryLayoutMode = LigasePreferences.getLibraryLayoutMode(this)
        currentPage = savedInstanceState?.getString(STATE_PAGE)
            ?.let { saved -> LigasePage.entries.firstOrNull { it.name == saved } }
            ?: if (initialInputState.onboarding) LigasePage.INPUT else LigasePage.HOME
        pendingLibraryHostUuid = savedInstanceState?.getString(STATE_LIBRARY_HOST_UUID)
        hostEndpointCoordinator = HostEndpointCoordinator(
            transport = LegacyComputerRegistryTransport(this) { managerBinder },
            postToMain = { action -> runOnUiThread(action) },
        )
        streamLaunchCoordinator = StreamLaunchCoordinator(
            currentHost = { libraryHost },
            launcher = LegacyGameStreamLauncher(this) { managerBinder },
        )
        pairingViewModel = ViewModelProvider(this)[AttendedPairingViewModel::class.java]
        hostPairingCoordinator = HostPairingCoordinator(
            transport = LegacyPairingTransport(this) { managerBinder },
            attendedViewModel = pairingViewModel,
            postToMain = { action -> runOnUiThread(action) },
            beforeLegacyPairing = { stopComputerUpdates(true) },
        )
        librarySessionViewModel = ViewModelProvider(this)[LibrarySessionViewModel::class.java]
        libraryHostCoordinator = LibraryHostCoordinator(
            session = librarySessionViewModel,
            repository = syncRepository,
            transport = LegacyGameStreamLibraryTransport(this) { managerBinder },
            hdrState = ::currentHdrState,
            postToMain = { action -> runOnUiThread(action) },
            onAssetLoaderChanged = { loader -> libraryAssetLoader = loader },
            onRefreshAccepted = { preservedContent ->
                startAppListUpdates()
                if (preservedContent) toast(R.string.ligase_refresh_success)
            },
            onRefreshFailed = { preservedContent, error ->
                LimeLog.warning("Ligase library sync failed: $error")
                if (preservedContent) toast(R.string.ligase_refresh_failed)
            },
        )
        libraryManualSortCoordinator = LibraryManualSortCoordinator(
            session = librarySessionViewModel,
            action = ManualLibrarySortAction(),
            repository = syncRepository,
            httpFactory = libraryHostCoordinator::createHttp,
            postToMain = { action -> runOnUiThread(action) },
            onRevisionConflict = {
                fetchLibrarySync(force = true)
            },
            onPermissionDenied = { host ->
                managerBinder?.invalidateStateForComputer(host.uuid)
            },
            onSuccess = { host ->
                librarySortMode = HostSortMode.MANUAL
                LigasePreferences.setLibrarySortMode(
                    this,
                    host.uuid,
                    HostSortMode.MANUAL,
                )
            },
        )
        libraryStreamingSettingsCoordinator = LibraryStreamingSettingsCoordinator(
            session = librarySessionViewModel,
            repository = syncRepository,
            httpFactory = libraryHostCoordinator::createHttp,
            postToMain = { action -> runOnUiThread(action) },
            onRevisionConflict = {
                fetchLibrarySync(force = true)
            },
            onPermissionDenied = { host ->
                managerBinder?.invalidateStateForComputer(host.uuid)
            },
            onResult = { _, result ->
                when (result) {
                    is LibraryStreamingSettingsResult.Success ->
                        toast(R.string.ligase_sync_saved)
                    LibraryStreamingSettingsResult.RevisionConflict -> {
                        toast(R.string.ligase_sync_revision_conflict)
                    }
                    LibraryStreamingSettingsResult.PermissionDenied -> {
                        toast(R.string.ligase_observe_mode_action_blocked)
                    }
                    is LibraryStreamingSettingsResult.Failed -> {
                        LimeLog.warning(
                            "Ligase streaming settings write failed: ${result.error}",
                        )
                        toast(R.string.ligase_sync_write_failed)
                    }
                }
            },
        )
        registerStreamingResolutionResult()
        layoutWorkspaceViewModel = ViewModelProvider(this)[LayoutWorkspaceViewModel::class.java]
        layoutCatalogV2Catalog = LayoutCatalogV2Catalog(
            LayoutCatalogV2LocalRepository(this),
            LayoutPreferredVariantV2Repository(this),
        )
        layoutCatalogV2SourceRegistry = LayoutCatalogV2SourceRegistry(
            layoutCatalogV2Catalog,
            LayoutCatalogV2PackagedSource(this),
        ) { state -> layoutCatalogV2State = state }
        layoutV2EditorWorkspaceViewModel =
            ViewModelProvider(this)[LayoutV2EditorWorkspaceViewModel::class.java]
        layoutV2CatalogRegistrationAttachment =
            layoutV2EditorWorkspaceViewModel.attachCatalogRegistration { records ->
                layoutCatalogV2SourceRegistry.refresh(registeredRecords = records)
                true
            }

        setContent {
            val libraryState = librarySessionViewModel.state
            val layoutV2EditorWorkspaceState by
                layoutV2EditorWorkspaceViewModel.state.collectAsState()
            LaunchedEffect(pairingViewModel.state) {
                if (pairingViewModel.state == com.limelight.ligase.pairing.AttendedPairingUiState.Completed) {
                    pairingViewModel.targetHostUuid
                        ?.let { managerBinder?.getComputer(it) }
                        ?.let(::openLibrary)
                    pairingViewModel.markCompletedHandled()
                }
            }
            LigaseRoot(
                themeMode = themeMode,
                streamBitrateState = checkNotNull(streamBitrateUiState),
                onStreamBitratePresetSelected = ::selectStreamBitratePreset,
                onStreamBitrateCustomSubmitted = ::submitCustomStreamBitrate,
                onboarding = checkNotNull(inputSelectionState).onboarding,
                currentPage = currentPage,
                selectedInput = checkNotNull(inputSelectionState).selectedMode,
                inputDevices = checkNotNull(inputSelectionState).devices,
                selectedGamepadKey = checkNotNull(inputSelectionState).selectedGamepadKey,
                selectedKeyboardKey = checkNotNull(inputSelectionState).selectedKeyboardKey,
                selectedMouseKey = checkNotNull(inputSelectionState).selectedMouseKey,
                touchLayouts = checkNotNull(inputSelectionState).touchLayouts,
                selectedTouchLayoutId =
                    checkNotNull(inputSelectionState).selectedTouchLayoutId,
                touchOverlayMode = checkNotNull(inputSelectionState).overlayMode,
                languageMode = languageMode,
                hosts = hosts,
                libraryHost = libraryHost,
                libraryItems = libraryState.content?.items.orEmpty(),
                libraryConnectivity = libraryState.connectivity,
                libraryLoading = libraryState.initialLoading,
                libraryRefreshing = libraryState.refreshing,
                libraryStatus = libraryState.status,
                libraryRevision = libraryState.content?.sync?.library?.revision,
                libraryGlobalResolution = libraryState.content?.sync?.streaming?.globalResolution,
                libraryHdrState = libraryState.content?.hdr ?: currentHdrState(null),
                libraryRunningAppId = libraryRunningAppId,
                librarySortMode = librarySortMode,
                libraryLayoutMode = libraryLayoutMode,
                libraryAssetLoader = libraryAssetLoader,
                libraryCanOperate = LibraryOperationGate.canOperate(
                    libraryState.connectivity,
                    libraryAccessMode,
                ),
                libraryCanConfigureInput =
                    if (
                        libraryHost != null &&
                        libraryHost?.pairState == PairState.PAIRED
                    ) {
                        LibraryOperationGate.canOperate(
                            libraryState.connectivity,
                            libraryAccessMode,
                        )
                    } else {
                        LigaseAccessUiPolicy.canConfigureInput(
                            hasSelectedHost = libraryHost != null,
                            paired = false,
                            accessMode = libraryAccessMode,
                        )
                    },
                manualSortState = librarySessionViewModel.manualSortState,
                layoutCatalogState = layoutWorkspaceViewModel.catalogState,
                layoutCatalogV2State = layoutCatalogV2State,
                layoutEditorState = layoutWorkspaceViewModel.editorState,
                layoutV2EditorWorkspaceState = layoutV2EditorWorkspaceState,
                pairingState = pairingViewModel.state,
                onPageSelected = ::selectPage,
                onInputSelected = ::selectInput,
                onInputConfirmed = ::confirmInput,
                onInputDeviceSelected = ::selectInputDevice,
                onTouchLayoutSelected = ::selectTouchLayout,
                onTouchOverlayModeChanged = ::selectTouchOverlayMode,
                onThemeSelected = ::selectTheme,
                onLanguageSelected = ::selectLanguage,
                onHostClick = ::onHostClicked,
                onRemoveHost = ::confirmRemoveHost,
                onAddHost = ::showAddHostDialog,
                onAdvancedSettings = {
                    startActivity(Intent(this, StreamSettings::class.java))
                },
                onLibrarySortModeChanged = ::changeLibrarySortMode,
                onLibraryLayoutModeChanged = ::changeLibraryLayoutMode,
                onLibraryLaunch = ::launchLibraryItem,
                onLibraryConfigure = ::showLibraryItemSettings,
                onLibraryRetrySync = ::retryLibrarySync,
                onManualOrderSubmit = ::submitManualLibraryOrder,
                onLayoutCatalogRefresh = {
                    layoutWorkspaceViewModel.refreshCatalog()
                    inputSelectionCoordinator.refreshLayouts()
                },
                onLayoutCatalogV2Refresh = {
                    layoutV2EditorWorkspaceViewModel.refreshCatalog()
                },
                onLayoutVariantPreferred = { layoutId, revision, variantId ->
                    layoutCatalogV2SourceRegistry.selectPreferredVariant(
                        layoutId,
                        revision,
                        variantId,
                    )
                },
                onLayoutVariantPreferenceCleared = { layoutId ->
                    layoutCatalogV2SourceRegistry.clearPreferredVariant(layoutId)
                },
                onLayoutSelect = { layoutId ->
                    if (layoutWorkspaceViewModel.selectGlobal(layoutId)) {
                        inputSelectionCoordinator.refreshLayouts()
                    }
                },
                onLayoutCreateCopy = { layoutWorkspaceViewModel.createEditableCopy(it) },
                onLayoutOpenEditor = { layoutWorkspaceViewModel.openEditor(it) },
                onLayoutPreview = { layoutId ->
                    startActivity(TouchKitLayoutPreviewActivity.createIntent(this, layoutId))
                },
                onLayoutMove = { id, x, y -> layoutWorkspaceViewModel.moveElement(id, x, y) },
                onLayoutResize = { id, width, height ->
                    layoutWorkspaceViewModel.resizeElement(id, width, height)
                },
                onLayoutDelete = { layoutWorkspaceViewModel.deleteElement(it) },
                onLayoutAdd = { layoutWorkspaceViewModel.addElement(it) },
                onLayoutSave = {
                    if (layoutWorkspaceViewModel.saveDraft() != null) {
                        inputSelectionCoordinator.refreshLayouts()
                    }
                },
                onLayoutDiscard = layoutWorkspaceViewModel::discardDraft,
                onLayoutV2CreateBlank = layoutV2EditorWorkspaceViewModel::createBlank,
                onLayoutV2CreateFromPackaged =
                    layoutV2EditorWorkspaceViewModel::createFromPackaged,
                onLayoutV2CreateFromLocal = layoutV2EditorWorkspaceViewModel::createFromLocal,
                onLayoutV2ResumeRecovery = layoutV2EditorWorkspaceViewModel::resumeRecovery,
                onLayoutV2DiscardRecovery = layoutV2EditorWorkspaceViewModel::discardRecovery,
                onLayoutV2SelectElement = layoutV2EditorWorkspaceViewModel::selectElement,
                onLayoutV2MoveElement = layoutV2EditorWorkspaceViewModel::moveElement,
                onLayoutV2ResizeElement = layoutV2EditorWorkspaceViewModel::resizeElement,
                onLayoutV2SetAnchors = layoutV2EditorWorkspaceViewModel::setAnchors,
                onLayoutV2SetZOrder = layoutV2EditorWorkspaceViewModel::setZOrder,
                onLayoutV2DeleteElement = layoutV2EditorWorkspaceViewModel::deleteElement,
                onLayoutV2UpdateProperties =
                    layoutV2EditorWorkspaceViewModel::updateProperties,
                onLayoutV2AddElement = layoutV2EditorWorkspaceViewModel::addElement,
                onLayoutV2Validate = layoutV2EditorWorkspaceViewModel::validate,
                onLayoutV2Save = layoutV2EditorWorkspaceViewModel::save,
                onLayoutV2Discard = layoutV2EditorWorkspaceViewModel::discard,
                onLayoutV2Leave = layoutV2EditorWorkspaceViewModel::leave,
                onLayoutV2EditorLaunchRequested = ::launchLayoutV2Editor,
                onGlobalResolutionClick = ::showGlobalResolutionSettings,
                onPairingCancel = pairingViewModel::cancel,
                onPairingDismiss = pairingViewModel::dismissStopped,
            )
        }

        setupBackBehavior()
        serviceBound = bindService(
            Intent(this, ComputerManagerService::class.java),
            serviceConnection,
            Service.BIND_AUTO_CREATE,
        )
    }

    private fun confirmInput() {
        if (inputSelectionCoordinator.confirmSelection()) currentPage = LigasePage.HOME
    }

    private fun selectInput(mode: InputDeviceMode) {
        inputSelectionCoordinator.selectMode(mode)
    }

    private fun selectInputDevice(category: LigaseInputCategory, stableKey: String) {
        inputSelectionCoordinator.selectDevice(category, stableKey)
    }

    private fun selectTouchLayout(layoutId: String) {
        if (!inputSelectionCoordinator.selectTouchLayout(layoutId)) {
            toast(R.string.ligase_touch_layout_missing_short)
        }
    }

    private fun selectTouchOverlayMode(mode: LigaseTouchOverlayMode) {
        inputSelectionCoordinator.selectOverlayMode(mode)
    }

    private fun selectTheme(mode: LigaseThemeMode) {
        if (mode == themeMode) return
        themeMode = mode
        LigasePreferences.setThemeMode(this, mode)
    }

    private fun selectLanguage(mode: LigaseLanguageMode) {
        if (mode == languageMode) return
        LigasePreferences.setLanguageMode(this, mode)
        UiHelper.setLocale(this)
        recreate()
    }

    private fun selectStreamBitratePreset(id: StreamBitratePresetId) {
        streamBitrateState.setStreamBitratePreset(id)
    }

    private fun submitCustomStreamBitrate(rawMbps: String) {
        streamBitrateState.submitCustomMbps(rawMbps)
    }

    private fun selectPage(page: LigasePage) {
        if (page == currentPage) return
        if (page != LigasePage.HOME) {
            stopAppListUpdates()
            librarySessionViewModel.cancelRefresh()
        }
        currentPage = page
        if (page == LigasePage.HOME) {
            startAppListUpdates()
            selectDefaultHostIfNeeded()
            libraryHost?.let(::handleSelectedHostCapabilities)
        }
    }

    private fun setupBackBehavior() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!checkNotNull(inputSelectionState).onboarding &&
                    currentPage != LigasePage.HOME
                ) {
                    currentPage = LigasePage.HOME
                    startAppListUpdates()
                    selectDefaultHostIfNeeded()
                    return
                }
                val now = SystemClock.elapsedRealtime()
                if (lastBackPressedAt != 0L && now - lastBackPressedAt <= EXIT_INTERVAL_MS) {
                    finish()
                    return
                }
                lastBackPressedAt = now
                toast(R.string.ligase_press_back_again_to_exit)
            }
        })
    }

    private fun startComputerUpdates() {
        hostEndpointCoordinator.startUpdates()
    }

    private fun stopComputerUpdates(wait: Boolean) {
        hostEndpointCoordinator.stopUpdates(wait)
    }

    private fun handleHostUpdate(details: ComputerDetails) {
        val index = hosts.indexOfFirst { it.uuid.equals(details.uuid, ignoreCase = true) }
        if (index >= 0) hosts[index] = details
        else {
            hosts += details
            hosts.sortBy { it.name.lowercase() }
        }
        if (libraryHost?.uuid?.equals(details.uuid, ignoreCase = true) == true) {
            val previousConnectivity = librarySessionViewModel.state.connectivity
            libraryHost = details
            libraryAccessMode = details.ligaseClientAccessMode
            libraryRunningAppId = details.runningGameId
            libraryHostCoordinator.updateConnectivity(details)
            if (details.state != ComputerDetails.State.ONLINE) {
                stopAppListUpdates()
            }
            handleSelectedHostCapabilities(details)
            if (
                details.state == ComputerDetails.State.ONLINE &&
                previousConnectivity != LibraryConnectivity.ONLINE &&
                librarySessionViewModel.state.content != null
            ) {
                fetchLibrarySync(force = true)
            }
            if (
                details.state == ComputerDetails.State.ONLINE &&
                librarySessionViewModel.state.content != null
            ) {
                updateLibraryFromRaw(details.rawAppList)
            }
        } else if (
            libraryHost == null &&
            currentPage == LigasePage.HOME &&
            details.state == ComputerDetails.State.ONLINE &&
            details.pairState == PairState.PAIRED
        ) {
            openLibrary(details)
        }
    }

    private fun onHostClicked(host: ComputerDetails) {
        when (hostEndpointCoordinator.actionFor(host)) {
            HostClickAction.IGNORE -> Unit
            HostClickAction.WAKE -> wakeHost(host)
            HostClickAction.PAIR -> pairHost(host)
            HostClickAction.OPEN -> openLibrary(host)
        }
    }

    private fun pairHost(host: ComputerDetails) {
        val binder = managerBinder
        if (host.state == ComputerDetails.State.OFFLINE || host.activeAddress == null) {
            toast(R.string.pair_pc_offline)
            return
        }
        if (binder == null) {
            toast(R.string.error_manager_not_running)
            return
        }

        if (hostPairingCoordinator.modeFor(host) == HostPairingMode.ATTENDED) {
            pairHostAttended(host)
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.ligase_pair_legacy_title)
            .setMessage(R.string.ligase_pair_legacy_summary)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.ligase_pair_continue) { _, _ ->
                pairHostLegacy(host)
            }
            .show()
    }

    private fun pairHostLegacy(host: ComputerDetails) {
        val pin = PairingManager.generatePinString()
        val progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.pair_pairing_title)
            .setMessage(
                getString(R.string.pair_pairing_msg) + " " + pin + "\n\n" +
                    getString(R.string.pair_pairing_help),
            )
            .setCancelable(false)
            .create()
        progressDialog.show()

        if (!hostPairingCoordinator.startLegacy(host, pin) { result ->
                progressDialog.dismiss()
                if (result == LegacyPairingResult.Paired) {
                    openLibrary(host)
                } else {
                    val message = when (result) {
                        LegacyPairingResult.PinWrong -> getString(R.string.pair_incorrect_pin)
                        LegacyPairingResult.AlreadyInProgress ->
                            getString(R.string.pair_already_in_progress)
                        LegacyPairingResult.HostInGame -> getString(R.string.pair_pc_ingame)
                        LegacyPairingResult.UnknownHost -> getString(R.string.error_unknown_host)
                        LegacyPairingResult.NotFound -> getString(R.string.error_404)
                        is LegacyPairingResult.TransportFailure ->
                            result.message ?: getString(R.string.pair_fail)
                        LegacyPairingResult.Failed -> getString(R.string.pair_fail)
                        LegacyPairingResult.Paired -> error("handled above")
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    startComputerUpdates()
                }
            }
        ) {
            progressDialog.dismiss()
        }
    }

    private fun pairHostAttended(host: ComputerDetails) {
        try {
            hostPairingCoordinator.startAttended(host, attendedDeviceName())
        } catch (_: IOException) {
            toast(R.string.pair_pc_offline)
        }
    }

    private fun attendedDeviceName(): String {
        val source = (Build.MODEL ?: "Android").trim().ifBlank { "Android" }
        val end = source.offsetByCodePoints(0, source.codePointCount(0, source.length).coerceAtMost(80))
        return source.substring(0, end)
    }

    private fun wakeHost(host: ComputerDetails) {
        hostEndpointCoordinator.wake(host) { result ->
            toast(
                when (result) {
                    HostWakeResult.Sent -> R.string.wol_waking_msg
                    HostWakeResult.MissingMacAddress -> R.string.wol_no_mac
                    HostWakeResult.Failed -> R.string.wol_fail
                },
            )
        }
    }

    private fun openLibrary(host: ComputerDetails) {
        stopAppListUpdates()
        libraryHostCoordinator.selectHost(host)
        libraryHost = host
        libraryAccessMode = host.ligaseClientAccessMode
        pendingLibraryHostUuid = host.uuid
        libraryRunningAppId = host.runningGameId
        librarySortMode = LigasePreferences.getLibrarySortMode(this, host.uuid)
        startComputerUpdates()
        handleSelectedHostCapabilities(host)
    }

    private fun clearLibraryState() {
        stopAppListUpdates()
        libraryHost = null
        libraryAccessMode = null
        libraryHostCoordinator.clearHost()
        pendingLibraryHostUuid = null
    }

    private fun startAppListUpdates() {
        if (managerBinder == null) return
        val host = libraryHost ?: return
        if (
            !foreground ||
            currentPage != LigasePage.HOME ||
            librarySessionViewModel.state.content == null
        ) return
        libraryHostCoordinator.startAppListUpdates(host, allowed = true)
    }

    private fun selectDefaultHostIfNeeded() {
        if (libraryHost != null) return
        hostEndpointCoordinator.defaultHost(hosts)?.let(::openLibrary)
    }

    private fun confirmRemoveHost(host: ComputerDetails) {
        MaterialAlertDialogBuilder(this)
            .setTitle(host.name)
            .setMessage(R.string.delete_pc_msg)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.pcview_menu_delete_pc) { _, _ ->
                removeHost(host)
            }
            .show()
    }

    private fun removeHost(host: ComputerDetails) {
        val wasSelected = libraryHost?.uuid?.equals(host.uuid, ignoreCase = true) == true
        if (wasSelected) clearLibraryState()
        if (hostEndpointCoordinator.remove(host)) {
            hosts.removeAll { it.uuid.equals(host.uuid, ignoreCase = true) }
            if (wasSelected) selectDefaultHostIfNeeded()
        }
    }

    private fun stopAppListUpdates() {
        if (::libraryHostCoordinator.isInitialized) {
            libraryHostCoordinator.stopAppListUpdates()
        }
    }

    private fun disposeLibraryAssets() {
        if (::libraryHostCoordinator.isInitialized) {
            libraryHostCoordinator.dispose()
        }
    }

    private fun handleSelectedHostCapabilities(host: ComputerDetails) {
        libraryHostCoordinator.updateConnectivity(host)
        if (host.state != ComputerDetails.State.ONLINE) return
        if (
            host.pairState == PairState.PAIRED &&
            host.ligaseClientAccessMode != "operate" &&
            currentPage == LigasePage.INPUT
        ) {
            currentPage = LigasePage.HOME
            startAppListUpdates()
        }
        if (
            host.ligaseSyncVersion != LigaseSyncRepository.SUPPORTED_SYNC_VERSION ||
            host.ligaseSyncPath.isNullOrBlank()
        ) {
            stopAppListUpdates()
            librarySessionViewModel.markIncompatible(host.uuid)
            return
        }
        val state = librarySessionViewModel.state
        if (
            LibrarySyncAutoLoadPolicy.shouldFetch(
                hasSnapshot = state.content != null,
                status = state.status,
            )
        ) {
            fetchLibrarySync(force = false)
        }
    }

    private fun fetchLibrarySync(force: Boolean) {
        val host = libraryHost ?: return
        libraryHostCoordinator.fetch(host, force)
    }

    private fun retryLibrarySync() {
        val host = libraryHost ?: return
        if (librarySessionViewModel.state.error == LibrarySessionError.INCOMPATIBLE) {
            managerBinder?.invalidateStateForComputer(host.uuid)
            return
        }
        managerBinder?.invalidateStateForComputer(host.uuid)
        fetchLibrarySync(force = true)
    }

    private fun currentHdrState(hostEncodingSupported: Boolean?): LibraryHdrState =
        LibraryHdrStateResolver.resolve(
            hostEncodingSupported = hostEncodingSupported,
            displaySupported = localHdrCapabilities.displaySupported,
            decoderSupported = localHdrCapabilities.decoderSupported,
            userEnabled = localHdrCapabilities.userEnabled,
        )

    private fun refreshLocalHdrCapabilities() {
        localHdrCapabilities = hdrCapabilityProbe.probe()
        val host = libraryHost ?: return
        val hostEncodingSupported =
            librarySessionViewModel.state.content?.sync?.capabilities?.hdrEncodingSupported
        librarySessionViewModel.updateHdr(
            host.uuid,
            currentHdrState(hostEncodingSupported),
        )
    }

    private fun updateLibraryFromRaw(rawAppList: String?) {
        val host = libraryHost ?: return
        libraryHostCoordinator.updateAppList(host, rawAppList)
    }

    private fun changeLibrarySortMode(sortMode: HostSortMode) {
        val host = libraryHost ?: return
        librarySortMode = sortMode
        LigasePreferences.setLibrarySortMode(this, host.uuid, sortMode)
    }

    private fun changeLibraryLayoutMode(layoutMode: LibraryLayoutMode) {
        libraryLayoutMode = layoutMode
        LigasePreferences.setLibraryLayoutMode(this, layoutMode)
    }

    private fun submitManualLibraryOrder(orderedAppUuids: List<String>) {
        val host = libraryHost ?: return
        if (!requireOperate(host)) return
        val snapshot = librarySessionViewModel.state.content?.sync ?: return
        libraryManualSortCoordinator.submit(host, snapshot, orderedAppUuids)
    }

    private fun launchLibraryItem(item: LigaseLibraryItem) {
        val host = libraryHost ?: return
        val snapshot = librarySessionViewModel.state.content?.sync ?: return
        val inputState = checkNotNull(inputSelectionState)
        val inputMode = inputSelectionCoordinator.effectiveMode()
        val result = streamLaunchCoordinator.plan(
            StreamLaunchRequest(
                item = item,
                snapshot = snapshot,
                connectivity = librarySessionViewModel.state.connectivity,
                inputMode = inputMode,
                selectedGamepadKey = inputState.selectedGamepadKey,
                selectedKeyboardKey = inputState.selectedKeyboardKey,
                selectedMouseKey = inputState.selectedMouseKey,
                connectedInputDevices = inputState.devices,
                selectedTouchLayoutId = inputState.selectedTouchLayoutId,
                availableTouchLayoutIds =
                    inputState.touchLayouts.mapTo(mutableSetOf()) { it.id },
                overlayMode = inputState.overlayMode,
                preferVirtualDisplay =
                    PreferenceConfiguration.readPreferences(this).useVirtualDisplay,
            ),
        )
        when (result) {
            is StreamLaunchPlanningResult.Blocked -> {
                when (result.reason) {
                    StreamLaunchBlockReason.OFFLINE -> toast(R.string.ligase_host_offline)
                    StreamLaunchBlockReason.PERMISSION_DENIED -> {
                        toast(R.string.ligase_observe_mode_action_blocked)
                        managerBinder?.invalidateStateForComputer(host.uuid)
                    }
                    StreamLaunchBlockReason.GAMEPAD_DISCONNECTED -> {
                        toast(R.string.ligase_connect_selected_controller)
                        currentPage = LigasePage.INPUT
                    }
                    StreamLaunchBlockReason.KEYBOARD_MOUSE_DISCONNECTED -> {
                        toast(R.string.ligase_connect_selected_keyboard_mouse)
                        currentPage = LigasePage.INPUT
                    }
                    StreamLaunchBlockReason.TOUCH_LAYOUT_UNAVAILABLE -> {
                        toast(R.string.ligase_touch_layout_reselect_before_stream)
                        currentPage = LigasePage.INPUT
                    }
                    StreamLaunchBlockReason.MANAGER_UNAVAILABLE ->
                        toast(R.string.error_manager_not_running)
                    StreamLaunchBlockReason.MISSING_APP_MAPPING,
                    StreamLaunchBlockReason.APP_IDENTITY_MISMATCH,
                    -> Unit
                }
            }
            is StreamLaunchPlanningResult.Ready -> {
                val launch = Runnable {
                    when (streamLaunchCoordinator.launch(result.plan)) {
                        StreamLaunchExecutionResult.STARTED,
                        StreamLaunchExecutionResult.ALREADY_LAUNCHED,
                        -> Unit
                        StreamLaunchExecutionResult.STALE_HOST,
                        StreamLaunchExecutionResult.OFFLINE,
                        -> toast(R.string.ligase_host_offline)
                        StreamLaunchExecutionResult.PERMISSION_DENIED ->
                            toast(R.string.ligase_observe_mode_action_blocked)
                        StreamLaunchExecutionResult.MANAGER_UNAVAILABLE ->
                            toast(R.string.error_manager_not_running)
                    }
                }
                when (result.plan.confirmation) {
                    StreamLaunchConfirmation.QUIT_RUNNING_GAME ->
                        UiHelper.displayQuitConfirmationDialog(this, launch, null)
                    StreamLaunchConfirmation.VIRTUAL_DISPLAY_UNAVAILABLE ->
                        UiHelper.displayVdisplayConfirmationDialog(this, host, launch, null)
                    StreamLaunchConfirmation.NONE -> launch.run()
                }
            }
        }
    }

    private fun showLibraryItemSettings(item: LigaseLibraryItem) {
        val host = libraryHost ?: return
        if (!requireOperate(host)) return
        val snapshot = librarySessionViewModel.state.content?.sync ?: return
        val appUuid = item.hostAppUuid ?: return
        val override = snapshot.streaming.overrideFor(appUuid)
        StreamingResolutionDialogFragment.show(
            supportFragmentManager,
            StreamingResolutionEditorRequest(
                target = StreamingResolutionTarget.APP,
                title = item.name,
                hostKey = host.uuid.normalizedHostKey(),
                baseRevision = snapshot.streaming.revision,
                initialResolution = override ?: snapshot.streaming.globalResolution,
                appUuid = appUuid,
                useGlobal = override == null,
            ),
            themeMode,
        )
    }

    private fun showGlobalResolutionSettings() {
        val host = libraryHost ?: return
        if (!requireOperate(host)) return
        val snapshot = librarySessionViewModel.state.content?.sync ?: return
        StreamingResolutionDialogFragment.show(
            supportFragmentManager,
            StreamingResolutionEditorRequest(
                target = StreamingResolutionTarget.GLOBAL,
                title = getString(R.string.ligase_global_resolution),
                hostKey = host.uuid.normalizedHostKey(),
                baseRevision = snapshot.streaming.revision,
                initialResolution = snapshot.streaming.globalResolution,
            ),
            themeMode,
        )
    }

    private fun registerStreamingResolutionResult() {
        supportFragmentManager.setFragmentResultListener(
            StreamingResolutionDialogFragment.RESULT_KEY,
            this,
        ) { _, bundle ->
            StreamingResolutionDialogFragment.resultFrom(bundle)
                ?.let(::submitStreamingResolution)
        }
    }

    private fun submitStreamingResolution(result: StreamingResolutionEditResult) {
        val host = libraryHost
        val snapshot = librarySessionViewModel.state.content?.sync
        when (
            streamingResolutionSubmissionDecision(
                request = result.request,
                selectedHostKey = host?.uuid,
                connectivity = librarySessionViewModel.state.connectivity,
                accessMode = host?.ligaseClientAccessMode,
                currentRevision = snapshot?.streaming?.revision,
            )
        ) {
            StreamingResolutionSubmissionDecision.STALE_HOST -> {
                toast(R.string.ligase_streaming_settings_host_changed)
                return
            }
            StreamingResolutionSubmissionDecision.OFFLINE -> {
                toast(R.string.ligase_host_offline)
                return
            }
            StreamingResolutionSubmissionDecision.PERMISSION_DENIED -> {
                toast(R.string.ligase_observe_mode_action_blocked)
                host?.let { managerBinder?.invalidateStateForComputer(it.uuid) }
                return
            }
            StreamingResolutionSubmissionDecision.CONTENT_UNAVAILABLE -> {
                toast(R.string.ligase_streaming_settings_unavailable)
                return
            }
            StreamingResolutionSubmissionDecision.REVISION_CONFLICT -> {
                fetchLibrarySync(force = true)
                toast(R.string.ligase_sync_revision_conflict)
                return
            }
            StreamingResolutionSubmissionDecision.READY -> Unit
        }
        checkNotNull(host)
        checkNotNull(snapshot)
        val accepted = when (result.request.target) {
            StreamingResolutionTarget.GLOBAL -> {
                val resolution = result.resolution ?: return
                libraryStreamingSettingsCoordinator.updateGlobal(host, snapshot, resolution)
            }
            StreamingResolutionTarget.APP -> {
                val appUuid = result.request.appUuid ?: return
                libraryStreamingSettingsCoordinator.updateApp(
                    host,
                    snapshot,
                    appUuid,
                    result.resolution,
                )
            }
        }
        if (!accepted) {
            toast(R.string.ligase_streaming_settings_in_flight)
        }
    }

    private fun showAddHostDialog() {
        LigaseAddHostDialog.show(this, ::addHost)
    }

    private fun requireOperate(host: ComputerDetails): Boolean {
        if (
            LibraryOperationGate.canOperate(
                librarySessionViewModel.state.connectivity,
                host.ligaseClientAccessMode,
            )
        ) {
            return true
        }
        if (librarySessionViewModel.state.connectivity != LibraryConnectivity.ONLINE) {
            toast(R.string.ligase_host_offline)
            return false
        }
        toast(R.string.ligase_observe_mode_action_blocked)
        managerBinder?.invalidateStateForComputer(host.uuid)
        return false
    }

    private fun addHost(endpoint: LigaseEndpoint) {
        if (
            hostEndpointCoordinator.add(endpoint) { result ->
                when (result) {
                    is HostAddResult.Added -> {
                        toast(R.string.addpc_success)
                        onHostClicked(result.details)
                    }
                    HostAddResult.ManagerUnavailable -> toast(R.string.error_manager_not_running)
                    HostAddResult.Failed -> toast(R.string.addpc_fail)
                }
            }
        ) {
            toast(R.string.msg_add_pc)
        }
    }

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun launchLayoutV2Editor(draftId: String) {
        when (val result = layoutV2EditorWorkspaceViewModel.checkpointAndRelease(draftId)) {
            is LayoutV2EditorHandoffResult.LaunchReady -> {
                layoutV2EditorLaunchInFlight = true
                startActivity(LayoutV2BlackEditorActivity.createIntent(this, result.draftId))
            }
            is LayoutV2EditorHandoffResult.Rejected -> toast(
                when (result.issue) {
                    LayoutV2EditorHandoffIssue.INVALID_DRAFT_ID ->
                        R.string.ligase_layout_error_invalid_id
                    LayoutV2EditorHandoffIssue.CHECKPOINT_FAILED ->
                        R.string.ligase_layout_error_save
                    LayoutV2EditorHandoffIssue.QUARANTINED ->
                        R.string.ligase_layout_error_malformed
                    LayoutV2EditorHandoffIssue.MISSING ->
                        R.string.ligase_layout_error_not_found
                    LayoutV2EditorHandoffIssue.NO_ACTIVE_DRAFT,
                    LayoutV2EditorHandoffIssue.DRAFT_ID_MISMATCH,
                    LayoutV2EditorHandoffIssue.ALREADY_OWNED,
                    LayoutV2EditorHandoffIssue.CLOSED ->
                        R.string.ligase_layout_error_no_draft
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (
            layoutV2EditorLaunchInFlight &&
            ::layoutV2EditorWorkspaceViewModel.isInitialized
        ) {
            layoutV2EditorLaunchInFlight = false
            layoutV2EditorWorkspaceViewModel.refreshAfterEditorReturn()
        }
        foreground = true
        refreshLocalHdrCapabilities()
        streamBitrateState.refresh(PreferenceConfiguration.getDefaultBitrate(this))
        inputSelectionCoordinator.refreshLayouts()
        inputSelectionCoordinator.start()
        hostEndpointCoordinator.onForeground(::handleHostUpdate)
        startAppListUpdates()
        UiHelper.showDecoderCrashDialog(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (BuildConfig.DEBUG && intent.action == ACTION_RECREATE_FOR_TEST) {
            recreate()
        }
    }

    override fun onPause() {
        foreground = false
        inputSelectionCoordinator.stop()
        stopAppListUpdates()
        hostEndpointCoordinator.onBackground()
        super.onPause()
    }

    override fun onStop() {
        if (::layoutV2EditorWorkspaceViewModel.isInitialized) {
            layoutV2EditorWorkspaceViewModel.onStop()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (::inputSelectionCoordinator.isInitialized) {
            inputSelectionCoordinator.stop()
        }
        if (::hostEndpointCoordinator.isInitialized) {
            hostEndpointCoordinator.close()
        }
        if (::libraryManualSortCoordinator.isInitialized) {
            libraryManualSortCoordinator.close()
        }
        if (::layoutCatalogV2SourceRegistry.isInitialized) {
            layoutCatalogV2SourceRegistry.close()
        }
        if (::layoutV2EditorWorkspaceViewModel.isInitialized) {
            layoutV2CatalogRegistrationAttachment?.let(
                layoutV2EditorWorkspaceViewModel::detachCatalogRegistration,
            )
            layoutV2CatalogRegistrationAttachment = null
        }
        stopAppListUpdates()
        disposeLibraryAssets()
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        managerBinder = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PAGE, currentPage.name)
        outState.putString(STATE_LIBRARY_HOST_UUID, libraryHost?.uuid ?: pendingLibraryHostUuid)
        super.onSaveInstanceState(outState)
    }

    companion object {
        internal const val ACTION_RECREATE_FOR_TEST =
            "com.litchicore.ligase.action.RECREATE_FOR_TEST"
        private const val STATE_PAGE = "ligase_page"
        private const val STATE_LIBRARY_HOST_UUID = "ligase_library_host_uuid"
        private const val EXIT_INTERVAL_MS = 2_000L
    }
}

private fun String.normalizedHostKey(): String = trim().lowercase(Locale.ROOT)
