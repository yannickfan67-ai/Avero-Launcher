package org.lwjgl.glfw

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object CallbackBridge {
    private const val CLIPBOARD_COPY = 2000
    private const val CLIPBOARD_PASTE = 2001

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    var isGrabbing: Boolean = false
        private set

    @Volatile
    var directInputRequested: Boolean = false
        private set

    @JvmStatic
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    @JvmStatic
    fun accessAndroidClipboard(type: Int, copy: String?): String? {
        val context = applicationContext ?: return ""
        val clipboard = context.getSystemService(
            Context.CLIPBOARD_SERVICE
        ) as? ClipboardManager ?: return ""

        return when (type) {
            CLIPBOARD_COPY -> {
                clipboard.setPrimaryClip(
                    ClipData.newPlainText("Minecraft", copy.orEmpty())
                )
                null
            }

            CLIPBOARD_PASTE ->
                clipboard.primaryClip
                    ?.getItemAt(0)
                    ?.coerceToText(context)
                    ?.toString()
                    .orEmpty()

            else -> ""
        }
    }

    @JvmStatic
    fun onGrabStateChanged(grabbing: Boolean) {
        isGrabbing = grabbing
    }

    @JvmStatic
    fun onDirectInputEnable() {
        directInputRequested = true
    }

    @JvmStatic
    fun getAndroidDPI(): Float =
        applicationContext
            ?.resources
            ?.displayMetrics
            ?.density
            ?.coerceAtLeast(1f)
            ?: 1f

    @JvmStatic
    fun notifyLauncher(
        type: Int,
        action: IntArray
    ): Boolean {
        // SDL/controller integrations will be added only when their
        // corresponding provider is installed. Unknown notifications are
        // deliberately not reported as handled.
        return false
    }
}
