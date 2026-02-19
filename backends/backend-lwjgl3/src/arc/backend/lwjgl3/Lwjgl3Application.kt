package arc.backend.lwjgl3

import arc.*
import arc.Application.ApplicationType
import arc.audio.Audio
import arc.backend.lwjgl3.*
import arc.backend.lwjgl3.Lwjgl3ApplicationConfiguration.GLEmulation
import arc.func.Cons
import arc.graphics.gl.GLVersion
import arc.math.geom.Point2
import arc.mock.MockAudio
import arc.struct.Seq
import arc.util.ArcNativesLoader
import arc.util.ArcRuntimeException
import arc.util.Log
import arc.util.OS
import org.lwjgl.glfw.GLFW
import arc.backend.lwjgl3.Lwjgl3ApplicationConfiguration.GLAngleBackend.*
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.opengl.GL11
import java.lang.reflect.Method
import kotlin.concurrent.Volatile
import kotlin.math.max
import kotlin.math.min

class Lwjgl3Application @JvmOverloads constructor(
    listener: ApplicationListener,
    config: Lwjgl3ApplicationConfiguration = Lwjgl3ApplicationConfiguration()
) :
    Application {
    private val config: Lwjgl3ApplicationConfiguration
    val windows: Seq<Lwjgl3Window> = Seq()

    @Volatile
    private var currentWindow: Lwjgl3Window? = null
    private var audio: Audio? = null
    private val files: Files

    @Volatile
    private var running = true
    private val runnables = Seq<Runnable>()
    private val executedRunnables = Seq<Runnable>()
    private val applicationListeners: Seq<ApplicationListener> = Seq<ApplicationListener>()
    private val sync: Sync

    init {
        var config = config
        if (config.glEmulation == GLEmulation.ANGLE_GLES20 || config.glEmulation == GLEmulation.ANGLE_GLES30) {
            // i test all of this and found metal is the best which can be loaded in 5000ms
            when (config.angleBackend) {
                none -> GLFW.glfwInitHint(
                    GLFW.GLFW_ANGLE_PLATFORM_TYPE,
                    GLFW.GLFW_ANGLE_PLATFORM_TYPE_NONE
                )
                vulkan -> GLFW.glfwInitHint(
                    GLFW.GLFW_ANGLE_PLATFORM_TYPE,
                    GLFW.GLFW_ANGLE_PLATFORM_TYPE_VULKAN
                )
                metal -> GLFW.glfwInitHint(
                    GLFW.GLFW_ANGLE_PLATFORM_TYPE,
                    GLFW.GLFW_ANGLE_PLATFORM_TYPE_METAL
                )
                d3d9 -> GLFW.glfwInitHint(
                    GLFW.GLFW_ANGLE_PLATFORM_TYPE,
                    GLFW.GLFW_ANGLE_PLATFORM_TYPE_D3D9
                )
                d3d11 -> GLFW.glfwInitHint(
                    GLFW.GLFW_ANGLE_PLATFORM_TYPE,
                    GLFW.GLFW_ANGLE_PLATFORM_TYPE_D3D11
                )
                opengl -> GLFW.glfwInitHint(
                    GLFW.GLFW_ANGLE_PLATFORM_TYPE,
                    GLFW.GLFW_ANGLE_PLATFORM_TYPE_OPENGL
                )
                opengles -> GLFW.glfwInitHint(
                    GLFW.GLFW_ANGLE_PLATFORM_TYPE,
                    GLFW.GLFW_ANGLE_PLATFORM_TYPE_OPENGLES
                )
            }
            loadANGLE()
        }
        initializeGlfw()

        config = Lwjgl3ApplicationConfiguration.copy(config)
        this.config = config
        if (config.title == null) config.title = listener.javaClass.simpleName

        Core.app = this
        Core.settings = Settings()
        if (!config.disableAudio) {
            try {
                this.audio = Audio(!config.disableAudio);
            } catch (t: Throwable) {
                Log.err("Lwjgl3Application", "Couldn't initialize audio, disabling audio", t)
                this.audio = MockAudio()
            }
        } else {
            this.audio = MockAudio()
        }
        Core.audio = audio
        Core.files = createFiles()
        this.files = Core.files

        this.sync = Sync()

        val window = createWindow(config, listener, 0)
        if (config.glEmulation == GLEmulation.ANGLE_GLES20 || config.glEmulation == GLEmulation.ANGLE_GLES30) postLoadANGLE()
        windows.add(window)
        try {
            loop()
            cleanupWindows()
        } catch (t: Throwable) {
            if (t is RuntimeException) throw t
            else throw ArcRuntimeException(t)
        } finally {
            cleanup()
        }
    }

    protected fun loop() {
        val closedWindows = Seq<Lwjgl3Window>()
        while (running && windows.size > 0) {
            var haveWindowsRendered = false
            closedWindows.clear()
            var targetFramerate = -2
            for (window in windows) {
                if (currentWindow !== window) {
                    window.makeCurrent()
                    currentWindow = window
                }
                if (targetFramerate == -2) targetFramerate = window.config.foregroundFPS
                synchronized(applicationListeners) {
                    haveWindowsRendered = haveWindowsRendered or window.update()
                }
                if (window.shouldClose()) {
                    closedWindows.add(window)
                }
            }
            GLFW.glfwPollEvents()

            defaultUpdate()

            listen { obj: ApplicationListener -> obj.update() }

            val shouldRequestRendering: Boolean
            synchronized(runnables) {
                shouldRequestRendering = runnables.size > 0
                executedRunnables.clear()
                executedRunnables.addAll(runnables)
                runnables.clear()
            }
            for (runnable in executedRunnables) {
                runnable.run()
            }
            if (shouldRequestRendering) {
                // Must follow Runnables execution so changes done by Runnables are reflected
                // in the following render.
                for (window in windows) {
                    if (!Core.graphics.isContinuousRendering()) window.requestRendering()
                }
            }

            for (closedWindow in closedWindows) {
                if (windows.size === 1) {
                    // Lifecycle listener methods have to be called before ApplicationListener methods. The
                    // application will be disposed when _all_ windows have been disposed, which is the case,
                    // when there is only 1 window left, which is in the process of being disposed.
                    for (i in applicationListeners.size - 1 downTo 0) {
                        val l: ApplicationListener = applicationListeners[i]
                        l.pause()
                        l.dispose()
                    }
                    applicationListeners.clear()
                }
                closedWindow.dispose()

                windows.remove(closedWindow, false)
            }

            if (!haveWindowsRendered) {
                // Sleep a few milliseconds in case no rendering was requested
                // with continuous rendering disabled.
                try {
                    Thread.sleep((1000 / config.idleFPS).toLong())
                } catch (e: InterruptedException) {
                    // ignore
                }
            } else if (targetFramerate > 0) {
                sync.sync(targetFramerate) // sleep as needed to meet the target framerate
            }
        }
    }

    private fun listen(cons: Cons<ApplicationListener>) {
        synchronized(listeners) {
            for (l in listeners) {
                cons[l]
            }
        }
    }

    protected fun cleanupWindows() {
        synchronized(applicationListeners) {
            for (applicationListener1 in applicationListeners) {
                applicationListener1.pause()
                applicationListener1.dispose()
            }
        }
        for (window in windows) {
            window.dispose()
        }
        windows.clear()
    }

    protected fun cleanup() {
        Lwjgl3Cursor.disposeSystemCursors()
        audio?.dispose()
        errorCallback!!.free()
        errorCallback = null
        GLFW.glfwTerminate()
    }

    val applicationListener: ApplicationListener
        get() = currentWindow!!.listener

    fun getAudio(): Audio? {
        return audio
    }

    fun getFiles(): Files {
        return files
    }

    override fun getListeners(): Seq<ApplicationListener> {
        return applicationListeners
    }

    override fun getType(): ApplicationType {
        return ApplicationType.desktop
    }

    override fun getClipboardText(): String {
        return GLFW.glfwGetClipboardString((Core.graphics as Lwjgl3Graphics).window.windowHandle)!!
    }

    override fun setClipboardText(text: String?) {
        GLFW.glfwSetClipboardString((Core.graphics as Lwjgl3Graphics).window.windowHandle, text)
    }

    override fun post(runnable: Runnable?) {
        synchronized(runnables) {
            runnables.add(runnable)
        }
    }

    override fun exit() {
        running = false
    }

    fun addLifecycleListener(listener: ApplicationListener?) {
        synchronized(applicationListeners) {
            applicationListeners.add(listener)
        }
    }

    fun removeLifecycleListener(listener: ApplicationListener?) {
        synchronized(applicationListeners) {
            applicationListeners.remove(listener, true)
        }
    }

    fun createInput(window: Lwjgl3Window): Lwjgl3Input {
        return Lwjgl3Input(window)
    }

    protected fun createFiles(): Files {
        return Lwjgl3Files()
    }

    /** Creates a new [Lwjgl3Window] using the provided listener and [Lwjgl3WindowConfiguration].
     *
     * This function only just instantiates a [Lwjgl3Window] and returns immediately. The actual window creation is postponed
     * with [Application.postRunnable] until after all existing windows are updated.  */
    fun newWindow(listener: ApplicationListener, config: Lwjgl3WindowConfiguration): Lwjgl3Window {
        val appConfig = Lwjgl3ApplicationConfiguration.copy(this.config)
        appConfig.setWindowConfiguration(config)
        if (appConfig.title == null) appConfig.title = listener.javaClass.simpleName
        return createWindow(appConfig, listener, windows[0].windowHandle)
    }

    private fun createWindow(
        config: Lwjgl3ApplicationConfiguration, listener: ApplicationListener,
        sharedContext: Long
    ): Lwjgl3Window {
        val window = Lwjgl3Window(listener, applicationListeners, config, this)
        if (sharedContext == 0L) {
            // the main window is created immediately
            createWindow(window, config, sharedContext)
        } else {
            // creation of additional windows is deferred to avoid GL context trouble
            post {
                createWindow(window, config, sharedContext)
                windows.add(window)
            }
        }
        return window
    }

    fun createWindow(window: Lwjgl3Window, config: Lwjgl3ApplicationConfiguration, sharedContext: Long) {
        val windowHandle = createGlfwWindow(config, sharedContext)
        window.create(windowHandle)
        window.setVisible(config.initialVisible)

        for (i in 0..1) {
            Core.gl20.glClearColor(
                config.initialBackgroundColor.r, config.initialBackgroundColor.g,
                config.initialBackgroundColor.b, config.initialBackgroundColor.a
            )
            Core.gl20.glClear(GL11.GL_COLOR_BUFFER_BIT)
            GLFW.glfwSwapBuffers(windowHandle)
        }

        if (currentWindow != null) {
            // the call above to createGlfwWindow switches the OpenGL context to the newly created window,
            // ensure that the invariant "currentWindow is the window with the current active OpenGL context" holds
            currentWindow!!.makeCurrent()
        }
    }

    companion object {
        private var errorCallback: GLFWErrorCallback? = null
        var glVersion: GLVersion? = null
        fun initializeGlfw() {
            if (errorCallback == null) {
                ArcNativesLoader.load()
                errorCallback = GLFWErrorCallback.createPrint(Lwjgl3ApplicationConfiguration.errorStream)
                GLFW.glfwSetErrorCallback(errorCallback)
                if (OS.isMac) GLFW.glfwInitHint(
                    GLFW.GLFW_ANGLE_PLATFORM_TYPE,
                    GLFW.GLFW_ANGLE_PLATFORM_TYPE_METAL
                )
                GLFW.glfwInitHint(GLFW.GLFW_JOYSTICK_HAT_BUTTONS, GLFW.GLFW_FALSE)
                if (!GLFW.glfwInit()) {
                    throw ArcRuntimeException("Unable to initialize GLFW")
                }
            }
        }

        fun loadANGLE() {
            try {
                arc.backend.lwjgl3.angle.ANGLELoader.load()
            } catch (t: ClassNotFoundException) {
                return
            } catch (t: Throwable) {
                throw ArcRuntimeException("Couldn't load ANGLE.", t)
            }
        }

        fun postLoadANGLE() {
            try {
                arc.backend.lwjgl3.angle.ANGLELoader.postGlfwInit()
            } catch (t: ClassNotFoundException) {
                return
            } catch (t: Throwable) {
                throw ArcRuntimeException("Couldn't load ANGLE.", t)
            }
        }

        fun createGlfwWindow(config: Lwjgl3ApplicationConfiguration, sharedContextWindow: Long): Long {
            GLFW.glfwDefaultWindowHints()
            GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE)
            GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, if (config.windowResizable) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE)
            GLFW.glfwWindowHint(GLFW.GLFW_MAXIMIZED, if (config.windowMaximized) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE)
            GLFW.glfwWindowHint(GLFW.GLFW_AUTO_ICONIFY, if (config.autoIconify) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE)

            GLFW.glfwWindowHint(GLFW.GLFW_RED_BITS, config.r)
            GLFW.glfwWindowHint(GLFW.GLFW_GREEN_BITS, config.g)
            GLFW.glfwWindowHint(GLFW.GLFW_BLUE_BITS, config.b)
            GLFW.glfwWindowHint(GLFW.GLFW_ALPHA_BITS, config.a)
            GLFW.glfwWindowHint(GLFW.GLFW_STENCIL_BITS, config.stencil)
            GLFW.glfwWindowHint(GLFW.GLFW_DEPTH_BITS, config.depth)
            GLFW.glfwWindowHint(GLFW.GLFW_SAMPLES, config.samples)

            if (config.glEmulation == GLEmulation.GL30) {
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, config.gles30ContextMajorVersion)
                GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, config.gles30ContextMinorVersion)
                if (OS.isMac) {
                    // hints mandatory on OS X for GL 3.2+ context creation, but fail on Windows if the
                    // WGL_ARB_create_context extension is not available
                    // see: http://www.glfw.org/docs/latest/compat.html
                    GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE)
                    GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE)
                }
            } else {
                if (config.glEmulation == GLEmulation.ANGLE_GLES20 || config.glEmulation == GLEmulation.ANGLE_GLES30) {
                    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_EGL_CONTEXT_API)
                    GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_OPENGL_ES_API)
                    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 2)
                    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 0)
                }
            }

            if (config.transparentFramebuffer) {
                GLFW.glfwWindowHint(GLFW.GLFW_TRANSPARENT_FRAMEBUFFER, GLFW.GLFW_TRUE)
            }

            if (config.debug) {
                GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_DEBUG_CONTEXT, GLFW.GLFW_TRUE)
            }

            var windowHandle: Long = 0

            if (config.fullscreenMode != null) {
                GLFW.glfwWindowHint(GLFW.GLFW_REFRESH_RATE, config.fullscreenMode!!.refreshRate)
                windowHandle = GLFW.glfwCreateWindow(
                    config.fullscreenMode!!.width, config.fullscreenMode!!.height, config.title,
                    config.fullscreenMode!!.monitor, sharedContextWindow
                )

                // On Ubuntu >= 22.04 with Nvidia GPU drivers and X11 display server there's a bug with EGL Context API
                // If the windows creation has failed for this reason try to create it again with the native context
                if (windowHandle == 0L && (config.glEmulation == GLEmulation.ANGLE_GLES20 || config.glEmulation == GLEmulation.ANGLE_GLES30)) {
                    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_NATIVE_CONTEXT_API)
                    windowHandle = GLFW.glfwCreateWindow(
                        config.fullscreenMode!!.width, config.fullscreenMode!!.height, config.title,
                        config.fullscreenMode!!.monitor, sharedContextWindow
                    )
                }
            } else {
                GLFW.glfwWindowHint(
                    GLFW.GLFW_DECORATED,
                    if (config.windowDecorated) GLFW.GLFW_TRUE else GLFW.GLFW_FALSE
                )
                windowHandle =
                    GLFW.glfwCreateWindow(config.windowWidth, config.windowHeight, config.title, 0, sharedContextWindow)

                // On Ubuntu >= 22.04 with Nvidia GPU drivers and X11 display server there's a bug with EGL Context API
                // If the windows creation has failed for this reason try to create it again with the native context
                if (windowHandle == 0L && (config.glEmulation == GLEmulation.ANGLE_GLES20 || config.glEmulation == GLEmulation.ANGLE_GLES30)) {
                    GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_CREATION_API, GLFW.GLFW_NATIVE_CONTEXT_API)
                    windowHandle = GLFW.glfwCreateWindow(
                        config.windowWidth,
                        config.windowHeight,
                        config.title,
                        0,
                        sharedContextWindow
                    )
                }
            }
            if (windowHandle == 0L) {
                throw ArcRuntimeException("Couldn't create window")
            }
