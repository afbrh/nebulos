package com.noryan.nebulos.api

/** One way to turn a ROM path or file name into text the per-game title rules can match: file name only, no extension, brackets removed, `_` as a space. */
object TitleMatch {
    private val BRACKETS = Regex("\\([^)]*\\)|\\[[^\\]]*]")
    private val SPACES = Regex("\\s+")

    private val EXTENSION = Regex("[A-Za-z0-9]{1,5}")

    @JvmStatic
    fun clean(raw: String): String {
        val name = raw.substringAfterLast('/')
        // Only cut something that looks like a file extension: "Super Mario Bros. Wonder" (a title, no extension) keeps its ". Wonder".
        val ext = name.substringAfterLast('.', "")
        val noExt = if (ext.isNotEmpty() && EXTENSION.matches(ext)) name.substringBeforeLast('.') else name
        return noExt.replace(BRACKETS, " ").replace('_', ' ').replace(SPACES, " ").trim()
    }
}
