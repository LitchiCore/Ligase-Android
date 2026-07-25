package com.limelight.ligase.feature.input.layout.v3.data

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import javax.xml.parsers.DocumentBuilderFactory

data class LayoutV3CutoverResult(
    val state: LayoutV3CutoverState,
    val issue: LayoutV3CutoverIssue? = null,
)

enum class LayoutV3CutoverIssue {
    TARGET_OUTSIDE_APP_PRIVATE_ROOT,
    UNKNOWN_PREFERENCE_KEY,
    QUARANTINE_FAILED,
    ORIGINAL_TARGET_REMAINS,
    MARKER_READBACK_FAILED,
}

class LayoutV3CutoverCoordinator private constructor(
    private val dataRoot: File,
    private val filesRoot: File,
    private val preferencesRoot: File,
) {
    @Volatile
    private var current = readMarker()

    val gate = LayoutV3RepositoryGate { current }

    @Synchronized
    fun ensureReady(): LayoutV3CutoverResult {
        if (readMarker() == LayoutV3CutoverState.V3_READY) {
            current = LayoutV3CutoverState.V3_READY
            return LayoutV3CutoverResult(current)
        }
        current = LayoutV3CutoverState.QUARANTINING
        return try {
            val targets = exactTargets()
            targets.forEach(::requireAppPrivate)
            validatePreferenceKeys(targets.last())
            val quarantine = File(filesRoot, QUARANTINE_DIRECTORY)
            if (!quarantine.exists() && !quarantine.mkdirs()) fail(LayoutV3CutoverIssue.QUARANTINE_FAILED)
            val generation = File(quarantine, QUARANTINE_GENERATION)
            if (!generation.exists() && !generation.mkdirs()) fail(LayoutV3CutoverIssue.QUARANTINE_FAILED)
            targets.forEach { moveIdempotently(it, File(generation, it.name)) }
            current = LayoutV3CutoverState.VERIFYING
            if (targets.any(File::exists)) fail(LayoutV3CutoverIssue.ORIGINAL_TARGET_REMAINS)
            writeMarker()
            if (readMarker() != LayoutV3CutoverState.V3_READY) {
                fail(LayoutV3CutoverIssue.MARKER_READBACK_FAILED)
            }
            current = LayoutV3CutoverState.V3_READY
            LayoutV3CutoverResult(current)
        } catch (failure: CutoverFailure) {
            current = LayoutV3CutoverState.FAILED_CLOSED
            LayoutV3CutoverResult(current, failure.issue)
        } catch (_: Exception) {
            current = LayoutV3CutoverState.FAILED_CLOSED
            LayoutV3CutoverResult(current, LayoutV3CutoverIssue.QUARANTINE_FAILED)
        }
    }

    private fun exactTargets(): List<File> = listOf(
        File(filesRoot, V2_CONTENT_DIRECTORY),
        File(filesRoot, V2_DRAFT_DIRECTORY),
        File(filesRoot, V2_GENERATION_DIRECTORY),
        File(preferencesRoot, "$V2_PREFERENCES_FILE.xml"),
    )

    private fun requireAppPrivate(target: File) {
        val root = dataRoot.canonicalFile.toPath()
        val resolved = target.canonicalFile.toPath()
        if (!resolved.startsWith(root) || resolved == root) {
            fail(LayoutV3CutoverIssue.TARGET_OUTSIDE_APP_PRIVATE_ROOT)
        }
    }

    private fun validatePreferenceKeys(file: File) {
        if (!file.exists()) return
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val root = factory.newDocumentBuilder().parse(file).documentElement
        for (index in 0 until root.childNodes.length) {
            val node = root.childNodes.item(index)
            if (node.nodeType != org.w3c.dom.Node.ELEMENT_NODE) continue
            val key = node.attributes?.getNamedItem("name")?.nodeValue
                ?: fail(LayoutV3CutoverIssue.UNKNOWN_PREFERENCE_KEY)
            val id = key.removePrefix(PREFERENCE_PREFIX)
            if (!key.startsWith(PREFERENCE_PREFIX) || id == key || !isCanonicalUuid(id)) {
                fail(LayoutV3CutoverIssue.UNKNOWN_PREFERENCE_KEY)
            }
        }
    }

    private fun moveIdempotently(source: File, destination: File) {
        when {
            source.exists() && destination.exists() -> fail(LayoutV3CutoverIssue.QUARANTINE_FAILED)
            source.exists() -> {
                Files.move(
                    source.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                )
            }
            destination.exists() -> Unit
            else -> Unit
        }
    }

    private fun writeMarker() {
        val marker = AtomicFile(File(filesRoot, MARKER_FILE))
        val stream = marker.startWrite()
        try {
            stream.write(MARKER_BYTES)
            stream.fd.sync()
            marker.finishWrite(stream)
        } catch (failure: Exception) {
            marker.failWrite(stream)
            throw failure
        }
    }

    private fun readMarker(): LayoutV3CutoverState {
        val file = File(filesRoot, MARKER_FILE)
        if (!file.exists()) return LayoutV3CutoverState.NOT_STARTED
        return if (file.readBytes().contentEquals(MARKER_BYTES)) {
            LayoutV3CutoverState.V3_READY
        } else {
            LayoutV3CutoverState.FAILED_CLOSED
        }
    }

    private fun isCanonicalUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false) &&
            UUID_PATTERN.matches(value)

    private class CutoverFailure(val issue: LayoutV3CutoverIssue) : RuntimeException()
    private fun fail(issue: LayoutV3CutoverIssue): Nothing = throw CutoverFailure(issue)

    companion object {
        private const val V2_CONTENT_DIRECTORY = "ligase-touch-layout-v2"
        private const val V2_DRAFT_DIRECTORY = "ligase-touch-layout-v2-drafts"
        private const val V2_GENERATION_DIRECTORY = "ligase-touch-layout-v2-generations"
        private const val V2_PREFERENCES_FILE = "ligase_touch_layout_v2_preferences"
        private const val PREFERENCE_PREFIX = "preferred:"
        private const val QUARANTINE_DIRECTORY = "ligase-touch-layout-v3-quarantine"
        private const val QUARANTINE_GENERATION = "v2-test-data"
        private const val MARKER_FILE = "ligase-touch-layout-v3-cutover.json"
        private val MARKER_BYTES =
            """{"format":"ligase-touch-layout-v3-cutover","state":"V3_READY","version":1}""".toByteArray()
        private val UUID_PATTERN =
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")

        fun from(context: Context): LayoutV3CutoverCoordinator {
            val application = context.applicationContext
            val data = application.dataDir
            return LayoutV3CutoverCoordinator(
                dataRoot = data,
                filesRoot = application.filesDir,
                preferencesRoot = File(data, "shared_prefs"),
            )
        }

        internal fun forTest(dataRoot: File, filesRoot: File, preferencesRoot: File) =
            LayoutV3CutoverCoordinator(dataRoot, filesRoot, preferencesRoot)
    }
}
