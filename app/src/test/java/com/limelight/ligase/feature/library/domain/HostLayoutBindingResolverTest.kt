package com.limelight.ligase.feature.library.domain

import com.limelight.ligase.feature.input.layout.v3.application.LayoutV3CommittedLayoutSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostLayoutBindingResolverTest {
    private val binding = HostLayoutBinding("0b7cd40f-64ae-4eac-845a-fb41dfed80d0", 7)

    @Test fun `exact committed revision resolves without name or identity fallback`() {
        val exact = summary(binding.layoutId, 7)
        val result = HostLayoutBindingResolver.resolve(binding, listOf(summary(binding.layoutId, 6), exact))
        assertEquals(HostLayoutBindingState.RESOLVED, result.state)
        assertEquals(exact, result.committed)
    }

    @Test fun `unknown retired and uninstalled revisions remain distinct and never fallback`() {
        val unknown = HostLayoutBindingResolver.resolve(binding, listOf(summary("11111111-1111-4111-8111-111111111111", 7)))
        val retired = HostLayoutBindingResolver.resolve(binding, emptyList(), listOf(HostLayoutCatalogRevision(binding.layoutId, 7, true)))
        val missingDraft = HostLayoutBindingResolver.resolve(binding, emptyList(), listOf(HostLayoutCatalogRevision(binding.layoutId, 7, false)))
        assertEquals(HostLayoutBindingState.BINDING_NOT_FOUND, unknown.state)
        assertEquals(HostLayoutBindingState.BINDING_RETIRED, retired.state)
        assertEquals(HostLayoutBindingState.BINDING_DRAFT_NOT_INSTALLED, missingDraft.state)
        assertNull(unknown.committed)
        assertNull(retired.committed)
        assertNull(missingDraft.committed)
    }

    @Test fun `absence is not automatic matching`() {
        assertEquals(HostLayoutBindingState.NO_EXPLICIT_BINDING, HostLayoutBindingResolver.resolve(null, emptyList()).state)
    }

    private fun summary(id: String, revision: Long) = LayoutV3CommittedLayoutSummary(id, revision, "redacted", emptyList())
}
