package com.limelight.ligase.feature.settings.presentation

import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.LigaseLanguageMode
import com.limelight.ligase.LigaseThemeMode
import com.limelight.ligase.feature.library.data.dto.LigaseResolutionDto
import com.limelight.ligase.feature.library.domain.LibraryHdrState

data class SettingsUiState(
    val selectedInput: InputDeviceMode,
    val themeMode: LigaseThemeMode,
    val languageMode: LigaseLanguageMode,
    val globalResolution: LigaseResolutionDto?,
    val hdrState: LibraryHdrState,
    val canOperate: Boolean,
) {
    val inputEnabled: Boolean
        get() = canOperate

    val resolutionEnabled: Boolean
        get() = canOperate && globalResolution != null

    val hasResolutionContent: Boolean
        get() = globalResolution != null
}
