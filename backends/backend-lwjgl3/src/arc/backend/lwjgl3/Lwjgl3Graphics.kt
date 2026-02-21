package arc.backend.lwjgl3

import arc.Application
import arc.Core
import arc.Core.gl20
import arc.Graphics
import arc.Graphics.Cursor.SystemCursor
import arc.graphics.GL20
import arc.graphics.GL30
import arc.graphics.Pixmap
import arc.graphics.Vulkan
import arc.graphics.gl.GLVersion
import arc.graphics.gl.HdpiMode
import arc.math.geom.Point2
import arc.struct.Seq
import arc.util.ArcRuntimeException
import arc.util.Disposable
import arc.util.Log
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWFramebufferSizeCallback
import org.lwjgl.opengl.GL11
import java.nio.IntBuffer
import kotlin.concurrent.Volatile


class Lwjgl3Graphics(val window: Lwjgl3Window) : Graphics(), Disposable {
    private var gLVersion: GLVersion? = null
    private var vulkanCompat: Lwjgl3VulkanCompatLayer? = null

    @Volatile
    private var backBufferWidth: Int = 0

    @Volatile
    private var backBufferHeight: Int = 0

    @Volatile
    private var logicalWidth: Int = 0

    @Volatile
    private var logicalHeight: Int = 0

    @Volatile
    private var isContinuousRendering: Boolean = true
    private var bufferFormat: BufferFormat? = null
    private var lastFrameTime: Long = -1
    private var deltaTime: Float = 0f
    private var resetDeltaTime = false
    private var frameId: Long = 0
    private var frameCounterStart: Long = 0
    private var frames = 0
    private var framesPerSecond: Int = 0
    private var windowPosXBeforeFullscreen = 0
    private var windowPosYBeforeFullscreen = 0
    private var windowWidthBeforeFullscreen = 0
    private var windowHeightBeforeFullscreen = 0
    private var displayModeBeforeFullscreen: Lwjgl3DisplayMode? = null

    var tmpBuffer: IntBuffer = BufferUtils.createIntBuffer(1)
    var tmpBuffer2: IntBuffer = BufferUtils.createIntBuffer(1)

    var resizeCallback: GLFWFramebufferSizeCallback = object : GLFWFramebufferSizeCallback() {
        override fun invoke(windowHandle: Long, width: Int, height: Int) {
            if ("glfw_async" != GLFW.getLibrary().name) {
                updateFramebufferInfo()
                if (!window.isListenerInitialized) {
                    return
                }
                window.makeCurrent()
                Core.gl20?.glViewport(0, 0, backBufferWidth, backBufferHeight)
                window.listener.resize(width, height)
                update()
                window.listener.update()
                if(!window.config.useVulkan){
                    GLFW.glfwSwapBuffers(windowHandle)
                }
            } else {
                window.asyncResized = true
            }
        }
    }

    init {
        if (window.config.useVulkan) {
            val vk = Lwjgl3VulkanCompatLayer(window.windowHandle)
            if (!vk.isSupported) {
                throw ArcRuntimeException("Vulkan backend requested, but Vulkan is not supported on this runtime.")
            }

            vulkanCompat = vk
            Core.vk = vk
            Core.gl30 = vk
            Core.gl20 = vk
            Core.gl = vk
            Log.info("Choosing Vulkan Backend @", vk.getBackendName())
        } else if (window.config.glEmulation == Lwjgl3ApplicationConfiguration.GLEmulation.GL30) {
            Core.vk = null
            Core.gl30 = Lwjgl3GL30()
            Core.gl20 = Core.gl30
            Core.gl = Core.gl20
        } else if (window.config.glEmulation == Lwjgl3ApplicationConfiguration.GLEmulation.ANGLE_GLES30) {
            Core.vk = null
            Core.gl30 = Class.forName("arc.backend.lwjgl3.angle.Lwjgl3GLES30").newInstance() as GL30
            Core.gl20 = Core.gl30
            Core.gl = Core.gl20
        } else if (window.config.glEmulation == Lwjgl3ApplicationConfiguration.GLEmulation.ANGLE_GLES20) {
            Core.vk = null
            Core.gl30 = null
            Core.gl20 = Class.forName("arc.backend.lwjgl3.angle.Lwjgl3GLES20").newInstance() as GL20
            Core.gl = Core.gl20
        } else if (window.config.glEmulation == Lwjgl3ApplicationConfiguration.GLEmulation.GL20) {
            try {
                Core.vk = null
                Core.gl20 = Lwjgl3GL20()
            } catch (t: Throwable) {
                throw ArcRuntimeException("Couldn't instantiate GL20.", t)
            }
            Core.gl30 = null
            Core.gl = Core.gl20
        } else {
            throw ArcRuntimeException("Couldn't find OpenGL.")
        }
        updateFramebufferInfo()
        initiateGL()
        if(!window.config.useVulkan && Lwjgl3Application.glVersion != null){
            Log.info("Choosing GL Version @", Lwjgl3Application.glVersion!!.debugVersionString)
        }
        GLFW.glfwSetFramebufferSizeCallback(window.windowHandle, resizeCallback)
    }

