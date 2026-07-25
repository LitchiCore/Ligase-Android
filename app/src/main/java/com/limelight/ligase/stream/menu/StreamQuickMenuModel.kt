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
        StreamMenuActionSpec("upload_clipboard", StreamMenuSection.CONTROLS, requiresGameFocus = true),
        StreamMenuActionSpec("fetch_clipboard", StreamMenuSection.CONTROLS, requiresGameFocus = true),
        StreamMenuActionSpec("server_commands", StreamMenuSection.CONTROLS, requiresGameFocus = true),
        StreamMenuActionSpec("keyboard", StreamMenuSection.CONTROLS, requiresGameFocus = true),
        StreamMenuActionSpec("zoom", StreamMenuSection.CONTROLS, requiresGameFocus = true),
        StreamMenuActionSpec("advanced", StreamMenuSection.MORE, requiresGameFocus = true),
        StreamMenuActionSpec("quit", StreamMenuSection.DANGER, StreamMenuActionStyle.DANGER),
    )
}
