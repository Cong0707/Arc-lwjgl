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

import arc.Graphics
import arc.Graphics.Cursor.SystemCursor
import arc.graphics.Pixmap
import arc.struct.Seq
import arc.util.ArcRuntimeException
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWImage
import java.util.*

class Lwjgl3Cursor internal constructor(window: Lwjgl3Window, pixmap: Pixmap, xHotspot: Int, yHotspot: Int) :
    Graphics.Cursor {
    val window: Lwjgl3Window = window
    var pixmapCopy: Pixmap?
    var glfwImage: GLFWImage
    val glfwCursor: Long

    init {
        if (pixmap.glFormat != Pixmap.Format.rgba8888.glFormat) {
            throw ArcRuntimeException("Cursor image pixmap is not in RGBA8888 format.")
        }

        if ((pixmap.getWidth() and (pixmap.getWidth() - 1)) != 0) {
            throw ArcRuntimeException(
                "Cursor image pixmap width of " + pixmap.getWidth() + " is not a power-of-two greater than zero."
            )
        }

        if ((pixmap.getHeight() and (pixmap.getHeight() - 1)) != 0) {
            throw ArcRuntimeException(
                "Cursor image pixmap height of " + pixmap.getHeight() + " is not a power-of-two greater than zero."
            )
        }

        if (xHotspot < 0 || xHotspot >= pixmap.getWidth()) {
            throw ArcRuntimeException(
                "xHotspot coordinate of " + xHotspot + " is not within image width bounds: [0, " + pixmap.getWidth() + ")."
            )
        }

        if (yHotspot < 0 || yHotspot >= pixmap.getHeight()) {
            throw ArcRuntimeException(
                "yHotspot coordinate of " + yHotspot + " is not within image height bounds: [0, " + pixmap.getHeight() + ")."
            )
        }

        this.pixmapCopy = Pixmap(pixmap.getWidth(), pixmap.getHeight())
        pixmapCopy!!.draw(pixmap, 0, 0)

        glfwImage = GLFWImage.malloc()
        glfwImage.width(pixmapCopy!!.getWidth())
        glfwImage.height(pixmapCopy!!.getHeight())
        glfwImage.pixels(pixmapCopy!!.getPixels())
        glfwCursor = GLFW.glfwCreateCursor(glfwImage, xHotspot, yHotspot)
        cursors.add(this)
    }

    override fun dispose() {
        if (pixmapCopy == null) {
            throw ArcRuntimeException("Cursor already disposed")
        }
        cursors.remove(this, true)
        pixmapCopy!!.dispose()
        pixmapCopy = null
        glfwImage.free()
        GLFW.glfwDestroyCursor(glfwCursor)
    }

    companion object {
        val cursors: Seq<Lwjgl3Cursor> = Seq()
        val systemCursors: MutableMap<SystemCursor, Long> = EnumMap(SystemCursor::class.java)

        fun dispose(window: Lwjgl3Window?) {
            for (i in cursors.size - 1 downTo 0) {
                val cursor = cursors[i]
                if (cursor.window.equals(window)) {
                    cursors.remove(i).dispose()
                }
            }
        }

        fun disposeSystemCursors() {
            for (systemCursor in systemCursors.values) {
                GLFW.glfwDestroyCursor(systemCursor)
            }
            systemCursors.clear()
        }

        fun setSystemCursor(windowHandle: Long, systemCursor: SystemCursor) {
            var glfwCursor = systemCursors[systemCursor]
            if (glfwCursor == null) {
                var handle: Long = 0
                handle = if (systemCursor === SystemCursor.arrow) {
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR)
                } else if (systemCursor === SystemCursor.crosshair) {
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_CROSSHAIR_CURSOR)
                } else if (systemCursor === SystemCursor.hand) {
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_HAND_CURSOR)
                } else if (systemCursor === SystemCursor.horizontalResize) {
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_HRESIZE_CURSOR)
                } else if (systemCursor === SystemCursor.verticalResize) {
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_VRESIZE_CURSOR)
                } else if (systemCursor === SystemCursor.ibeam) {
                    GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR)
                } else {
                    throw ArcRuntimeException("Unknown system cursor $systemCursor")
                }

                if (handle == 0L) {
                    return
                }
                glfwCursor = handle
                systemCursors[systemCursor] = glfwCursor
            }
            GLFW.glfwSetCursor(windowHandle, glfwCursor)
        }
    }
}
