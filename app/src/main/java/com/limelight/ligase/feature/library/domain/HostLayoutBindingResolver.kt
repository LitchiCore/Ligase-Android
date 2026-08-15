package com.limelight.ligase.feature.library.domain

import com.limelight.ligase.feature.input.layout.v3.application.LayoutV3CommittedLayoutSummary

enum class HostLayoutBindingState {
    NO_EXPLICIT_BINDING, RESOLVED, BINDING_NOT_FOUND, BINDING_RETIRED,
    BINDING_DRAFT_NOT_INSTALLED,
}

data class HostLayoutCatalogRevision(val layoutId: String, val revision: Long, val retired: Boolean)

data class HostLayoutBindingResolution(
    val state: HostLayoutBindingState,
    val layoutId: String? = null,
    val revision: Long? = null,
    val committed: LayoutV3CommittedLayoutSummary? = null,
) {
    override fun toString(): String =
        "HostLayoutBindingResolution(state=$state,identity=redacted,committed=${committed != null})"
}

object HostLayoutBindingResolver {
    fun resolve(
        binding: HostLayoutBinding?,
        committed: List<LayoutV3CommittedLayoutSummary>,
        knownRevisions: List<HostLayoutCatalogRevision> = emptyList(),
    ): HostLayoutBindingResolution {
        if (binding == null) return HostLayoutBindingResolution(HostLayoutBindingState.NO_EXPLICIT_BINDING)
        committed.singleOrNull {
            it.layoutId == binding.layoutId && it.revision == binding.revision && it.contentVerified
        }?.let {
            return HostLayoutBindingResolution(
                HostLayoutBindingState.RESOLVED, binding.layoutId, binding.revision, it,
            )
        }
        val descriptor = knownRevisions.singleOrNull {
            it.layoutId == binding.layoutId && it.revision == binding.revision
        }
        val state = when {
            descriptor == null -> HostLayoutBindingState.BINDING_NOT_FOUND
            descriptor.retired -> HostLayoutBindingState.BINDING_RETIRED
            else -> HostLayoutBindingState.BINDING_DRAFT_NOT_INSTALLED
        }
        return HostLayoutBindingResolution(state, binding.layoutId, binding.revision)
    }
}
