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

import Lwjgl3Graphics
import arc.ApplicationListener
import arc.Core
import arc.graphics.Pixmap
import arc.struct.Seq
import arc.util.Disposable
import arc.util.OS
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.*
import java.nio.IntBuffer


class Lwjgl3Window internal constructor(
    /** @return the [ApplicationListener] associated with this window
     */
    val listener: ApplicationListener,
    config: Lwjgl3ApplicationConfiguration,
    application: Lwjgl3Application
) : Disposable {
    var windowHandle: Long = 0
        private set
    val application: Lwjgl3Application
    var isListenerInitialized: Boolean = false
        private set
    private var windowListener: Lwjgl3WindowListener? = null
    var graphics: Lwjgl3Graphics? = null
        private set
    private var input: Lwjgl3Input? = null
    private val config: Lwjgl3ApplicationConfiguration
    private val runnables = Seq<Runnable>()
    private val executedRunnables = Seq<Runnable>()
    private val tmpBuffer: IntBuffer
    private val tmpBuffer2: IntBuffer

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
                        listener.resume()
                    }
                    windowListener?.focusGained()
                } else {
                    windowListener?.focusLost()
                    if (config.pauseWhenLostFocus) {
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
                windowListener?.iconified(iconified)
                this@Lwjgl3Window.isIconified = iconified
                if (iconified) {
                    if (config.pauseWhenMinimized) {
                        listener.pause()
                    }
                } else {
                    if (config.pauseWhenMinimized) {
                        listener.resume()
                    }
                }
            }
        }
    }

    private val maximizeCallback: GLFWWindowMaximizeCallback = object : GLFWWindowMaximizeCallback() {
        override fun invoke(windowHandle: Long, maximized: Boolean) {
            postRunnable {
                windowListener?.maximized(maximized)
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
                windowListener?.filesDropped(files)
            }
        }
    }

    private val refreshCallback: GLFWWindowRefreshCallback = object : GLFWWindowRefreshCallback() {
        override fun invoke(windowHandle: Long) {
            postRunnable {
                windowListener?.refreshRequested()
            }
        }
    }

    init {
        this.windowListener = config.windowListener
        this.config = config
        this.application = application
        this.tmpBuffer = BufferUtils.createIntBuffer(1)
        this.tmpBuffer2 = BufferUtils.createIntBuffer(1)
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

        windowListener?.created(this)
    }

    /** @return the [Lwjgl3WindowListener] set on this window
     */
    fun getWindowListener(): Lwjgl3WindowListener? {
        return windowListener
    }

    fun setWindowListener(listener: Lwjgl3WindowListener?) {
        this.windowListener = listener
    }

    /** Post a [Runnable] to this window's event queue. Use this if you access statics like [Gdx.graphics] in your
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
     * 32x32 and 48x48. Pixmap format [RGBA8888][arc.graphics.Pixmap.Format.RGBA8888] is preferred so
     * the images will not have to be copied and converted. The chosen image is copied, and the provided Pixmaps are not
     * disposed.
     */
    fun setIcon(vararg image: Pixmap?) {
        Companion.setIcon(windowHandle, image.toList().toTypedArray())
    }

    /** Sets minimum and maximum size limits for the window. If the window is full screen or not resizable, these limits are
     * ignored. Use -1 to indicate an unrestricted dimension.  */
    fun setSizeLimits(minWidth: Int, minHeight: Int, maxWidth: Int, maxHeight: Int) {
        setSizeLimits(windowHandle, minWidth, minHeight, maxWidth, maxHeight)
    }

    fun getInput(): Lwjgl3Input? {
        return input
    }

    fun windowHandleChanged(windowHandle: Long) {
        this.windowHandle = windowHandle
        input?.windowHandleChanged(windowHandle)
    }

    fun update(): Boolean {
        if (!isListenerInitialized) {
            initializeListener()
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

        if (!isIconified) input?.update()

        synchronized(this) {
            shouldRender = shouldRender or (requestRendering && !isIconified)
            requestRendering = false
        }

        // In case glfw_async is used, we need to resize outside the GLFW
        if (asyncResized) {
            asyncResized = false
            graphics!!.updateFramebufferInfo()
            graphics!!.gl20!!.glViewport(0, 0, graphics!!.backBufferWidth, graphics!!.backBufferHeight)
            listener.resize(graphics!!.width, graphics!!.height)
            graphics!!.update()
            listener.update()
            GLFW.glfwSwapBuffers(windowHandle)
            return true
        }

        if (shouldRender) {
            graphics!!.update()
            listener.update()
            GLFW.glfwSwapBuffers(windowHandle)
        }

        if (!isIconified) input?.prepareNext()

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

    fun getConfig(): Lwjgl3ApplicationConfiguration {
        return config
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
        Core.gl30 = graphics!!.gL30
        Core.gl20 = if (Core.gl30 != null) Core.gl30 else graphics!!.gL20
        Core.gl = Core.gl20
        Core.input = input

        GLFW.glfwMakeContextCurrent(windowHandle)
    }

    override fun dispose() {
        listener.pause()
        listener.dispose()
        Lwjgl3Cursor.dispose(this)
        graphics!!.dispose()
        input?.dispose()
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

        fun setSizeLimits(windowHandle: Long, minWidth: Int, minHeight: Int, maxWidth: Int, maxHeight: Int) {
            GLFW.glfwSetWindowSizeLimits(
                windowHandle,
                if (minWidth > -1) minWidth else GLFW.GLFW_DONT_CARE,
                if (minHeight > -1) minHeight else GLFW.GLFW_DONT_CARE,
                if (maxWidth > -1) maxWidth else GLFW.GLFW_DONT_CARE,
                if (maxHeight > -1) maxHeight else GLFW.GLFW_DONT_CARE
            )
        }
    }
}