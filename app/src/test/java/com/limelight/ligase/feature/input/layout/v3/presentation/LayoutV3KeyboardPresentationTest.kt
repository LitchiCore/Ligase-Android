package com.limelight.ligase.feature.input.layout.v3.presentation

import com.limelight.ligase.feature.input.layout.v3.domain.InputCode
import com.limelight.ligase.feature.input.layout.v3.domain.InputCodeNamespace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutV3KeyboardPresentationTest {
    @Test
    fun `ANSI keyboard exposes exactly 104 unique selectable key caps`() {
        val keys = LAYOUT_V3_ANSI_SELECTABLE_KEYS.mapNotNull { it.inputCode }

        assertEquals(104, keys.size)
        assertEquals(104, keys.toSet().size)
        assertTrue(keys.all { it.namespace == InputCodeNamespace.ANDROID_KEY_CODE })
    }

    @Test
    fun `selection toggles without changing other keys`() {
        val a = InputCode(InputCodeNamespace.ANDROID_KEY_CODE, 29)
        val b = InputCode(InputCodeNamespace.ANDROID_KEY_CODE, 30)

        val selected = toggleLayoutV3KeyboardSelection(setOf(a), b)
        assertTrue(a in selected)
        assertTrue(b in selected)

        val deselected = toggleLayoutV3KeyboardSelection(selected, a)
        assertFalse(a in deselected)
        assertTrue(b in deselected)
    }

    @Test
    fun `keyboard shrinks to available width before horizontal scrolling`() {
        val fittedUnit = layoutV3KeyboardUnitWidthDp(900f)
        assertTrue(fittedUnit in LAYOUT_V3_KEYBOARD_MIN_KEY_DP..LAYOUT_V3_KEYBOARD_MAX_KEY_DP)
        assertTrue(layoutV3KeyboardRequiredWidthDp(fittedUnit) <= 900.01f)

        val narrowUnit = layoutV3KeyboardUnitWidthDp(320f)
        assertEquals(LAYOUT_V3_KEYBOARD_MIN_KEY_DP, narrowUnit)
        assertTrue(layoutV3KeyboardRequiredWidthDp(narrowUnit) > 320f)
    }

    @Test
    fun `navigation and numpad use independent aligned row grids`() {
        assertEquals(5, LAYOUT_V3_ANSI_MAIN_ROWS.size)
        assertEquals(5, LAYOUT_V3_ANSI_NAVIGATION_ROWS.size)
        assertEquals(5, LAYOUT_V3_ANSI_NUMPAD_ROWS.size)
        assertEquals(listOf("Num", "/", "*", "-"), LAYOUT_V3_ANSI_NUMPAD_ROWS[0].map { it.label })
        assertEquals(listOf("7", "8", "9", "+"), LAYOUT_V3_ANSI_NUMPAD_ROWS[1].map { it.label })
        assertEquals(listOf("4", "5", "6"), LAYOUT_V3_ANSI_NUMPAD_ROWS[2].map { it.label })
        assertEquals(listOf("1", "2", "3", "Enter"), LAYOUT_V3_ANSI_NUMPAD_ROWS[3].map { it.label })
        assertEquals(listOf("0", "."), LAYOUT_V3_ANSI_NUMPAD_ROWS[4].map { it.label })
        assertEquals(19, LAYOUT_V3_ANSI_NAVIGATION_ROWS[3][1].code)
    }

    @Test
    fun `numpad plus and enter span rows while zero spans columns`() {
        val plus = LAYOUT_V3_ANSI_NUMPAD_GRID.single { it.key.label == "+" }
        val enter = LAYOUT_V3_ANSI_NUMPAD_GRID.single { it.key.label == "Enter" }
        val zero = LAYOUT_V3_ANSI_NUMPAD_GRID.single { it.key.label == "0" }

        assertEquals(2, plus.rowSpan)
        assertEquals(2, enter.rowSpan)
        assertEquals(2, zero.columnSpan)
        assertEquals(3, plus.column)
        assertEquals(3, enter.column)
    }
}
