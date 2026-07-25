package com.limelight.ligase.feature.library.ui.settings

import android.os.Bundle
import com.limelight.ligase.feature.library.data.dto.LigaseResolutionDto
import com.limelight.ligase.library.LibraryConnectivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StreamingResolutionEditorStateTest {
    @Test
    fun `global requires a valid non-null resolution`() {
        val request = request(StreamingResolutionTarget.GLOBAL)

        assertEquals(
            StreamingResolutionDraftResult.Ready(LigaseResolutionDto(1600, 900)),
            parseStreamingResolutionDraft(
                request,
                StreamingResolutionDraft("1600", "900", useGlobal = false),
            ),
        )
        assertEquals(
            StreamingResolutionDraftResult.Invalid,
            parseStreamingResolutionDraft(
                request,
                StreamingResolutionDraft("", "900", useGlobal = false),
            ),
        )
        assertEquals(
            StreamingResolutionDraftResult.Invalid,
            parseStreamingResolutionDraft(
                request,
                StreamingResolutionDraft("319", "239", useGlobal = false),
            ),
        )
    }

    @Test
    fun `app use-global maps exactly to nullable resolution`() {
        val request = request(StreamingResolutionTarget.APP)

        assertEquals(
            StreamingResolutionDraftResult.Ready(null),
            parseStreamingResolutionDraft(
                request,
                StreamingResolutionDraft("invalid", "invalid", useGlobal = true),
            ),
        )
        assertEquals(
            StreamingResolutionDraftResult.Ready(LigaseResolutionDto(3840, 2160)),
            parseStreamingResolutionDraft(
                request,
                StreamingResolutionDraft("3840", "2160", useGlobal = false),
            ),
        )
    }

    @Test
    fun `submission gate checks host connectivity permission content and revision`() {
        val request = request(StreamingResolutionTarget.GLOBAL)
        fun decision(
            host: String? = HOST,
            connectivity: LibraryConnectivity = LibraryConnectivity.ONLINE,
            access: String? = "operate",
            revision: Long? = REVISION,
        ) = streamingResolutionSubmissionDecision(
            request,
            host,
            connectivity,
            access,
            revision,
        )

        assertEquals(StreamingResolutionSubmissionDecision.READY, decision())
        assertEquals(
            StreamingResolutionSubmissionDecision.STALE_HOST,
            decision(host = "other"),
        )
        assertEquals(
            StreamingResolutionSubmissionDecision.OFFLINE,
            decision(connectivity = LibraryConnectivity.OFFLINE),
        )
        assertEquals(
            StreamingResolutionSubmissionDecision.PERMISSION_DENIED,
            decision(access = "observe"),
        )
        assertEquals(
            StreamingResolutionSubmissionDecision.CONTENT_UNAVAILABLE,
            decision(revision = null),
        )
        assertEquals(
            StreamingResolutionSubmissionDecision.REVISION_CONFLICT,
            decision(revision = REVISION + 1),
        )
    }

    @Test
    fun `dialog result round trip preserves base revision and nullable app override`() {
        val request = request(StreamingResolutionTarget.APP)
        val appResult = StreamingResolutionDialogFragment.resultFrom(
            StreamingResolutionDialogFragment.resultBundle(request, null),
        )

        assertEquals(request, appResult?.request)
        assertNull(appResult?.resolution)
        assertNull(
            StreamingResolutionDialogFragment.resultFrom(
                StreamingResolutionDialogFragment.resultBundle(
                    request(StreamingResolutionTarget.GLOBAL),
                    null,
                ),
            ),
        )
    }

    @Test
    fun `result gate emits at most once and restores sent state`() {
        val gate = StreamingResolutionResultGate()
        assertTrue(gate.trySend())
        assertFalse(gate.trySend())
        assertFalse(StreamingResolutionResultGate(sent = gate.sent).trySend())
    }

    @Test
    fun `dialog draft survives saved-state round trip`() {
        val draft = StreamingResolutionDraft(
            widthText = "2560",
            heightText = "1440",
            useGlobal = false,
        )
        val saved = Bundle()

        StreamingResolutionDialogFragment.putDraft(saved, draft)

        assertEquals(draft, StreamingResolutionDialogFragment.draftFrom(saved))
        assertNull(StreamingResolutionDialogFragment.draftFrom(Bundle()))
    }

    private fun request(target: StreamingResolutionTarget) =
        StreamingResolutionEditorRequest(
            target = target,
            title = "Resolution",
            hostKey = HOST,
            baseRevision = REVISION,
            initialResolution = LigaseResolutionDto(1920, 1080),
            appUuid = if (target == StreamingResolutionTarget.APP) APP_UUID else null,
            useGlobal = target == StreamingResolutionTarget.APP,
        )

    companion object {
        private const val HOST = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        private const val APP_UUID = "f3d67f4d-b1fe-4c5d-a77e-b78a51051c1a"
        private const val REVISION = 7L
    }
}
