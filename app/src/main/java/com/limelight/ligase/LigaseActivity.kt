package com.limelight.ligase

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.limelight.R
import com.limelight.binding.PlatformBinding
import com.limelight.computers.ComputerManagerListener
import com.limelight.computers.ComputerManagerService
import com.limelight.grid.assets.CachedAppAssetLoader
import com.limelight.grid.assets.DiskAssetLoader
import com.limelight.grid.assets.MemoryAssetLoader
import com.limelight.grid.assets.NetworkAssetLoader
import com.limelight.ligase.library.HostSortMode
import com.limelight.ligase.library.LigaseLibraryAdapter
import com.limelight.ligase.library.LigaseLibraryItem
import com.limelight.ligase.library.LibraryLayoutMode
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.nvstream.http.PairingManager.PairState
import com.limelight.nvstream.wol.WakeOnLanSender
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.preferences.StreamSettings
import com.limelight.utils.CacheHelper
import com.limelight.utils.ServerHelper
import com.limelight.utils.UiHelper
import org.xmlpull.v1.XmlPullParserException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.StringReader
import java.net.UnknownHostException

class LigaseActivity : AppCompatActivity() {
    private var currentPage by mutableStateOf(LigasePage.HOME)
    private var onboarding by mutableStateOf(false)
    private var selectedInput by mutableStateOf<InputDeviceMode?>(null)
    private var themeMode by mutableStateOf(LigaseThemeMode.SYSTEM)
    private var languageMode by mutableStateOf(LigaseLanguageMode.SYSTEM)
    private val hosts = mutableStateListOf<ComputerDetails>()
    private val libraryItems = mutableStateListOf<LigaseLibraryItem>()
    private var libraryHost by mutableStateOf<ComputerDetails?>(null)
    private var libraryLoading by mutableStateOf(false)
    private var libraryRunningAppId by mutableStateOf(0)
    private var librarySortMode by mutableStateOf(HostSortMode.NAME_ASCENDING)
    private var libraryLayoutMode by mutableStateOf(LibraryLayoutMode.LIST)
    private var libraryAssetLoader by mutableStateOf<CachedAppAssetLoader?>(null)
    private var lastLibraryRawAppList: String? = null
    private var pendingLibraryHostUuid: String? = null

    private var managerBinder: ComputerManagerService.ComputerManagerBinder? = null
    private var appListPoller: ComputerManagerService.ApplistPoller? = null
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
            appListPoller = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiHelper.setLocale(this)
        enableEdgeToEdge()
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

        onboarding = !LigasePreferences.hasInputDeviceMode(this)
        selectedInput = if (onboarding) null else LigasePreferences.getInputDeviceMode(this)
        themeMode = LigasePreferences.getThemeMode(this)
        languageMode = LigasePreferences.getLanguageMode(this)
        libraryLayoutMode = LigasePreferences.getLibraryLayoutMode(this)
        currentPage = savedInstanceState?.getString(STATE_PAGE)
            ?.let { saved -> LigasePage.entries.firstOrNull { it.name == saved } }
            ?: if (onboarding) LigasePage.INPUT else LigasePage.HOME
        pendingLibraryHostUuid = savedInstanceState?.getString(STATE_LIBRARY_HOST_UUID)

