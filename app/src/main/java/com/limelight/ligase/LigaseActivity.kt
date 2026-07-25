package com.limelight.ligase

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Bundle
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.View
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.limelight.BuildConfig
import com.limelight.LimeLog
import com.limelight.R
import com.limelight.TouchKitLayoutPreviewActivity
import com.limelight.binding.PlatformBinding
import com.limelight.computers.ComputerManagerListener
import com.limelight.computers.ComputerManagerService
import com.limelight.grid.assets.CachedAppAssetLoader
import com.limelight.grid.assets.DiskAssetLoader
import com.limelight.ligase.feature.library.application.LibraryHostCoordinator
import com.limelight.ligase.feature.library.application.LibraryStreamingSettingsCoordinator
import com.limelight.ligase.feature.library.application.LibraryStreamingSettingsResult
import com.limelight.ligase.feature.library.data.dto.LigaseResolutionDto
import com.limelight.ligase.feature.library.data.dto.LigaseSyncSnapshotDto
import com.limelight.ligase.feature.library.data.repository.LigaseSyncRepository
import com.limelight.ligase.feature.library.domain.HostSortMode
import com.limelight.ligase.feature.library.domain.LibraryHdrState
import com.limelight.ligase.feature.library.domain.LibraryHdrStateResolver
import com.limelight.ligase.feature.library.domain.LibraryLayoutMode
import com.limelight.ligase.feature.library.domain.LibrarySyncAutoLoadPolicy
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.feature.library.infrastructure.LegacyGameStreamLibraryTransport
import com.limelight.ligase.feature.layout.editor.LayoutWorkspaceViewModel
import com.limelight.ligase.feature.pairing.application.HostPairingCoordinator
import com.limelight.ligase.feature.pairing.application.HostPairingMode
import com.limelight.ligase.feature.pairing.infrastructure.LegacyPairingResult
import com.limelight.ligase.feature.pairing.infrastructure.LegacyPairingTransport
import com.limelight.ligase.library.LibraryConnectivity
import com.limelight.ligase.library.LibrarySessionError
import com.limelight.ligase.library.LibrarySessionViewModel
import com.limelight.ligase.library.LibraryOperationGate
import com.limelight.ligase.library.ManualLibrarySortAction
import com.limelight.ligase.library.ManualLibrarySortResult
import com.limelight.ligase.endpoint.LigaseAddHostDialog
import com.limelight.ligase.endpoint.LigaseEndpoint
import com.limelight.ligase.input.LigaseInputCategory
import com.limelight.ligase.input.LigaseInputDevice
import com.limelight.ligase.input.LigaseInputDeviceRepository
import com.limelight.ligase.input.LigaseInputLaunchPolicy
import com.limelight.ligase.input.LigaseTouchLayout
import com.limelight.ligase.input.LigaseTouchLayoutRepository
import com.limelight.ligase.input.LigaseTouchOverlayMode
import com.limelight.ligase.pairing.AttendedPairingViewModel
import com.limelight.ligase.pairing.LigaseAccessUiPolicy
import com.limelight.ligase.pairing.LigaseClientAccessMode
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager
import com.limelight.nvstream.http.PairingManager.PairState
import com.limelight.nvstream.wol.WakeOnLanSender
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.preferences.StreamSettings
import com.limelight.utils.ServerHelper
import com.limelight.utils.UiHelper
import java.io.IOException

class LigaseActivity : AppCompatActivity() {
    private var currentPage by mutableStateOf(LigasePage.HOME)
    private var onboarding by mutableStateOf(false)
    private var selectedInput by mutableStateOf<InputDeviceMode?>(null)
    private val inputDevices = mutableStateListOf<LigaseInputDevice>()
    private val touchLayouts = mutableStateListOf<LigaseTouchLayout>()
    private var selectedGamepadKey by mutableStateOf<String?>(null)
    private var selectedKeyboardKey by mutableStateOf<String?>(null)
    private var selectedMouseKey by mutableStateOf<String?>(null)
    private var selectedTouchLayoutId by mutableStateOf<String?>(null)
    private var touchOverlayMode by mutableStateOf(LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD)
    private var themeMode by mutableStateOf(LigaseThemeMode.SYSTEM)
    private var languageMode by mutableStateOf(LigaseLanguageMode.SYSTEM)
    private val hosts = mutableStateListOf<ComputerDetails>()
    private var libraryHost by mutableStateOf<ComputerDetails?>(null)
    private var libraryAccessMode by mutableStateOf<String?>(null)
    private var displayHdrSupported: Boolean? = null
    private var decoderHdrSupported: Boolean? = null
    private var userHdrEnabled: Boolean? = null
    private var libraryRunningAppId by mutableStateOf(0)
    private var librarySortMode by mutableStateOf(HostSortMode.NAME_ASCENDING)
    private var libraryLayoutMode by mutableStateOf(LibraryLayoutMode.LIST)
    private var libraryAssetLoader by mutableStateOf<CachedAppAssetLoader?>(null)
    private var pendingLibraryHostUuid: String? = null
    private val syncRepository = LigaseSyncRepository()
    private val manualSortAction = ManualLibrarySortAction()
    private lateinit var inputDeviceRepository: LigaseInputDeviceRepository
    private lateinit var touchLayoutRepository: LigaseTouchLayoutRepository
    private lateinit var pairingViewModel: AttendedPairingViewModel
    private lateinit var hostPairingCoordinator: HostPairingCoordinator
    private lateinit var librarySessionViewModel: LibrarySessionViewModel
    private lateinit var libraryHostCoordinator: LibraryHostCoordinator
    private lateinit var libraryStreamingSettingsCoordinator:
        LibraryStreamingSettingsCoordinator
    private lateinit var layoutWorkspaceViewModel: LayoutWorkspaceViewModel

