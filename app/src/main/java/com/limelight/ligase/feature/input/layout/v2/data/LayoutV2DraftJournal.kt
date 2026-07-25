package com.limelight.ligase.feature.input.layout.v2.data

import android.content.Context
import android.util.AtomicFile
import com.limelight.ligase.feature.input.layout.v2.application.TouchLayoutV2ContentVerifier
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutContentVerificationResult
import com.limelight.ligase.feature.input.layout.v2.domain.TouchLayoutV2Document
import com.limelight.ligase.feature.input.layout.v2.editor.*
import com.limelight.ligase.feature.input.layout.v2.serialization.StrictJson
import com.limelight.ligase.feature.input.layout.v2.serialization.StrictJsonValue
import com.limelight.ligase.feature.input.layout.v2.serialization.TouchLayoutV2Codec
import com.limelight.ligase.feature.input.layout.v2.serialization.TouchLayoutV2Jcs
import com.limelight.ligase.layout.LayoutContractV1Validator
import java.io.File
import java.io.FileOutputStream

data class LayoutV2JournalEntry(
    val identity: LayoutV2DraftIdentity,
    val updatedAtEpochMillis: Long,
    val document: TouchLayoutV2Document,
) {
    override fun toString(): String =
        "LayoutV2JournalEntry(identity=redacted,updatedAt=redacted,document=redacted)"
}

sealed interface LayoutV2JournalReadResult {
    data class Ready(val entry: LayoutV2JournalEntry) : LayoutV2JournalReadResult
    data object Missing : LayoutV2JournalReadResult
    data class Quarantined(val issue: LayoutV2RecoveryIssue) : LayoutV2JournalReadResult
}

enum class LayoutV2JournalWriteResult { SAVED, INVALID, WRITE_FAILED, READBACK_FAILED }

