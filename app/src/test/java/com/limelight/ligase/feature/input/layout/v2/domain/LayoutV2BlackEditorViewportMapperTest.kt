package com.limelight.ligase.feature.input.layout.v2.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutV2BlackEditorViewportMapperTest {
    @Test
    fun uniformContainUsesFixedCanvasAndDeterministicOddLetterbox() {
        val viewport = LayoutV2BlackEditorViewportMapper.viewport(1000, 1000)!!

        assertEquals(IntSize(1920, 1080), LayoutV2BlackEditorViewportMapper.CANONICAL_CANVAS)
        assertEquals(IntRect(0, 218, 1000, 563), viewport.contentRectPx)
        assertNull(LayoutV2BlackEditorViewportMapper.viewport(0, 1000))
    }

    @Test
    fun pointerClampsBeforeIntegerRoundHalfUpMapping() {
        val viewport = LayoutV2BlackEditorViewportMapper.viewport(1920, 1200)!!

        assertEquals(
            LayoutV2EditorPoint(0, 0),
            LayoutV2BlackEditorViewportMapper.pointerToCanvas(
                viewport,
                LayoutV2EditorPoint(-20, -20),
            ),
        )
        assertEquals(
            LayoutV2EditorPoint(1920, 1080),
            LayoutV2BlackEditorViewportMapper.pointerToCanvas(
                viewport,
                LayoutV2EditorPoint(3000, 3000),
            ),
        )
        assertEquals(
            LayoutV2EditorPoint(960, 540),
            LayoutV2BlackEditorViewportMapper.pointerToCanvas(
                viewport,
                LayoutV2EditorPoint(960, 600),
            ),
        )
    }

    @Test
    fun canvasRoundTripAndRectMappingHaveAtLeastOnePixelExtent() {
        val viewport = LayoutV2BlackEditorViewportMapper.viewport(1280, 720)!!
        val point = LayoutV2EditorPoint(731, 419)
        val pixels = LayoutV2BlackEditorViewportMapper.canvasToPointer(viewport, point)
        val roundTrip = LayoutV2BlackEditorViewportMapper.pointerToCanvas(viewport, pixels)

        assertTrue(kotlin.math.abs(point.x - roundTrip.x) <= 1)
        assertTrue(kotlin.math.abs(point.y - roundTrip.y) <= 1)
        val mapped = LayoutV2BlackEditorViewportMapper.canvasRectToPixels(
            LayoutV2BlackEditorViewportMapper.viewport(1, 1)!!,
            IntRect(0, 0, 1, 1),
        )
        assertEquals(1, mapped.width)
        assertEquals(1, mapped.height)
        val canvasRect = LayoutV2BlackEditorViewportMapper.pixelsRectToCanvas(
            viewport,
            IntRect(-100, -100, 10, 10),
        )
        assertEquals(IntRect(0, 0, 1, 1), canvasRect)
    }

    @Test
    fun onlyPointerEndCommitsTypedMutation() {
        assertEquals(
            false,
            LayoutV2BlackEditorGestureContract.shouldCommit(
                LayoutV2EditorGesturePhase.PREVIEW,
            ),
        )
        assertTrue(
            LayoutV2BlackEditorGestureContract.shouldCommit(
                LayoutV2EditorGesturePhase.POINTER_UP,
            ),
        )
        assertTrue(
            LayoutV2BlackEditorGestureContract.shouldCommit(
                LayoutV2EditorGesturePhase.POINTER_CANCEL,
            ),
        )
    }
}
