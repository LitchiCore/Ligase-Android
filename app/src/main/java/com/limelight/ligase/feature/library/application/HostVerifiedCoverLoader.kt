package com.limelight.ligase.feature.library.application

import com.limelight.ligase.feature.library.data.repository.HostAppAssetRepository
import com.limelight.ligase.feature.library.data.repository.HostCoverFetchResult
import com.limelight.ligase.feature.library.data.repository.HostCoverIssue
import com.limelight.ligase.feature.library.data.repository.VerifiedHostCover
import com.limelight.ligase.feature.library.domain.HostCoverAuthority
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.Executor

sealed interface HostVerifiedCoverState {
    data object Loading : HostVerifiedCoverState
    data class Current(val cover: VerifiedHostCover) : HostVerifiedCoverState
    data class Rejected(val issue: HostCoverIssue, val stale: VerifiedHostCover? = null) :
        HostVerifiedCoverState
}

fun interface HostCoverSubscription : AutoCloseable {
    override fun close()
}

class HostVerifiedCoverLoader(
    cacheRoot: File,
    private val http: () -> NvHTTP,
    private val repository: HostAppAssetRepository = HostAppAssetRepository(),
    private val fetchCover: (NvApp, HostCoverAuthority, VerifiedHostCover?) -> HostCoverFetchResult =
        { app, authority, previous -> repository.fetch(http(), app, authority, previous) },
    private val background: Executor = Executor { action ->
        Thread(action, "Ligase verified cover").start()
    },
    private val postToMain: ((() -> Unit) -> Unit),
) : AutoCloseable {
    private val cacheDir = File(cacheRoot, VERIFIED_CACHE_DIRECTORY)
    private val lock = Any()
    private val states = mutableMapOf<Key, HostVerifiedCoverState>()
    private val subscribers = mutableMapOf<Key, MutableMap<Long, (HostVerifiedCoverState) -> Unit>>()
    private val inFlight = mutableSetOf<Key>()
    private var nextSubscriber = 0L
    private var generation = 0L
    private var closed = false

    fun subscribe(
        app: NvApp,
        authority: HostCoverAuthority,
        observer: (HostVerifiedCoverState) -> Unit,
    ): HostCoverSubscription {
        val key = Key.from(authority) ?: run {
            observer(HostVerifiedCoverState.Rejected(HostCoverIssue.INVALID_AUTHORITY))
            return HostCoverSubscription {}
        }
        val subscriberId: Long
        val initial: HostVerifiedCoverState
        val shouldLoad: Boolean
        synchronized(lock) {
            if (closed) {
                observer(HostVerifiedCoverState.Rejected(HostCoverIssue.INVALID_AUTHORITY))
                return HostCoverSubscription {}
            }
            subscriberId = ++nextSubscriber
            subscribers.getOrPut(key, ::mutableMapOf)[subscriberId] = observer
            initial = states[key] ?: HostVerifiedCoverState.Loading.also { states[key] = it }
            shouldLoad = inFlight.add(key) && initial == HostVerifiedCoverState.Loading
        }
        observer(initial)
        if (shouldLoad) startLoad(key, app, authority)
        return HostCoverSubscription {
            synchronized(lock) {
                subscribers[key]?.let {
                    it.remove(subscriberId)
                    if (it.isEmpty()) subscribers.remove(key)
                }
            }
        }
    }

    override fun close() {
        synchronized(lock) {
            closed = true
            generation++
            subscribers.clear()
            inFlight.clear()
            states.clear()
        }
    }

    private fun startLoad(key: Key, app: NvApp, authority: HostCoverAuthority) {
        val ticket = synchronized(lock) { generation }
        background.execute {
            val state = load(key, app, authority)
            postToMain {
                val callbacks = synchronized(lock) {
                    if (closed || ticket != generation) return@postToMain
                    inFlight.remove(key)
                    states[key] = state
                    subscribers[key]?.values?.toList().orEmpty()
                }
                callbacks.forEach { it(state) }
            }
        }
    }

    private fun load(key: Key, app: NvApp, authority: HostCoverAuthority): HostVerifiedCoverState {
        val cache = File(cacheDir, key.fileName)
        readVerified(cache, authority)?.let { return HostVerifiedCoverState.Current(it) }
        val previous = synchronized(lock) {
            (states[key] as? HostVerifiedCoverState.Current)?.cover
        }
        return try {
            when (val result = fetchCover(app, authority, previous)) {
                is HostCoverFetchResult.Current -> {
                    if (!writeVerified(cache, result.cover.bytes)) {
                        HostVerifiedCoverState.Rejected(HostCoverIssue.INVALID_LENGTH, previous)
                    } else {
                        HostVerifiedCoverState.Current(result.cover)
                    }
                }
                is HostCoverFetchResult.Rejected ->
                    HostVerifiedCoverState.Rejected(result.issue, result.stale)
            }
        } catch (_: IOException) {
            HostVerifiedCoverState.Rejected(HostCoverIssue.INVALID_LENGTH, previous)
        }
    }

    private fun readVerified(file: File, authority: HostCoverAuthority): VerifiedHostCover? =
        try {
            if (!file.isFile || file.length() !in 1..HostAppAssetRepository.MAX_BYTES.toLong()) null
            else repository.verifyStored(authority, file.readBytes())
        } catch (_: IOException) {
            null
        }

    private fun writeVerified(file: File, bytes: ByteArray): Boolean {
        return try {
            if (!cacheDir.exists() && !cacheDir.mkdirs()) return false
            val temporary = File(cacheDir, ".${file.name}.${System.nanoTime()}.tmp")
            FileOutputStream(temporary).use {
                it.write(bytes)
                it.fd.sync()
            }
            if (file.exists() && !file.delete()) {
                temporary.delete()
                false
            } else if (!temporary.renameTo(file)) {
                temporary.delete()
                false
            } else true
        } catch (_: IOException) {
            false
        }
    }

    private data class Key(val appUuid: String, val expectedSha256: String) {
        val fileName: String get() = "$appUuid-$expectedSha256.png"

        companion object {
            private val UUID = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
            private val SHA = Regex("^[0-9a-f]{64}$")
            fun from(authority: HostCoverAuthority): Key? =
                if (UUID.matches(authority.appUuid) && SHA.matches(authority.expectedSha256)) {
                    Key(authority.appUuid, authority.expectedSha256)
                } else null
        }
    }

    companion object { private const val VERIFIED_CACHE_DIRECTORY = "ligase-verified-covers-v1" }
}
