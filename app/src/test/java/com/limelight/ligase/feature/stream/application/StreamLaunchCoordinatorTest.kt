package com.limelight.ligase.feature.stream.application

import com.limelight.ligase.InputDeviceMode
import com.limelight.ligase.feature.library.data.dto.HostLibraryItemDto
import com.limelight.ligase.feature.library.data.dto.LigaseAppStreamingDto
import com.limelight.ligase.feature.library.data.dto.LigaseCapabilitiesDto
import com.limelight.ligase.feature.library.data.dto.LigaseLibrarySyncDto
import com.limelight.ligase.feature.library.data.dto.LigaseResolutionDto
import com.limelight.ligase.feature.library.data.dto.LigaseStreamingSyncDto
import com.limelight.ligase.feature.library.data.dto.LigaseSyncSnapshotDto
import com.limelight.ligase.feature.library.domain.HostLibraryKind
import com.limelight.ligase.feature.library.domain.LibraryItemKey
import com.limelight.ligase.feature.library.domain.LigaseLibraryItem
import com.limelight.ligase.feature.stream.infrastructure.LegacyGameStreamLauncher
import com.limelight.ligase.input.LigaseInputCategory
import com.limelight.ligase.input.LigaseInputConnection
import com.limelight.ligase.input.LigaseInputDevice
import com.limelight.ligase.input.LigaseTouchOverlayMode
import com.limelight.ligase.library.LibraryConnectivity
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.PairingManager.PairState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamLaunchCoordinatorTest {
    @Test
    fun `offline and observe fail closed before launcher`() {
        val fixture = fixture()

        assertBlocked(
            fixture.coordinator.plan(request(connectivity = LibraryConnectivity.OFFLINE)),
            StreamLaunchBlockReason.OFFLINE,
        )
        fixture.currentHost.ligaseClientAccessMode = "observe"
        assertBlocked(
            fixture.coordinator.plan(request()),
            StreamLaunchBlockReason.PERMISSION_DENIED,
        )
        assertEquals(0, fixture.launcher.launchCount)
    }

    @Test
    fun `selected physical input must still be present`() {
        val fixture = fixture()

        assertBlocked(
            fixture.coordinator.plan(
                request(
                    mode = InputDeviceMode.GAMEPAD,
                    selectedGamepad = "pad",
                ),
            ),
            StreamLaunchBlockReason.GAMEPAD_DISCONNECTED,
        )
        assertBlocked(
            fixture.coordinator.plan(
                request(
                    mode = InputDeviceMode.KEYBOARD_MOUSE,
                    selectedKeyboard = "keyboard",
                ),
            ),
            StreamLaunchBlockReason.KEYBOARD_MOUSE_DISCONNECTED,
        )
        assertTrue(
            fixture.coordinator.plan(
                request(
                    mode = InputDeviceMode.GAMEPAD,
                    selectedGamepad = "pad",
                    devices = listOf(device("pad", LigaseInputCategory.GAMEPAD)),
                ),
            ) is StreamLaunchPlanningResult.Ready,
        )
    }

    @Test
    fun `touch screen controls remain exactly one of keyboard gamepad or none`() {
        val fixture = fixture()
        val keyboard = ready(
            fixture.coordinator.plan(
                request(
                    overlay = LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD,
                    selectedLayout = "layout-1",
                    layouts = setOf("layout-1"),
                ),
            ),
        )
        val gamepad = ready(
            fixture.coordinator.plan(
                request(overlay = LigaseTouchOverlayMode.VIRTUAL_GAMEPAD),
            ),
        )
        val none = ready(
            fixture.coordinator.plan(
                request(overlay = LigaseTouchOverlayMode.GESTURES_ONLY),
            ),
        )

        assertTrue(keyboard.input.showTouchKitKeyboard)
        assertFalse(keyboard.input.showVirtualGamepad)
        assertEquals("layout-1", keyboard.input.touchLayoutId)
        assertTrue(gamepad.input.showVirtualGamepad)
        assertFalse(gamepad.input.showTouchKitKeyboard)
        assertFalse(none.input.showTouchControls)
        assertEquals(null, none.input.touchLayoutId)
    }

    @Test
    fun `missing TouchKit layout fails closed`() {
        val result = fixture().coordinator.plan(
            request(
                overlay = LigaseTouchOverlayMode.TOUCHKIT_KEYBOARD,
                selectedLayout = "deleted",
                layouts = emptySet(),
            ),
        )

        assertBlocked(result, StreamLaunchBlockReason.TOUCH_LAYOUT_UNAVAILABLE)
    }

    @Test
    fun `virtual display policy keeps system entry physical and prompts unsupported ordinary`() {
        val fixture = fixture()
        val systemPlan = ready(
            fixture.coordinator.plan(
                request(item = item(kind = HostLibraryKind.DESKTOP), virtualDisplay = true),
            ),
        )
        val ordinaryPlan = ready(
            fixture.coordinator.plan(request(virtualDisplay = true)),
        )

        assertFalse(systemPlan.withVirtualDisplay)
        assertEquals(StreamLaunchConfirmation.NONE, systemPlan.confirmation)
        assertTrue(ordinaryPlan.withVirtualDisplay)
        assertEquals(
            StreamLaunchConfirmation.VIRTUAL_DISPLAY_UNAVAILABLE,
            ordinaryPlan.confirmation,
        )
    }

    @Test
    fun `plan carries Sync resolution HDR and numeric launch ABI`() {
        val fixture = fixture()
        val plan = ready(fixture.coordinator.plan(request()))

        assertEquals(APP_UUID, plan.canonicalAppUuid)
        assertEquals(42, plan.numericAppId)
        assertEquals(2560, plan.width)
        assertEquals(1440, plan.height)
        assertTrue(plan.hostHdrSupported)
    }

    @Test
    fun `UUID mismatch never falls back to name or numeric appid`() {
        val mismatched = item(
            app = NvApp("Same Name", OTHER_UUID, 42, false),
        )

        assertBlocked(
            fixture().coordinator.plan(request(item = mismatched)),
            StreamLaunchBlockReason.APP_IDENTITY_MISMATCH,
        )
    }

    @Test
    fun `launch is one shot and stale Host cannot launch`() {
        val fixture = fixture()
        val first = ready(fixture.coordinator.plan(request()))

        assertEquals(
            StreamLaunchExecutionResult.STARTED,
            fixture.coordinator.launch(first),
        )
        assertEquals(
            StreamLaunchExecutionResult.ALREADY_LAUNCHED,
            fixture.coordinator.launch(first),
        )
        assertEquals(1, fixture.launcher.launchCount)

        val stale = ready(fixture.coordinator.plan(request()))
        fixture.currentHost = host(uuid = OTHER_UUID)
        assertEquals(
            StreamLaunchExecutionResult.STALE_HOST,
            fixture.coordinator.launch(stale),
        )
        assertEquals(1, fixture.launcher.launchCount)
    }

    private fun fixture(): Fixture {
        val launcher = FakeLauncher()
        return Fixture(host(), launcher).also { fixture ->
            fixture.coordinator = StreamLaunchCoordinator(
                currentHost = { fixture.currentHost },
                launcher = launcher,
            )
        }
    }

    private fun request(
        item: LigaseLibraryItem = item(),
        connectivity: LibraryConnectivity = LibraryConnectivity.ONLINE,
        mode: InputDeviceMode = InputDeviceMode.TOUCH,
        selectedGamepad: String? = null,
        selectedKeyboard: String? = null,
        selectedMouse: String? = null,
        devices: List<LigaseInputDevice> = emptyList(),
        selectedLayout: String? = null,
        layouts: Set<String> = emptySet(),
        overlay: LigaseTouchOverlayMode = LigaseTouchOverlayMode.GESTURES_ONLY,
        virtualDisplay: Boolean = false,
    ) = StreamLaunchRequest(
        item = item,
        snapshot = snapshot(),
        connectivity = connectivity,
        inputMode = mode,
        selectedGamepadKey = selectedGamepad,
        selectedKeyboardKey = selectedKeyboard,
        selectedMouseKey = selectedMouse,
        connectedInputDevices = devices,
        selectedTouchLayoutId = selectedLayout,
        availableTouchLayoutIds = layouts,
        overlayMode = overlay,
        preferVirtualDisplay = virtualDisplay,
    )

    private fun item(
        kind: HostLibraryKind = HostLibraryKind.STEAM,
        app: NvApp = NvApp("Game", APP_UUID, 42, false),
    ) = LigaseLibraryItem(
        key = LibraryItemKey.HostUuid(APP_UUID),
        name = "Game",
        kind = kind,
        hostAppUuid = APP_UUID,
        appId = app.appId,
        steamAppId = null,
        addedAt = "",
        updatedAt = "",
        lastPlayedAt = null,
        launchApp = app,
    )

    private fun snapshot() = LigaseSyncSnapshotDto(
        schemaVersion = 1,
        capabilities = LigaseCapabilitiesDto(hdrEncodingSupported = true),
        library = LigaseLibrarySyncDto(
            revision = 1,
            updatedAt = "",
            sortMode = "manual",
            items = emptyList<HostLibraryItemDto>(),
        ),
        streaming = LigaseStreamingSyncDto(
            schemaVersion = 1,
            revision = 2,
            updatedAt = "",
            globalResolution = LigaseResolutionDto(1920, 1080),
            apps = mapOf(APP_UUID to LigaseAppStreamingDto(LigaseResolutionDto(2560, 1440))),
        ),
    )

    private fun host(uuid: String = HOST_UUID) = ComputerDetails().apply {
        this.uuid = uuid
        name = "Host"
        state = ComputerDetails.State.ONLINE
        pairState = PairState.PAIRED
        activeAddress = ComputerDetails.AddressTuple("192.0.2.1", 48989)
        httpsPort = 48984
        ligaseClientAccessMode = "operate"
        vDisplaySupported = false
        vDisplayDriverReady = false
    }

    private fun device(key: String, category: LigaseInputCategory) = LigaseInputDevice(
        stableKey = key,
        descriptor = key,
        vendorId = 1,
        productId = 2,
        category = category,
        name = key,
        connection = LigaseInputConnection.EXTERNAL,
    )

    private fun ready(result: StreamLaunchPlanningResult): StreamLaunchPlan =
        (result as StreamLaunchPlanningResult.Ready).plan

    private fun assertBlocked(
        result: StreamLaunchPlanningResult,
        reason: StreamLaunchBlockReason,
    ) {
        assertEquals(reason, (result as StreamLaunchPlanningResult.Blocked).reason)
    }

    private class FakeLauncher : LegacyGameStreamLauncher() {
        var available = true
        var launchCount = 0
        override fun isAvailable(): Boolean = available
        override fun launch(plan: StreamLaunchPlan): Boolean {
            launchCount++
            return available
        }
    }

    private class Fixture(
        var currentHost: ComputerDetails,
        val launcher: FakeLauncher,
    ) {
        lateinit var coordinator: StreamLaunchCoordinator
    }

    companion object {
        private const val HOST_UUID = "53beb7ec-9788-cc23-461a-061f153029a5"
        private const val APP_UUID = "f3d67f4d-b1fe-4c5d-a77e-b78a51051c1a"
        private const val OTHER_UUID = "cdd1aadf-6f41-4895-8615-43c35543fb3e"
    }
}