    private fun initiateGL() {
        if(window.config.useVulkan){
            gLVersion = GLVersion(
                Application.ApplicationType.desktop,
                "Vulkan 1.0 (Arc native Vulkan backend)",
                "Arc",
                "LWJGL3 Vulkan"
            )
            return
        }

        val versionString: String = Core.gl20!!.glGetString(GL11.GL_VERSION)
        val vendorString: String = Core.gl20!!.glGetString(GL11.GL_VENDOR)
        val rendererString: String = Core.gl20!!.glGetString(GL11.GL_RENDERER)
        gLVersion = GLVersion(Application.ApplicationType.desktop, versionString, vendorString, rendererString)
    }

    fun updateFramebufferInfo() {
        GLFW.glfwGetFramebufferSize(window.windowHandle, tmpBuffer, tmpBuffer2)
        this.backBufferWidth = tmpBuffer[0]
        this.backBufferHeight = tmpBuffer2[0]
        GLFW.glfwGetWindowSize(window.windowHandle, tmpBuffer, tmpBuffer2)
        this@Lwjgl3Graphics.logicalWidth = tmpBuffer[0]
        this@Lwjgl3Graphics.logicalHeight = tmpBuffer2[0]
        val config = window.config
        bufferFormat = BufferFormat(
            config.r, config.g, config.b, config.a, config.depth, config.stencil, config.samples,
            false
        )
    }

    fun update() {
        val time = System.nanoTime()
        if (lastFrameTime == -1L) lastFrameTime = time
        if (resetDeltaTime) {
            resetDeltaTime = false
            deltaTime = 0f
        } else deltaTime = (time - lastFrameTime) / 1000000000.0f
        lastFrameTime = time

        if (time - frameCounterStart >= 1000000000) {
            framesPerSecond = frames
            frames = 0
            frameCounterStart = time
        }
        frames++
        frameId++
    }

    override fun isGL30Available(): Boolean {
        return Core.gl30 != null
    }

    override fun getGL20(): GL20 {
        return Core.gl20
    }

    override fun setGL20(gl20: GL20?) {
        Core.gl20 = gl20
        Core.gl = gl20
        if (gl20 !is Vulkan) {
            Core.vk = null
        }
    }

    override fun getGL30(): GL30? {
        return Core.gl30
    }

    override fun setGL30(gl30: GL30?) {
        Core.gl30 = gl30
        if (gl30 != null) {
            Core.gl20 = gl30
            Core.gl = gl30
        }
        Core.vk = if (gl30 is Vulkan) gl30 else null
    }

    override fun getWidth(): Int {
        return if (window.config.hdpiMode == HdpiMode.pixels) {
            backBufferWidth
        } else {
            logicalWidth
        }
    }

    override fun getHeight(): Int {
        return if (window.config.hdpiMode == HdpiMode.pixels) {
            backBufferHeight
        } else {
            logicalHeight
        }
    }

    override fun getBackBufferWidth(): Int {
        return backBufferWidth
    }

    override fun getBackBufferHeight(): Int {
        return backBufferHeight
    }

    override fun getFrameId(): Long {
        return frameId
    }

    override fun getDeltaTime(): Float {
        return deltaTime
    }

    override fun getFramesPerSecond(): Int {
        return framesPerSecond
    }

    override fun getGLVersion(): GLVersion {
        if(window.config.useVulkan){
            return gLVersion!!
        }
        val versionString = gl20.glGetString(GL11.GL_VERSION)
        val vendorString = gl20.glGetString(GL11.GL_VENDOR)
        val rendererString = gl20.glGetString(GL11.GL_RENDERER)
        return GLVersion(Application.ApplicationType.desktop, versionString, vendorString, rendererString);
    }

    fun resetDeltaTime() {
        resetDeltaTime = true
    }

    override fun getPpiX(): Float = getPpcX() * 2.54f

    override fun getPpiY(): Float = getPpcY() * 2.54f

    override fun getPpcX(): Float {
        val monitor = getMonitor()
        GLFW.glfwGetMonitorPhysicalSize(monitor!!.monitorHandle, tmpBuffer, tmpBuffer2)
        val sizeX = tmpBuffer[0]
        val mode = getDisplayMode()
        return mode.width / sizeX.toFloat() * 10
    }

