package com.limelight.ligase.feature.input.application

import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.input.LigaseCloudTouchMode
import com.limelight.ligase.input.LigaseEffectiveInputProfile
import com.limelight.ligase.input.LigaseInputOverrideWriteResult
import com.limelight.ligase.input.LigaseInputProfile
import com.limelight.ligase.input.LigaseTouchOverlayMode
import com.limelight.ligase.feature.library.domain.HostLibraryKind
import com.limelight.ligase.feature.library.domain.HostPortableIdentity
import com.limelight.ligase.feature.library.domain.LibraryItemKey
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class GameInputOverrideEditorTest {
    private val uuid = "67209ea3-7129-42d0-9349-52f8799d292d"
    private val global = LigaseInputProfile(
        InputDeviceMode.TOUCH,
        LigaseTouchOverlayMode.CLOUD_CONTROLS,
        LigaseCloudTouchMode.SINGLE_TOUCH,
    )

    @Test
    fun syncItemIdProjectsTargetWhenLegacyLaunchUuidIsAbsent() {
        val targets = gameInputOverrideTargets(
            listOf(item(uuid.uppercase(), hostAppUuid = null)),
        )

        assertEquals(
            listOf(GameInputOverrideTarget(uuid, "Game", "Steam · App ID 3548580")),
            targets,
        )
    }

    @Test
    fun systemInvalidAndDuplicateCanonicalIdsFailClosed() {
        val targets = gameInputOverrideTargets(
            listOf(
                item(uuid, hostAppUuid = null),
                item(uuid.uppercase(), hostAppUuid = "unrelated-legacy-value"),
                item("not-a-uuid", hostAppUuid = uuid),
                item(
                    "78a25216-f239-45bd-b4aa-f41c814066e9",
                    hostAppUuid = null,
                    kind = HostLibraryKind.DESKTOP,
                ),
            ),
        )

        assertEquals(emptyList<GameInputOverrideTarget>(), targets)
    }

    @Test
    fun saveCallsExactCanonicalTargetOnceAndRequiresReadback() {
        var stored: LigaseInputProfile? = null
        var calls = 0
        val editor = editor(
            resolve = { profile(stored) },
            save = { actualUuid, value, _ ->
                assertEquals(uuid, actualUuid)
                calls++
                stored = value
                LigaseInputOverrideWriteResult.SAVED
            },
        )

        val result = editor.submit(
            GameInputOverrideEditorAction.Save(
                uuid,
                LigaseTouchOverlayMode.VIRTUAL_GAMEPAD,
                LigaseCloudTouchMode.TRACKPAD,
            ),
            targetStillExists = true,
            canOperate = true,
        )

        assertEquals(GameInputOverrideEditorResult.SAVED, result)
        assertEquals(1, calls)
        assertEquals(InputDeviceMode.TOUCH, stored?.mode)
    }

    @Test
    fun clearCallsExactCanonicalTargetOnceAndRequiresReadback() {
        var stored: LigaseInputProfile? = global.copy(cloudTouchMode = LigaseCloudTouchMode.TRACKPAD)
        var calls = 0
        val editor = editor(
            resolve = { profile(stored) },
            clear = { actualUuid, _ ->
                assertEquals(uuid, actualUuid)
                calls++
                stored = null
                LigaseInputOverrideWriteResult.CLEARED
            },
        )
        assertEquals(
            GameInputOverrideEditorResult.CLEARED,
            editor.submit(GameInputOverrideEditorAction.Clear(uuid), true, true),
        )
        assertEquals(1, calls)
    }

    @Test
    fun invalidMissingOrObserveTargetsWriteZeroTimes() {
        var writes = 0
        val editor = editor(
            save = { _, _, _ -> writes++; LigaseInputOverrideWriteResult.SAVED },
            clear = { _, _ -> writes++; LigaseInputOverrideWriteResult.CLEARED },
        )
        val invalid = GameInputOverrideEditorAction.Clear("Steam-3548580")
        assertEquals(GameInputOverrideEditorResult.INVALID_TARGET, editor.submit(invalid, true, true))
        assertEquals(
            GameInputOverrideEditorResult.INVALID_TARGET,
            editor.submit(GameInputOverrideEditorAction.Clear(uuid), false, true),
        )
        assertEquals(
            GameInputOverrideEditorResult.READ_ONLY,
            editor.submit(GameInputOverrideEditorAction.Clear(uuid), true, false),
        )
        assertEquals(0, writes)
    }

    @Test
    fun readbackMismatchNeverReportsSuccess() {
        var calls = 0
        val editor = editor(
            save = { _, _, _ -> calls++; LigaseInputOverrideWriteResult.SAVED },
        )
        val result = editor.submit(
            GameInputOverrideEditorAction.Save(
                uuid,
                LigaseTouchOverlayMode.HIDDEN,
                LigaseCloudTouchMode.MULTI_TOUCH,
            ),
            true,
            true,
        )
        assertEquals(GameInputOverrideEditorResult.READBACK_FAILED, result)
        assertEquals(1, calls)
    }

    @Test
    fun stateUsesAuthoritativeResolvedProfileAndObserveIsReadOnly() {
        val editor = editor(resolve = {
            LigaseEffectiveInputProfile(
                global.copy(overlayMode = LigaseTouchOverlayMode.HIDDEN),
                LigaseEffectiveInputProfile.Source.OBSERVE_FORCED_HIDDEN,
                false,
            )
        })
        val state = editor.state(GameInputOverrideTarget(uuid, "Game", "Steam · App ID 3548580"), false)
        assertNotNull(state)
        assertEquals(LigaseTouchOverlayMode.HIDDEN, state?.overlayMode)
        assertFalse(checkNotNull(state).writable)
    }

    private fun editor(
        resolve: (String) -> LigaseEffectiveInputProfile = { profile(null) },
        save: (String, LigaseInputProfile, Boolean) -> LigaseInputOverrideWriteResult =
            { _, _, _ -> LigaseInputOverrideWriteResult.SAVED },
        clear: (String, Boolean) -> LigaseInputOverrideWriteResult =
            { _, _ -> LigaseInputOverrideWriteResult.CLEARED },
    ) = GameInputOverrideEditor(
        resolve = { value, _ -> resolve(value) },
        save = save,
        clear = clear,
    )

    private fun profile(override: LigaseInputProfile?) = LigaseEffectiveInputProfile(
        profile = override ?: global,
        source = if (override == null) {
            LigaseEffectiveInputProfile.Source.GLOBAL
        } else LigaseEffectiveInputProfile.Source.GAME_OVERRIDE,
        writable = true,
    )

    private fun item(
        id: String,
        hostAppUuid: String?,
        kind: HostLibraryKind = HostLibraryKind.STEAM,
    ) = LigaseLibraryItem(
        key = LibraryItemKey.HostUuid(id),
        name = "Game",
        kind = kind,
        hostAppUuid = hostAppUuid,
        appId = null,
        steamAppId = 3548580,
        addedAt = null,
        updatedAt = null,
        lastPlayedAt = null,
        launchApp = null,
        portableIdentity = HostPortableIdentity("steam", "3548580"),
    )
}
