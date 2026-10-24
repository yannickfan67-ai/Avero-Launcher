package io.yannickfan.avero.game

import android.view.Surface
import java.io.File

object AndroidLwjglBridge {
    init {
        System.loadLibrary("avero_jvm")
    }

    fun prepare(
        nativeDirectory: File,
        surface: Surface
    ) {
        require(nativeDirectory.isDirectory) {
            "Android LWJGL native directory is missing"
        }
        require(surface.isValid) {
            "Android LWJGL bridge requires a valid Surface"
        }

        val libraries = nativeDirectory
            .listFiles()
            ?.filter { it.isFile && it.name.endsWith(".so") }
            ?.sortedBy { it.name }
            .orEmpty()
        require(libraries.isNotEmpty()) {
            "Android LWJGL native directory is empty"
        }

        val pojavexec = libraries.firstOrNull {
            it.name == "libpojavexec.so"
        } ?: error(
            "Installed Android LWJGL provider does not contain libpojavexec.so"
        )

        libraries.forEach { library ->
            require(!library.canWrite()) {
                "Provider native library must be read-only: ${library.name}"
            }
        }

        nativePrepare(
            providerPath = pojavexec.absolutePath,
            nativeDirectory = nativeDirectory.absolutePath,
            preloadLibraries = libraries.map(File::getAbsolutePath)
                .toTypedArray(),
            surface = surface
        )
    }

    fun release() {
        nativeRelease()
    }

    private external fun nativePrepare(
        providerPath: String,
        nativeDirectory: String,
        preloadLibraries: Array<String>,
        surface: Surface
    )

    private external fun nativeRelease()
}