class LayoutV2DraftJournal internal constructor(
    context: Context,
    private val clock: () -> Long,
    private val afterWriteForTest: (File) -> Unit,
) {
    constructor(context: Context) : this(context, System::currentTimeMillis, {})

    private val directory = File(context.applicationContext.filesDir, DIRECTORY)
    private val quarantine = File(directory, QUARANTINE)

    fun write(
        identity: LayoutV2DraftIdentity,
        documentRaw: ByteArray,
    ): LayoutV2JournalWriteResult = synchronized(IO_LOCK) {
        writeLocked(identity, documentRaw)
    }

    private fun writeLocked(
        identity: LayoutV2DraftIdentity,
        documentRaw: ByteArray,
    ): LayoutV2JournalWriteResult {
        if (!validIdentity(identity)) return LayoutV2JournalWriteResult.INVALID
        val verified = runCatching { TouchLayoutV2Codec.decodeDraft(documentRaw) }.getOrNull()
            ?: return LayoutV2JournalWriteResult.INVALID
        if (
            verified.layoutId != identity.layoutId ||
            verified.revision != identity.revision ||
            verified.variants.none { it.variantId == identity.variantId }
        ) {
            return LayoutV2JournalWriteResult.INVALID
        }
        val now = clock()
        if (now !in 0..MAX_EPOCH_MILLIS) return LayoutV2JournalWriteResult.INVALID
        val previous = read(identity.layoutId)
        val updatedAt = if (previous is LayoutV2JournalReadResult.Ready) {
            maxOf(previous.entry.updatedAtEpochMillis, now)
        } else {
            now
        }
        val artifact = StrictJson.parse(documentRaw) as? StrictJsonValue.ObjectValue
            ?: return LayoutV2JournalWriteResult.INVALID
        val root = StrictJsonValue.ObjectValue(
            linkedMapOf(
                "journalFormat" to StrictJsonValue.StringValue(FORMAT),
                "schemaVersion" to StrictJsonValue.IntegerValue(SCHEMA_VERSION),
                "draftId" to StrictJsonValue.StringValue(identity.layoutId),
                "origin" to StrictJsonValue.StringValue(identity.origin.name),
                "sourceLayoutId" to nullable(identity.sourceLayoutId),
                "sourceRevision" to nullable(identity.sourceRevision),
                "variantId" to StrictJsonValue.StringValue(identity.variantId),
                "updatedAtEpochMillis" to StrictJsonValue.IntegerValue(updatedAt),
                "artifact" to artifact,
            ),
        )
        val raw = TouchLayoutV2Jcs.canonicalBytes(root)
        if (raw.size > MAX_BYTES || !directory.exists() && !directory.mkdirs()) {
            return LayoutV2JournalWriteResult.WRITE_FAILED
        }
        val target = target(identity.layoutId) ?: return LayoutV2JournalWriteResult.INVALID
        if (!atomicWrite(target, raw)) return LayoutV2JournalWriteResult.WRITE_FAILED
        runCatching { afterWriteForTest(target) }
        val readback = readBounded(target)
        if (readback == null || !readback.contentEquals(raw) || decode(readback) == null) {
            return LayoutV2JournalWriteResult.READBACK_FAILED
        }
        return LayoutV2JournalWriteResult.SAVED
    }

    fun read(draftId: String): LayoutV2JournalReadResult = synchronized(IO_LOCK) {
        val file = target(draftId) ?: return LayoutV2JournalReadResult.Missing
        if (!file.isFile) return LayoutV2JournalReadResult.Missing
        val raw = readBounded(file)
        val decoded = raw?.let(::decode)
        if (decoded != null) return LayoutV2JournalReadResult.Ready(decoded)
        quarantine(file)
        LayoutV2JournalReadResult.Quarantined(LayoutV2RecoveryIssue.QUARANTINED_CORRUPT)
    }

    fun summaries(): List<RecoverableDraftSummary> {
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(SUFFIX) }
            .mapNotNull { file ->
                when (val result = read(file.name.removeSuffix(SUFFIX))) {
                    is LayoutV2JournalReadResult.Ready -> RecoverableDraftSummary(
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

    private fun decode(raw: ByteArray): LayoutV2JournalEntry? = runCatching {
        val root = StrictJson.parse(raw) as StrictJsonValue.ObjectValue
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
        val identity = LayoutV2DraftIdentity(
            layoutId,
            root.artifactRevision(),
            variantId,
            LayoutV2DraftOrigin.valueOf(root.string("origin")),
            root.optionalString("sourceLayoutId"),
            root.optionalLong("sourceRevision"),
        )
        require(validIdentity(identity))
        val artifact = root.fields.getValue("artifact") as StrictJsonValue.ObjectValue
        val artifactRaw = TouchLayoutV2Jcs.canonicalBytes(artifact)
        val document = TouchLayoutV2Codec.decodeDraft(artifactRaw)
        require(document.layoutId == layoutId)
        require(document.variants.any { it.variantId == variantId })
        LayoutV2JournalEntry(identity, updated, document)
    }.getOrNull()

    private fun StrictJsonValue.ObjectValue.artifactRevision(): Long =
        ((fields["artifact"] as? StrictJsonValue.ObjectValue)
            ?.fields?.get("revision") as? StrictJsonValue.IntegerValue)?.value
            ?: error("artifact revision")
    private fun StrictJsonValue.ObjectValue.string(name: String) =
        (fields[name] as StrictJsonValue.StringValue).value
    private fun StrictJsonValue.ObjectValue.long(name: String) =
        (fields[name] as StrictJsonValue.IntegerValue).value
    private fun StrictJsonValue.ObjectValue.optionalString(name: String) =
        when (val value = fields[name]) {
            StrictJsonValue.NullValue -> null
            is StrictJsonValue.StringValue -> value.value
            else -> error(name)
        }
    private fun StrictJsonValue.ObjectValue.optionalLong(name: String) =
        when (val value = fields[name]) {
            StrictJsonValue.NullValue -> null
            is StrictJsonValue.IntegerValue -> value.value
            else -> error(name)
        }

    private fun validIdentity(value: LayoutV2DraftIdentity): Boolean =
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
        value?.let(StrictJsonValue::StringValue) ?: StrictJsonValue.NullValue
    private fun nullable(value: Long?) =
        value?.let(StrictJsonValue::IntegerValue) ?: StrictJsonValue.NullValue

    private companion object {
        const val FORMAT = "ligase-touch-layout-draft-journal"
        const val SCHEMA_VERSION = 1L
        const val DIRECTORY = "ligase-touch-layout-v2-drafts"
        const val QUARANTINE = "quarantine"
        const val SUFFIX = ".draft.json"
        const val MAX_BYTES = 1_048_576
        const val MAX_EPOCH_MILLIS = 253_402_300_799_999L
        val IO_LOCK = Any()
    }
}
