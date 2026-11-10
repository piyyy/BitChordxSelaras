package com.music.bitchord.data.lyrics

/*
 * iOS stand-ins for the three lyric sources whose JVM versions lean on jsoup, the W3C DOM and
 * java.io (see the excludes in shared/build.gradle.kts). Same API, so LyricsRepository and the
 * providers compile unchanged; the other sources (LRCLIB, KuGou, YouTube, Musixmatch...) still run.
 */

object Genius {
    suspend fun lyrics(title: String, artist: String): List<LyricLine>? = null
    fun parseHtml(html: String): List<LyricLine>? = null
    fun stripArtifacts(raw: String): String = raw.replace(' ', ' ').replace('​', ' ').replace('﻿', ' ').trim()
    fun textToLyricLines(text: String): List<LyricLine> =
        text.lines().map { it.trim() }.filter { it.isNotEmpty() }.map { LyricLine(timeMs = 0L, text = it) }
    fun isSectionHeader(text: String): Boolean = isGeniusSectionHeader(text)
}

object TtmlLyrics {
    fun parse(ttml: String): List<LyricLine> = emptyList()

    fun time(value: String?): Long? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (raw.endsWith("ms")) return raw.dropLast(2).toDoubleOrNull()?.toLong()
        val parts = raw.removeSuffix("s").split(':')
        val seconds = when (parts.size) {
            1 -> parts[0].toDoubleOrNull()
            2 -> parts[0].toDoubleOrNull()?.let { m -> parts[1].toDoubleOrNull()?.let { m * 60 + it } }
            3 -> parts[0].toDoubleOrNull()?.let { h -> parts[1].toDoubleOrNull()?.let { m -> parts[2].toDoubleOrNull()?.let { h * 3600 + m * 60 + it } } }
            else -> null
        } ?: return null
        return (seconds * 1000).toLong()
    }
}

object LyricsTranslation {
    sealed interface Result {
        data class Translated(val lines: List<LyricLine>, val sourceLanguage: String, val fromCache: Boolean) : Result
        data class SameLanguage(val language: String) : Result
        data object Unavailable : Result
    }

    sealed interface RomanizationResult {
        data class Romanized(val lines: List<LyricLine>, val sourceLanguage: String, val fromCache: Boolean) : RomanizationResult
        data object AlreadyRomanized : RomanizationResult
        data object Unavailable : RomanizationResult
    }

    var cacheDir: Any? = null

    suspend fun translate(trackId: String, lines: List<LyricLine>, targetLanguageTag: String): Result = Result.Unavailable
    suspend fun romanize(trackId: String, lines: List<LyricLine>, targetLanguageTag: String): RomanizationResult = RomanizationResult.Unavailable
}
