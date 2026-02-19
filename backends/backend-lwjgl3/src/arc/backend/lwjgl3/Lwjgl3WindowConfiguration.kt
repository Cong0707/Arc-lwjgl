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

import arc.Files
import arc.graphics.Color
import arc.Files.FileType
import arc.Graphics
import arc.struct.Seq
import java.awt.DisplayMode

open class Lwjgl3WindowConfiguration {
    var windowX: Int = -1
    var windowY: Int = -1
    var windowWidth: Int = 640
    var windowHeight: Int = 480
    var windowMinWidth: Int = -1
    var windowMinHeight: Int = -1
    var windowMaxWidth: Int = -1
    var windowMaxHeight: Int = -1
    var windowResizable: Boolean = true
    var windowDecorated: Boolean = true
    var windowMaximized: Boolean = false
    var maximizedMonitor: Lwjgl3Graphics.Lwjgl3Monitor? = null
    var autoIconify: Boolean = true
    var windowIconFileType: Files.FileType? = null
    var windowIconPaths: Seq<String?>? = null
    var windowListener: Lwjgl3WindowListener? = null
    var fullscreenMode: Lwjgl3Graphics.Lwjgl3DisplayMode? = null
    var title: String? = null
    var initialBackgroundColor: Color = Color.black
    var initialVisible: Boolean = true
    var vSyncEnabled: Boolean = true

    fun setWindowConfiguration(config: Lwjgl3WindowConfiguration) {
        windowX = config.windowX
        windowY = config.windowY
        windowWidth = config.windowWidth
        windowHeight = config.windowHeight
        windowMinWidth = config.windowMinWidth
        windowMinHeight = config.windowMinHeight
        windowMaxWidth = config.windowMaxWidth
        windowMaxHeight = config.windowMaxHeight
        windowResizable = config.windowResizable
        windowDecorated = config.windowDecorated
        windowMaximized = config.windowMaximized
        maximizedMonitor = config.maximizedMonitor
        autoIconify = config.autoIconify
        windowIconFileType = config.windowIconFileType
        if (config.windowIconPaths != null) windowIconPaths =
            config.windowIconPaths!!.copy()
        windowListener = config.windowListener
        fullscreenMode = config.fullscreenMode
        title = config.title
        initialBackgroundColor = config.initialBackgroundColor
        initialVisible = config.initialVisible
        vSyncEnabled = config.vSyncEnabled
    }

    /** Sets the app to use windowed mode.
     *
     * @param width the width of the window (default 640)
     * @param height the height of the window (default 480)
     */
    fun setWindowedMode(width: Int, height: Int) {
        this.windowWidth = width
        this.windowHeight = height
    }

    /** @param resizable whether the windowed mode window is resizable (default true)
     */
    fun setResizable(resizable: Boolean) {
        this.windowResizable = resizable
    }

    /** @param decorated whether the windowed mode window is decorated, i.e. displaying the title bars (default true)
     */
    fun setDecorated(decorated: Boolean) {
        this.windowDecorated = decorated
    }

    /** @param maximized whether the window starts maximized. Ignored if the window is full screen. (default false)
     */
    fun setMaximized(maximized: Boolean) {
        this.windowMaximized = maximized
    }

    /** Sets the position of the window in windowed mode. Default -1 for both coordinates for centered on primary monitor.  */
    fun setWindowPosition(x: Int, y: Int) {
        windowX = x
        windowY = y
    }

    /** Sets minimum and maximum size limits for the window. If the window is full screen or not resizable, these limits are
     * ignored. The default for all four parameters is -1, which means unrestricted.  */
    fun setWindowSizeLimits(minWidth: Int, minHeight: Int, maxWidth: Int, maxHeight: Int) {
        windowMinWidth = minWidth
        windowMinHeight = minHeight
        windowMaxWidth = maxWidth
        windowMaxHeight = maxHeight
    }

    /** Sets the icon that will be used in the window's title bar. Has no effect in macOS, which doesn't use window icons.
     * @param filePaths One or more [internal][FileType.Internal] image paths. Must be JPEG, PNG, or BMP format. The one
     * closest to the system's desired size will be scaled. Good sizes include 16x16, 32x32 and 48x48.
     */
    fun setWindowIcon(vararg filePaths: String?) {
        setWindowIcon(Files.FileType.internal, *filePaths)
    }

    /** Sets the icon that will be used in the window's title bar. Has no effect in macOS, which doesn't use window icons.
     * @param fileType The type of file handle the paths are relative to.
     * @param filePaths One or more image paths, relative to the given [FileType]. Must be JPEG, PNG, or BMP format. The
     * one closest to the system's desired size will be scaled. Good sizes include 16x16, 32x32 and 48x48.
     */
    fun setWindowIcon(fileType: Files.FileType?, vararg filePaths: String?) {
        windowIconFileType = fileType
        windowIconPaths = Seq<String?>(filePaths)
    }

    /** Sets the app to use fullscreen mode. Use the static methods like [Lwjgl3ApplicationConfiguration.getDisplayMode] on
     * this class to enumerate connected monitors and their fullscreen display modes.  */
    fun setFullscreenMode(mode: DisplayMode?) {
        this.fullscreenMode = mode as Lwjgl3Graphics.Lwjgl3DisplayMode?
    }

    /** Sets whether to use vsync. This setting can be changed anytime at runtime via [Graphics.setVSync].
     *
     * For multi-window applications, only one (the main) window should enable vsync. Otherwise, every window will wait for the
     * vertical blank on swap individually, effectively cutting the frame rate to (refreshRate / numberOfWindows).  */
    fun useVsync(vsync: Boolean) {
        this.vSyncEnabled = vsync
    }
}