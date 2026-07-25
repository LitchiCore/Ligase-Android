package com.limelight.ligase.feature.input.layout.v3.data

import android.content.Context
import android.util.AtomicFile
import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3Codec
import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3ContentVerifier
import com.limelight.ligase.feature.input.layout.v3.serialization.LayoutV3ContentVerificationResult
import com.limelight.ligase.feature.input.layout.v3.serialization.StrictJsonV3
import com.limelight.ligase.feature.input.layout.v3.serialization.StrictJsonV3Value
import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3Jcs
import com.limelight.ligase.layout.*
import java.io.File
import java.io.FileOutputStream

enum class LayoutV3GenerationWriteCode {
    SAVED, INVALID_DESCRIPTOR, CONTENT_REJECTED, ALIGNMENT_INVALID,
    WRITE_FAILED, READBACK_FAILED, REGISTRATION_FAILED, ROLLBACK_FAILED,
}

data class LayoutV3GenerationWriteResult(val code: LayoutV3GenerationWriteCode)

data class LayoutV3CommittedGeneration(
    val descriptor: LayoutDescriptorV1,
    val artifact: ByteArray,
)

class LayoutV3GenerationRepository internal constructor(
    context: Context,
    private val afterWriteForTest: (File) -> Unit,
    gate: LayoutV3RepositoryGate = LayoutV3CutoverCoordinator.from(context).let {
        it.ensureReady()
        it.gate
    },
) {
    constructor(context: Context) : this(context, {})

    private val directory = File(context.applicationContext.filesDir, DIRECTORY)

    init {
        gate.requireReady()
    }

    fun commit(
        descriptor: LayoutDescriptorV1,
        artifact: ByteArray,
        register: (LayoutDescriptorV1) -> Boolean = { true },
    ): LayoutV3GenerationWriteResult {
        if (LayoutContractV1Validator.validateDescriptor(descriptor) != null) {
            return result(LayoutV3GenerationWriteCode.INVALID_DESCRIPTOR)
        }
        val verified = when (val value = TouchLayoutV3ContentVerifier.verify(artifact)) {
            is LayoutV3ContentVerificationResult.Verified -> value.content
            is LayoutV3ContentVerificationResult.Rejected ->
                return result(LayoutV3GenerationWriteCode.CONTENT_REJECTED)
        }
        if (!contentAligned(descriptor, verified.document)) {
            return result(LayoutV3GenerationWriteCode.ALIGNMENT_INVALID)
        }
        val target = target(descriptor.layoutId, descriptor.revision)
            ?: return result(LayoutV3GenerationWriteCode.INVALID_DESCRIPTOR)
        if (!directory.exists() && !directory.mkdirs()) {
            return result(LayoutV3GenerationWriteCode.WRITE_FAILED)
        }
        val old = target.takeIf(File::isFile)?.let { readBounded(it) }
        val artifactObject = StrictJsonV3.parse(artifact) as StrictJsonV3Value.ObjectValue
        val canonicalArtifact = TouchLayoutV3Jcs.canonicalBytes(artifactObject)
        val bundle = StrictJsonV3Value.ObjectValue(
            linkedMapOf(
                "generationFormat" to StrictJsonV3Value.StringValue(FORMAT),
                "schemaVersion" to StrictJsonV3Value.IntegerValue(1),
                "descriptor" to descriptorValue(descriptor),
                "artifact" to artifactObject,
            ),
        )
        val raw = TouchLayoutV3Jcs.canonicalBytes(bundle)
        if (!atomicWrite(target, raw)) return result(LayoutV3GenerationWriteCode.WRITE_FAILED)
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
                if (restored) LayoutV3GenerationWriteCode.READBACK_FAILED
                else LayoutV3GenerationWriteCode.ROLLBACK_FAILED,
            )
        }
        if (!register(descriptor)) {
            val restored = restore(target, old)
            return result(
                if (restored) LayoutV3GenerationWriteCode.REGISTRATION_FAILED
                else LayoutV3GenerationWriteCode.ROLLBACK_FAILED,
            )
        }
        return result(LayoutV3GenerationWriteCode.SAVED)
    }

    fun read(layoutId: String, revision: Long): LayoutV3CommittedGeneration? {
        val file = target(layoutId, revision) ?: return null
        return readFile(file)?.takeIf {
            it.descriptor.layoutId == layoutId && it.descriptor.revision == revision
        }
    }

    fun listCommitted(): List<LayoutV3CommittedGeneration> {
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.name.endsWith(SUFFIX) }
            .mapNotNull(::readFile)
            .distinctBy { it.descriptor.layoutId to it.descriptor.revision }
            .sortedWith(
                compareBy<LayoutV3CommittedGeneration>(
                    { it.descriptor.layoutId },
                    { it.descriptor.revision },
                ),
            )
    }

    private fun readFile(file: File): LayoutV3CommittedGeneration? {
        val raw = readBounded(file) ?: return null
        return runCatching {
            val root = StrictJsonV3.parse(raw) as StrictJsonV3Value.ObjectValue
            require(root.fields.keys == setOf("generationFormat", "schemaVersion", "descriptor", "artifact"))
            require((root.fields["generationFormat"] as StrictJsonV3Value.StringValue).value == FORMAT)
            require((root.fields["schemaVersion"] as StrictJsonV3Value.IntegerValue).value == 1L)
            val descriptor = parseDescriptor(root.fields["descriptor"] as StrictJsonV3Value.ObjectValue)
            val artifact = TouchLayoutV3Jcs.canonicalBytes(
                root.fields["artifact"] as StrictJsonV3Value.ObjectValue,
            )
            val verified = TouchLayoutV3ContentVerifier.verify(artifact)
            require(verified is LayoutV3ContentVerificationResult.Verified)
            require(contentAligned(descriptor, verified.content.document))
            LayoutV3CommittedGeneration(descriptor, artifact)
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

    private fun contentAligned(
        descriptor: LayoutDescriptorV1,
        document: com.limelight.ligase.feature.input.layout.v3.domain.TouchLayoutV3Document,
    ): Boolean = runCatching {
        val projection = com.limelight.ligase.feature.input.layout.v3.domain.LayoutDescriptorProjection(
            descriptor.layoutId,
            descriptor.revision,
            descriptor.variants.map { variant ->
                com.limelight.ligase.feature.input.layout.v3.domain.DescriptorVariantProjection(
                    variant.variantId,
                    variant.deviceClasses.map {
                        com.limelight.ligase.feature.input.layout.v3.domain.DeviceClass.valueOf(
                            it.uppercase(),
                        )
                    },
                    variant.orientations.map {
                        com.limelight.ligase.feature.input.layout.v3.domain.LayoutOrientation.valueOf(
                            it.uppercase(),
                        )
                    },
                )
            },
        )
        com.limelight.ligase.feature.input.layout.v3.domain.TouchLayoutV3Validator
            .validateDescriptorAlignment(document, projection)
    }.isSuccess

    private fun parseDescriptor(value: StrictJsonV3Value.ObjectValue): LayoutDescriptorV1 {
        fun text(obj: StrictJsonV3Value.ObjectValue, key: String) =
            (obj.fields.getValue(key) as StrictJsonV3Value.StringValue).value
        fun number(obj: StrictJsonV3Value.ObjectValue, key: String) =
            (obj.fields.getValue(key) as StrictJsonV3Value.IntegerValue).value
        fun objects(obj: StrictJsonV3Value.ObjectValue, key: String) =
            (obj.fields.getValue(key) as StrictJsonV3Value.ArrayValue).values
                .map { it as StrictJsonV3Value.ObjectValue }
        val compatibility = value.fields.getValue("compatibility") as StrictJsonV3Value.ObjectValue
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
                    (it.fields.getValue("deviceClasses") as StrictJsonV3Value.ArrayValue)
                        .values.map { item -> (item as StrictJsonV3Value.StringValue).value },
                    (it.fields.getValue("orientations") as StrictJsonV3Value.ArrayValue)
                        .values.map { item -> (item as StrictJsonV3Value.StringValue).value },
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
    private fun result(code: LayoutV3GenerationWriteCode) = LayoutV3GenerationWriteResult(code)
    private fun objectValue(vararg pairs: Pair<String, StrictJsonV3Value>) =
        StrictJsonV3Value.ObjectValue(linkedMapOf(*pairs))
    private fun array(values: List<StrictJsonV3Value>) = StrictJsonV3Value.ArrayValue(values)
    private fun string(value: String) = StrictJsonV3Value.StringValue(value)
    private fun integer(value: Int) = StrictJsonV3Value.IntegerValue(value.toLong())
    private fun integer(value: Long) = StrictJsonV3Value.IntegerValue(value)

    private companion object {
        const val FORMAT = "ligase-touch-layout-generation"
        const val DIRECTORY = "ligase-touch-layout-v3-generations"
        const val SUFFIX = ".generation.json"
        const val MAX_BYTES = 2_097_152
    }
}
