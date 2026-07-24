package com.limelight.ligase.feature.layout.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutCanvasViewportTest {
    @Test
    fun `aspect fit uses available phone and tablet area`() {
        val phone = fitLayoutCanvas(LayoutCanvasSize(1080f, 1500f), 16f / 9f)
        assertEquals(1080f, phone.width, 0.01f)
        assertEquals(607.5f, phone.height, 0.01f)

        val tablet = fitLayoutCanvas(LayoutCanvasSize(1050f, 900f), 16f / 9f)
        assertEquals(1050f, tablet.width, 0.01f)
        assertEquals(590.625f, tablet.height, 0.01f)
        assertTrue(tablet.height > 500f)
    }

    @Test
    fun `zoom pan and normalized drag remain invertible`() {
        val frame = LayoutCanvasSize(1000f, 600f)
        val state = LayoutCanvasViewportState()
            .transformed(2f, 120f, -60f, frame)
        val x = state.screenX(0.35f, frame.width)
        val y = state.screenY(0.7f, frame.height)

        assertEquals(0.35f, ((x - state.panX) / frame.width - 0.5f) / state.zoom + 0.5f, 0.0001f)
        assertEquals(0.7f, ((y - state.panY) / frame.height - 0.5f) / state.zoom + 0.5f, 0.0001f)
        assertEquals(0.05f, state.normalizedDeltaX(100f, frame.width), 0.0001f)
        assertEquals(0.05f, state.normalizedDeltaY(60f, frame.height), 0.0001f)
    }

    @Test
    fun `viewport clamps gesture state and reset fits`() {
        val frame = LayoutCanvasSize(1000f, 600f)
        val state = LayoutCanvasViewportState()
            .transformed(10f, 5000f, -5000f, frame)

        assertEquals(4f, state.zoom, 0f)
        assertEquals(1500f, state.panX, 0f)
        assertEquals(-900f, state.panY, 0f)
        assertEquals(LayoutCanvasViewportState(), state.reset())
    }

    @Test
    fun `dense canvas labels are selected only`() {
        assertFalse(showCanvasElementLabel(selected = false))
        assertTrue(showCanvasElementLabel(selected = true))
    }

    @Test
    fun `hall breakpoints are deterministic`() {
        assertEquals(1, layoutHallColumns(719f))
        assertEquals(2, layoutHallColumns(720f))
        assertEquals(3, layoutHallColumns(1240f))
    }
}
