package com.limelight.ligase.feature.input.layout.v3.application

import com.limelight.ligase.layout.LayoutContractV1Validator

internal data class LayoutV3DraftLease(
    val draftId: String,
    val ownerToken: String,
    val generation: Long,
)

internal class LayoutV3DraftLeaseRegistry {
    private val leases = mutableMapOf<String, LayoutV3DraftLease>()
    private var generation = 0L

    @Synchronized
    fun acquire(draftId: String, ownerToken: String): LayoutV3DraftLease? {
        if (
            LayoutContractV1Validator.normalizeUuid(draftId) != draftId ||
            ownerToken.isBlank() ||
            leases.containsKey(draftId)
        ) {
            return null
        }
        return LayoutV3DraftLease(draftId, ownerToken, ++generation).also {
            leases[draftId] = it
        }
    }

    @Synchronized
    fun release(lease: LayoutV3DraftLease): Boolean {
        if (leases[lease.draftId] != lease) return false
        leases.remove(lease.draftId)
        return true
    }

    @Synchronized
    fun isCurrent(lease: LayoutV3DraftLease): Boolean = leases[lease.draftId] == lease
}

internal object LayoutV3EditorProcessLeases {
    val registry = LayoutV3DraftLeaseRegistry()
}
