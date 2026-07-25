package com.limelight.ligase.feature.input.layout.v3.application

import com.limelight.ligase.feature.input.layout.v3.data.LayoutV3CommittedGeneration
import com.limelight.ligase.feature.input.layout.v3.serialization.LayoutV3ContentVerificationResult
import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3ContentVerifier

data class LayoutV3CommittedVariantSummary(
    val variantId: String,
    val controlCount: Int,
)

data class LayoutV3CommittedLayoutSummary(
    val layoutId: String,
    val revision: Long,
    val displayName: String,
    val variants: List<LayoutV3CommittedVariantSummary>,
    val contentVerified: Boolean = true,
    val localCopy: Boolean = true,
    val runtimeExecutable: Boolean = false,
) {
    override fun toString(): String =
        "LayoutV3CommittedLayoutSummary(identity=redacted,displayName=redacted," +
            "variants=${variants.size},contentVerified=$contentVerified," +
            "localCopy=$localCopy,runtimeExecutable=$runtimeExecutable)"
}

internal fun LayoutV3CommittedGeneration.toSafeSummary():
    LayoutV3CommittedLayoutSummary? {
    val verified = TouchLayoutV3ContentVerifier.verify(artifact)
        as? LayoutV3ContentVerificationResult.Verified
        ?: return null
    val document = verified.content.document
    if (
        document.layoutId != descriptor.layoutId ||
        document.revision != descriptor.revision
    ) {
        return null
    }
    return LayoutV3CommittedLayoutSummary(
        layoutId = document.layoutId,
        revision = document.revision,
        displayName = document.displayName,
        variants = document.variants.map {
            LayoutV3CommittedVariantSummary(it.variantId, it.elements.size)
        },
    )
}
