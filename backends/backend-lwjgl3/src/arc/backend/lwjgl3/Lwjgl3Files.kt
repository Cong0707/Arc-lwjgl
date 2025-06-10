package arc.backend.lwjgl3

import arc.Files
import arc.Files.FileType
import arc.files.Fi
import arc.util.ArcRuntimeException
import arc.util.OS
import java.io.File

class Lwjgl3Files: Files {
    override fun get(fileName: String?, type: FileType?): Fi {
        return Lwjgl3Fi(fileName, type)
    }

    override fun getExternalStoragePath(): String {
        return externalPath
    }

    override fun isExternalStorageAvailable(): Boolean {
        return true
    }

    override fun getLocalStoragePath(): String {
        return localStoragePath
    }

    override fun isLocalStorageAvailable(): Boolean {
        return true
    }

    companion object {
        val externalPath: String = OS.userHome + File.separator
        val localPath: String = File("").absolutePath + File.separator
    }

    internal class Lwjgl3Fi : Fi {
        constructor(fileName: String?, type: FileType?) : super(fileName, type)

        constructor(file: File?, type: FileType?) : super(file, type)

        override fun child(name: String?): Fi {
            if (file.path.length === 0) return Lwjgl3Fi(File(name), type)
            return Lwjgl3Fi(File(file, name), type)
        }

        override fun sibling(name: String?): Fi {
            if (file.path.length === 0) throw ArcRuntimeException("Cannot get the sibling of the root.")
            return Lwjgl3Fi(File(file.parent, name), type)
        }

        override fun parent(): Fi {
            var parent = file.parentFile
            if (parent == null) {
                parent = if (type === FileType.absolute) File("/")
                else File("")
            }
            return Lwjgl3Fi(parent, type)
        }

        override fun file(): File {
            if (type === FileType.external) return File(externalPath, file.path)
            if (type === FileType.local) return File(localPath, file.path)
            return file
        }
    }
}