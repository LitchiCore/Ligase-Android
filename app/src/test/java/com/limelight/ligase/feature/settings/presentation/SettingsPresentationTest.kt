package com.limelight.ligase.feature.settings.presentation

import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.LigaseLanguageMode
import com.limelight.ligase.LigaseThemeMode
import com.limelight.ligase.feature.library.data.dto.LigaseResolutionDto
import com.limelight.ligase.feature.library.domain.LibraryHdrReason
import com.limelight.ligase.feature.library.domain.LibraryHdrState
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
    )

    private fun resolution() = LigaseResolutionDto(
        width = 1920,
        height = 1080,
    )
}
