package com.limelight.ligase.feature.pairing.infrastructure

import android.content.Context
import com.limelight.binding.PlatformBinding
import com.limelight.computers.ComputerManagerService
import com.limelight.ligase.pairing.AttendedPairingCoordinator
import com.limelight.ligase.pairing.AttendedPairingCrypto
import com.limelight.ligase.pairing.AttendedPairingRepository
import com.limelight.ligase.pairing.AttendedPairingViewModel
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.utils.ServerHelper
import org.xmlpull.v1.XmlPullParserException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.UnknownHostException

sealed interface LegacyPairingResult {
    data object Paired : LegacyPairingResult
    data object PinWrong : LegacyPairingResult
    data object AlreadyInProgress : LegacyPairingResult
    data object HostInGame : LegacyPairingResult
    data object Failed : LegacyPairingResult
    data object UnknownHost : LegacyPairingResult
    data object NotFound : LegacyPairingResult
    data class TransportFailure(val message: String?) : LegacyPairingResult
}

/**
 * Concrete adapter for the legacy Moonlight pairing ABI.
 *
 * This is the only new pairing component that knows NvHTTP, PairingManager,
 * ComputerManagerBinder certificate persistence, and the Android crypto provider.
 */
open class LegacyPairingTransport private constructor(
    private val context: Context?,
    private val binderProvider: () -> ComputerManagerService.ComputerManagerBinder?,
    @Suppress("UNUSED_PARAMETER") marker: Unit,
) {
    constructor(
        context: Context,
        binderProvider: () -> ComputerManagerService.ComputerManagerBinder?,
    ) : this(context.applicationContext, binderProvider, Unit)

    internal constructor() : this(null, { null }, Unit)

    open fun pairLegacy(host: ComputerDetails, pin: String): LegacyPairingResult {
        return try {
            val session = createSession(host)
            if (session.http.pairState == PairingManager.PairState.PAIRED) {
                LegacyPairingResult.Paired
            } else {
                when (session.pairingManager.pair(session.http.getServerInfo(true), pin, null)) {
                    PairingManager.PairState.PAIRED -> {
                        persistPairedCertificate(host, session.pairingManager)
                        LegacyPairingResult.Paired
                    }
                    PairingManager.PairState.PIN_WRONG -> LegacyPairingResult.PinWrong
                    PairingManager.PairState.ALREADY_IN_PROGRESS ->
                        LegacyPairingResult.AlreadyInProgress
                    PairingManager.PairState.FAILED ->
                        if (host.runningGameId != 0) {
                            LegacyPairingResult.HostInGame
                        } else {
                            LegacyPairingResult.Failed
                        }
                    else -> LegacyPairingResult.Failed
                }
            }
        } catch (_: UnknownHostException) {
            LegacyPairingResult.UnknownHost
        } catch (_: FileNotFoundException) {
            LegacyPairingResult.NotFound
        } catch (error: XmlPullParserException) {
            LegacyPairingResult.TransportFailure(error.message)
        } catch (error: IOException) {
            LegacyPairingResult.TransportFailure(error.message)
        }
    }

    internal open fun createAttended(
        host: ComputerDetails,
        binding: AttendedPairingViewModel.Binding,
    ): AttendedPairingCoordinator {
        val session = createSession(host)
        return AttendedPairingCoordinator(
            repository = AttendedPairingRepository(session.http),
            crypto = AttendedPairingCrypto(),
            clock = android.os.SystemClock::elapsedRealtime,
            legacyPairing = object : AttendedPairingCoordinator.LegacyPairing {
                override fun pair(
                    pin: String,
                    requestId: String,
                    expectedCertificateSha256: ByteArray,
                ): PairingManager.PairState = session.pairingManager.pairAttended(
                    session.http.getServerInfo(true),
                    pin,
                    requestId,
                    expectedCertificateSha256,
                )

                override fun cancel() {
                    session.http.cancelActivePairingCall()
                }

                override fun onPaired(certificate: java.security.cert.X509Certificate?) {
                    persistPairedCertificate(host, session.pairingManager)
                }
            },
            listener = binding.stateListener,
            auditListener = binding.auditListener,
        )
    }

    open fun clientCertificate(): java.security.cert.X509Certificate =
        PlatformBinding.getCryptoProvider(checkNotNull(context)).clientCertificate

    private fun createSession(host: ComputerDetails): Session {
        val appContext = checkNotNull(context)
        val binder = binderProvider() ?: throw IOException("Computer manager is unavailable")
        val http = NvHTTP(
            ServerHelper.getCurrentAddressFromComputer(host),
            host.httpsPort,
            binder.uniqueId,
            host.serverCert,
            PlatformBinding.getCryptoProvider(appContext),
        )
        return Session(http, http.pairingManager)
    }

    private fun persistPairedCertificate(
        host: ComputerDetails,
        pairingManager: PairingManager,
    ) {
        val certificate = pairingManager.pairedCert ?: return
        val binder = binderProvider() ?: return
        binder.getComputer(host.uuid).serverCert = certificate
        binder.invalidateStateForComputer(host.uuid)
    }

    private data class Session(
        val http: NvHTTP,
        val pairingManager: PairingManager,
    )
}
