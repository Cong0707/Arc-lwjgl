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

import arc.graphics.gl.HdpiMode
import arc.ApplicationListener
import arc.audio.*
import arc.Files
import arc.Files.FileType
import arc.Graphics.*
import arc.audio.Music
import arc.graphics.GL20
import arc.graphics.gl.HdpiUtils
import arc.math.*
import arc.math.geom.Point2
import arc.struct.Seq
import org.lwjgl.BufferUtils
import org.lwjgl.glfw.GLFW
import java.awt.DisplayMode
import java.io.PrintStream
import kotlin.math.max

open class Lwjgl3ApplicationConfiguration : Lwjgl3WindowConfiguration() {
    var disableAudio: Boolean = false

    /** The maximum number of threads to use for network requests. Default is [Integer.MAX_VALUE].  */
    private var maxNetThreads: Int = Int.MAX_VALUE

    internal var audioDeviceSimultaneousSources: Int = 16
    var audioDeviceBufferSize: Int = 512
    var audioDeviceBufferCount: Int = 9

    enum class GLEmulation {
        ANGLE_GLES20, GL20, ANGLE_GLES30, GL30
    }

    enum class GLAngleBackend {
        none, d3d9, d3d11, opengl, opengles, metal, vulkan
    }

    var glEmulation: GLEmulation = GLEmulation.GL20
    /** Enables the dedicated Vulkan backend path. */
    var useVulkan: Boolean = false
    var gles30ContextMajorVersion: Int = 3
    var gles30ContextMinorVersion: Int = 2

    var angleBackend = GLAngleBackend.none

    var r: Int = 8
    var g: Int = 8
    var b: Int = 8
    var a: Int = 8
    var depth: Int = 16
    var stencil: Int = 0
    var samples: Int = 0
    var transparentFramebuffer: Boolean = false

    var idleFPS: Int = 60
    var foregroundFPS: Int = 0

    var pauseWhenMinimized: Boolean = true
    var pauseWhenLostFocus: Boolean = false

    var preferencesDirectory: String = ".prefs/"
    var preferencesFileType: Files.FileType = FileType.external

    var hdpiMode: HdpiMode = HdpiMode.logical

    var debug: Boolean = false
    var debugStream: PrintStream = System.err

    fun set(config: Lwjgl3ApplicationConfiguration) {
        super.setWindowConfiguration(config)
        disableAudio = config.disableAudio
        audioDeviceSimultaneousSources = config.audioDeviceSimultaneousSources
        audioDeviceBufferSize = config.audioDeviceBufferSize
        audioDeviceBufferCount = config.audioDeviceBufferCount
        glEmulation = config.glEmulation
        angleBackend = config.angleBackend
        useVulkan = config.useVulkan
        gles30ContextMajorVersion = config.gles30ContextMajorVersion
        gles30ContextMinorVersion = config.gles30ContextMinorVersion
        r = config.r
        g = config.g
        b = config.b
        a = config.a
        depth = config.depth
        stencil = config.stencil
        samples = config.samples
        transparentFramebuffer = config.transparentFramebuffer
        idleFPS = config.idleFPS
        foregroundFPS = config.foregroundFPS
        pauseWhenMinimized = config.pauseWhenMinimized
        pauseWhenLostFocus = config.pauseWhenLostFocus
        preferencesDirectory = config.preferencesDirectory
        preferencesFileType = config.preferencesFileType
        hdpiMode = config.hdpiMode
        debug = config.debug
        debugStream = config.debugStream
    }

    /** Whether to disable audio or not. If set to true, the returned audio class instances like [Audio] or [Music]
     * will be mock implementations.  */
    fun disableAudio(disableAudio: Boolean) {
        this.disableAudio = disableAudio
    }

    /** Sets the maximum number of threads to use for network requests.  */
    fun setMaxNetThreads(maxNetThreads: Int) {
        this.maxNetThreads = maxNetThreads
    }

    /** Sets the audio device configuration.
     *
     * @param simultaneousSources the maximum number of sources that can be played simultaniously (default 16)
     * @param bufferSize the audio device buffer size in samples (default 512)
     * @param bufferCount the audio device buffer count (default 9)
     */
    fun setAudioConfig(simultaneousSources: Int, bufferSize: Int, bufferCount: Int) {
        this.audioDeviceSimultaneousSources = simultaneousSources
        this.audioDeviceBufferSize = bufferSize
        this.audioDeviceBufferCount = bufferCount
    }

