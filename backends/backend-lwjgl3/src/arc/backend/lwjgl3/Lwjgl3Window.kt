package arc.backend.lwjgl3

import arc.Application
import arc.ApplicationListener
import arc.Core
import arc.Files
import arc.backend.lwjgl3.*
import arc.files.Fi
import arc.graphics.Pixmap
import arc.graphics.Vulkan
import arc.struct.Seq
import arc.util.Disposable
import arc.util.OS
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.*
import java.nio.IntBuffer

class Lwjgl3Window internal constructor(
    /** @return the [ApplicationListener] associated with this window
     */
    val listener: ApplicationListener, private val lifecycleListeners: Seq<ApplicationListener>,
    val config: Lwjgl3ApplicationConfiguration,
    application: Lwjgl3Application
) : Disposable {
    var windowHandle: Long = 0
        private set
    val application: Lwjgl3Application = application
    var isListenerInitialized: Boolean = false
        private set

    /** @return the [Lwjgl3WindowListener] set on this window
     */
    var windowListener: Lwjgl3WindowListener? = config.windowListener
    var graphics: Lwjgl3Graphics? = null
        private set
    var input: Lwjgl3Input? = null
        private set
    private val runnables = Seq<Runnable>()
    private val executedRunnables = Seq<Runnable>()
    private val tmpBuffer: IntBuffer = BufferUtils.createIntBuffer(1)
    private val tmpBuffer2: IntBuffer = BufferUtils.createIntBuffer(1)

    /** Whether the window is iconfieid  */
    var isIconified: Boolean = false
    var isFocused: Boolean = false
    var asyncResized: Boolean = false
    private var requestRendering = false

    private val focusCallback: GLFWWindowFocusCallback = object : GLFWWindowFocusCallback() {
        override fun invoke(windowHandle: Long, focused: Boolean) {
            postRunnable {
                if (focused) {
                    if (config.pauseWhenLostFocus) {
                        synchronized(lifecycleListeners) {
                            for (lifecycleListener in lifecycleListeners) {
                                lifecycleListener.resume()
                            }
                        }
                        listener.resume()
                    }
                    if (windowListener != null) {
                        windowListener!!.focusGained()
                    }
                } else {
                    input?.resetPollingStates()
                    if (windowListener != null) {
                        windowListener!!.focusLost()
                    }
                    if (config.pauseWhenLostFocus) {
                        synchronized(lifecycleListeners) {
                            for (lifecycleListener in lifecycleListeners) {
                                lifecycleListener.pause()
                            }
                        }
                        listener.pause()
                    }
                }
                this@Lwjgl3Window.isFocused = focused
            }
        }
    }

    private val iconifyCallback: GLFWWindowIconifyCallback = object : GLFWWindowIconifyCallback() {
        override fun invoke(windowHandle: Long, iconified: Boolean) {
            postRunnable {
                if (windowListener != null) {
                    windowListener!!.iconified(iconified)
                }
                this@Lwjgl3Window.isIconified = iconified
                if (iconified) {
                    input?.resetPollingStates()
                    if (config.pauseWhenMinimized) {
                        synchronized(lifecycleListeners) {
                            for (lifecycleListener in lifecycleListeners) {
                                lifecycleListener.pause()
                            }
                        }
                        listener.pause()
                    }
                } else {
                    if (config.pauseWhenMinimized) {
                        synchronized(lifecycleListeners) {
                            for (lifecycleListener in lifecycleListeners) {
                                lifecycleListener.resume()
                            }
                        }
                        listener.resume()
                    }
                }
            }
        }
    }

    private val maximizeCallback: GLFWWindowMaximizeCallback = object : GLFWWindowMaximizeCallback() {
        override fun invoke(windowHandle: Long, maximized: Boolean) {
            postRunnable {
                if (windowListener != null) {
                    windowListener!!.maximized(maximized)
                }
            }
        }
    }

    private val closeCallback: GLFWWindowCloseCallback = object : GLFWWindowCloseCallback() {
        override fun invoke(windowHandle: Long) {
            postRunnable {
                if (windowListener != null) {
                    if (!windowListener!!.closeRequested()) {
                        GLFW.glfwSetWindowShouldClose(windowHandle, false)
                    }
                }
            }
        }
    }

    private val dropCallback: GLFWDropCallback = object : GLFWDropCallback() {
        override fun invoke(windowHandle: Long, count: Int, names: Long) {
            val files = arrayOfNulls<String>(count)
            for (i in 0..<count) {
                files[i] = getName(names, i)
            }
            postRunnable {
                if (windowListener != null) {
                    windowListener!!.filesDropped(files)
                }
            }
        }
    }

    private val refreshCallback: GLFWWindowRefreshCallback = object : GLFWWindowRefreshCallback() {
        override fun invoke(windowHandle: Long) {
            postRunnable {
                if (windowListener != null) {
                    windowListener!!.refreshRequested()
                }
            }
        }
    }

    fun create(windowHandle: Long) {
        this.windowHandle = windowHandle
        this.input = application.createInput(this)
        this.graphics = Lwjgl3Graphics(this)

        GLFW.glfwSetWindowFocusCallback(windowHandle, focusCallback)
        GLFW.glfwSetWindowIconifyCallback(windowHandle, iconifyCallback)
        GLFW.glfwSetWindowMaximizeCallback(windowHandle, maximizeCallback)
        GLFW.glfwSetWindowCloseCallback(windowHandle, closeCallback)
        GLFW.glfwSetDropCallback(windowHandle, dropCallback)
        GLFW.glfwSetWindowRefreshCallback(windowHandle, refreshCallback)

        if (windowListener != null) {
            windowListener!!.created(this)
        }
    }

    /** Post a [Runnable] to this window's event queue. Use this if you access statics like [Core.graphics] in your
     * runnable instead of [Application.postRunnable].  */
    fun postRunnable(runnable: Runnable?) {
        synchronized(runnables) {
            runnables.add(runnable)
        }
    }

    /** Sets the position of the window in logical coordinates. All monitors span a virtual surface together. The coordinates are
     * relative to the first monitor in the virtual surface.  */
    fun setPosition(x: Int, y: Int) {
        if (GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) return
        GLFW.glfwSetWindowPos(windowHandle, x, y)
    }

    val positionX: Int
        /** @return the window position in logical coordinates. All monitors span a virtual surface together. The coordinates are
         * relative to the first monitor in the virtual surface.
         */
        get() {
            GLFW.glfwGetWindowPos(windowHandle, tmpBuffer, tmpBuffer2)
            return tmpBuffer[0]
        }

    val positionY: Int
        /** @return the window position in logical coordinates. All monitors span a virtual surface together. The coordinates are
         * relative to the first monitor in the virtual surface.
         */
        get() {
            GLFW.glfwGetWindowPos(windowHandle, tmpBuffer, tmpBuffer2)
            return tmpBuffer2[0]
        }

    /** Sets the visibility of the window. Invisible windows will still call their [ApplicationListener]  */
    fun setVisible(visible: Boolean) {
        if (visible) {
            GLFW.glfwShowWindow(windowHandle)
        } else {
            GLFW.glfwHideWindow(windowHandle)
        }
    }

    /** Closes this window and pauses and disposes the associated [ApplicationListener].  */
    fun closeWindow() {
        GLFW.glfwSetWindowShouldClose(windowHandle, true)
    }

    /** Minimizes (iconifies) the window. Iconified windows do not call their [ApplicationListener] until the window is
     * restored.  */
    fun iconifyWindow() {
        GLFW.glfwIconifyWindow(windowHandle)
    }

    /** De-minimizes (de-iconifies) and de-maximizes the window.  */
    fun restoreWindow() {
        GLFW.glfwRestoreWindow(windowHandle)
    }

    /** Maximizes the window.  */
    fun maximizeWindow() {
        GLFW.glfwMaximizeWindow(windowHandle)
    }

    /** Brings the window to front and sets input focus. The window should already be visible and not iconified.  */
    fun focusWindow() {
        GLFW.glfwFocusWindow(windowHandle)
    }

    /** Sets the icon that will be used in the window's title bar. Has no effect in macOS, which doesn't use window icons.
     * @param image One or more images. The one closest to the system's desired size will be scaled. Good sizes include 16x16,
     * 32x32 and 48x48. Pixmap format [RGBA8888][com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888] is preferred so
     * the images will not have to be copied and converted. The chosen image is copied, and the provided Pixmaps are not
     * disposed.
     */
    fun setIcon(vararg image: Pixmap?) {
        setIcon(*image)
    }

    fun setTitle(title: CharSequence) {
        GLFW.glfwSetWindowTitle(windowHandle, title)
    }

    fun windowHandleChanged(windowHandle: Long) {
        this.windowHandle = windowHandle
        input!!.windowHandleChanged(windowHandle)
    }

    fun update(): Boolean {
        if (!isListenerInitialized) {
            if(config.useVulkan){
                val vk = Core.vk
                vk?.beginFrame()
                try{
                    initializeListener()
                } finally{
                    vk?.endFrame()
                }
            }else{
                initializeListener()
            }
        }
        synchronized(runnables) {
            executedRunnables.addAll(runnables)
            runnables.clear()
        }
        for (runnable in executedRunnables) {
            runnable.run()
        }
        var shouldRender = executedRunnables.size > 0 || graphics!!.isContinuousRendering
        executedRunnables.clear()

        if (!isIconified) input!!.update()

        synchronized(this) {
            shouldRender = shouldRender or (requestRendering && !isIconified)
            requestRendering = false
        }

        // In case glfw_async is used, we need to resize outside the GLFW
        if (asyncResized) {
            asyncResized = false
            graphics!!.updateFramebufferInfo()
            Core.gl20.glViewport(0, 0, graphics!!.backBufferWidth, graphics!!.backBufferHeight)
            listener.resize(graphics!!.width, graphics!!.height)
            graphics!!.update()
            val vk = Core.vk
            vk?.beginFrame()
            try {
                listener.update()
            } finally {
                vk?.endFrame()
            }
            if(!config.useVulkan){
                GLFW.glfwSwapBuffers(windowHandle)
            }
            return true
        }

        if (shouldRender) {
            graphics!!.update()
            val vk = Core.vk
            vk?.beginFrame()
            try {
                listener.update()
            } finally {
                vk?.endFrame()
            }
            if(!config.useVulkan){
                GLFW.glfwSwapBuffers(windowHandle)
            }
        }

        if (!isIconified) input!!.prepareNext()

        return shouldRender
    }

    fun requestRendering() {
        synchronized(this) {
            this.requestRendering = true
        }
    }

    fun shouldClose(): Boolean {
        return GLFW.glfwWindowShouldClose(windowHandle)
    }

    fun initializeListener() {
        if (!isListenerInitialized) {
            listener.init()
            listener.resize(graphics!!.width, graphics!!.height)
            isListenerInitialized = true
        }
    }

    fun makeCurrent() {
        Core.graphics = graphics
        Core.gl30 = graphics?.gL30
        Core.gl20 = if (Core.gl30 != null) Core.gl30 else graphics!!.gL20
        Core.gl = Core.gl20
        Core.vk = if (Core.gl30 is Vulkan) Core.gl30 as Vulkan else null
        Core.input = input

        if(!config.useVulkan){
            GLFW.glfwMakeContextCurrent(windowHandle)
        }
    }

    override fun dispose() {
        listener.pause()
        listener.dispose()
        Lwjgl3Cursor.dispose(this)
        graphics!!.dispose()
        input!!.dispose()
        GLFW.glfwSetWindowFocusCallback(windowHandle, null)
        GLFW.glfwSetWindowIconifyCallback(windowHandle, null)
        GLFW.glfwSetWindowCloseCallback(windowHandle, null)
        GLFW.glfwSetDropCallback(windowHandle, null)
        GLFW.glfwDestroyWindow(windowHandle)

        focusCallback.free()
        iconifyCallback.free()
        maximizeCallback.free()
        closeCallback.free()
        dropCallback.free()
        refreshCallback.free()
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + (windowHandle xor (windowHandle ushr 32)).toInt()
        return result
    }

    override fun equals(obj: Any?): Boolean {
        if (this === obj) return true
        if (obj == null) return false
        if (javaClass != obj.javaClass) return false
        val other = obj as Lwjgl3Window
        if (windowHandle != other.windowHandle) return false
        return true
    }

    fun flash() {
        GLFW.glfwRequestWindowAttention(windowHandle)
    }

    companion object {
        fun setIcon(windowHandle: Long, imagePaths: Array<String?>, imageFileType: arc.Files.FileType?) {
            if (OS.isMac) return

            val pixmaps = arrayOfNulls<Pixmap>(imagePaths.size)
            for (i in imagePaths.indices) {
                pixmaps[i] = Pixmap(Core.files.get(imagePaths[i], imageFileType))
            }

            setIcon(windowHandle, pixmaps)

            for (pixmap in pixmaps) {
                pixmap?.dispose()
            }
        }

        fun setIcon(windowHandle: Long, images: Array<Pixmap?>) {
            if (OS.isMac) return
            if (GLFW.glfwGetPlatform() == GLFW.GLFW_PLATFORM_WAYLAND) return

            val buffer = GLFWImage.malloc(images.size)
            val tmpPixmaps = arrayOfNulls<Pixmap>(images.size)

            for (i in images.indices) {
                var pixmap = images[i]

                if (pixmap!!.glFormat != Pixmap.Format.rgba8888.glFormat) {
                    val rgba: Pixmap = Pixmap(pixmap.getWidth(), pixmap.getHeight())
                    rgba.draw(pixmap, 0, 0)
                    tmpPixmaps[i] = rgba
                    pixmap = rgba
                }

                val icon = GLFWImage.malloc()
                icon[pixmap.getWidth(), pixmap.getHeight()] = pixmap.getPixels()
                buffer.put(icon)

                icon.free()
            }

            buffer.position(0)
            GLFW.glfwSetWindowIcon(windowHandle, buffer)

            buffer.free()
            for (pixmap in tmpPixmaps) {
                pixmap?.dispose()
            }
        }
    }
}
