package com.limelight.ligase.feature.input.layout.v3.data

import android.content.Context
import android.util.AtomicFile
import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3ContentVerifier
import com.limelight.ligase.feature.input.layout.v3.serialization.LayoutV3ContentVerificationResult
import com.limelight.ligase.feature.input.layout.v3.domain.TouchLayoutV3Document
import com.limelight.ligase.feature.input.layout.v3.editor.*
import com.limelight.ligase.feature.input.layout.v3.serialization.StrictJsonV3
import com.limelight.ligase.feature.input.layout.v3.serialization.StrictJsonV3Value
import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3Codec
import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3Jcs
import com.limelight.ligase.layout.LayoutContractV1Validator
import java.io.File
import java.io.FileOutputStream

data class LayoutV3JournalEntry(
    val identity: LayoutV3DraftIdentity,
    val updatedAtEpochMillis: Long,
    val document: TouchLayoutV3Document,
) {
    override fun toString(): String =
        "LayoutV3JournalEntry(identity=redacted,updatedAt=redacted,document=redacted)"
}

sealed interface LayoutV3JournalReadResult {
    data class Ready(val entry: LayoutV3JournalEntry) : LayoutV3JournalReadResult
    data object Missing : LayoutV3JournalReadResult
    data class Quarantined(val issue: LayoutV3RecoveryIssue) : LayoutV3JournalReadResult
}

enum class LayoutV3JournalWriteResult { SAVED, INVALID, WRITE_FAILED, READBACK_FAILED }

