package com.github.vhrabar.issuehub.toolWindow

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.io.HttpRequests
import com.intellij.util.ui.JBUI
import java.awt.Image
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import javax.swing.Icon

/**
 * Resolves GitHub avatar URLs to circular icons, loading them off the EDT and caching by URL.
 *
 * Rendering asks for an avatar synchronously; until the image is downloaded the caller's fallback
 * (initials) is shown, and [onLoaded] is invoked once the real image is ready so the list repaints.
 */
internal class AvatarLoader(
    private val onLoaded: () -> Unit,
) {
    private val cache = ConcurrentHashMap<String, Image>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /** The cached avatar for [url] at [size], or [fallback] while it loads (or if [url] is null). */
    fun avatar(
        url: String?,
        fallback: Icon,
        size: Int = CircularAvatarIcon.SIZE,
    ): Icon {
        if (url.isNullOrBlank()) return fallback
        cache[url]?.let { return CircularAvatarIcon(it, size) }
        scheduleLoad(url)
        return fallback
    }

    private fun scheduleLoad(url: String) {
        if (!inFlight.add(url)) return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                runCatching { download(url) }.getOrNull()?.let { image ->
                    cache[url] = image
                    ApplicationManager.getApplication().invokeLater(onLoaded)
                }
            } finally {
                inFlight.remove(url)
            }
        }
    }

    /** Fetches the avatar at device resolution for the largest size anything draws it at. */
    private fun download(url: String): Image? {
        val px = JBUI.scale(CircularAvatarIcon.MAX_SIZE) * 2
        val sized = if ('?' in url) "$url&s=$px" else "$url?s=$px"
        val bytes = HttpRequests.request(sized).readBytes(null)
        return ImageIO.read(ByteArrayInputStream(bytes))
    }
}
