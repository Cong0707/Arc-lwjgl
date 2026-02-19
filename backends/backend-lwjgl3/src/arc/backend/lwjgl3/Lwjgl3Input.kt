/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package arc.backend.lwjgl3

import arc.Core
import arc.Input
import arc.graphics.gl.HdpiMode
import arc.input.InputEventQueue
import arc.input.KeyCode
import arc.struct.IntSet
import arc.util.Disposable
import org.lwjgl.glfw.*
import kotlin.math.max


class Lwjgl3Input(val window: Lwjgl3Window) : Disposable, Input() {
    val eventQueue: InputEventQueue = InputEventQueue()

    var mouseX: Int = 0
    var mouseY: Int = 0
    var mousePressed: Int = 0
    var deltaX: Int = 0
    var deltaY: Int = 0
    var justTouched: Boolean = false
    val justPressedButtons: BooleanArray = BooleanArray(500)
    var lastCharacter: Char = 0.toChar()

    protected val pressedKeys: BooleanArray = BooleanArray(500)
    protected val justPressedKeys: BooleanArray = BooleanArray(500)
    private val keysToCatch = IntSet()
    protected var pressedKeyCount: Int = 0
    protected var keyJustPressed: Boolean = false

    private val keyCallback: GLFWKeyCallback = object : GLFWKeyCallback() {
        override fun invoke(window: Long, key: Int, scancode: Int, action: Int, mods: Int) {
            keyCallback(window, key, scancode, action, mods)
        }
    }

    var charCallback: GLFWCharCallback = object : GLFWCharCallback() {
        override fun invoke(window: Long, codepoint: Int) {
            if ((codepoint and 0xff00) == 0xf700) return
            lastCharacter = codepoint.toChar()
            (Core.graphics as Lwjgl3Graphics).requestRendering()
            eventQueue.keyTyped(codepoint.toChar())
        }
    }

    private val scrollCallback: GLFWScrollCallback = object : GLFWScrollCallback() {
        override fun invoke(window: Long, scrollX: Double, scrollY: Double) {
            (Core.graphics as Lwjgl3Graphics).requestRendering()
            eventQueue.scrolled(-scrollX.toFloat(), -scrollY.toFloat())
        }
    }

    private val cursorPosCallback: GLFWCursorPosCallback = object : GLFWCursorPosCallback() {
        private var logicalMouseY = 0
        private var logicalMouseX = 0

        override fun invoke(windowHandle: Long, x: Double, y: Double) {
            deltaX = x.toInt() - logicalMouseX
            deltaY = y.toInt() - logicalMouseY
            logicalMouseX = x.toInt()
            this@Lwjgl3Input.mouseX = logicalMouseX
            logicalMouseY = window.graphics!!.height - y.toInt() //y翻转
            this@Lwjgl3Input.mouseY = logicalMouseY

            if (window.config.hdpiMode == HdpiMode.pixels) {
                val xScale = window.graphics!!.backBufferWidth.toFloat() / window.graphics!!.width.toFloat()
                val yScale = window.graphics!!.backBufferHeight.toFloat() / window.graphics!!.height.toFloat()
                deltaX = (deltaX * xScale).toInt()
                deltaY = (deltaY * yScale).toInt()
                this@Lwjgl3Input.mouseX = (this@Lwjgl3Input.mouseX * xScale).toInt()
                this@Lwjgl3Input.mouseY = (this@Lwjgl3Input.mouseY * yScale).toInt()
            }

            (Core.graphics as Lwjgl3Graphics).requestRendering()
            val time = System.nanoTime()
            if (mousePressed > 0) {
                eventQueue.touchDragged(this@Lwjgl3Input.mouseX, this@Lwjgl3Input.mouseY, 0)
            } else {
                eventQueue.mouseMoved(this@Lwjgl3Input.mouseX, this@Lwjgl3Input.mouseY)
            }
        }
    }

