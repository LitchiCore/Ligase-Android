package com.limelight.ligase

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import com.limelight.AppView
import com.limelight.R
import com.limelight.binding.PlatformBinding
import com.limelight.computers.ComputerManagerListener
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.nvstream.http.PairingManager.PairState
import com.limelight.nvstream.wol.WakeOnLanSender
import com.limelight.preferences.StreamSettings
import com.limelight.utils.ServerHelper
import com.limelight.utils.UiHelper
import org.xmlpull.v1.XmlPullParserException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.UnknownHostException

class LigaseActivity : AppCompatActivity() {
    private var currentPage by mutableStateOf(LigasePage.HOME)
    private var onboarding by mutableStateOf(false)
    private var selectedInput by mutableStateOf<InputDeviceMode?>(null)
    private var themeMode by mutableStateOf(LigaseThemeMode.SYSTEM)
    private val hosts = mutableStateListOf<ComputerDetails>()

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
                runOnUiThread { startComputerUpdates() }
                PlatformBinding.getCryptoProvider(this@LigaseActivity).clientCertificate
            }.start()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            managerBinder = null
            polling = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)

        onboarding = !LigasePreferences.hasInputDeviceMode(this)
        selectedInput = if (onboarding) null else LigasePreferences.getInputDeviceMode(this)
        themeMode = LigasePreferences.getThemeMode(this)
        currentPage = savedInstanceState?.getString(STATE_PAGE)
            ?.let { saved -> LigasePage.entries.firstOrNull { it.name == saved } }
            ?: if (onboarding) LigasePage.INPUT else LigasePage.HOME

        setContent {
            LigaseRoot(
                themeMode = themeMode,
                onboarding = onboarding,
                currentPage = currentPage,
                selectedInput = selectedInput,
                hosts = hosts,
                onPageSelected = { currentPage = it },
                onInputSelected = { selectedInput = it },
                onInputConfirmed = ::confirmInput,
                onThemeSelected = ::selectTheme,
                onHostClick = ::onHostClicked,
                onHostLongClick = ::showHostActions,
                onAddHost = ::showAddHostDialog,
                onAdvancedSettings = {
                    startActivity(Intent(this, StreamSettings::class.java))
                },
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

    private fun setupBackBehavior() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!onboarding && currentPage != LigasePage.HOME) {
                    currentPage = LigasePage.HOME
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
            else -> openAppList(host, newlyPaired = false)
        }
    }

    private fun showHostActions(host: ComputerDetails) {
        val action = if (host.state == ComputerDetails.State.OFFLINE) {
            R.string.pcview_menu_send_wol
        } else {
            R.string.pcview_menu_app_list
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(host.name)
            .setItems(arrayOf(getString(action))) { _, _ -> onHostClicked(host) }
            .show()
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
                if (success) openAppList(host, newlyPaired = true)
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

    private fun openAppList(host: ComputerDetails, newlyPaired: Boolean) {
        startActivity(
            Intent(this, AppView::class.java)
                .putExtra(AppView.NAME_EXTRA, host.name)
                .putExtra(AppView.UUID_EXTRA, host.uuid)
                .putExtra(AppView.NEW_PAIR_EXTRA, newlyPaired)
                .putExtra(AppView.SHOW_HIDDEN_APPS_EXTRA, false),
        )
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
        UiHelper.showDecoderCrashDialog(this)
    }

    override fun onPause() {
        foreground = false
        stopComputerUpdates(false)
        super.onPause()
    }

    override fun onDestroy() {
        stopComputerUpdates(false)
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
        managerBinder = null
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PAGE, currentPage.name)
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val STATE_PAGE = "ligase_page"
        private const val EXIT_INTERVAL_MS = 2_000L
    }
}
