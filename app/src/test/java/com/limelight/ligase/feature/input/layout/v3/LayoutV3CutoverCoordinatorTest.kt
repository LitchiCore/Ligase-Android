package com.limelight.ligase.feature.input.layout.v3

import com.limelight.ligase.feature.input.layout.v3.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LayoutV3CutoverCoordinatorTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun exactTargetsAreQuarantinedBeforeGateOpensAndRetryIsIdempotent() {
        val roots = roots()
        listOf(
            "ligase-touch-layout-v2",
            "ligase-touch-layout-v2-drafts",
            "ligase-touch-layout-v2-generations",
            "ligase-touch-layout-v3-drafts",
            "ligase-touch-layout-v3-generations",
        ).forEach { File(roots.files, it).mkdirs() }
        File(roots.preferences, "ligase_touch_layout_v2_preferences.xml").writeText(
            """<map><string name="preferred:00000000-0000-0000-0000-000000000001">variant</string></map>""",
        )
        val mustNotTouch = File(roots.data, "computers4.db").also { it.writeText("safe") }
        val coordinator = LayoutV3CutoverCoordinator.forTest(roots.data, roots.files, roots.preferences)
        assertThrows(LayoutV3RepositoryClosedException::class.java) { coordinator.gate.requireReady() }
        assertEquals(LayoutV3CutoverState.V3_READY, coordinator.ensureReady().state)
        coordinator.gate.requireReady()
        assertEquals(LayoutV3CutoverState.V3_READY, coordinator.ensureReady().state)
        assertFalse(File(roots.files, "ligase-touch-layout-v2").exists())
        assertFalse(File(roots.files, "ligase-touch-layout-v3-drafts").exists())
        assertFalse(File(roots.files, "ligase-touch-layout-v3-generations").exists())
        assertEquals("safe", mustNotTouch.readText())
    }

    @Test
    fun unknownPreferenceFailsClosedAndPreservesEverySource() {
        val roots = roots()
        val source = File(roots.files, "ligase-touch-layout-v2").also { it.mkdirs() }
        val preferences = File(roots.preferences, "ligase_touch_layout_v2_preferences.xml")
        preferences.writeText("""<map><string name="hostIdentity">must remain</string></map>""")
        val coordinator = LayoutV3CutoverCoordinator.forTest(roots.data, roots.files, roots.preferences)
        val result = coordinator.ensureReady()
        assertEquals(LayoutV3CutoverState.FAILED_CLOSED, result.state)
        assertEquals(LayoutV3CutoverIssue.UNKNOWN_PREFERENCE_KEY, result.issue)
        assertTrue(source.exists())
        assertTrue(preferences.exists())
        assertThrows(LayoutV3RepositoryClosedException::class.java) { coordinator.gate.requireReady() }
    }

    @Test
    fun partialQuarantineNeverWritesReadyMarkerAndGateRemainsClosed() {
        val roots = roots()
        File(roots.files, "ligase-touch-layout-v2").mkdirs()
        File(roots.files, "ligase-touch-layout-v2-drafts").mkdirs()
        val quarantine = File(roots.files, "ligase-touch-layout-v3-quarantine/pre-authority-test-data")
            .also { it.mkdirs() }
        File(quarantine, "ligase-touch-layout-v2-drafts").mkdirs()
        val coordinator = LayoutV3CutoverCoordinator.forTest(roots.data, roots.files, roots.preferences)
        val result = coordinator.ensureReady()
        assertEquals(LayoutV3CutoverState.FAILED_CLOSED, result.state)
        assertEquals(LayoutV3CutoverIssue.QUARANTINE_FAILED, result.issue)
        assertFalse(File(roots.files, "ligase-touch-layout-v3-cutover.json").exists())
        assertThrows(LayoutV3RepositoryClosedException::class.java) { coordinator.gate.requireReady() }
    }

    private fun roots(): Roots {
        val data = temporary.newFolder()
        val files = File(data, "files").also { it.mkdirs() }
        val preferences = File(data, "shared_prefs").also { it.mkdirs() }
        return Roots(data, files, preferences)
    }
    private data class Roots(val data: File, val files: File, val preferences: File)
}
