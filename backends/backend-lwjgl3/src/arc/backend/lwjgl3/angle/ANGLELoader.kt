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
package arc.backend.lwjgl3.angle

import arc.util.ArcRuntimeException
import arc.util.Log
import java.io.*
import java.util.*
import java.util.zip.CRC32

object ANGLELoader {
    var isWindows: Boolean = System.getProperty("os.name").contains("Windows")
    var isLinux: Boolean = System.getProperty("os.name").contains("Linux")
            || System.getProperty("os.name").contains("FreeBSD")
    var isMac: Boolean = System.getProperty("os.name").contains("Mac")
    var isARM: Boolean = System.getProperty("os.arch").startsWith("arm")
            || System.getProperty("os.arch").startsWith("aarch64")
    var is64Bit: Boolean = System.getProperty("os.arch").contains("64")
            || System.getProperty("os.arch").startsWith("armv8")

    private val random = Random()
    private var egl: File? = null
    private var gles: File? = null
    private var lastWorkingDir: File? = null

    fun closeQuietly(c: Closeable?) {
        if (c != null) {
            try {
                c.close()
            } catch (ignored: Throwable) {
            }
        }
    }

    fun randomUUID(): String {
        return UUID(random.nextLong(), random.nextLong()).toString()
    }

    fun crc(input: InputStream): String {
        requireNotNull(input) { "input cannot be null." }
        val crc = CRC32()
        val buffer = ByteArray(4096)
        try {
            while (true) {
                val length = input.read(buffer)
                if (length == -1) break
                crc.update(buffer, 0, length)
            }
        } catch (ex: Exception) {
        } finally {
            closeQuietly(input)
        }
        return crc.value.toString(16)
    }

    private fun extractFile(sourcePath: String, outFile: File): File {
        try {
            if (!outFile.parentFile.exists() && !outFile.parentFile.mkdirs()) throw ArcRuntimeException(
                "Couldn't create ANGLE native library output directory " + outFile.parentFile.absolutePath
            )
            var out: OutputStream? = null
            var `in`: InputStream? = null

            if (outFile.exists()) {
                return outFile
            }

            try {
                out = FileOutputStream(outFile)
                `in` = ANGLELoader::class.java.getResourceAsStream("/$sourcePath")
                val buffer = ByteArray(4096)
                while (true) {
                    val length = `in`.read(buffer)
                    if (length == -1) break
                    out.write(buffer, 0, length)
                }
                return outFile
            } finally {
                closeQuietly(out)
                closeQuietly(`in`)
            }
        } catch (t: Throwable) {
            throw ArcRuntimeException("Couldn't load ANGLE shared library $sourcePath", t)
        }
    }

    /** Returns a path to a file that can be written. Tries multiple locations and verifies writing succeeds.
     * @return null if a writable path could not be found.
     */
    private fun getExtractedFile(dirName: String, fileName: String): File? {
        // Temp directory with username in path.
        val idealFile = File(
            System.getProperty("java.io.tmpdir") + "/libgdx" + System.getProperty("user.name") + "/" + dirName, fileName
        )
        if (canWrite(idealFile)) return idealFile

        // System provided temp directory.
        try {
            var file = File.createTempFile(dirName, null)
            if (file.delete()) {
                file = File(file, fileName)
                if (canWrite(file)) return file
            }
        } catch (ignored: IOException) {
        }

        // User home.
        var file = File(System.getProperty("user.home") + "/.libgdx/" + dirName, fileName)
        if (canWrite(file)) return file

        // Relative directory.
        file = File(".temp/$dirName", fileName)
        if (canWrite(file)) return file

        // We are running in the OS X sandbox.
        if (System.getenv("APP_SANDBOX_CONTAINER_ID") != null) return idealFile

        return null
    }

    /** Returns true if the parent directories of the file can be created and the file can be written.  */
    private fun canWrite(file: File): Boolean {
        val parent = file.parentFile
        val testFile: File
        if (file.exists()) {
            if (!file.canWrite() || !canExecute(file)) return false
            // Don't overwrite existing file just to check if we can write to directory.
            testFile = File(parent, randomUUID().toString())
        } else {
            parent.mkdirs()
            if (!parent.isDirectory) return false
            testFile = file
        }
        try {
            FileOutputStream(testFile).close()
            if (!canExecute(testFile)) return false
            return true
        } catch (ex: Throwable) {
            return false
        } finally {
            testFile.delete()
        }
    }

    private fun canExecute(file: File): Boolean {
        try {
            val canExecute = File::class.java.getMethod("canExecute")
            if (canExecute.invoke(file) as Boolean) return true

            val setExecutable =
                File::class.java.getMethod(
                    "setExecutable",
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
            setExecutable.invoke(file, true, false)

            return canExecute.invoke(file) as Boolean
        } catch (ignored: Exception) {
        }
        return false
    }

    fun load() {
        if ((isARM && !isMac) || (!isWindows && !isLinux && !isMac)) throw ArcRuntimeException("ANGLE is only supported on x86/x86_64 Windows, x64 Linux, and x64/arm64 macOS.")
        var osDir: String? = null
        var ext: String? = null
        if (isWindows) {
            osDir = if (is64Bit) "windows64" else "windows32"
            ext = ".dll"
        }
        if (isLinux) {
            osDir = "linux64"
            ext = ".so"
        }
        if (isMac) {
            osDir = if (isARM) "macosxarm64" else "macosx64"
            ext = ".dylib"
        }

        val eglSource = "$osDir/libEGL$ext"
        val glesSource = "$osDir/libGLESv2$ext"
        Log.info("Load angle from @, @", eglSource, glesSource)
        val crc = (crc(ANGLELoader::class.java.getResourceAsStream("/$eglSource"))
                + crc(ANGLELoader::class.java.getResourceAsStream("/$glesSource")))
        egl = getExtractedFile(crc, File(eglSource).name)
        gles = getExtractedFile(crc, File(glesSource).name)

        if (!isMac) {
            extractFile(eglSource, egl!!)
            System.load(egl!!.absolutePath)
            extractFile(glesSource, gles!!)
            System.load(gles!!.absolutePath)
        } else {
            // On macOS, we can't preload the shared libraries. calling dlopen("path1/lib.dylib")
            // then calling dlopen("lib.dylib") will not return the dylib loaded in the first dlopen()
            // call, but instead perform the dlopen library search algorithm anew. Since the dylibs
            // we extract are not in any paths dlopen knows about, GLFW fails to load them.
            // Instead, we need to copy the shared libraries to the current working directory (which
            // we can't temporarily change in pure Java either...). The dylibs will get deleted
            // in postGlfwInit() once the first window has been created, and GLFW has loaded the dylibs.
            lastWorkingDir = File(".")
            extractFile(eglSource, File(lastWorkingDir, egl!!.name))
            extractFile(glesSource, File(lastWorkingDir, gles!!.name))
        }
    }

    fun postGlfwInit() {
        File(lastWorkingDir, egl!!.name).delete()
        File(lastWorkingDir, gles!!.name).delete()
    }
}