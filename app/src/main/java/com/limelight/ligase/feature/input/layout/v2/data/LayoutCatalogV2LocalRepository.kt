package com.limelight.ligase.feature.input.layout.v2.data

import android.content.Context
import android.util.AtomicFile
import com.limelight.ligase.feature.input.layout.v2.application.TouchLayoutV2ContentVerifier
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutContentVerificationResult
import com.limelight.ligase.feature.input.layout.v2.domain.VerifiedTouchLayoutV2Content
import com.limelight.ligase.layout.LayoutContractV1Validator
import java.io.File
import java.io.FileOutputStream

sealed interface LayoutCatalogV2LocalReadResult {
    data class Ready(
        val content: VerifiedTouchLayoutV2Content,
        val raw: ByteArray,
    ) : LayoutCatalogV2LocalReadResult
    data object Missing : LayoutCatalogV2LocalReadResult
    data class Rejected(val code: String) : LayoutCatalogV2LocalReadResult
    data object StorageFailure : LayoutCatalogV2LocalReadResult
}

enum class LayoutCatalogV2LocalWriteResult {
    SAVED,
    INVALID_IDENTITY,
    WRITE_FAILED,
    READBACK_FAILED,
    ROLLBACK_FAILED,
}

class LayoutCatalogV2LocalRepository internal constructor(
    context: Context,
    private val afterWriteForTest: (File) -> Unit,
) {
    constructor(context: Context) : this(context, {})

    private val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME)

    fun read(layoutId: String, revision: Long): LayoutCatalogV2LocalReadResult {
        val target = target(layoutId, revision) ?: return LayoutCatalogV2LocalReadResult.Rejected(
            "invalidIdentity",
        )
        if (!target.isFile) return LayoutCatalogV2LocalReadResult.Missing
        val raw = readBounded(target) ?: return LayoutCatalogV2LocalReadResult.StorageFailure
        return when (val verified = TouchLayoutV2ContentVerifier.verify(raw)) {
            is LayoutContentVerificationResult.Verified ->
                LayoutCatalogV2LocalReadResult.Ready(verified.content, raw)
            is LayoutContentVerificationResult.Rejected ->
                LayoutCatalogV2LocalReadResult.Rejected(verified.code)
        }
    }

    fun write(
        layoutId: String,
        revision: Long,
        raw: ByteArray,
    ): LayoutCatalogV2LocalWriteResult {
        val target = target(layoutId, revision)
            ?: return LayoutCatalogV2LocalWriteResult.INVALID_IDENTITY
        if (!directory.exists() && !directory.mkdirs()) {
            return LayoutCatalogV2LocalWriteResult.WRITE_FAILED
        }
        val old = when {
            !target.exists() -> null
            target.length() > MAX_BYTES -> return LayoutCatalogV2LocalWriteResult.WRITE_FAILED
            else -> runCatching { target.readBytes() }.getOrNull()
                ?: return LayoutCatalogV2LocalWriteResult.WRITE_FAILED
        }
        if (!atomicWrite(target, raw)) return LayoutCatalogV2LocalWriteResult.WRITE_FAILED
        runCatching { afterWriteForTest(target) }
        val readback = readBounded(target)
        val verified = readback?.let(TouchLayoutV2ContentVerifier::verify)
        if (
            readback == null ||
            !readback.contentEquals(raw) ||
            verified !is LayoutContentVerificationResult.Verified
        ) {
            val restored = if (old == null) {
                !target.exists() || target.delete()
            } else {
                restorePreviousBytes(target, old)
            }
            return if (restored) {
                LayoutCatalogV2LocalWriteResult.READBACK_FAILED
            } else {
                LayoutCatalogV2LocalWriteResult.ROLLBACK_FAILED
            }
        }
        return LayoutCatalogV2LocalWriteResult.SAVED
    }

    internal fun fileForTest(layoutId: String, revision: Long): File? =
        target(layoutId, revision)

    private fun target(layoutId: String, revision: Long): File? {
        val canonical = LayoutContractV1Validator.normalizeUuid(layoutId)
        if (
            canonical == null ||
            canonical != layoutId ||
            !LayoutContractV1Validator.isValidRevision(revision)
        ) {
            return null
        }
        val file = File(directory, "$canonical-$revision.json")
        return file.takeIf { it.parentFile?.canonicalFile == directory.canonicalFile }
    }

    private fun readBounded(file: File): ByteArray? {
        if (file.length() !in 0..MAX_BYTES.toLong()) return null
        return runCatching { file.readBytes() }
            .getOrNull()
            ?.takeIf { it.size <= MAX_BYTES }
    }

    private fun atomicWrite(target: File, raw: ByteArray): Boolean {
        if (raw.size > MAX_BYTES) return false
        val atomic = AtomicFile(target)
        var output: FileOutputStream? = null
        return try {
            output = atomic.startWrite()
            output.write(raw)
            output.flush()
            output.fd.sync()
            atomic.finishWrite(output)
            true
        } catch (_: Exception) {
            output?.let(atomic::failWrite)
            false
        }
    }

    private fun restorePreviousBytes(target: File, old: ByteArray): Boolean {
        // AtomicFile has already committed the candidate before readback. Remove that
        // rejected base explicitly so a fresh AtomicFile transaction cannot retain it
        // as the backup chosen by platform-specific recovery implementations.
        if (target.exists() && !target.delete()) return false
        return atomicWrite(target, old) &&
            readBounded(target)?.contentEquals(old) == true
    }

    private companion object {
        const val DIRECTORY_NAME = "ligase-touch-layout-v2"
        const val MAX_BYTES = 1_048_576
    }
}
