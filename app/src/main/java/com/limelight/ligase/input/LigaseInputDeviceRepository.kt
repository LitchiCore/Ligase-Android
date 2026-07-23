package com.limelight.ligase.input

import android.content.Context
import android.hardware.input.InputManager
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice

class LigaseInputDeviceRepository(
    context: Context,
    private val onDevicesChanged: (List<LigaseInputDevice>) -> Unit,
) : InputManager.InputDeviceListener {
    private val inputManager =
        context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val usbManager =
        context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listening = false

    fun start() {
        if (!listening) {
            inputManager.registerInputDeviceListener(this, mainHandler)
            listening = true
        }
        refresh()
    }

    fun stop() {
        if (!listening) return
        inputManager.unregisterInputDeviceListener(this)
        listening = false
    }

    fun refresh() {
        val usbFingerprints = usbManager.deviceList.values
            .map { it.vendorId to it.productId }
            .toSet()
        val devices = InputDevice.getDeviceIds()
            .asSequence()
            .mapNotNull { deviceId -> inputManager.getInputDevice(deviceId) }
            .filter { it.isExternal && !it.isVirtual }
            .flatMap { device ->
                val categories = LigaseInputSourceClassifier.categories(
                    sources = device.sources,
                    keyboardType = device.keyboardType,
                    sourceGamepad = InputDevice.SOURCE_GAMEPAD,
                    sourceJoystick = InputDevice.SOURCE_JOYSTICK,
                    sourceKeyboard = InputDevice.SOURCE_KEYBOARD,
                    sourceMouse = InputDevice.SOURCE_MOUSE,
                    sourceMouseRelative = InputDevice.SOURCE_MOUSE_RELATIVE,
                    keyboardTypeAlphabetic = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
                )
                val connection = if (
                    device.vendorId to device.productId in usbFingerprints
                ) {
                    LigaseInputConnection.USB_OTG
                } else {
                    // Android does not expose a reliable transport for every HID device.
                    // Do not guess Bluetooth from the device name.
                    LigaseInputConnection.EXTERNAL
                }
                categories.mapNotNull { category ->
                    val stableKey = LigaseInputIdentity.stableKey(
                        descriptor = device.descriptor,
                        vendorId = device.vendorId,
                        productId = device.productId,
                        category = category,
                    ) ?: return@mapNotNull null
                    LigaseInputDevice(
                        stableKey = stableKey,
                        descriptor = device.descriptor,
                        vendorId = device.vendorId,
                        productId = device.productId,
                        category = category,
                        name = device.name,
                        connection = connection,
                    )
                }
            }
            .toList()
            .distinctBy(LigaseInputDevice::stableKey)
            .sortedWith(compareBy({ it.category.ordinal }, { it.name.lowercase() }))
        onDevicesChanged(devices)
    }

    override fun onInputDeviceAdded(deviceId: Int) = refresh()

    override fun onInputDeviceRemoved(deviceId: Int) = refresh()

    override fun onInputDeviceChanged(deviceId: Int) = refresh()
}
