package com.limelight.ligase.feature.host.application

import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.limelight.nvstream.http.NvHTTP
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class DevicePresenceClientState {
    IDLE,
    SENDING,
    ACKNOWLEDGED,
    FAILED,
}

class DevicePresenceTarget private constructor(
    val hostId: String,
    internal val heartbeat: () -> Boolean,
) {
    companion object {
        @JvmStatic
        @JvmOverloads
        fun authenticated(
            hostId: String,
            http: NvHTTP,
            repository: DevicePresenceRepository = DevicePresenceRepository(),
        ): DevicePresenceTarget = DevicePresenceTarget(hostId) {
            repository.heartbeat(http)
        }

        internal fun test(hostId: String, heartbeat: () -> Boolean): DevicePresenceTarget =
            DevicePresenceTarget(hostId, heartbeat)
    }
}

open class DevicePresenceRepository {
    open fun heartbeat(http: NvHTTP): Boolean = try {
        validate(http.postDevicePresenceHeartbeat())
    } catch (_: Exception) {
        false
    }

    internal fun validate(response: NvHTTP.DevicePresenceResponse): Boolean {
        if (response.statusCode != 200) return false
        if (response.contentType != "application/json") return false
        if (response.body.size > MAX_RESPONSE_BYTES) return false
        return parseExactResult(response.body)
    }

    private fun parseExactResult(body: ByteArray): Boolean {
        return try {
        if (body.isEmpty() || body.take(3) == listOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) {
            return false
        }
        JsonReader(
            InputStreamReader(ByteArrayInputStream(body), StandardCharsets.UTF_8),
        ).use { reader ->
            reader.isLenient = false
            val seen = mutableSetOf<String>()
            var schemaVersion: Int? = null
            var presenceState: String? = null
            var heartbeatIntervalMs: Int? = null
            var presenceTimeoutMs: Int? = null
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (!seen.add(name)) return false
                when (name) {
                    "schemaVersion" -> schemaVersion = reader.nextInt()
                    "presenceState" -> presenceState = reader.nextString()
                    "heartbeatIntervalMs" -> heartbeatIntervalMs = reader.nextInt()
                    "presenceTimeoutMs" -> presenceTimeoutMs = reader.nextInt()
                    else -> return false
                }
            }
            reader.endObject()
            if (reader.peek() != JsonToken.END_DOCUMENT) return false
            schemaVersion == 1 && presenceState == "online" &&
                heartbeatIntervalMs == HEARTBEAT_INTERVAL_MS &&
                presenceTimeoutMs == PRESENCE_TIMEOUT_MS && seen.size == 4
        }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        const val HEARTBEAT_INTERVAL_MS = 5_000
        const val PRESENCE_TIMEOUT_MS = 15_000
        const val MAX_RESPONSE_BYTES = 512
    }
}

class DevicePresenceCoordinator(
    private val onState: (DevicePresenceClientState) -> Unit = {},
) : AutoCloseable {
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val worker = Executors.newSingleThreadExecutor()
    private val requestInFlight = AtomicBoolean(false)
    private var generation = 0L
    private var target: DevicePresenceTarget? = null
    private var future: ScheduledFuture<*>? = null

    @Synchronized
    fun onActive(target: DevicePresenceTarget?) {
        if (this.target?.hostId == target?.hostId && future != null) return
        invalidateLocked()
        this.target = target
        if (target == null) {
            onState(DevicePresenceClientState.IDLE)
            return
        }
        val ticket = generation
        future = scheduler.scheduleAtFixedRate(
            { tick(ticket, target) },
            0,
            DevicePresenceRepository.HEARTBEAT_INTERVAL_MS.toLong(),
            TimeUnit.MILLISECONDS,
        )
    }

    @Synchronized
    fun onInactive() {
        invalidateLocked()
        target = null
        onState(DevicePresenceClientState.IDLE)
    }

    private fun tick(ticket: Long, target: DevicePresenceTarget) {
        if (!isCurrent(ticket, target.hostId) || !requestInFlight.compareAndSet(false, true)) return
        onState(DevicePresenceClientState.SENDING)
        worker.execute {
            val success = target.heartbeat()
            requestInFlight.set(false)
            if (isCurrent(ticket, target.hostId)) {
                onState(
                    if (success) DevicePresenceClientState.ACKNOWLEDGED
                    else DevicePresenceClientState.FAILED,
                )
            }
        }
    }

    @Synchronized
    private fun isCurrent(ticket: Long, hostId: String): Boolean =
        generation == ticket && target?.hostId == hostId

    private fun invalidateLocked() {
        generation += 1
        future?.cancel(false)
        future = null
    }

    @Synchronized
    override fun close() {
        invalidateLocked()
        target = null
        scheduler.shutdownNow()
        worker.shutdownNow()
    }
}