    private val mouseButtonCallback: GLFWMouseButtonCallback = object : GLFWMouseButtonCallback() {
        override fun invoke(window: Long, button: Int, action: Int, mods: Int) {
            val gdxButton = toGdxButton(button)
            if (button != -1 && gdxButton == KeyCode.unknown) return

            val time = System.nanoTime()
            if (action == GLFW.GLFW_PRESS) {
                mousePressed++
                justTouched = true
                justPressedButtons[button] = true
                (Core.graphics as Lwjgl3Graphics).requestRendering()
                eventQueue.touchDown(mouseX, mouseY, 0, gdxButton)
            } else {
                mousePressed = max(0.0, (mousePressed - 1).toDouble()).toInt()
                (Core.graphics as Lwjgl3Graphics).requestRendering()
                eventQueue.touchUp(mouseX, mouseY, 0, gdxButton)
            }
        }

        private fun toGdxButton(button: Int): KeyCode {
            if (button == 0) return KeyCode.mouseLeft
            if (button == 1) return KeyCode.mouseRight
            if (button == 2) return KeyCode.mouseMiddle
            if (button == 3) return KeyCode.mouseBack
            if (button == 4) return KeyCode.mouseForward
            return KeyCode.unknown
        }
    }

    init {
        windowHandleChanged(window.windowHandle)
    }

    fun keyCallback(window: Long, key: Int, scancode: Int, action: Int, mods: Int) {
        var keyCode: KeyCode
        when (action) {
            GLFW.GLFW_PRESS -> {
                keyCode = getGdxKeyCode(key)
                eventQueue.keyDown(keyCode)
                pressedKeyCount++
                keyJustPressed = true
                pressedKeys.set(key, true)
                justPressedKeys.set(key, true)
                Core.graphics!!.requestRendering()
                lastCharacter = 0.toChar()
                val character = characterForKeyCode(keyCode)
                if (character.code != 0) charCallback.invoke(window, character.code)
            }

            GLFW.GLFW_RELEASE -> {
                keyCode = getGdxKeyCode(key)
                pressedKeyCount--
                pressedKeys.set(key, false)
                Core.graphics!!.requestRendering()
                eventQueue.keyUp(keyCode)
            }

            GLFW.GLFW_REPEAT -> if (lastCharacter.code != 0) {
                Core.graphics!!.requestRendering()
                eventQueue.keyTyped(lastCharacter)
            }
        }
    }

    fun resetPollingStates() {
        justTouched = false
        keyJustPressed = false
        for (i in 0..<justPressedKeys.asList().size) {
            justPressedKeys.set(i, false)
        }
        for (i in justPressedButtons.indices) {
            justPressedButtons[i] = false
        }
        eventQueue.drain()
    }

    fun windowHandleChanged(windowHandle: Long) {
        resetPollingStates()
        GLFW.glfwSetKeyCallback(window.windowHandle, keyCallback)
        GLFW.glfwSetCharCallback(window.windowHandle, charCallback)
        GLFW.glfwSetScrollCallback(window.windowHandle, scrollCallback)
        GLFW.glfwSetCursorPosCallback(window.windowHandle, cursorPosCallback)
        GLFW.glfwSetMouseButtonCallback(window.windowHandle, mouseButtonCallback)
    }

    fun update() {
        eventQueue.processor = inputMultiplexer
        eventQueue.drain()
    }

    fun prepareNext() {
        if (justTouched) {
            justTouched = false
            for (i in justPressedButtons.indices) {
                justPressedButtons[i] = false
            }
        }

        if (keyJustPressed) {
            keyJustPressed = false
            for (i in 0..<justPressedKeys.asList().size) {
                justPressedKeys.set(i ,false)
            }
        }

        deltaX = 0
        deltaY = 0
    }

    val maxPointers: Int
        get() = 1

    fun getX(pointer: Int): Int {
        return if (pointer == 0) mouseX else 0
    }

    fun getDeltaX(pointer: Int): Int {
        return if (pointer == 0) deltaX else 0
    }

    fun getY(pointer: Int): Int {
        return if (pointer == 0) mouseY else 0
    }

    fun getDeltaY(pointer: Int): Int {
        return if (pointer == 0) deltaY else 0
    }

    override fun justTouched(): Boolean {
        return justTouched
    }

    override fun mouseX(): Int {
        return mouseX
    }

    override fun mouseX(pointer: Int): Int {
        return if (pointer == 0) mouseX else 0
    }

    override fun deltaX(): Int {
        return deltaX
    }

    override fun deltaX(pointer: Int): Int {
        return if (pointer == 0) deltaX else 0
    }

    override fun mouseY(): Int {
        return mouseY
    }

    override fun mouseY(pointer: Int): Int {
        return if (pointer == 0) mouseY else 0
    }

