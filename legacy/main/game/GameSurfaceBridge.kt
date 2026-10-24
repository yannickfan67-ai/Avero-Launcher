package io.yannickfan.avero.game

import android.view.Surface

object GameSurfaceBridge {
    init {
        System.loadLibrary("avero_jvm")
    }

    fun attach(surface: Surface) {
        require(surface.isValid) { "Cannot attach an invalid game surface" }
        nativeAttachSurface(surface)
    }

    fun detach() {
        nativeDetachSurface()
    }

    fun size(): Pair<Int, Int> {
        val values = nativeSurfaceSize()
        return values.getOrElse(0) { 0 } to values.getOrElse(1) { 0 }
    }

    private external fun nativeAttachSurface(surface: Surface)
    private external fun nativeDetachSurface()
    private external fun nativeSurfaceSize(): IntArray
}
