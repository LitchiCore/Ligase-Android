package com.limelight.ligase.stream.menu

import androidx.annotation.StringRes
import com.limelight.R

enum class StreamMenuSection(@StringRes val titleRes: Int) {
    SESSION(R.string.ligase_stream_section_session),
    CONTROLS(R.string.ligase_stream_section_controls),
    MORE(R.string.ligase_stream_section_more),
    DANGER(R.string.ligase_stream_section_danger),
}

enum class StreamMenuActionStyle {
    NORMAL,
    CANCEL,
    DANGER,
}

data class StreamMenuActionSpec(
    val id: String,
    val section: StreamMenuSection,
    val style: StreamMenuActionStyle = StreamMenuActionStyle.NORMAL,
    val requiresGameFocus: Boolean = false,
    val enabled: Boolean = true,
)

object StreamQuickMenuContract {
    val requiredMainActions = listOf(
        StreamMenuActionSpec("continue", StreamMenuSection.SESSION, StreamMenuActionStyle.CANCEL),
        StreamMenuActionSpec("disconnect", StreamMenuSection.SESSION),
        StreamMenuActionSpec("upload_clipboard", StreamMenuSection.CONTROLS),
        StreamMenuActionSpec("fetch_clipboard", StreamMenuSection.CONTROLS),
        StreamMenuActionSpec("keyboard", StreamMenuSection.CONTROLS, requiresGameFocus = true),
        StreamMenuActionSpec("advanced", StreamMenuSection.MORE, requiresGameFocus = true),
        StreamMenuActionSpec("quit", StreamMenuSection.DANGER, StreamMenuActionStyle.DANGER),
    )
}

object StreamMenuEntryPolicy {
    @JvmStatic
    fun showPersistentButton(
        ligaseSessionControls: Boolean,
        backMenuEnabled: Boolean,
        floatingButtonEnabled: Boolean,
    ): Boolean = !ligaseSessionControls && backMenuEnabled && floatingButtonEnabled

    @JvmStatic
    fun allowAutomaticClipboardSync(
        ligaseSessionControls: Boolean,
        connected: Boolean,
        legacySmartSyncEnabled: Boolean,
    ): Boolean = !ligaseSessionControls && connected && legacySmartSyncEnabled
}