/*            Lwjgl3Window.setSizeLimits(
                windowHandle, config.windowMinWidth, config.windowMinHeight, config.windowMaxWidth,
                config.windowMaxHeight
            )*/
            if (config.fullscreenMode == null) {
                if (GLFW.glfwGetPlatform() != GLFW.GLFW_PLATFORM_WAYLAND) {
                    if (config.windowX == -1 && config.windowY == -1) { // i.e., center the window
                        var windowWidth = max(config.windowWidth.toDouble(), config.windowMinWidth.toDouble()).toInt()
                        var windowHeight =
                            max(config.windowHeight.toDouble(), config.windowMinHeight.toDouble()).toInt()
                        if (config.windowMaxWidth > -1) windowWidth =
                            min(windowWidth.toDouble(), config.windowMaxWidth.toDouble()).toInt()
                        if (config.windowMaxHeight > -1) windowHeight =
                            min(windowHeight.toDouble(), config.windowMaxHeight.toDouble()).toInt()

                        var monitorHandle = GLFW.glfwGetPrimaryMonitor()
                        if (config.windowMaximized && config.maximizedMonitor != null) {
                            monitorHandle = config.maximizedMonitor!!.monitorHandle
                        }

                        val newPos: Point2 = Lwjgl3ApplicationConfiguration.calculateCenteredWindowPosition(
                            Lwjgl3ApplicationConfiguration.toLwjgl3Monitor(monitorHandle), windowWidth, windowHeight
                        )
                        GLFW.glfwSetWindowPos(windowHandle, newPos.x, newPos.y)
                    } else {
                        GLFW.glfwSetWindowPos(windowHandle, config.windowX, config.windowY)
                    }
                }

                if (config.windowMaximized) {
                    GLFW.glfwMaximizeWindow(windowHandle)
                }
            }
            if (config.windowIconPaths != null) {
                Lwjgl3Window.setIcon(windowHandle, config.windowIconPaths!!.toArray(), config.windowIconFileType)
            }
            GLFW.glfwMakeContextCurrent(windowHandle)
            GLFW.glfwSwapInterval(if (config.vSyncEnabled) 1 else 0)
            if (config.glEmulation == GLEmulation.ANGLE_GLES20 || config.glEmulation == GLEmulation.ANGLE_GLES30) {
                try {
                    val gles = Class.forName("org.lwjgl.opengles.GLES")
                    gles.getMethod("createCapabilities").invoke(gles)
                } catch (e: Throwable) {
                    throw ArcRuntimeException("Couldn't initialize GLES", e)
                }
            } else {
                GL.createCapabilities()
            }

            initiateGL(config.glEmulation == GLEmulation.ANGLE_GLES20 || config.glEmulation == GLEmulation.ANGLE_GLES30)
            if (!glVersion!!.atLeast(2, 0)) throw ArcRuntimeException(
                ("""
    OpenGL 2.0 or higher with the FBO extension is required. OpenGL version: ${glVersion!!.debugVersionString}
    ${glVersion!!.debugVersionString}
    """.trimIndent())
            )

            if ((config.glEmulation != GLEmulation.ANGLE_GLES20 && config.glEmulation != GLEmulation.ANGLE_GLES30) && !supportsFBO()) {
                throw ArcRuntimeException(
                    ("""
    OpenGL 2.0 or higher with the FBO extension is required. OpenGL version: ${glVersion!!.debugVersionString}, FBO extension: false
    ${glVersion!!.debugVersionString}
    """.trimIndent())
                )
            }

            if (config.debug) {
                check(config.glEmulation != GLEmulation.ANGLE_GLES20 && config.glEmulation != GLEmulation.ANGLE_GLES30) { "ANGLE currently can't be used with with Lwjgl3ApplicationConfiguration#enableGLDebugOutput" }
            }

            return windowHandle
        }

        private fun initiateGL(useGLES20: Boolean) {
            if (!useGLES20) {
                val versionString = GL11.glGetString(GL11.GL_VERSION)
                val vendorString = GL11.glGetString(GL11.GL_VENDOR)
                val rendererString = GL11.glGetString(GL11.GL_RENDERER)
                glVersion = GLVersion(ApplicationType.desktop, versionString, vendorString, rendererString)
            } else {
                try {
                    val gles = Class.forName("org.lwjgl.opengles.GLES20")
                    val getString: Method = gles.getMethod("glGetString", Int::class.javaPrimitiveType)
                    val versionString = getString.invoke(gles, GL11.GL_VERSION) as String
                    val vendorString = getString.invoke(gles, GL11.GL_VENDOR) as String
                    val rendererString = getString.invoke(gles, GL11.GL_RENDERER) as String
                    glVersion = GLVersion(ApplicationType.desktop, versionString, vendorString, rendererString)
                } catch (e: Throwable) {
                    throw ArcRuntimeException("Couldn't get GLES version string.", e)
                }
            }
        }

        private fun supportsFBO(): Boolean {
            // FBO is in core since OpenGL 3.0, see https://www.opengl.org/wiki/Framebuffer_Object
            return glVersion!!.atLeast(3, 0) || GLFW.glfwExtensionSupported("GL_EXT_framebuffer_object")
                    || GLFW.glfwExtensionSupported("GL_ARB_framebuffer_object")
        }
    }
}