    override fun deltaY(): Int {
        return deltaY
    }

    override fun deltaY(pointer: Int): Int {
        return if (pointer == 0) deltaY else 0
    }

    override fun isTouched(): Boolean {
        return keyDown(KeyCode.mouseLeft) || keyDown(KeyCode.mouseRight)
    }

    override fun isTouched(pointer: Int): Boolean {
        return if (pointer == 0) isTouched else false
    }

    override fun getPressure(pointer: Int): Float {
        return (if (isTouched(pointer)) 1 else 0).toFloat()
    }

    fun isButtonPressed(button: Int): Boolean {
        return GLFW.glfwGetMouseButton(window.windowHandle, button) == GLFW.GLFW_PRESS
    }

    fun isButtonJustPressed(button: Int): Boolean {
        if (button < 0 || button >= justPressedButtons.size) {
            return false
        }
        return justPressedButtons[button]
    }

    var isCursorCatched: Boolean
        get() = GLFW.glfwGetInputMode(window.windowHandle, GLFW.GLFW_CURSOR) == GLFW.GLFW_CURSOR_DISABLED
        set(catched) {
            GLFW.glfwSetInputMode(
                window.windowHandle, GLFW.GLFW_CURSOR,
                if (catched) GLFW.GLFW_CURSOR_DISABLED else GLFW.GLFW_CURSOR_NORMAL
            )
        }

    fun setCursorPosition(x: Int, y: Int) {
        var x = x
        var y = y
        if (window.config.hdpiMode == HdpiMode.pixels) {
            val xScale = Core.graphics!!.width / Core.graphics!!.getBackBufferWidth().toFloat()
            val yScale = Core.graphics!!.height / Core.graphics!!.getBackBufferHeight().toFloat()
            x = (x * xScale).toInt()
            y = (y * yScale).toInt()
        }
        GLFW.glfwSetCursorPos(window.windowHandle, x.toDouble(), y.toDouble())
        cursorPosCallback.invoke(window.windowHandle, x.toDouble(), y.toDouble())
    }

    protected fun characterForKeyCode(key: KeyCode): Char {
        // Map certain key codes to character codes.
        when (key) {
            KeyCode.backspace -> return 8.toChar()
            KeyCode.tab -> return '\t'
            KeyCode.forwardDel -> return 127.toChar()
            KeyCode.dpadCenter, KeyCode.enter -> return '\n'
            else -> return 0.toChar()
        }
    }