class LayoutV3DraftJournal internal constructor(
    context: Context,
    private val clock: () -> Long,
    private val afterWriteForTest: (File) -> Unit,
    gate: LayoutV3RepositoryGate = LayoutV3CutoverCoordinator.from(context).let {
        it.ensureReady()
        it.gate
    },
) {
    constructor(context: Context) : this(context, System::currentTimeMillis, {})

    private val directory = File(context.applicationContext.filesDir, DIRECTORY)
    private val quarantine = File(directory, QUARANTINE)

    init {
        gate.requireReady()
    }

    fun write(
        identity: LayoutV3DraftIdentity,
        documentRaw: ByteArray,
    ): LayoutV3JournalWriteResult = synchronized(IO_LOCK) {
        writeLocked(identity, documentRaw)
    }

    private fun writeLocked(
        identity: LayoutV3DraftIdentity,
        documentRaw: ByteArray,
    ): LayoutV3JournalWriteResult {
        if (!validIdentity(identity)) return LayoutV3JournalWriteResult.INVALID
        val verified = runCatching { TouchLayoutV3Codec.decodeDraft(documentRaw) }.getOrNull()
            ?: return LayoutV3JournalWriteResult.INVALID
        if (
            verified.layoutId != identity.layoutId ||
            verified.revision != identity.revision ||
            verified.variants.none { it.variantId == identity.variantId }
        ) {
            return LayoutV3JournalWriteResult.INVALID
        }
        val now = clock()
        if (now !in 0..MAX_EPOCH_MILLIS) return LayoutV3JournalWriteResult.INVALID
        val previous = read(identity.layoutId)
        val updatedAt = if (previous is LayoutV3JournalReadResult.Ready) {
            maxOf(previous.entry.updatedAtEpochMillis, now)
        } else {
            now
        }
        val artifact = StrictJsonV3.parse(documentRaw) as? StrictJsonV3Value.ObjectValue
            ?: return LayoutV3JournalWriteResult.INVALID
        val root = StrictJsonV3Value.ObjectValue(
            linkedMapOf(
                "journalFormat" to StrictJsonV3Value.StringValue(FORMAT),
                "schemaVersion" to StrictJsonV3Value.IntegerValue(SCHEMA_VERSION),
                "draftId" to StrictJsonV3Value.StringValue(identity.layoutId),
                "origin" to StrictJsonV3Value.StringValue(identity.origin.name),
                "sourceLayoutId" to nullable(identity.sourceLayoutId),
                "sourceRevision" to nullable(identity.sourceRevision),
                "variantId" to StrictJsonV3Value.StringValue(identity.variantId),
                "updatedAtEpochMillis" to StrictJsonV3Value.IntegerValue(updatedAt),
                "artifact" to artifact,
            ),
        )
        val raw = TouchLayoutV3Jcs.canonicalBytes(root)
        if (raw.size > MAX_BYTES || !directory.exists() && !directory.mkdirs()) {
            return LayoutV3JournalWriteResult.WRITE_FAILED
        }
        val target = target(identity.layoutId) ?: return LayoutV3JournalWriteResult.INVALID
        if (!atomicWrite(target, raw)) return LayoutV3JournalWriteResult.WRITE_FAILED
        runCatching { afterWriteForTest(target) }
        val readback = readBounded(target)
        if (readback == null || !readback.contentEquals(raw) || decode(readback) == null) {
            return LayoutV3JournalWriteResult.READBACK_FAILED
        }
        return LayoutV3JournalWriteResult.SAVED
    }

    fun read(draftId: String): LayoutV3JournalReadResult = synchronized(IO_LOCK) {
        val file = target(draftId) ?: return LayoutV3JournalReadResult.Missing
        if (!file.isFile) return LayoutV3JournalReadResult.Missing
        val raw = readBounded(file)
        val decoded = raw?.let(::decode)
        if (decoded != null) return LayoutV3JournalReadResult.Ready(decoded)
        quarantine(file)
        LayoutV3JournalReadResult.Quarantined(LayoutV3RecoveryIssue.QUARANTINED_CORRUPT)
    }

    fun summaries(): List<RecoverableDraftSummary> {
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(SUFFIX) }
            .mapNotNull { file ->
                when (val result = read(file.name.removeSuffix(SUFFIX))) {
                    is LayoutV3JournalReadResult.Ready -> RecoverableDraftSummary(
                        result.entry.identity.layoutId,
                        result.entry.document.displayName,
                        result.entry.identity.origin,
                        result.entry.updatedAtEpochMillis,
                    )
                    else -> null
                }
            }
            .sortedWith(
                compareByDescending<RecoverableDraftSummary> { it.updatedAtEpochMillis }
                    .thenBy { it.draftId },
            )
    }

    fun discard(draftId: String): Boolean = synchronized(IO_LOCK) {
        val target = target(draftId) ?: return false
        !target.exists() || target.delete()
    }

    private fun decode(raw: ByteArray): LayoutV3JournalEntry? = runCatching {
        val root = StrictJsonV3.parse(raw) as StrictJsonV3Value.ObjectValue
        val expected = setOf(
            "journalFormat", "schemaVersion", "draftId", "origin", "sourceLayoutId",
            "sourceRevision", "variantId", "updatedAtEpochMillis", "artifact",
        )
        require(root.fields.keys == expected)
        require(root.string("journalFormat") == FORMAT)
        require(root.long("schemaVersion") == SCHEMA_VERSION)
        val layoutId = root.string("draftId")
        val variantId = root.string("variantId")
        val updated = root.long("updatedAtEpochMillis")
        require(updated in 0..MAX_EPOCH_MILLIS)
        val identity = LayoutV3DraftIdentity(
            layoutId,
            root.artifactRevision(),
            variantId,
            LayoutV3DraftOrigin.valueOf(root.string("origin")),
            root.optionalString("sourceLayoutId"),
            root.optionalLong("sourceRevision"),
        )
        require(validIdentity(identity))
        val artifact = root.fields.getValue("artifact") as StrictJsonV3Value.ObjectValue
        val artifactRaw = TouchLayoutV3Jcs.canonicalBytes(artifact)
        val document = TouchLayoutV3Codec.decodeDraft(artifactRaw)
        require(document.layoutId == layoutId)
        require(document.variants.any { it.variantId == variantId })
        LayoutV3JournalEntry(identity, updated, document)
    }.getOrNull()

    private fun StrictJsonV3Value.ObjectValue.artifactRevision(): Long =
        ((fields["artifact"] as? StrictJsonV3Value.ObjectValue)
            ?.fields?.get("revision") as? StrictJsonV3Value.IntegerValue)?.value
            ?: error("artifact revision")
    private fun StrictJsonV3Value.ObjectValue.string(name: String) =
        (fields[name] as StrictJsonV3Value.StringValue).value
    private fun StrictJsonV3Value.ObjectValue.long(name: String) =
        (fields[name] as StrictJsonV3Value.IntegerValue).value
    private fun StrictJsonV3Value.ObjectValue.optionalString(name: String) =
        when (val value = fields[name]) {
            StrictJsonV3Value.NullValue -> null
            is StrictJsonV3Value.StringValue -> value.value
            else -> error(name)
        }
    private fun StrictJsonV3Value.ObjectValue.optionalLong(name: String) =
        when (val value = fields[name]) {
            StrictJsonV3Value.NullValue -> null
            is StrictJsonV3Value.IntegerValue -> value.value
            else -> error(name)
        }

    private fun validIdentity(value: LayoutV3DraftIdentity): Boolean =
        LayoutContractV1Validator.normalizeUuid(value.layoutId) == value.layoutId &&
            LayoutContractV1Validator.normalizeUuid(value.variantId) == value.variantId &&
            LayoutContractV1Validator.isValidRevision(value.revision) &&
            (value.sourceLayoutId == null ||
                LayoutContractV1Validator.normalizeUuid(value.sourceLayoutId) ==
                value.sourceLayoutId) &&
            (value.sourceRevision == null ||
                LayoutContractV1Validator.isValidRevision(value.sourceRevision))

    private fun target(draftId: String): File? {
        if (LayoutContractV1Validator.normalizeUuid(draftId) != draftId) return null
        val file = File(directory, "$draftId$SUFFIX")
        return file.takeIf { it.parentFile?.canonicalFile == directory.canonicalFile }
    }
    private fun quarantine(file: File) {
        if (!quarantine.exists()) quarantine.mkdirs()
        val destination = File(quarantine, file.name)
        if (destination.parentFile?.canonicalFile == quarantine.canonicalFile) {
            if (destination.exists()) destination.delete()
            file.renameTo(destination)
        }
    }
    private fun readBounded(file: File): ByteArray? =
        file.takeIf { it.length() in 1..MAX_BYTES.toLong() }
            ?.let { runCatching { it.readBytes() }.getOrNull() }
            ?.takeIf { it.size <= MAX_BYTES }
    private fun atomicWrite(file: File, raw: ByteArray): Boolean {
        val atomic = AtomicFile(file)
        var stream: FileOutputStream? = null
        return try {
            stream = atomic.startWrite()
            stream.write(raw)
            stream.flush()
            stream.fd.sync()
            atomic.finishWrite(stream)
            true
        } catch (_: Exception) {
            stream?.let(atomic::failWrite)
            false
        }
    }
    private fun nullable(value: String?) =
        value?.let(StrictJsonV3Value::StringValue) ?: StrictJsonV3Value.NullValue
    private fun nullable(value: Long?) =
        value?.let(StrictJsonV3Value::IntegerValue) ?: StrictJsonV3Value.NullValue

    private companion object {
        const val FORMAT = "ligase-touch-layout-draft-journal"
        const val SCHEMA_VERSION = 1L
        const val DIRECTORY = "ligase-touch-layout-v3-drafts"
        const val QUARANTINE = "quarantine"
        const val SUFFIX = ".draft.json"
        const val MAX_BYTES = 1_048_576
        const val MAX_EPOCH_MILLIS = 253_402_300_799_999L
        val IO_LOCK = Any()
    }
}
