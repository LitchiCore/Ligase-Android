package com.limelight.ligase.feature.library.application

import com.limelight.ligase.feature.library.data.repository.HostAppAssetRepository
import com.limelight.ligase.feature.library.data.repository.HostCoverFetchResult
import com.limelight.ligase.feature.library.data.repository.HostCoverIssue
import com.limelight.ligase.feature.library.data.repository.VerifiedHostCover
import com.limelight.ligase.feature.library.domain.HostCoverAuthority
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Base64
import java.util.concurrent.Executor

class HostVerifiedCoverLoaderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val app = NvApp("Example", UUID.uppercase(), 7, false)
    private val authority = authority(SHA)

    @Test fun `same authority subscribers deduplicate exact network fetch`() {
        val queued = QueuedExecutor()
        var calls = 0
        val loader = loader(queued) { _, _, _ -> calls++; HostCoverFetchResult.Current(COVER) }
        val first = mutableListOf<HostVerifiedCoverState>()
        val second = mutableListOf<HostVerifiedCoverState>()

        loader.subscribe(app, authority, first::add)
        loader.subscribe(app, authority, second::add)
        assertEquals(1, queued.tasks.size)
        queued.runAll()

        assertEquals(1, calls)
        assertTrue(first.last() is HostVerifiedCoverState.Current)
        assertTrue(second.last() is HostVerifiedCoverState.Current)
    }

    @Test fun `restart revalidates exact UUID SHA cache without legacy or network fetch`() {
        var firstCalls = 0
        loader(DirectExecutor) { _, _, _ -> firstCalls++; HostCoverFetchResult.Current(COVER) }
            .subscribe(app, authority) {}
        var restartCalls = 0
        val states = mutableListOf<HostVerifiedCoverState>()

        loader(DirectExecutor) { _, _, _ ->
            restartCalls++
            HostCoverFetchResult.Rejected(HostCoverIssue.INVALID_LENGTH)
        }.subscribe(app, authority, states::add)

        assertEquals(1, firstCalls)
        assertEquals(0, restartCalls)
        assertTrue(states.last() is HostVerifiedCoverState.Current)
    }

    @Test fun `authority SHA change never reuses old cache and rejection is placeholder`() {
        loader(DirectExecutor) { _, _, _ -> HostCoverFetchResult.Current(COVER) }
            .subscribe(app, authority) {}
        var calls = 0
        val states = mutableListOf<HostVerifiedCoverState>()
        val changed = authority("a".repeat(64))

        loader(DirectExecutor) { _, _, _ ->
            calls++
            HostCoverFetchResult.Rejected(HostCoverIssue.INVALID_HEADER_SHA)
        }.subscribe(app, changed, states::add)

        assertEquals(1, calls)
        val rejected = states.last() as HostVerifiedCoverState.Rejected
        assertEquals(null, rejected.stale)
    }

    @Test fun `close rejects late generation publication`() {
        val queued = QueuedExecutor()
        val states = mutableListOf<HostVerifiedCoverState>()
        val loader = loader(queued) { _, _, _ -> HostCoverFetchResult.Current(COVER) }
        loader.subscribe(app, authority, states::add)

        loader.close()
        queued.runAll()

        assertEquals(listOf(HostVerifiedCoverState.Loading), states)
    }

    private fun loader(
        executor: Executor,
        fetch: (NvApp, HostCoverAuthority, VerifiedHostCover?) -> HostCoverFetchResult,
    ) = HostVerifiedCoverLoader(
        cacheRoot = temporary.root,
        http = { throw AssertionError("HTTP must be replaced in tests") },
        repository = HostAppAssetRepository(),
        fetchCover = fetch,
        background = executor,
        postToMain = { it() },
    )

    private fun authority(sha: String) = HostCoverAuthority(
        UUID, sha, "steamClientLibraryCache", "123", "thirdPartyArtworkLocalUseOnlyNoRedistribution",
    )

    private class QueuedExecutor : Executor {
        val tasks = mutableListOf<Runnable>()
        override fun execute(command: Runnable) { tasks += command }
        fun runAll() { tasks.toList().also { tasks.clear() }.forEach(Runnable::run) }
    }

    private data object DirectExecutor : Executor {
        override fun execute(command: Runnable) = command.run()
    }

    companion object {
        private const val UUID = "2c42a3d0-79f1-4bb6-98f8-40c18cd5bc91"
        private const val SHA = "431ced6916a2a21a156e38701afe55bbd7f88969fbbfc56d7fe099d47f265460"
        private val PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
        )
        private val COVER = VerifiedHostCover(UUID, SHA, PNG)
    }
}
