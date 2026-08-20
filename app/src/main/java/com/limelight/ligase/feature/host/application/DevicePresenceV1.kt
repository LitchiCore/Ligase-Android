package com.limelight.ligase.feature.host.application

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.limelight.LimeLog
import com.limelight.nvstream.http.NvHTTP
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLException

enum class DevicePresenceClientState { IDLE, SENDING, ACKNOWLEDGED, FAILED }

enum class DevicePresenceEligibilityReason {
    ELIGIBLE, BACKGROUND, NO_SELECTED_HOST, HOST_NOT_ONLINE, HOST_NOT_PAIRED,
    MISSING_SERVER_CERT, TRANSPORT_UNAVAILABLE, TARGET_CREATION_FAILED,
}

enum class DevicePresenceAttemptResult {
    ACK_200_VALID, TIMEOUT, CANCELLED, TLS_AUTH_FAILED, HTTP_STATUS_CLASS,
    CONTENT_TYPE, TOO_LARGE, EOF_SHAPE, BODY_SCHEMA, IO_OTHER,
}

data class DevicePresenceEligibility(
    val reason: DevicePresenceEligibilityReason,
    val foreground: Boolean,
    val activeStream: Boolean,
    val selectedAuthenticatedTarget: Boolean,
)

sealed interface DevicePresenceDiagnosticEvent {
    val generation: Long
    data class Eligibility(override val generation: Long, val value: DevicePresenceEligibility) : DevicePresenceDiagnosticEvent
    data class AttemptStarted(override val generation: Long) : DevicePresenceDiagnosticEvent
    data class Result(override val generation: Long, val value: DevicePresenceAttemptResult) : DevicePresenceDiagnosticEvent
    data class GenerationDiscarded(override val generation: Long) : DevicePresenceDiagnosticEvent
}

fun interface DevicePresenceDiagnosticSink {
    fun emit(event: DevicePresenceDiagnosticEvent)

    companion object {
        @JvmField
        val SAFE_LOGCAT = DevicePresenceDiagnosticSink { event ->
            val message = when (event) {
                is DevicePresenceDiagnosticEvent.Eligibility ->
                    "eligibility generation=${event.generation} reason=${event.value.reason} " +
                        "foreground=${event.value.foreground} activeStream=${event.value.activeStream} " +
                        "selectedAuthenticatedTarget=${event.value.selectedAuthenticatedTarget}"
                is DevicePresenceDiagnosticEvent.AttemptStarted -> "attemptStarted generation=${event.generation}"
                is DevicePresenceDiagnosticEvent.Result -> "result generation=${event.generation} result=${event.value}"
                is DevicePresenceDiagnosticEvent.GenerationDiscarded -> "generationDiscarded generation=${event.generation}"
            }
            LimeLog.info("LigasePresence $message")
        }
    }
}

class DevicePresenceTarget private constructor(
    val hostId: String,
    internal val heartbeat: () -> DevicePresenceAttemptResult,
) {
    companion object {
        @JvmStatic @JvmOverloads
        fun authenticated(
            hostId: String,
            http: NvHTTP,
            repository: DevicePresenceRepository = DevicePresenceRepository(),
        ) = DevicePresenceTarget(hostId) { repository.heartbeat(http) }

        internal fun test(hostId: String, heartbeat: () -> DevicePresenceAttemptResult) =
            DevicePresenceTarget(hostId, heartbeat)
    }
}

open class DevicePresenceRepository {
    open fun heartbeat(http: NvHTTP): DevicePresenceAttemptResult = try {
        classify(http.postDevicePresenceHeartbeat())
    } catch (_: SocketTimeoutException) {
        DevicePresenceAttemptResult.TIMEOUT
    } catch (_: SSLException) {
        DevicePresenceAttemptResult.TLS_AUTH_FAILED
    } catch (_: IOException) {
        if (Thread.currentThread().isInterrupted) DevicePresenceAttemptResult.CANCELLED else DevicePresenceAttemptResult.IO_OTHER
    } catch (_: Exception) {
        DevicePresenceAttemptResult.IO_OTHER
    }

    internal fun validate(response: NvHTTP.DevicePresenceResponse) =
        classify(response) == DevicePresenceAttemptResult.ACK_200_VALID

    internal fun classify(response: NvHTTP.DevicePresenceResponse): DevicePresenceAttemptResult {
        if (response.statusCode != 200) return DevicePresenceAttemptResult.HTTP_STATUS_CLASS
        if (response.contentType != "application/json") return DevicePresenceAttemptResult.CONTENT_TYPE
        if (response.body.size > MAX_RESPONSE_BYTES) return DevicePresenceAttemptResult.TOO_LARGE
        return parseExactResult(response.body)
    }

