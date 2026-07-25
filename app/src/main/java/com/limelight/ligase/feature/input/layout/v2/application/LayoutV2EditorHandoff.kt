package com.limelight.ligase.feature.input.layout.v2.application

import com.limelight.ligase.layout.LayoutContractV1Validator

internal data class LayoutV2DraftLease(
    val draftId: String,
    val ownerToken: String,
    val generation: Long,
)

internal class LayoutV2DraftLeaseRegistry {
    private val leases = mutableMapOf<String, LayoutV2DraftLease>()
    private var generation = 0L

    @Synchronized
    fun acquire(draftId: String, ownerToken: String): LayoutV2DraftLease? {
        if (
            LayoutContractV1Validator.normalizeUuid(draftId) != draftId ||
            ownerToken.isBlank() ||
            leases.containsKey(draftId)
        ) {
            return null
        }
        return LayoutV2DraftLease(draftId, ownerToken, ++generation).also {
            leases[draftId] = it
        }
    }

    @Synchronized
    fun release(lease: LayoutV2DraftLease): Boolean {
        if (leases[lease.draftId] != lease) return false
        leases.remove(lease.draftId)
        return true
    }

    @Synchronized
    fun isCurrent(lease: LayoutV2DraftLease): Boolean = leases[lease.draftId] == lease
}

internal object LayoutV2EditorProcessLeases {
    val registry = LayoutV2DraftLeaseRegistry()
}