    /** Sets which OpenGL version to use to emulate OpenGL ES. If the given major/minor version is not supported, the backend falls
     * back to OpenGL ES 2.0 emulation through OpenGL 2.0. The default parameters for major and minor should be 3 and 2
     * respectively to be compatible with Mac OS X. Specifying major version 4 and minor version 2 will ensure that all OpenGL ES
     * 3.0 features are supported. Note however that Mac OS X does only support 3.2.
     *
     * @see [ LWJGL OSX ContextAttribs note](http://legacy.lwjgl.org/javadoc/org/lwjgl/opengl/ContextAttribs.html)
     *
     *
     * @param glVersion which OpenGL ES emulation version to use
     * @param gles3MajorVersion OpenGL ES major version, use 3 as default
     * @param gles3MinorVersion OpenGL ES minor version, use 2 as default
     */
    fun setOpenGLEmulation(glVersion: GLEmulation, gles3MajorVersion: Int, gles3MinorVersion: Int) {
        this.glEmulation = glVersion
        this.gles30ContextMajorVersion = gles3MajorVersion
        this.gles30ContextMinorVersion = gles3MinorVersion
    }

    /** Enables the Vulkan backend path while keeping GL API compatibility for existing rendering code. */
    fun setVulkan(useVulkan: Boolean){
        this.useVulkan = useVulkan
    }

    /** Sets the bit depth of the color, depth and stencil buffer as well as multi-sampling.
     *
     * @param r red bits (default 8)
     * @param g green bits (default 8)
     * @param b blue bits (default 8)
     * @param a alpha bits (default 8)
     * @param depth depth bits (default 16)
     * @param stencil stencil bits (default 0)
     * @param samples MSAA samples (default 0)
     */
    fun setBackBufferConfig(r: Int, g: Int, b: Int, a: Int, depth: Int, stencil: Int, samples: Int) {
        this.r = r
        this.g = g
        this.b = b
        this.a = a
        this.depth = depth
        this.stencil = stencil
        this.samples = samples
    }

    /** Enables use of OpenGL debug message callbacks. If not supported by the core GL driver (since GL 4.3), this uses the
     * KHR_debug, ARB_debug_output or AMD_debug_output extension if available. By default, debug messages with NOTIFICATION
     * severity are disabled to avoid log spam.
     *
     * You can call with [System.err] to output to the "standard" error output stream.
     *
     * Use [Lwjgl3Application.setGLDebugMessageControl] to enable or
     * disable other severity debug levels.  */
    fun enableGLDebugOutput(enable: Boolean, debugOutputStream: PrintStream) {
        debug = enable
        debugStream = debugOutputStream
    }

    companion object {
        var errorStream: PrintStream = System.err

        fun copy(config: Lwjgl3ApplicationConfiguration): Lwjgl3ApplicationConfiguration {
            val copy = Lwjgl3ApplicationConfiguration()
            copy.set(config)
            return copy
        }

        val displayMode: Lwjgl3Graphics.Lwjgl3DisplayMode
            /** @return the currently active [DisplayMode] of the primary monitor
             */
            get() {
                Lwjgl3Application.initializeGlfw()
                val videoMode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor())
                return Lwjgl3Graphics.Lwjgl3DisplayMode(
                    GLFW.glfwGetPrimaryMonitor(), videoMode!!.width(), videoMode.height(),
                    videoMode.refreshRate(), videoMode.redBits() + videoMode.greenBits() + videoMode.blueBits()
                )
            }

        /** @return the currently active [DisplayMode] of the given monitor
         */
        fun getDisplayMode(monitor: Lwjgl3Graphics.Lwjgl3Monitor): Lwjgl3Graphics.Lwjgl3DisplayMode {
            Lwjgl3Application.initializeGlfw()
            val videoMode = GLFW.glfwGetVideoMode(monitor.monitorHandle)
            return Lwjgl3Graphics.Lwjgl3DisplayMode(
                monitor.monitorHandle, videoMode!!.width(), videoMode.height(),
                videoMode.refreshRate(), videoMode.redBits() + videoMode.greenBits() + videoMode.blueBits()
            )
        }