    private fun parseExactResult(body: ByteArray): DevicePresenceAttemptResult {
        if (body.isEmpty() || body.take(3) == listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) {
            return DevicePresenceAttemptResult.EOF_SHAPE
        }
        return try {
            JsonReader(InputStreamReader(ByteArrayInputStream(body), StandardCharsets.UTF_8)).use { reader ->
                reader.isLenient = false
                val seen = mutableSetOf<String>()
                var schemaVersion: Int? = null
                var presenceState: String? = null
                var heartbeatIntervalMs: Int? = null
                var presenceTimeoutMs: Int? = null
                reader.beginObject()
                while (reader.hasNext()) {
                    val name = reader.nextName()
                    if (!seen.add(name)) return DevicePresenceAttemptResult.BODY_SCHEMA
                    when (name) {
                        "schemaVersion" -> schemaVersion = reader.nextInt()
                        "presenceState" -> presenceState = reader.nextString()
                        "heartbeatIntervalMs" -> heartbeatIntervalMs = reader.nextInt()
                        "presenceTimeoutMs" -> presenceTimeoutMs = reader.nextInt()
                        else -> return DevicePresenceAttemptResult.BODY_SCHEMA
                    }
                }
                reader.endObject()
                if (reader.peek() != JsonToken.END_DOCUMENT) return DevicePresenceAttemptResult.EOF_SHAPE
                if (schemaVersion == 1 && presenceState == "online" &&
                    heartbeatIntervalMs == HEARTBEAT_INTERVAL_MS && presenceTimeoutMs == PRESENCE_TIMEOUT_MS && seen.size == 4
                ) DevicePresenceAttemptResult.ACK_200_VALID else DevicePresenceAttemptResult.BODY_SCHEMA
            }
        } catch (_: Exception) {
            DevicePresenceAttemptResult.EOF_SHAPE
        }
    }

    companion object {
        const val HEARTBEAT_INTERVAL_MS = 5_000
        const val PRESENCE_TIMEOUT_MS = 15_000
        const val MAX_RESPONSE_BYTES = 512
    }
}

class DevicePresenceCoordinator @JvmOverloads constructor(
    private val diagnostics: DevicePresenceDiagnosticSink = DevicePresenceDiagnosticSink.SAFE_LOGCAT,
    private val onState: (DevicePresenceClientState) -> Unit = {},
) : AutoCloseable {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val worker = Executors.newSingleThreadExecutor()
    private val requestInFlight = AtomicBoolean(false)
    private var generation = 0L
    private var target: DevicePresenceTarget? = null
    private var future: ScheduledFuture<*>? = null
    private var eligibility: DevicePresenceEligibility? = null
    private var attemptLoggedGeneration = Long.MIN_VALUE
    private var lastResult: Pair<Long, DevicePresenceAttemptResult>? = null
    private var lastDiscardedGeneration = Long.MIN_VALUE

    @Synchronized fun updateEligibility(value: DevicePresenceEligibility) {
        if (eligibility == value) return
        eligibility = value
        diagnostics.emit(DevicePresenceDiagnosticEvent.Eligibility(generation, value))
    }

    @Synchronized fun onActive(target: DevicePresenceTarget?) {
        if (this.target?.hostId == target?.hostId && future != null) return
        invalidateLocked()
        this.target = target
        if (target == null) {
            onState(DevicePresenceClientState.IDLE)
            return
        }
        val ticket = generation
        future = scheduler.scheduleAtFixedRate(
            { tick(ticket, target) }, 0, DevicePresenceRepository.HEARTBEAT_INTERVAL_MS.toLong(), TimeUnit.MILLISECONDS,
        )
    }

    @Synchronized fun onInactive() {
        invalidateLocked()
        target = null
        onState(DevicePresenceClientState.IDLE)
    }

    private fun tick(ticket: Long, target: DevicePresenceTarget) {
        if (!isCurrent(ticket, target.hostId) || !requestInFlight.compareAndSet(false, true)) return
        synchronized(this) {
            if (attemptLoggedGeneration != ticket) {
                attemptLoggedGeneration = ticket
                diagnostics.emit(DevicePresenceDiagnosticEvent.AttemptStarted(ticket))
            }
        }
        onState(DevicePresenceClientState.SENDING)
        worker.execute {
            val result = target.heartbeat()
            requestInFlight.set(false)
            if (isCurrent(ticket, target.hostId)) {
                synchronized(this) {
                    if (lastResult != ticket to result) {
                        lastResult = ticket to result
                        diagnostics.emit(DevicePresenceDiagnosticEvent.Result(ticket, result))
                    }
                }
                onState(if (result == DevicePresenceAttemptResult.ACK_200_VALID) DevicePresenceClientState.ACKNOWLEDGED else DevicePresenceClientState.FAILED)
            } else synchronized(this) {
                if (lastDiscardedGeneration != ticket) {
                    lastDiscardedGeneration = ticket
                    diagnostics.emit(DevicePresenceDiagnosticEvent.GenerationDiscarded(ticket))
                }
            }
        }
    }

    @Synchronized private fun isCurrent(ticket: Long, hostId: String) =
        generation == ticket && target?.hostId == hostId

    private fun invalidateLocked() {
        generation += 1
        future?.cancel(false)
        future = null
    }

    @Synchronized override fun close() {
        invalidateLocked()
        target = null
        scheduler.shutdownNow()
        worker.shutdownNow()
    }
}
