package com.limelight.ligase.feature.input.application

import com.limelight.ligase.input.LigaseCanonicalGameUuid
import com.limelight.ligase.input.LigaseCloudTouchMode
import com.limelight.ligase.input.LigaseEffectiveInputProfile
import com.limelight.ligase.input.LigaseInputOverrideWriteResult
import com.limelight.ligase.input.LigaseInputProfile
import com.limelight.ligase.input.LigaseTouchOverlayMode

data class GameInputOverrideTarget(
    val gameUuid: String,
    val title: String,
    val safeIdentity: String?,
)

data class GameInputOverrideEditorState(
    val target: GameInputOverrideTarget,
    val useGlobal: Boolean,
    val overlayMode: LigaseTouchOverlayMode,
    val cloudTouchMode: LigaseCloudTouchMode,
    val writable: Boolean,
)

sealed interface GameInputOverrideEditorAction {
    data class Save(
        val gameUuid: String,
        val overlayMode: LigaseTouchOverlayMode,
        val cloudTouchMode: LigaseCloudTouchMode,
    ) : GameInputOverrideEditorAction

    data class Clear(val gameUuid: String) : GameInputOverrideEditorAction
}

enum class GameInputOverrideEditorResult {
    SAVED,
    CLEARED,
    INVALID_TARGET,
    READ_ONLY,
    WRITE_FAILED,
    READBACK_FAILED,
}

class GameInputOverrideEditor(
    private val resolve: (String, Boolean) -> LigaseEffectiveInputProfile,
    private val save: (String, LigaseInputProfile, Boolean) -> LigaseInputOverrideWriteResult,
    private val clear: (String, Boolean) -> LigaseInputOverrideWriteResult,
) {
    fun state(target: GameInputOverrideTarget, canOperate: Boolean): GameInputOverrideEditorState? {
        val uuid = LigaseCanonicalGameUuid.parse(target.gameUuid) ?: return null
        val effective = resolve(uuid, canOperate)
        return GameInputOverrideEditorState(
            target = target.copy(gameUuid = uuid),
            useGlobal = effective.source != LigaseEffectiveInputProfile.Source.GAME_OVERRIDE,
            overlayMode = effective.profile.overlayMode,
            cloudTouchMode = effective.profile.cloudTouchMode,
            writable = effective.writable,
        )
    }

    fun submit(
        action: GameInputOverrideEditorAction,
        targetStillExists: Boolean,
        canOperate: Boolean,
    ): GameInputOverrideEditorResult {
        val uuid = when (action) {
            is GameInputOverrideEditorAction.Save -> action.gameUuid
            is GameInputOverrideEditorAction.Clear -> action.gameUuid
        }
        if (!targetStillExists || LigaseCanonicalGameUuid.parse(uuid) == null) {
            return GameInputOverrideEditorResult.INVALID_TARGET
        }
        if (!canOperate) return GameInputOverrideEditorResult.READ_ONLY
        return when (action) {
            is GameInputOverrideEditorAction.Clear -> {
                when (clear(uuid, true)) {
                    LigaseInputOverrideWriteResult.CLEARED -> {
                        if (resolve(uuid, true).source == LigaseEffectiveInputProfile.Source.GLOBAL) {
                            GameInputOverrideEditorResult.CLEARED
                        } else GameInputOverrideEditorResult.READBACK_FAILED
                    }
                    LigaseInputOverrideWriteResult.READ_ONLY -> GameInputOverrideEditorResult.READ_ONLY
                    LigaseInputOverrideWriteResult.INVALID_GAME_UUID -> GameInputOverrideEditorResult.INVALID_TARGET
                    else -> GameInputOverrideEditorResult.WRITE_FAILED
                }
            }
            is GameInputOverrideEditorAction.Save -> {
                val current = resolve(uuid, true)
                val profile = LigaseInputProfile(
                    mode = current.profile.mode,
                    overlayMode = action.overlayMode,
                    cloudTouchMode = action.cloudTouchMode,
                )
                when (save(uuid, profile, true)) {
                    LigaseInputOverrideWriteResult.SAVED -> {
                        val readback = resolve(uuid, true)
                        if (
                            readback.source == LigaseEffectiveInputProfile.Source.GAME_OVERRIDE &&
                            readback.profile == profile
                        ) GameInputOverrideEditorResult.SAVED
                        else GameInputOverrideEditorResult.READBACK_FAILED
                    }
                    LigaseInputOverrideWriteResult.READ_ONLY -> GameInputOverrideEditorResult.READ_ONLY
                    LigaseInputOverrideWriteResult.INVALID_GAME_UUID -> GameInputOverrideEditorResult.INVALID_TARGET
                    else -> GameInputOverrideEditorResult.WRITE_FAILED
                }
            }
        }
    }
}
