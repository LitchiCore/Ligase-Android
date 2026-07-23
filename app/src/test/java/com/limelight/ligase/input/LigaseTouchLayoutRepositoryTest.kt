package com.limelight.ligase.input

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.limelight.ligase.LigasePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `global selection persists stable layout id`() {
        val available = listOf(
            LigaseTouchLayout("OSC_Keyboard_1", "默认布局"),
            LigaseTouchLayout("OSC_Keyboard_2", "第二布局"),
        )

        assertTrue(repository.select("OSC_Keyboard_2", available))
        assertEquals(
            "OSC_Keyboard_2",
            LigasePreferences.getGlobalTouchLayoutId(context),
        )
    }

    @Test
    fun `missing saved layout is retained for explicit recovery`() {
        LigasePreferences.setGlobalTouchLayoutId(context, "deleted-layout")
        val available = listOf(LigaseTouchLayout("available-layout", "可用布局"))

        assertEquals("deleted-layout", repository.initializeSelection(available))
        assertFalse(repository.select("same-name-but-different-id", available))
        assertEquals(
            "deleted-layout",
            LigasePreferences.getGlobalTouchLayoutId(context),
        )
    }

    @Test
    fun `empty catalog does not invent a layout id`() {
        assertNull(repository.initializeSelection(emptyList()))
    }
}
