package com.limelight.ligase.feature.input.layout.v3.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.limelight.ligase.feature.input.layout.v3.domain.*
import com.limelight.ligase.feature.input.layout.v3.serialization.TouchLayoutV3Codec
import com.limelight.ligase.layout.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LayoutV3GenerationRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun commitReadbackAndFreshRepositoryEnumerationAreStrict() {
        val raw = fixture()
        val document = TouchLayoutV3Codec.decode(raw)
        val descriptor = LayoutDescriptorV1(
            1, document.layoutId, document.revision, emptyList(),
            LayoutCompatibilityV1(1, 1), "draft",
            document.variants.map {
                LayoutVariantV1(
                    it.variantId, "touch",
                    it.deviceClasses.map { value -> value.name.lowercase() },
                    it.orientations.map { value -> value.name.lowercase() },
                )
            },
        )
        val first = LayoutV3GenerationRepository(context)
        assertEquals(LayoutV3GenerationWriteCode.SAVED, first.commit(descriptor, raw).code)
        val fresh = LayoutV3GenerationRepository(context)
        assertEquals(descriptor, fresh.listCommitted().single().descriptor)
    }

    @Test(expected = LayoutV3RepositoryClosedException::class)
    fun repositoryConstructionFailsClosedBeforeCutoverReady() {
        LayoutV3GenerationRepository(
            context,
            {},
            LayoutV3RepositoryGate { LayoutV3CutoverState.FAILED_CLOSED },
        )
    }

    private fun fixture() = File(
        System.getProperty("user.dir"),
        "../tests/fixtures/ligase-touch-layout-v3-positive.json",
    ).readBytes()
}
