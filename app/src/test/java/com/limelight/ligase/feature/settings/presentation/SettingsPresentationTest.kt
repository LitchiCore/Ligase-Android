package com.limelight.ligase.feature.settings.presentation

import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.LigaseLanguageMode
import com.limelight.ligase.LigaseThemeMode
import com.limelight.ligase.feature.library.data.dto.LigaseResolutionDto
import com.limelight.ligase.feature.library.domain.LibraryHdrReason
import com.limelight.ligase.feature.library.domain.LibraryHdrState
import com.limelight.ligase.feature.stream.application.StreamBitrateUiState
import com.limelight.ligase.feature.stream.domain.StreamBitratePolicy
import com.limelight.ligase.feature.stream.domain.StreamBitratePresetId
import com.limelight.ligase.feature.settings.ui.StreamBitrateDialogState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPresentationTest {
    @Test
    fun `operate with synced resolution enables input and resolution settings`() {
        val state = state(canOperate = true, resolution = resolution())

        assertTrue(state.inputEnabled)
        assertTrue(state.resolutionEnabled)
        assertTrue(state.hasResolutionContent)
    }

    @Test
    fun `observe or offline gate keeps synced content visible but disables writes`() {
        val state = state(canOperate = false, resolution = resolution())

        assertFalse(state.inputEnabled)
        assertFalse(state.resolutionEnabled)
        assertTrue(state.hasResolutionContent)
    }

    @Test
    fun `missing synced resolution remains unavailable even with operate permission`() {
        val state = state(canOperate = true, resolution = null)

        assertTrue(state.inputEnabled)
        assertFalse(state.resolutionEnabled)
        assertFalse(state.hasResolutionContent)
    }

    @Test
    fun `legacy half megabit value remains a valid custom draft`() {
        val bitrate = bitrateState(currentKbps = 15_500)

        assertEquals(
            StreamBitrateDraftValidation.Ready(15_500),
            validateStreamBitrateDraft(bitrate, null, "15.5"),
        )
    }

    @Test
    fun `preset draft resolves through the backend provided preset list`() {
        val bitrate = bitrateState(currentKbps = 15_500)

        assertEquals(
            StreamBitrateDraftValidation.Ready(30_000),
            validateStreamBitrateDraft(
                bitrate,
                StreamBitratePresetId.MBPS_30,
                "not-used",
            ),
        )
    }

    @Test
    fun `custom validation rejects unsupported fractions without rounding`() {
        val bitrate = bitrateState(currentKbps = 15_500)

        assertEquals(
            StreamBitrateDraftValidation.NotCanonicalNumber,
            validateStreamBitrateDraft(bitrate, null, "15.2"),
        )
    }

    @Test
    fun `save navigation waits closes on matching state and keeps errors open`() {
        val state = bitrateState(currentKbps = 15_500)

        assertEquals(
            StreamBitrateSaveNavigation.WAITING,
            streamBitrateSaveNavigation(20_000, state),
        )
        assertEquals(
            StreamBitrateSaveNavigation.CLOSE,
            streamBitrateSaveNavigation(15_500, state),
        )
        assertEquals(
            StreamBitrateSaveNavigation.KEEP_OPEN,
            streamBitrateSaveNavigation(
                20_000,
                state.copy(
                    error = com.limelight.ligase.feature.stream.application
                        .StreamBitrateSaveError.WRITE_FAILED,
                ),
            ),
        )
    }

    @Test
    fun `dialog saver restores advanced draft across recreation`() {
        val restored = StreamBitrateDialogState.Saver.restore(
            listOf(true, "", "15.5", true, -1),
        )!!

        assertTrue(restored.visible)
        assertEquals(null, restored.selectedPresetName)
        assertEquals("15.5", restored.customMbps)
        assertTrue(restored.customExpanded)
        assertEquals(null, restored.pendingKbps)
    }

    private fun state(
        canOperate: Boolean,
        resolution: LigaseResolutionDto?,
    ) = SettingsUiState(
        selectedInput = InputDeviceMode.TOUCH,
        themeMode = LigaseThemeMode.SYSTEM,
        languageMode = LigaseLanguageMode.SYSTEM,
        globalResolution = resolution,
        hdrState = LibraryHdrState(
            available = false,
            reason = LibraryHdrReason.HOST_CAPABILITY_UNKNOWN,
        ),
        canOperate = canOperate,
        streamBitrate = bitrateState(),
    )

    private fun bitrateState(currentKbps: Int = 15_000): StreamBitrateUiState =
        StreamBitrateUiState(
            currentKbps = currentKbps,
            currentDisplayMbps = StreamBitratePolicy.formatMbps(currentKbps),
            selectedPreset = StreamBitratePolicy.presetFor(currentKbps),
            recommendedPreset = StreamBitratePolicy.recommendedPreset(15_000),
            presets = StreamBitratePolicy.presets,
            customRangeKbps = StreamBitratePolicy.MIN_KBPS..StreamBitratePolicy.MAX_KBPS,
        )

    private fun resolution() = LigaseResolutionDto(
        width = 1920,
        height = 1080,
    )
}