        setContent {
            LigaseRoot(
                themeMode = themeMode,
                onboarding = onboarding,
                currentPage = currentPage,
                selectedInput = selectedInput,
                languageMode = languageMode,
                hosts = hosts,
                libraryHost = libraryHost,
                libraryItems = libraryItems,
                libraryLoading = libraryLoading,
                libraryRunningAppId = libraryRunningAppId,
                librarySortMode = librarySortMode,
                libraryLayoutMode = libraryLayoutMode,
                libraryAssetLoader = libraryAssetLoader,
                onPageSelected = ::selectPage,
                onInputSelected = { selectedInput = it },
                onInputConfirmed = ::confirmInput,
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
        if (page != LigasePage.HOME) stopAppListUpdates()
        currentPage = page
        if (page == LigasePage.HOME) {
            startAppListUpdates()
            selectDefaultHostIfNeeded()
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
                val index = hosts.indexOfFirst { it.uuid == details.uuid }
                if (index >= 0) hosts[index] = details
                else {
                    hosts += details
                    hosts.sortBy { it.name.lowercase() }
                }
                if (libraryHost?.uuid == details.uuid) {
                    libraryHost = details
                    libraryRunningAppId = details.runningGameId
                    updateLibraryFromRaw(details.rawAppList)
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

        Thread {
            var message: String? = null
            var success = false
            try {
                stopComputerUpdates(true)
                val http = NvHTTP(
                    ServerHelper.getCurrentAddressFromComputer(host),
                    host.httpsPort,
                    binder.uniqueId,
                    host.serverCert,
                    PlatformBinding.getCryptoProvider(this),
                )
                if (http.pairState == PairState.PAIRED) {
                    success = true
                } else {
                    val pairing = http.pairingManager
                    when (pairing.pair(http.getServerInfo(true), pin, null)) {
                        PairState.PAIRED -> {
                            success = true
                            binder.getComputer(host.uuid).serverCert = pairing.pairedCert
                            binder.invalidateStateForComputer(host.uuid)
                        }
                        PairState.PIN_WRONG -> message = getString(R.string.pair_incorrect_pin)
                        PairState.ALREADY_IN_PROGRESS ->
                            message = getString(R.string.pair_already_in_progress)
                        PairState.FAILED -> message = getString(
                            if (host.runningGameId != 0) R.string.pair_pc_ingame
                            else R.string.pair_fail,
                        )
                        else -> message = getString(R.string.pair_fail)
                    }
                }
            } catch (_: UnknownHostException) {
                message = getString(R.string.error_unknown_host)
            } catch (_: FileNotFoundException) {
                message = getString(R.string.error_404)
            } catch (error: XmlPullParserException) {
                message = error.message
            } catch (error: IOException) {
                message = error.message
            }

            runOnUiThread {
                progressDialog.dismiss()
                if (success) openLibrary(host)
                else {
                    Toast.makeText(
                        this,
                        message ?: getString(R.string.pair_fail),
                        Toast.LENGTH_LONG,
                    ).show()
                    startComputerUpdates()
                }
            }
        }.start()
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
        disposeLibraryAssets()
        libraryHost = host
        pendingLibraryHostUuid = host.uuid
        libraryItems.clear()
        lastLibraryRawAppList = null
        libraryRunningAppId = host.runningGameId
        librarySortMode = LigasePreferences.getLibrarySortMode(this, host.uuid)
        libraryAssetLoader = managerBinder?.let { binder ->
            CachedAppAssetLoader(
                host,
                1.0,
                NetworkAssetLoader(this, binder.uniqueId),
                MemoryAssetLoader(),
                DiskAssetLoader(this),
                BitmapFactory.decodeResource(resources, R.drawable.no_app_image),
            )
        }
        libraryLoading = true
        startComputerUpdates()
        updateLibraryFromRaw(host.rawAppList)
        loadCachedLibrary(host)
        startAppListUpdates()
    }

    private fun clearLibraryState() {
        stopAppListUpdates()
        libraryHost = null
        pendingLibraryHostUuid = null
        libraryItems.clear()
        lastLibraryRawAppList = null
        libraryLoading = false
        disposeLibraryAssets()
    }

    private fun startAppListUpdates() {
        val binder = managerBinder ?: return
        val host = libraryHost ?: return
        if (!foreground || currentPage != LigasePage.HOME || appListPoller != null) return
        appListPoller = binder.createAppListPoller(host).also { it.start() }
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
        val wasSelected = libraryHost?.uuid == host.uuid
        if (wasSelected) clearLibraryState()
        managerBinder?.removeComputer(host)
        DiskAssetLoader(this).deleteAssetsForComputer(host.uuid)
        hosts.removeAll { it.uuid == host.uuid }
        if (wasSelected) selectDefaultHostIfNeeded()
    }

    private fun stopAppListUpdates() {
        appListPoller?.stop()
        appListPoller = null
    }

    private fun disposeLibraryAssets() {
        libraryAssetLoader?.cancelForegroundLoads()
        libraryAssetLoader?.cancelBackgroundLoads()
        libraryAssetLoader?.freeCacheMemory()
        libraryAssetLoader = null
    }

    private fun loadCachedLibrary(host: ComputerDetails) {
        if (host.rawAppList != null) return
        Thread {
            val raw = try {
                CacheHelper.readInputStreamToString(
                    CacheHelper.openCacheFileForInput(cacheDir, "applist", host.uuid),
                )
            } catch (_: IOException) {
                null
            }
            runOnUiThread {
                if (libraryHost?.uuid == host.uuid) {
                    if (raw != null) updateLibraryFromRaw(raw)
                    else if (libraryItems.isEmpty()) libraryLoading = true
                }
            }
        }.start()
    }

    private fun updateLibraryFromRaw(rawAppList: String?) {
        val host = libraryHost ?: return
        if (rawAppList == null || rawAppList == lastLibraryRawAppList) return
        try {
            val apps = NvHTTP.getAppListByReader(StringReader(rawAppList))
            val mapped = LigaseLibraryAdapter.fromGameStream(host.uuid, apps)
            libraryItems.clear()
            libraryItems.addAll(mapped)
            lastLibraryRawAppList = rawAppList
            libraryLoading = false
        } catch (_: XmlPullParserException) {
            libraryLoading = false
        } catch (_: IOException) {
            libraryLoading = false
        }
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

    private fun launchLibraryItem(item: LigaseLibraryItem) {
        val host = libraryHost ?: return
        val app = item.launchApp ?: return
        val binder = managerBinder
        if (binder == null) {
            toast(R.string.error_manager_not_running)
            return
        }

        val preference = PreferenceConfiguration.readPreferences(this)
        val withVirtualDisplay = if (item.isSystem) false else preference.useVirtualDisplay
        val launch = Runnable {
            ServerHelper.doStart(this, app, host, binder, withVirtualDisplay)
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

    private fun showAddHostDialog() {
        val inputLayout = TextInputLayout(this).apply {
            hint = getString(R.string.title_add_pc)
            setPadding(48, 8, 48, 0)
        }
        val input = TextInputEditText(inputLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        inputLayout.addView(input)
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_add_pc)
            .setMessage(R.string.msg_add_pc)
            .setView(inputLayout)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.proceed, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val address = input.text?.toString()?.trim().orEmpty()
                val parsed = parseAddress(address)
                if (parsed == null) {
                    inputLayout.error = getString(R.string.addpc_unknown_host)
                    return@setOnClickListener
                }
                dialog.dismiss()
                addHost(parsed.first, parsed.second)
            }
            input.requestFocus()
            input.post {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        dialog.show()
    }

    private fun parseAddress(raw: String): Pair<String, Int>? {
        if (raw.isBlank()) return null
        for (candidate in listOf("art://$raw", "art://[$raw]")) {
            val uri = Uri.parse(candidate)
            val host = uri.host
            if (!host.isNullOrBlank()) {
                return host to if (uri.port == -1) NvHTTP.DEFAULT_HTTP_PORT else uri.port
            }
        }
        return null
    }

    private fun addHost(host: String, port: Int) {
        val binder = managerBinder
        if (binder == null) {
            toast(R.string.error_manager_not_running)
            return
        }
        toast(R.string.msg_add_pc)
        Thread {
            val details = ComputerDetails().apply {
                manualAddress = ComputerDetails.AddressTuple(host, port)
            }
            val success = try {
                binder.addComputerBlocking(details)
            } catch (_: InterruptedException) {
                false
            } catch (_: IllegalArgumentException) {
                false
            }
            runOnUiThread {
                toast(if (success) R.string.addpc_success else R.string.addpc_fail)
            }
        }.start()
    }

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        startComputerUpdates()
        startAppListUpdates()
        UiHelper.showDecoderCrashDialog(this)
    }

    override fun onPause() {
        foreground = false
        stopAppListUpdates()
        stopComputerUpdates(false)
        super.onPause()
    }

    override fun onDestroy() {
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
        private const val STATE_PAGE = "ligase_page"
        private const val STATE_LIBRARY_HOST_UUID = "ligase_library_host_uuid"
        private const val EXIT_INTERVAL_MS = 2_000L
    }
}
