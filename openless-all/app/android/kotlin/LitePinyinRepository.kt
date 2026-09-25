package com.openless.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Single-character full-pinyin lookup for Pinyin mode (phase 3 of the
 * lite-pinyin plan — docs/pinyin-lite/phase-0-audit.md). Same background-
 * load-off-a-dedicated-thread shape as EnglishCandidateProvider/
 * StrokePhraseRepository, but a plain exact-match Map instead of a trie —
 * ~5000 characters is small enough that a prefix index buys nothing (see
 * the plan's own 8.1: "当前规模下无需 SQLite，也无需大型 Trie").
 *
 * Reads android/assets/pinyin_chars.tsv (character<TAB>pinyin<TAB>weight,
 * one row per (character, reading) pair — see
 * scripts/generate-pinyin-characters.mjs and its own .LICENSE.txt for
 * where that file comes from). Phrase lookup (pinyin_phrases.tsv) is a
 * later phase, not this class's job.
 */
internal class LitePinyinRepository(context: Context) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "openless-pinyin-candidate").apply { isDaemon = true }
    }

    private data class Entry(val char: String, val weight: Int)

    // pinyin -> its characters, sorted by weight descending once at load
    // time (mirrors EnglishCandidateProvider's Node.topWords: sort once on
    // load, not per query).
    private val index = HashMap<String, List<Entry>>()

    @Volatile
    private var loaded = false

    /** Off the caller's thread; posts [callback] back to the main looper. Exact match only — no prefix/fuzzy matching in this phase. */
    fun queryExact(pinyin: String, limit: Int = 20, callback: (List<String>) -> Unit) {
        val normalized = pinyin.trim().lowercase()
        if (normalized.isEmpty()) {
            callback(emptyList())
            return
        }
        executor.execute {
            ensureLoaded()
            val results = index[normalized].orEmpty().take(limit).map { it.char }
            Handler(Looper.getMainLooper()).post { callback(results) }
        }
    }

    /** Warms the index off the caller's thread without waiting for a query — mirrors StrokePhraseRepository.preloadAsync(); call once, e.g. when the Pinyin panel first becomes reachable. */
    fun preloadAsync() {
        executor.execute { ensureLoaded() }
    }

    fun shutdown() = executor.shutdownNow()

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val grouped = HashMap<String, MutableList<Entry>>()
            runCatching {
                appContext.assets.open("pinyin_chars.tsv").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.split('\t')
                        if (parts.size < 3) return@forEach
                        val char = parts[0]
                        val pinyin = parts[1]
                        val weight = parts[2].toIntOrNull() ?: 0
                        if (char.isEmpty() || pinyin.isEmpty()) return@forEach
                        grouped.getOrPut(pinyin) { mutableListOf() }.add(Entry(char, weight))
                    }
                }
            }
            grouped.forEach { (pinyin, entries) -> index[pinyin] = entries.sortedByDescending { it.weight } }
            loaded = true
        }
    }
}