    private var managerBinder: ComputerManagerService.ComputerManagerBinder? = null
    private var serviceBound = false
    private var polling = false
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
                    startComputerUpdates()
                    if (currentPage == LigasePage.HOME && restoredHost != null) {
                        openLibrary(restoredHost)
                    }
                }
                PlatformBinding.getCryptoProvider(this@LigaseActivity).clientCertificate
            }.start()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            managerBinder = null
            polling = false
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
        displayHdrSupported = detectDisplayHdrSupport()
        decoderHdrSupported = detectHdrDecoderSupport()
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        userHdrEnabled = PreferenceConfiguration.readPreferences(this).enableHdr

        onboarding = !LigasePreferences.hasInputDeviceMode(this)
        selectedInput = if (onboarding) null else LigasePreferences.getInputDeviceMode(this)
        selectedGamepadKey = LigasePreferences.getSelectedInputDevice(
            this,
            LigaseInputCategory.GAMEPAD,
        )
        selectedKeyboardKey = LigasePreferences.getSelectedInputDevice(
            this,
            LigaseInputCategory.KEYBOARD,
        )
        selectedMouseKey = LigasePreferences.getSelectedInputDevice(
            this,
            LigaseInputCategory.MOUSE,
        )
        touchOverlayMode = LigasePreferences.getTouchOverlayMode(this)
        inputDeviceRepository = LigaseInputDeviceRepository(this) { devices ->
            inputDevices.clear()
            inputDevices.addAll(devices)
        }
        touchLayoutRepository = LigaseTouchLayoutRepository(this)
        reloadTouchLayouts()
        themeMode = LigasePreferences.getThemeMode(this)
        languageMode = LigasePreferences.getLanguageMode(this)
        libraryLayoutMode = LigasePreferences.getLibraryLayoutMode(this)
        currentPage = savedInstanceState?.getString(STATE_PAGE)
            ?.let { saved -> LigasePage.entries.firstOrNull { it.name == saved } }
            ?: if (onboarding) LigasePage.INPUT else LigasePage.HOME
        pendingLibraryHostUuid = savedInstanceState?.getString(STATE_LIBRARY_HOST_UUID)
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
        layoutWorkspaceViewModel = ViewModelProvider(this)[LayoutWorkspaceViewModel::class.java]

        setContent {
            val libraryState = librarySessionViewModel.state
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
                layoutEditorState = layoutWorkspaceViewModel.editorState,
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
                    reloadTouchLayouts()
                },
                onLayoutSelect = { layoutId ->
                    if (layoutWorkspaceViewModel.selectGlobal(layoutId)) {
                        selectedTouchLayoutId = layoutId
                        reloadTouchLayouts()
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
                        reloadTouchLayouts()
                    }
                },
                onLayoutDiscard = layoutWorkspaceViewModel::discardDraft,
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
        val mode = selectedInput ?: return
        LigasePreferences.setInputDeviceMode(this, mode)
        onboarding = false
        currentPage = LigasePage.HOME
    }

    private fun selectInput(mode: InputDeviceMode) {
        selectedInput = mode
        if (!onboarding) {
            LigasePreferences.setInputDeviceMode(this, mode)
        }
    }

    private fun selectInputDevice(category: LigaseInputCategory, stableKey: String) {
        LigasePreferences.setSelectedInputDevice(this, category, stableKey)
        when (category) {
            LigaseInputCategory.GAMEPAD -> selectedGamepadKey = stableKey
            LigaseInputCategory.KEYBOARD -> selectedKeyboardKey = stableKey
            LigaseInputCategory.MOUSE -> selectedMouseKey = stableKey
        }
    }

    private fun reloadTouchLayouts() {
        val available = touchLayoutRepository.layouts()
        touchLayouts.clear()
        touchLayouts.addAll(available)
        selectedTouchLayoutId = touchLayoutRepository.initializeSelection(available)
    }

    private fun selectTouchLayout(layoutId: String) {
        if (touchLayoutRepository.select(layoutId, touchLayouts)) {
            selectedTouchLayoutId = layoutId
        } else {
            toast(R.string.ligase_touch_layout_missing_short)
        }
    }

    private fun selectTouchOverlayMode(mode: LigaseTouchOverlayMode) {
        touchOverlayMode = mode
        LigasePreferences.setTouchOverlayMode(this, mode)
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
                if (!onboarding && currentPage != LigasePage.HOME) {
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
        val binder = managerBinder ?: return
        if (polling || !foreground) return
        binder.startPolling(ComputerManagerListener { details ->
            runOnUiThread {
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
        })
        polling = true
    }

    private fun stopComputerUpdates(wait: Boolean) {
        val binder = managerBinder ?: return
        if (!polling) return
        binder.stopPolling()
        if (wait) binder.waitForPollingStopped()
        polling = false
    }

    private fun onHostClicked(host: ComputerDetails) {
        when {
            host.state == ComputerDetails.State.UNKNOWN -> Unit
            host.state == ComputerDetails.State.OFFLINE -> wakeHost(host)
            host.pairState != PairState.PAIRED -> pairHost(host)
            else -> openLibrary(host)
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
        if (host.macAddress == null) {
            toast(R.string.wol_no_mac)
            return
        }
        Thread {
            val message = try {
                WakeOnLanSender.sendWolPacket(host)
                R.string.wol_waking_msg
            } catch (_: IOException) {
                R.string.wol_fail
            }
            runOnUiThread { toast(message) }
        }.start()
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
        hosts.firstOrNull {
            it.state == ComputerDetails.State.ONLINE && it.pairState == PairState.PAIRED
        }?.let(::openLibrary)
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
        managerBinder?.removeComputer(host)
        DiskAssetLoader(this).deleteAssetsForComputer(host.uuid)
        hosts.removeAll { it.uuid.equals(host.uuid, ignoreCase = true) }
        if (wasSelected) selectDefaultHostIfNeeded()
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
            displaySupported = displayHdrSupported,
            decoderSupported = decoderHdrSupported,
            userEnabled = userHdrEnabled,
        )

    private fun refreshLocalHdrCapabilities() {
        displayHdrSupported = detectDisplayHdrSupport()
        decoderHdrSupported = detectHdrDecoderSupport()
        userHdrEnabled = PreferenceConfiguration.readPreferences(this).enableHdr
        val host = libraryHost ?: return
        val hostEncodingSupported =
            librarySessionViewModel.state.content?.sync?.capabilities?.hdrEncodingSupported
        librarySessionViewModel.updateHdr(
            host.uuid,
            currentHdrState(hostEncodingSupported),
        )
    }

    private fun detectDisplayHdrSupport(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val capabilities = windowManager.defaultDisplay.hdrCapabilities ?: return false
        return capabilities.supportedHdrTypes.any {
            it == android.view.Display.HdrCapabilities.HDR_TYPE_HDR10
        }
    }

    private fun detectHdrDecoderSupport(): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        return try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .asSequence()
                .filterNot(MediaCodecInfo::isEncoder)
                .any { codec ->
                    codec.supportedTypes.any { type ->
                        when {
                            type.equals("video/hevc", ignoreCase = true) ->
                                codec.getCapabilitiesForType(type).profileLevels.any {
                                    it.profile ==
                                        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
                                }
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                                type.equals("video/av01", ignoreCase = true) ->
                                codec.getCapabilitiesForType(type).profileLevels.any {
                                    it.profile ==
                                        MediaCodecInfo.CodecProfileLevel.AV1ProfileMain10HDR10
                                }
                            else -> false
                        }
                    }
                }
        } catch (error: RuntimeException) {
            LimeLog.warning("Unable to inspect local HDR decoder capability: $error")
            null
        }
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
        val ticket = librarySessionViewModel.beginManualSort(host.uuid) ?: return
        Thread {
            val result = manualSortAction.submit(
                snapshot = snapshot,
                orderedAppUuids = orderedAppUuids,
                writer = { request ->
                    syncRepository.updateManualOrder(
                        libraryHostCoordinator.createHttp(host),
                        request,
                    )
                },
            )
            runOnUiThread {
                val acceptedResult =
                    if (
                        result is ManualLibrarySortResult.Success &&
                        !librarySessionViewModel.applyManualOrder(host.uuid, result.response)
                    ) {
                        ManualLibrarySortResult.Failed
                    } else {
                        result
                    }
                if (acceptedResult is ManualLibrarySortResult.Success) {
                    librarySortMode = HostSortMode.MANUAL
                    LigasePreferences.setLibrarySortMode(
                        this,
                        host.uuid,
                        HostSortMode.MANUAL,
                    )
                }
                if (!librarySessionViewModel.acceptManualSort(ticket, acceptedResult)) {
                    return@runOnUiThread
                }
                when (acceptedResult) {
                    is ManualLibrarySortResult.RevisionConflict ->
                        fetchLibrarySync(force = true)
                    ManualLibrarySortResult.PermissionDenied ->
                        managerBinder?.invalidateStateForComputer(host.uuid)
                    else -> Unit
                }
            }
        }.start()
    }

    private fun launchLibraryItem(item: LigaseLibraryItem) {
        val host = libraryHost ?: return
        if (!requireOperate(host)) return
        val snapshot = librarySessionViewModel.state.content?.sync ?: return
        val app = item.launchApp ?: return
        val appUuid = item.hostAppUuid ?: return
        val inputMode = selectedInput ?: LigasePreferences.getInputDeviceMode(this)
        val externalInputReady = when (inputMode) {
            InputDeviceMode.GAMEPAD -> selectedGamepadKey != null &&
                inputDevices.any {
                    it.category == LigaseInputCategory.GAMEPAD &&
                        it.stableKey == selectedGamepadKey
                }
            InputDeviceMode.KEYBOARD_MOUSE -> inputDevices.any {
                (
                    it.category == LigaseInputCategory.KEYBOARD &&
                        it.stableKey == selectedKeyboardKey
                    ) ||
                    (
                        it.category == LigaseInputCategory.MOUSE &&
                            it.stableKey == selectedMouseKey
                        )
            }
            InputDeviceMode.TOUCH -> true
        }
        if (!externalInputReady) {
            toast(
                if (inputMode == InputDeviceMode.GAMEPAD) {
                    R.string.ligase_connect_selected_controller
                } else {
                    R.string.ligase_connect_selected_keyboard_mouse
                },
            )
            currentPage = LigasePage.INPUT
            return
        }
        if (
            LigaseInputLaunchPolicy.resolve(
                mode = inputMode,
                selectedTouchLayoutId = selectedTouchLayoutId,
                availableTouchLayoutIds = touchLayouts.mapTo(mutableSetOf()) { it.id },
                overlayMode = touchOverlayMode,
            ) == null
        ) {
            toast(R.string.ligase_touch_layout_reselect_before_stream)
            currentPage = LigasePage.INPUT
            return
        }
        val binder = managerBinder
        if (binder == null) {
            toast(R.string.error_manager_not_running)
            return
        }

        val preference = PreferenceConfiguration.readPreferences(this)
        val withVirtualDisplay = if (item.isSystem) false else preference.useVirtualDisplay
        val resolution = snapshot.streaming.resolutionFor(appUuid)
        val launch = Runnable {
            ServerHelper.doStart(
                this,
                app,
                host,
                binder,
                withVirtualDisplay,
                resolution.width,
                resolution.height,
                snapshot.capabilities.hdrEncodingSupported,
                true,
            )
        }

        if (host.runningGameId != 0 && host.runningGameId != app.appId) {
            UiHelper.displayQuitConfirmationDialog(this, launch, null)
        } else if (
            withVirtualDisplay &&
            !(host.vDisplaySupported && host.vDisplayDriverReady)
        ) {
            UiHelper.displayVdisplayConfirmationDialog(this, host, launch, null)
        } else {
            launch.run()
        }
    }

    private fun showLibraryItemSettings(item: LigaseLibraryItem) {
        val host = libraryHost ?: return
        if (!requireOperate(host)) return
        val snapshot = librarySessionViewModel.state.content?.sync ?: return
        val appUuid = item.hostAppUuid ?: return
        val override = snapshot.streaming.overrideFor(appUuid)
        showResolutionEditor(
            title = item.name,
            initial = override ?: snapshot.streaming.globalResolution,
            allowUseGlobal = true,
            useGlobal = override == null,
        ) { resolution ->
            updateAppResolution(host, snapshot, appUuid, resolution)
        }
    }

    private fun showGlobalResolutionSettings() {
        val host = libraryHost ?: return
        if (!requireOperate(host)) return
        val snapshot = librarySessionViewModel.state.content?.sync ?: return
        showResolutionEditor(
            title = getString(R.string.ligase_global_resolution),
            initial = snapshot.streaming.globalResolution,
            allowUseGlobal = false,
            useGlobal = false,
        ) { resolution ->
            if (resolution != null) {
                updateGlobalResolution(host, snapshot, resolution)
            }
        }
    }

    private fun showResolutionEditor(
        title: String,
        initial: LigaseResolutionDto,
        allowUseGlobal: Boolean,
        useGlobal: Boolean,
        onSave: (LigaseResolutionDto?) -> Unit,
    ) {
        val density = resources.displayMetrics.density
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val horizontal = (24 * density).toInt()
            setPadding(horizontal, 0, horizontal, 0)
        }
        val choiceGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            visibility = if (allowUseGlobal) View.VISIBLE else View.GONE
        }
        val globalChoice = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.ligase_resolution_use_global)
        }
        val customChoice = RadioButton(this).apply {
            id = View.generateViewId()
            text = getString(R.string.ligase_resolution_custom)
        }
        choiceGroup.addView(globalChoice)
        choiceGroup.addView(customChoice)
        container.addView(choiceGroup)

        fun numberInput(label: Int, value: Int): Pair<TextInputLayout, TextInputEditText> {
            val layout = TextInputLayout(this).apply {
                hint = getString(label)
            }
            val input = TextInputEditText(layout.context).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setSingleLine(true)
                setText(value.toString())
            }
            layout.addView(input)
            container.addView(layout)
            return layout to input
        }

        val (widthLayout, widthInput) =
            numberInput(R.string.ligase_resolution_width, initial.width)
        val (heightLayout, heightInput) =
            numberInput(R.string.ligase_resolution_height, initial.height)

        fun updateInputState() {
            val enabled = !allowUseGlobal || customChoice.isChecked
            widthLayout.isEnabled = enabled
            heightLayout.isEnabled = enabled
        }
        if (allowUseGlobal) {
            choiceGroup.check(if (useGlobal) globalChoice.id else customChoice.id)
            choiceGroup.setOnCheckedChangeListener { _, _ -> updateInputState() }
        }
        updateInputState()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if (allowUseGlobal && globalChoice.isChecked) {
                dialog.dismiss()
                onSave(null)
                return@setOnClickListener
            }
            val width = widthInput.text?.toString()?.toIntOrNull()
            val height = heightInput.text?.toString()?.toIntOrNull()
            val resolution = if (width != null && height != null) {
                LigaseResolutionDto(width, height)
            } else {
                null
            }
            if (resolution == null || !resolution.isValid()) {
                val error = getString(R.string.ligase_resolution_invalid)
                widthLayout.error = error
                heightLayout.error = error
                return@setOnClickListener
            }
            dialog.dismiss()
            onSave(resolution)
        }
    }

    private fun updateGlobalResolution(
        host: ComputerDetails,
        snapshot: LigaseSyncSnapshotDto,
        resolution: LigaseResolutionDto,
    ) {
        libraryStreamingSettingsCoordinator.updateGlobal(host, snapshot, resolution)
    }

    private fun updateAppResolution(
        host: ComputerDetails,
        snapshot: LigaseSyncSnapshotDto,
        appUuid: String,
        resolution: LigaseResolutionDto?,
    ) {
        libraryStreamingSettingsCoordinator.updateApp(
            host,
            snapshot,
            appUuid,
            resolution,
        )
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
        val binder = managerBinder
        if (binder == null) {
            toast(R.string.error_manager_not_running)
            return
        }
        toast(R.string.msg_add_pc)
        Thread {
            val details = ComputerDetails().apply {
                endpoints = listOf(endpoint)
                manualAddress = endpoint.toLegacyAddressTuple()
            }
            val success = try {
                binder.addComputerBlocking(details)
            } catch (_: InterruptedException) {
                false
            } catch (_: IllegalArgumentException) {
                false
            }
            runOnUiThread {
                if (success) {
                    toast(R.string.addpc_success)
                    onHostClicked(details)
                } else {
                    toast(R.string.addpc_fail)
                }
            }
        }.start()
    }

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        refreshLocalHdrCapabilities()
        reloadTouchLayouts()
        inputDeviceRepository.start()
        startComputerUpdates()
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
        inputDeviceRepository.stop()
        stopAppListUpdates()
        stopComputerUpdates(false)
        super.onPause()
    }

    override fun onDestroy() {
        if (::inputDeviceRepository.isInitialized) {
            inputDeviceRepository.stop()
        }
        stopComputerUpdates(false)
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
