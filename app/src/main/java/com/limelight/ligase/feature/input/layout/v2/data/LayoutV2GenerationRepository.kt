package com.limelight.ligase.feature.input.layout.v2.data

import android.content.Context
import android.util.AtomicFile
import com.limelight.ligase.feature.input.layout.v2.application.LayoutCatalogV2Projector
import com.limelight.ligase.feature.input.layout.v2.application.TouchLayoutV2ContentVerifier
import com.limelight.ligase.feature.input.layout.v2.domain.LayoutContentVerificationResult
import com.limelight.ligase.feature.input.layout.v2.serialization.StrictJson
import com.limelight.ligase.feature.input.layout.v2.serialization.StrictJsonValue
import com.limelight.ligase.feature.input.layout.v2.serialization.TouchLayoutV2Jcs
import com.limelight.ligase.layout.*
import java.io.File
import java.io.FileOutputStream

enum class LayoutV2GenerationWriteCode {
    SAVED, INVALID_DESCRIPTOR, CONTENT_REJECTED, ALIGNMENT_INVALID,
    WRITE_FAILED, READBACK_FAILED, REGISTRATION_FAILED, ROLLBACK_FAILED,
}

data class LayoutV2GenerationWriteResult(val code: LayoutV2GenerationWriteCode)

data class LayoutV2CommittedGeneration(
    val descriptor: LayoutDescriptorV1,
    val artifact: ByteArray,
)

