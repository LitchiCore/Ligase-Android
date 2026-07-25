package com.limelight.ligase.input

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LigaseTouchLayoutRepositoryTest {
    private lateinit var context: Context
    private lateinit var repository: LigaseTouchLayoutRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("ligase_product_preferences", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        repository = LigaseTouchLayoutRepository(context)
    }

    @Test
    fun `residual v1 repository is closed and performs zero preference writes`() {
        context.getSharedPreferences("ligase_product_preferences", Context.MODE_PRIVATE)
            .edit()
            .putString("global_touch_layout", "sentinel")
            .commit()
        val available = listOf(
            LigaseTouchLayout("OSC_Keyboard_1", "默认布局"),
            LigaseTouchLayout("OSC_Keyboard_2", "第二布局"),
        )

        assertEquals(emptyList<LigaseTouchLayout>(), repository.layouts())
        assertNull(repository.initializeSelection(available))
        assertFalse(repository.select("OSC_Keyboard_2", available))
        assertEquals(
            "sentinel",
            context.getSharedPreferences("ligase_product_preferences", Context.MODE_PRIVATE)
                .getString("global_touch_layout", null),
        )
    }

    @Test
    fun `empty catalog does not invent a layout id`() {
        assertNull(repository.initializeSelection(emptyList()))
    }
}
