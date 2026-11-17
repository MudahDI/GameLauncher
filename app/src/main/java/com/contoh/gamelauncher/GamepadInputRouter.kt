package com.mdh.gamelauncher

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max

/**
 * One input boundary for the launcher.
 *
 * Binary controls remain Android KeyEvents. Analog/HAT motion is normalized to
 * DPAD key pairs so Compose has one predictable navigation path.
 */
internal class GamepadInputRouter(
    context: Context,
    private val onNotice: (String?) -> Unit,
    private val onInput: (String) -> Unit = {}
) : InputManager.InputDeviceListener {

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableControllers = MutableStateFlow<List<String>>(emptyList())
    val controllers: StateFlow<List<String>> = mutableControllers.asStateFlow()

    private var repeatJob: Job? = null
    private var activeDirection = 0
    private var closed = false

    init {
        inputManager.registerInputDeviceListener(this, android.os.Handler(context.mainLooper))
        refresh()
    }

    fun refresh() {
        if (closed) return
        val names = InputDevice.getDeviceIds()
            .map { id -> InputDevice.getDevice(id) }
            .filterNotNull()
            .filter(::isController)
            .map { device -> device.name.ifBlank { "Gamepad" } }
            .distinct()
        val previous = mutableControllers.value
        if (names != previous) {
            mutableControllers.value = names
            onNotice(
                when {
                    names.isNotEmpty() -> "Gamepad tersambung\n${names.joinToString(", ")}"
                    previous.isNotEmpty() -> "Gamepad terputus"
                    else -> null
                }
            )
        }
    }

    /** Return a normalized event only for actual controller-originated keys. */
    fun mapKeyEvent(event: KeyEvent): KeyEvent? {
        if (event.deviceId >= 0) {
            val deviceName = InputDevice.getDevice(event.deviceId)?.name ?: "Unknown device"
            onInput("KEY  ${KeyEvent.keyCodeToString(event.keyCode)} · ${if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"} · source=0x${event.source.toString(16)} · device=$deviceName")
        }
        // Preserve the Android button layout. Only A is normalized to the
        // standard Compose activation key; B is handled by the root back action.
        val mappedCode = when (event.keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_DPAD_CENTER
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BACK
            else -> return null
        }
        return KeyEvent(
            event.downTime, event.eventTime, event.action, mappedCode,
            event.repeatCount, event.metaState, event.deviceId, event.scanCode,
            event.flags, event.source
        )
    }

    /**
     * Read only joystick-originated motion. HAT wins when present; otherwise use
     * the left stick with each device's reported flat/dead-zone range.
     */
    fun handleMotionEvent(
        event: MotionEvent,
        dispatchDirection: (keyCode: Int, deviceId: Int) -> Unit
    ): Boolean {
        if (closed || event.action != MotionEvent.ACTION_MOVE) return false
        val device = event.device ?: return false
        val joystickSource = event.isFromSource(InputDevice.SOURCE_JOYSTICK) ||
            event.isFromSource(InputDevice.SOURCE_GAMEPAD)
        if (!isController(device) || !joystickSource) return false

        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)
        val xFlat = (device.getMotionRange(MotionEvent.AXIS_X, event.source)
            ?: device.getMotionRange(MotionEvent.AXIS_X))?.flat ?: 0.12f
        val yFlat = (device.getMotionRange(MotionEvent.AXIS_Y, event.source)
            ?: device.getMotionRange(MotionEvent.AXIS_Y))?.flat ?: 0.12f
        val direction = resolveDirection(x, y, hatX, hatY, xFlat, yFlat)
        onInput("AXIS  x=${"%.2f".format(x)} y=${"%.2f".format(y)} hat=(${"%.1f".format(hatX)},${"%.1f".format(hatY)}) · source=0x${event.source.toString(16)} · device=${device.name}")

        updateDirection(direction, device.id, dispatchDirection)
        return true
    }

    private fun resolveDirection(
        x: Float, y: Float, hatX: Float, hatY: Float, flatX: Float, flatY: Float
    ): Int {
        val hasHatX = abs(hatX) > 0.5f
        val hasHatY = abs(hatY) > 0.5f
        val axisX = if (hasHatX) hatX else x
        val axisY = if (hasHatY) hatY else y
        val thresholdX = if (hasHatX) 0.5f else max(0.55f, flatX * 2f)
        val thresholdY = if (hasHatY) 0.5f else max(0.55f, flatY * 2f)

        return if (abs(axisX) > abs(axisY)) {
            when {
                axisX < -thresholdX -> KeyEvent.KEYCODE_DPAD_LEFT
                axisX > thresholdX -> KeyEvent.KEYCODE_DPAD_RIGHT
                else -> 0
            }
        } else {
            when {
                axisY < -thresholdY -> KeyEvent.KEYCODE_DPAD_UP
                axisY > thresholdY -> KeyEvent.KEYCODE_DPAD_DOWN
                else -> 0
            }
        }
    }

    private fun updateDirection(
        direction: Int,
        deviceId: Int,
        dispatch: (Int, Int) -> Unit
    ) {
        if (direction == activeDirection) return
        repeatJob?.cancel()
        repeatJob = null
        activeDirection = direction
        if (direction == 0) return

        dispatch(direction, deviceId)
        repeatJob = scope.launch {
            delay(320L)
            while (isActive && activeDirection == direction) {
                dispatch(direction, deviceId)
                delay(115L)
            }
        }
    }

    fun onStop() {
        activeDirection = 0
        repeatJob?.cancel()
        repeatJob = null
    }

    fun close() {
        if (closed) return
        closed = true
        onStop()
        inputManager.unregisterInputDeviceListener(this)
        scope.cancel()
    }

    override fun onInputDeviceAdded(deviceId: Int) = refresh()
    override fun onInputDeviceRemoved(deviceId: Int) {
        onStop()
        refresh()
    }
    override fun onInputDeviceChanged(deviceId: Int) = refresh()

    private fun isController(device: InputDevice): Boolean =
        !device.isVirtual && (
            device.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
            device.supportsSource(InputDevice.SOURCE_JOYSTICK) ||
            device.supportsSource(InputDevice.SOURCE_DPAD)
        )

}