    fun getGdxKeyCode(lwjglKeyCode: Int): KeyCode {
        return when (lwjglKeyCode) {
            GLFW.GLFW_KEY_SPACE -> KeyCode.space
            GLFW.GLFW_KEY_APOSTROPHE -> KeyCode.apostrophe
            GLFW.GLFW_KEY_COMMA -> KeyCode.comma
            GLFW.GLFW_KEY_MINUS -> KeyCode.minus
            GLFW.GLFW_KEY_PERIOD -> KeyCode.period
            GLFW.GLFW_KEY_SLASH -> KeyCode.slash
            GLFW.GLFW_KEY_0 -> KeyCode.num0
            GLFW.GLFW_KEY_1 -> KeyCode.num1
            GLFW.GLFW_KEY_2 -> KeyCode.num2
            GLFW.GLFW_KEY_3 -> KeyCode.num3
            GLFW.GLFW_KEY_4 -> KeyCode.num4
            GLFW.GLFW_KEY_5 -> KeyCode.num5
            GLFW.GLFW_KEY_6 -> KeyCode.num6
            GLFW.GLFW_KEY_7 -> KeyCode.num7
            GLFW.GLFW_KEY_8 -> KeyCode.num8
            GLFW.GLFW_KEY_9 -> KeyCode.num9
            GLFW.GLFW_KEY_SEMICOLON -> KeyCode.semicolon
            GLFW.GLFW_KEY_EQUAL -> KeyCode.equals
            GLFW.GLFW_KEY_A -> KeyCode.a
            GLFW.GLFW_KEY_B -> KeyCode.b
            GLFW.GLFW_KEY_C -> KeyCode.c
            GLFW.GLFW_KEY_D -> KeyCode.d
            GLFW.GLFW_KEY_E -> KeyCode.e
            GLFW.GLFW_KEY_F -> KeyCode.f
            GLFW.GLFW_KEY_G -> KeyCode.g
            GLFW.GLFW_KEY_H -> KeyCode.h
            GLFW.GLFW_KEY_I -> KeyCode.i
            GLFW.GLFW_KEY_J -> KeyCode.j
            GLFW.GLFW_KEY_K -> KeyCode.k
            GLFW.GLFW_KEY_L -> KeyCode.l
            GLFW.GLFW_KEY_M -> KeyCode.m
            GLFW.GLFW_KEY_N -> KeyCode.n
            GLFW.GLFW_KEY_O -> KeyCode.o
            GLFW.GLFW_KEY_P -> KeyCode.p
            GLFW.GLFW_KEY_Q -> KeyCode.q
            GLFW.GLFW_KEY_R -> KeyCode.r
            GLFW.GLFW_KEY_S -> KeyCode.s
            GLFW.GLFW_KEY_T -> KeyCode.t
            GLFW.GLFW_KEY_U -> KeyCode.u
            GLFW.GLFW_KEY_V -> KeyCode.v
            GLFW.GLFW_KEY_W -> KeyCode.w
            GLFW.GLFW_KEY_X -> KeyCode.x
            GLFW.GLFW_KEY_Y -> KeyCode.y
            GLFW.GLFW_KEY_Z -> KeyCode.z
            GLFW.GLFW_KEY_LEFT_BRACKET -> KeyCode.leftBracket
            GLFW.GLFW_KEY_BACKSLASH -> KeyCode.backslash
            GLFW.GLFW_KEY_RIGHT_BRACKET -> KeyCode.rightBracket
            GLFW.GLFW_KEY_GRAVE_ACCENT -> KeyCode.unknown
            GLFW.GLFW_KEY_WORLD_1 -> KeyCode.unknown
            GLFW.GLFW_KEY_WORLD_2 -> KeyCode.unknown
            GLFW.GLFW_KEY_ESCAPE -> KeyCode.escape
            GLFW.GLFW_KEY_ENTER -> KeyCode.enter
            GLFW.GLFW_KEY_TAB -> KeyCode.tab
            GLFW.GLFW_KEY_BACKSPACE -> KeyCode.backspace
            GLFW.GLFW_KEY_INSERT -> KeyCode.insert
            GLFW.GLFW_KEY_DELETE -> KeyCode.del
            GLFW.GLFW_KEY_RIGHT -> KeyCode.right
            GLFW.GLFW_KEY_LEFT -> KeyCode.left
            GLFW.GLFW_KEY_DOWN -> KeyCode.down
            GLFW.GLFW_KEY_UP -> KeyCode.up
            GLFW.GLFW_KEY_PAGE_UP -> KeyCode.pageUp
            GLFW.GLFW_KEY_PAGE_DOWN -> KeyCode.pageDown
            GLFW.GLFW_KEY_HOME -> KeyCode.home
            GLFW.GLFW_KEY_END -> KeyCode.end
            GLFW.GLFW_KEY_CAPS_LOCK -> KeyCode.capsLock
            GLFW.GLFW_KEY_SCROLL_LOCK -> KeyCode.scrollLock
            GLFW.GLFW_KEY_PRINT_SCREEN -> KeyCode.printScreen
            GLFW.GLFW_KEY_PAUSE -> KeyCode.pause
            GLFW.GLFW_KEY_F1 -> KeyCode.f1
            GLFW.GLFW_KEY_F2 -> KeyCode.f2
            GLFW.GLFW_KEY_F3 -> KeyCode.f3
            GLFW.GLFW_KEY_F4 -> KeyCode.f4
            GLFW.GLFW_KEY_F5 -> KeyCode.f5
            GLFW.GLFW_KEY_F6 -> KeyCode.f6
            GLFW.GLFW_KEY_F7 -> KeyCode.f7
            GLFW.GLFW_KEY_F8 -> KeyCode.f8
            GLFW.GLFW_KEY_F9 -> KeyCode.f9
            GLFW.GLFW_KEY_F10 -> KeyCode.f10
            GLFW.GLFW_KEY_F11 -> KeyCode.f11
            GLFW.GLFW_KEY_F12 -> KeyCode.f12
            GLFW.GLFW_KEY_F13 -> KeyCode.unknown
            GLFW.GLFW_KEY_F14 -> KeyCode.unknown
            GLFW.GLFW_KEY_F15 -> KeyCode.unknown
            GLFW.GLFW_KEY_F16 -> KeyCode.unknown
            GLFW.GLFW_KEY_F17 -> KeyCode.unknown
            GLFW.GLFW_KEY_F18 -> KeyCode.unknown
            GLFW.GLFW_KEY_F19 -> KeyCode.unknown
            GLFW.GLFW_KEY_F20 -> KeyCode.unknown
            GLFW.GLFW_KEY_F21 -> KeyCode.unknown
            GLFW.GLFW_KEY_F22 -> KeyCode.unknown
            GLFW.GLFW_KEY_F23 -> KeyCode.unknown
            GLFW.GLFW_KEY_F24 -> KeyCode.unknown
            GLFW.GLFW_KEY_F25 -> KeyCode.unknown
            GLFW.GLFW_KEY_NUM_LOCK -> KeyCode.unknown
            GLFW.GLFW_KEY_KP_0 -> KeyCode.numpad0
            GLFW.GLFW_KEY_KP_1 -> KeyCode.numpad1
            GLFW.GLFW_KEY_KP_2 -> KeyCode.numpad2
            GLFW.GLFW_KEY_KP_3 -> KeyCode.numpad3
            GLFW.GLFW_KEY_KP_4 -> KeyCode.numpad4
            GLFW.GLFW_KEY_KP_5 -> KeyCode.numpad5
            GLFW.GLFW_KEY_KP_6 -> KeyCode.numpad6
            GLFW.GLFW_KEY_KP_7 -> KeyCode.numpad7
            GLFW.GLFW_KEY_KP_8 -> KeyCode.numpad8
            GLFW.GLFW_KEY_KP_9 -> KeyCode.numpad9
            GLFW.GLFW_KEY_KP_DECIMAL -> KeyCode.unknown
            GLFW.GLFW_KEY_KP_DIVIDE -> KeyCode.unknown
            GLFW.GLFW_KEY_KP_MULTIPLY -> KeyCode.unknown
            GLFW.GLFW_KEY_KP_SUBTRACT -> KeyCode.unknown
            GLFW.GLFW_KEY_KP_ADD -> KeyCode.unknown
            GLFW.GLFW_KEY_KP_ENTER -> KeyCode.enter
            GLFW.GLFW_KEY_KP_EQUAL -> KeyCode.equals
            GLFW.GLFW_KEY_LEFT_SHIFT -> KeyCode.shiftLeft
            GLFW.GLFW_KEY_LEFT_CONTROL -> KeyCode.controlLeft
            GLFW.GLFW_KEY_LEFT_ALT -> KeyCode.altLeft
            GLFW.GLFW_KEY_LEFT_SUPER -> KeyCode.sym  // or "super" if preferred
            GLFW.GLFW_KEY_RIGHT_SHIFT -> KeyCode.shiftRight
            GLFW.GLFW_KEY_RIGHT_CONTROL -> KeyCode.controlRight
            GLFW.GLFW_KEY_RIGHT_ALT -> KeyCode.altRight
            GLFW.GLFW_KEY_RIGHT_SUPER -> KeyCode.sym  // or "super" if preferred
            GLFW.GLFW_KEY_MENU -> KeyCode.menu
            else -> KeyCode.unknown
        }
    }

    override fun dispose() {
        keyCallback.free()
        charCallback.free()
        scrollCallback.free()
        cursorPosCallback.free()
        mouseButtonCallback.free()
    }

    val accelerometerX: Float
        // --------------------------------------------------------------------------
        get() = 0f

    val accelerometerY: Float
        get() = 0f

    val accelerometerZ: Float
        get() = 0f

    override fun isPeripheralAvailable(peripheral: Peripheral): Boolean {
        return peripheral == Peripheral.hardwareKeyboard
    }

    override fun setOnscreenKeyboardVisible(visible: Boolean) {
    }

    override fun vibrate(milliseconds: Int) {
    }

    val azimuth: Float
        get() = 0f

    val pitch: Float
        get() = 0f

    val roll: Float
        get() = 0f

    override fun getRotationMatrix(matrix: FloatArray?) {
    }

    override fun getCurrentEventTime(): Long {
        return eventQueue.currentEventTime
    }

    val gyroscopeX: Float
        get() = 0f

    val gyroscopeY: Float
        get() = 0f

    val gyroscopeZ: Float
        get() = 0f
}