    override fun getPpcY(): Float {
        val monitor = getMonitor()
        GLFW.glfwGetMonitorPhysicalSize(monitor!!.monitorHandle, tmpBuffer, tmpBuffer2)
        val sizeY = tmpBuffer2[0]
        val mode = getDisplayMode()
        return mode.height / sizeY.toFloat() * 10
    }

    override fun getDensity(): Float {
        return 0f
    }

    fun getPrimaryMonitor(): Lwjgl3Monitor {
        return Lwjgl3ApplicationConfiguration.toLwjgl3Monitor(GLFW.glfwGetPrimaryMonitor())
    }

    fun getMonitor(): Lwjgl3Monitor? {
        val monitors: Seq<Lwjgl3Monitor?> = getMonitors()
        var result: Lwjgl3Monitor? = monitors[0]

        GLFW.glfwGetWindowPos(window.windowHandle, tmpBuffer, tmpBuffer2)
        val windowX = tmpBuffer[0]
        val windowY = tmpBuffer2[0]
        GLFW.glfwGetWindowSize(window.windowHandle, tmpBuffer, tmpBuffer2)
        val windowWidth = tmpBuffer[0]
        val windowHeight = tmpBuffer2[0]
        var overlap: Int
        var bestOverlap = 0

        for (monitor in monitors) {
            val mode = getDisplayMode(monitor)

            overlap = (Math.max(
                0,
                Math.min(windowX + windowWidth, monitor!!.virtualX + mode.width) - Math.max(windowX, monitor!!.virtualX)
            )
                    * Math.max(
                0,
                Math.min(windowY + windowHeight, monitor.virtualY + mode.height) - Math.max(windowY, monitor.virtualY)
            ))

            if (bestOverlap < overlap) {
                bestOverlap = overlap
                result = monitor
            }
        }
        return result
    }

    fun getMonitors(): Seq<Lwjgl3Monitor?> {
        val glfwMonitors = GLFW.glfwGetMonitors()
        val monitors: Seq<Lwjgl3Monitor?> = Seq<Lwjgl3Monitor?>(glfwMonitors!!.limit())
        for (i in 0..<glfwMonitors.limit()) {
            monitors[i] = Lwjgl3ApplicationConfiguration.toLwjgl3Monitor(glfwMonitors[i])
        }
        return monitors
    }

    fun getDisplayModes(): Seq<Lwjgl3DisplayMode?> {
        return Lwjgl3ApplicationConfiguration.getDisplayModes(getMonitor()!!)
    }

    fun getDisplayModes(monitor: Lwjgl3Monitor?): Seq<Lwjgl3DisplayMode?> {
        return Lwjgl3ApplicationConfiguration.getDisplayModes(monitor!!)
    }

    fun getDisplayMode(): Lwjgl3DisplayMode {
        return Lwjgl3ApplicationConfiguration.getDisplayMode(getMonitor()!!)
    }

    fun getDisplayMode(monitor: Lwjgl3Monitor?): Lwjgl3DisplayMode {
        return Lwjgl3ApplicationConfiguration.getDisplayMode(monitor!!)
    }

    fun setFullscreenMode(displayMode: Lwjgl3DisplayMode): Boolean {
        window.input!!.resetPollingStates()
        val newMode = displayMode as Lwjgl3DisplayMode
        if (isFullscreen) {
            val currentMode = this.getDisplayMode() as Lwjgl3DisplayMode
            if (currentMode.monitor == newMode.monitor && currentMode.refreshRate == newMode.refreshRate) {
                // same monitor and refresh rate
                GLFW.glfwSetWindowSize(window.windowHandle, newMode.width, newMode.height)
            } else {
                // different monitor and/or refresh rate
                GLFW.glfwSetWindowMonitor(
                    window.windowHandle, newMode.monitor, 0, 0, newMode.width, newMode.height,
                    newMode.refreshRate
                )
            }
        } else {
            // store window position so we can restore it when switching from fullscreen to windowed later
            storeCurrentWindowPositionAndDisplayMode()

            // switch from windowed to fullscreen
            GLFW.glfwSetWindowMonitor(
                window.windowHandle, newMode.monitor, 0, 0, newMode.width, newMode.height,
                newMode.refreshRate
            )
        }
        updateFramebufferInfo()

        setVSync(window.config.vSyncEnabled)

        return true
    }