        val displayModes: Seq<Lwjgl3Graphics.Lwjgl3DisplayMode?>
            /** @return the available [DisplayMode]s of the primary monitor
             */
            get() {
                Lwjgl3Application.initializeGlfw()
                val videoModes = GLFW.glfwGetVideoModes(GLFW.glfwGetPrimaryMonitor())
                val result = Seq<Lwjgl3Graphics.Lwjgl3DisplayMode?>(videoModes!!.limit())
                for (i in result.list().indices) {
                    val videoMode = videoModes[i]
                    result[i] = Lwjgl3Graphics.Lwjgl3DisplayMode(
                        GLFW.glfwGetPrimaryMonitor(), videoMode.width(), videoMode.height(),
                        videoMode.refreshRate(), videoMode.redBits() + videoMode.greenBits() + videoMode.blueBits()
                    )
                }
                return result
            }

        /** @return the available [DisplayMode]s of the given [Monitor]
         */
        fun getDisplayModes(monitor: Lwjgl3Graphics.Lwjgl3Monitor): Seq<Lwjgl3Graphics.Lwjgl3DisplayMode?> {
            Lwjgl3Application.initializeGlfw()
            val videoModes = GLFW.glfwGetVideoModes(monitor.monitorHandle)
            val result = Seq<Lwjgl3Graphics.Lwjgl3DisplayMode?>(videoModes!!.limit())
            for (i in result.list().indices) {
                val videoMode = videoModes[i]
                result[i] = Lwjgl3Graphics.Lwjgl3DisplayMode(
                    monitor.monitorHandle,
                    videoMode.width(),
                    videoMode.height(),
                    videoMode.refreshRate(),
                    videoMode.redBits() + videoMode.greenBits() + videoMode.blueBits()
                )
            }
            return result
        }

        val primaryMonitor: Lwjgl3Graphics.Lwjgl3Monitor
            /** @return the primary [Monitor]
             */
            get() {
                Lwjgl3Application.initializeGlfw()
                return toLwjgl3Monitor(GLFW.glfwGetPrimaryMonitor())
            }

        val monitors: Seq<Lwjgl3Graphics.Lwjgl3Monitor>
            /** @return the connected [Monitor]s
             */
            get() {
                Lwjgl3Application.initializeGlfw()
                val glfwMonitors = GLFW.glfwGetMonitors()
                val monitors = Seq<Lwjgl3Graphics.Lwjgl3Monitor>(glfwMonitors!!.limit())
                for (i in 0..<glfwMonitors.limit()) {
                    monitors[i] = toLwjgl3Monitor(glfwMonitors[i])
                }
                return monitors
            }

        fun toLwjgl3Monitor(glfwMonitor: Long): Lwjgl3Graphics.Lwjgl3Monitor {
            val tmp = BufferUtils.createIntBuffer(1)
            val tmp2 = BufferUtils.createIntBuffer(1)
            GLFW.glfwGetMonitorPos(glfwMonitor, tmp, tmp2)
            val virtualX = tmp[0]
            val virtualY = tmp2[0]
            val name = GLFW.glfwGetMonitorName(glfwMonitor)
            return Lwjgl3Graphics.Lwjgl3Monitor(glfwMonitor, virtualX, virtualY, name)
        }

        fun calculateCenteredWindowPosition(monitor: Lwjgl3Graphics.Lwjgl3Monitor, newWidth: Int, newHeight: Int): Point2 {
            val tmp = BufferUtils.createIntBuffer(1)
            val tmp2 = BufferUtils.createIntBuffer(1)
            val tmp3 = BufferUtils.createIntBuffer(1)
            val tmp4 = BufferUtils.createIntBuffer(1)

            val displayMode = getDisplayMode(monitor)

            GLFW.glfwGetMonitorWorkarea(monitor.monitorHandle, tmp, tmp2, tmp3, tmp4)
            val workareaWidth = tmp3[0]
            val workareaHeight = tmp4[0]

            val minX: Int
            val minY: Int
            val maxX: Int
            val maxY: Int

            // If the new width is greater than the working area, we have to ignore stuff like the taskbar for centering and use the
            // whole monitor's size
            if (newWidth > workareaWidth) {
                minX = monitor.virtualX
                maxX = displayMode.width
            } else {
                minX = tmp[0]
                maxX = workareaWidth
            }
            // The same is true for height
            if (newHeight > workareaHeight) {
                minY = monitor.virtualY
                maxY = displayMode.height
            } else {
                minY = tmp2[0]
                maxY = workareaHeight
            }

            return Point2(
                max(minX.toDouble(), (minX + (maxX - newWidth) / 2).toDouble()).toInt(),
                max(minY.toDouble(), (minY + (maxY - newHeight) / 2).toDouble()).toInt()
            )
        }
    }
}
