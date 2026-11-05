package com.noryan.nebulos.api.ui

import java.io.File

/**
 * Remembers which games have been started to the point of showing a picture, so the loading screen can say that a game's FIRST launch
 * takes longer (shader builds, ROM conversion, first-time setup). One key per line in a small file in the app's private files dir;
 * appended to only, so several core processes can use it at once.
 */
object FirstLaunch {
    private const val FILE = "first_launch_seen.txt"

    fun file(filesDir: File) = File(filesDir, FILE)

    fun isFirst(filesDir: File, key: String): Boolean =
        key.isNotBlank() && runCatching { file(filesDir).takeIf { it.isFile }?.useLines { ls -> ls.none { it == key } } ?: true }.getOrDefault(false)

    fun markSeen(filesDir: File, key: String) {
        if (key.isBlank() || !isFirst(filesDir, key)) return
        runCatching { file(filesDir).appendText(key.replace('\n', ' ') + "\n") }
    }
}