    private fun storeCurrentWindowPositionAndDisplayMode() {
        windowPosXBeforeFullscreen = window.positionX
        windowPosYBeforeFullscreen = window.positionY
        windowWidthBeforeFullscreen = logicalWidth
        windowHeightBeforeFullscreen = logicalHeight
        displayModeBeforeFullscreen = getDisplayMode()
    }

    override fun setWindowedMode(width: Int, height: Int): Boolean {
        window.input!!.resetPollingStates()
        if (!isFullscreen) {
            var newPos: Point2? = null
            var centerWindow = false
            if (width != logicalWidth || height != logicalHeight) {
                centerWindow = true // recenter the window since its size changed
                newPos = Lwjgl3ApplicationConfiguration.calculateCenteredWindowPosition(
                    getMonitor()!!,
                    width,
                    height
                )
            }
            GLFW.glfwSetWindowSize(window.windowHandle, width, height)
            if (centerWindow) {
                window.setPosition(
                    newPos!!.x,
                    newPos.y
                ) // on macOS the centering has to happen _after_ the new window size was set
            }
        } else { // if we were in fullscreen mode, we should consider restoring a previous display mode
            if (displayModeBeforeFullscreen == null) {
                storeCurrentWindowPositionAndDisplayMode()
            }
            if (width != windowWidthBeforeFullscreen || height != windowHeightBeforeFullscreen) { // center the window since its size
                // changed
                val newPos: Point2 = Lwjgl3ApplicationConfiguration.calculateCenteredWindowPosition(
                    getMonitor()!!, width,
                    height
                )
                GLFW.glfwSetWindowMonitor(
                    window.windowHandle, 0, newPos.x, newPos.y, width, height,
                    displayModeBeforeFullscreen!!.refreshRate
                )
            } else { // restore previous position
                GLFW.glfwSetWindowMonitor(
                    window.windowHandle, 0, windowPosXBeforeFullscreen, windowPosYBeforeFullscreen, width,
                    height, displayModeBeforeFullscreen!!.refreshRate
                )
            }
        }
        updateFramebufferInfo()
        return true
    }

    override fun setTitle(title: String?) {
        var title = title
        if (title == null) {
            title = ""
        }
        GLFW.glfwSetWindowTitle(window.windowHandle, title)
    }

    override fun setBorderless(undecorated: Boolean) {
        return
    }

    override fun setVSync(vsync: Boolean) {
        window.config.vSyncEnabled = vsync
        if(!window.config.useVulkan){
            GLFW.glfwSwapInterval(if (vsync) 1 else 0)
        }
    }

    override fun getBufferFormat(): BufferFormat {
        return bufferFormat!!
    }

    /** Sets the target framerate for the application, when using continuous rendering. Must be positive. The cpu sleeps as needed.
     * Use 0 to never sleep. If there are multiple windows, the value for the first window created is used for all. Default is 0.
     *
     * @param fps fps
     */
    fun setForegroundFPS(fps: Int) {
        window.config.foregroundFPS = fps
    }

    override fun supportsExtension(extension: String): Boolean {
        if(window.config.useVulkan){
            return false
        }
        return GLFW.glfwExtensionSupported(extension)
    }

    override fun isContinuousRendering(): Boolean {
        return isContinuousRendering
    }

    override fun setContinuousRendering(isContinuous: Boolean) {
        isContinuousRendering = isContinuous
    }

    override fun requestRendering() {
        window.requestRendering()
    }

    override fun isFullscreen(): Boolean = GLFW.glfwGetWindowMonitor(window.windowHandle) != 0L

    override fun newCursor(pixmap: Pixmap, xHotspot: Int, yHotspot: Int): Cursor {
        return Lwjgl3Cursor(window, pixmap, xHotspot, yHotspot)
    }

    override fun setCursor(cursor: Cursor) {
        GLFW.glfwSetCursor(window.windowHandle, (cursor as Lwjgl3Cursor).glfwCursor)
    }

    override fun setSystemCursor(systemCursor: SystemCursor?) {
        Lwjgl3Cursor.setSystemCursor(window.windowHandle, systemCursor!!)
    }

    override fun dispose() {
        vulkanCompat?.dispose()
        vulkanCompat = null
        resizeCallback.free()
    }

    class Lwjgl3DisplayMode internal constructor(
        val monitor: Long,
        val width: Int = 0,
        val height: Int = 0,
        val refreshRate: Int = 0,
        val bitsPerPixel: Int = 0
    ) {
        override fun toString(): String {
            return width.toString() + "x" + height + ", bpp: " + bitsPerPixel + ", hz: " + refreshRate
        }
    }

    class Lwjgl3Monitor internal constructor(
        val monitorHandle: Long,
        val virtualX: Int,
        val virtualY: Int,
        val name: String?
    )
}