class LayoutV2GenerationRepository internal constructor(
    context: Context,
    private val afterWriteForTest: (File) -> Unit,
) {
    constructor(context: Context) : this(context, {})

    private val directory = File(context.applicationContext.filesDir, DIRECTORY)

    fun commit(
        descriptor: LayoutDescriptorV1,
        artifact: ByteArray,
        register: (LayoutDescriptorV1) -> Boolean = { true },
    ): LayoutV2GenerationWriteResult {
        if (LayoutContractV1Validator.validateDescriptor(descriptor) != null) {
            return result(LayoutV2GenerationWriteCode.INVALID_DESCRIPTOR)
        }
        val verified = when (val value = TouchLayoutV2ContentVerifier.verify(artifact)) {
            is LayoutContentVerificationResult.Verified -> value.content
            is LayoutContentVerificationResult.Rejected ->
                return result(LayoutV2GenerationWriteCode.CONTENT_REJECTED)
        }
        if (!LayoutCatalogV2Projector.contentAligned(descriptor, verified)) {
            return result(LayoutV2GenerationWriteCode.ALIGNMENT_INVALID)
        }
        val target = target(descriptor.layoutId, descriptor.revision)
            ?: return result(LayoutV2GenerationWriteCode.INVALID_DESCRIPTOR)
        if (!directory.exists() && !directory.mkdirs()) {
            return result(LayoutV2GenerationWriteCode.WRITE_FAILED)
        }
        val old = target.takeIf(File::isFile)?.let { readBounded(it) }
        val artifactObject = StrictJson.parse(artifact) as StrictJsonValue.ObjectValue
        val canonicalArtifact = TouchLayoutV2Jcs.canonicalBytes(artifactObject)
        val bundle = StrictJsonValue.ObjectValue(
            linkedMapOf(
                "generationFormat" to StrictJsonValue.StringValue(FORMAT),
                "schemaVersion" to StrictJsonValue.IntegerValue(1),
                "descriptor" to descriptorValue(descriptor),
                "artifact" to artifactObject,
            ),
        )
        val raw = TouchLayoutV2Jcs.canonicalBytes(bundle)
        if (!atomicWrite(target, raw)) return result(LayoutV2GenerationWriteCode.WRITE_FAILED)
        runCatching { afterWriteForTest(target) }
        val readback = read(descriptor.layoutId, descriptor.revision)
        if (
            readback == null ||
            readback.descriptor != descriptor ||
            !readback.artifact.contentEquals(canonicalArtifact)
        ) {
            val restored = if (old == null) {
                !target.exists() || target.delete()
            } else {
                if (target.exists()) target.delete()
                atomicWrite(target, old)
            }
            return result(
                if (restored) LayoutV2GenerationWriteCode.READBACK_FAILED
                else LayoutV2GenerationWriteCode.ROLLBACK_FAILED,
            )
        }
        if (!register(descriptor)) {
            val restored = restore(target, old)
            return result(
                if (restored) LayoutV2GenerationWriteCode.REGISTRATION_FAILED
                else LayoutV2GenerationWriteCode.ROLLBACK_FAILED,
            )
        }
        return result(LayoutV2GenerationWriteCode.SAVED)
    }

    fun read(layoutId: String, revision: Long): LayoutV2CommittedGeneration? {
        val file = target(layoutId, revision) ?: return null
        return readFile(file)?.takeIf {
            it.descriptor.layoutId == layoutId && it.descriptor.revision == revision
        }
    }

    fun listCommitted(): List<LayoutV2CommittedGeneration> {
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(SUFFIX) }
            .mapNotNull(::readFile)
            .distinctBy { it.descriptor.layoutId to it.descriptor.revision }
            .sortedWith(
                compareBy<LayoutV2CommittedGeneration>(
                    { it.descriptor.layoutId },
                    { it.descriptor.revision },
                ),
            )
    }

    private fun readFile(file: File): LayoutV2CommittedGeneration? {
        val raw = readBounded(file) ?: return null
        return runCatching {
            val root = StrictJson.parse(raw) as StrictJsonValue.ObjectValue
            require(root.fields.keys == setOf("generationFormat", "schemaVersion", "descriptor", "artifact"))
            require((root.fields["generationFormat"] as StrictJsonValue.StringValue).value == FORMAT)
            require((root.fields["schemaVersion"] as StrictJsonValue.IntegerValue).value == 1L)
            val descriptor = parseDescriptor(root.fields["descriptor"] as StrictJsonValue.ObjectValue)
            val artifact = TouchLayoutV2Jcs.canonicalBytes(
                root.fields["artifact"] as StrictJsonValue.ObjectValue,
            )
            val verified = TouchLayoutV2ContentVerifier.verify(artifact)
            require(verified is LayoutContentVerificationResult.Verified)
            require(LayoutCatalogV2Projector.contentAligned(descriptor, verified.content))
            LayoutV2CommittedGeneration(descriptor, artifact)
        }.getOrNull()
    }

    fun export(layoutId: String, revision: Long): ByteArray? =
        read(layoutId, revision)?.artifact?.copyOf()

    private fun descriptorValue(value: LayoutDescriptorV1) = objectValue(
        "schemaVersion" to integer(value.schemaVersion),
        "layoutId" to string(value.layoutId),
        "revision" to integer(value.revision),
        "portableIdentities" to array(value.portableIdentities.map {
            objectValue("provider" to string(it.provider), "id" to string(it.id))
        }),
        "compatibility" to objectValue(
            "minClientContractVersion" to integer(value.compatibility.minClientContractVersion),
            "minLayoutRuntimeVersion" to integer(value.compatibility.minLayoutRuntimeVersion),
        ),
        "publicationStatus" to string(value.publicationStatus),
        "variants" to array(value.variants.map {
            objectValue(
                "variantId" to string(it.variantId),
                "inputProfile" to string(it.inputProfile),
                "deviceClasses" to array(it.deviceClasses.map(::string)),
                "orientations" to array(it.orientations.map(::string)),
            )
        }),
    )

    private fun parseDescriptor(value: StrictJsonValue.ObjectValue): LayoutDescriptorV1 {
        fun text(obj: StrictJsonValue.ObjectValue, key: String) =
            (obj.fields.getValue(key) as StrictJsonValue.StringValue).value
        fun number(obj: StrictJsonValue.ObjectValue, key: String) =
            (obj.fields.getValue(key) as StrictJsonValue.IntegerValue).value
        fun objects(obj: StrictJsonValue.ObjectValue, key: String) =
            (obj.fields.getValue(key) as StrictJsonValue.ArrayValue).values
                .map { it as StrictJsonValue.ObjectValue }
        val compatibility = value.fields.getValue("compatibility") as StrictJsonValue.ObjectValue
        return LayoutDescriptorV1(
            number(value, "schemaVersion").toInt(),
            text(value, "layoutId"),
            number(value, "revision"),
            objects(value, "portableIdentities").map {
                PortableGameIdentityV1(text(it, "provider"), text(it, "id"))
            },
            LayoutCompatibilityV1(
                number(compatibility, "minClientContractVersion").toInt(),
                number(compatibility, "minLayoutRuntimeVersion").toInt(),
            ),
            text(value, "publicationStatus"),
            objects(value, "variants").map {
                LayoutVariantV1(
                    text(it, "variantId"),
                    text(it, "inputProfile"),
                    (it.fields.getValue("deviceClasses") as StrictJsonValue.ArrayValue)
                        .values.map { item -> (item as StrictJsonValue.StringValue).value },
                    (it.fields.getValue("orientations") as StrictJsonValue.ArrayValue)
                        .values.map { item -> (item as StrictJsonValue.StringValue).value },
                )
            },
        ).also { require(LayoutContractV1Validator.validateDescriptor(it) == null) }
    }

    private fun target(layoutId: String, revision: Long): File? {
        if (
            LayoutContractV1Validator.normalizeUuid(layoutId) != layoutId ||
            !LayoutContractV1Validator.isValidRevision(revision)
        ) return null
        val file = File(directory, "$layoutId-$revision$SUFFIX")
        return file.takeIf { it.parentFile?.canonicalFile == directory.canonicalFile }
    }
    private fun readBounded(file: File): ByteArray? =
        file.takeIf { it.length() in 1..MAX_BYTES.toLong() }
            ?.let { runCatching { it.readBytes() }.getOrNull() }
            ?.takeIf { it.size <= MAX_BYTES }
    private fun atomicWrite(file: File, raw: ByteArray): Boolean {
        if (raw.size > MAX_BYTES) return false
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
    private fun restore(target: File, old: ByteArray?): Boolean =
        if (old == null) {
            !target.exists() || target.delete()
        } else {
            if (target.exists() && !target.delete()) false else atomicWrite(target, old)
        }
    private fun result(code: LayoutV2GenerationWriteCode) = LayoutV2GenerationWriteResult(code)
    private fun objectValue(vararg pairs: Pair<String, StrictJsonValue>) =
        StrictJsonValue.ObjectValue(linkedMapOf(*pairs))
    private fun array(values: List<StrictJsonValue>) = StrictJsonValue.ArrayValue(values)
    private fun string(value: String) = StrictJsonValue.StringValue(value)
    private fun integer(value: Int) = StrictJsonValue.IntegerValue(value.toLong())
    private fun integer(value: Long) = StrictJsonValue.IntegerValue(value)

    private companion object {
        const val FORMAT = "ligase-touch-layout-generation"
        const val DIRECTORY = "ligase-touch-layout-v2-generations"
        const val SUFFIX = ".generation.json"
        const val MAX_BYTES = 2_097_152
    }
}
