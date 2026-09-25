package com.openless.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Full-pinyin single-character AND high-frequency-abbreviation lookup for
 * Pinyin mode (phases 3+4 of the lite-pinyin plan —
 * docs/pinyin-lite/phase-0-audit.md). Same background-load-off-a-dedicated-
 * thread shape as EnglishCandidateProvider/StrokePhraseRepository, but plain
 * exact-match Maps instead of a trie — ~5000 characters and ~2000 phrases is
 * small enough that a prefix index buys nothing (plan 8.1: "当前规模下无需
 * SQLite，也无需大型 Trie").
 *
 * Reads two android/assets files, each one row per entry:
 *   pinyin_chars.tsv:   character<TAB>pinyin<TAB>weight
 *   pinyin_phrases.tsv: phrase<TAB>full_pinyin<TAB>abbreviation<TAB>weight
 * (see scripts/generate-pinyin-characters.mjs / generate-pinyin-phrases.mjs
 * and their .LICENSE.txt files for where these come from).
 */
internal class LitePinyinRepository(context: Context) {
    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "openless-pinyin-candidate").apply { isDaemon = true }
    }

    private data class Entry(val text: String, val weight: Int)

    // pinyin -> its characters; abbreviation -> its phrases. Each list is
    // sorted by weight descending once at load time (mirrors
    // EnglishCandidateProvider's Node.topWords: sort once on load, not per
    // query).
    private val charIndex = HashMap<String, List<Entry>>()
    private val abbreviationIndex = HashMap<String, List<Entry>>()

    @Volatile
    private var loaded = false

    /**
     * Off the caller's thread; posts [callback] back to the main looper.
     * Exact match only — no prefix/fuzzy matching in this phase. Checks
     * both indexes per plan 3.5 ("单字全拼索引 + 词语简拼索引"): single-
     * character full-pinyin matches are listed before abbreviation-phrase
     * matches (plan's own priority tiers 2 and 3 — tier 1, "用户在当前编码
     * 下选择过的候选", is phase 5's user-frequency work, not this phase's).
     * The abbreviation half only activates at length >= 2 (plan 3.4: "简拼
     * 至少输入 2 个字母后才触发查询").
     */
    fun query(encoding: String, limit: Int = 20, callback: (List<String>) -> Unit) {
        val normalized = encoding.trim().lowercase()
        if (normalized.isEmpty()) {
            callback(emptyList())
            return
        }
        executor.execute {
            ensureLoaded()
            val charMatches = charIndex[normalized].orEmpty()
            val phraseMatches = if (normalized.length >= 2) abbreviationIndex[normalized].orEmpty() else emptyList()
            val merged = (charMatches.asSequence() + phraseMatches.asSequence()).map { it.text }.take(limit).toList()
            Handler(Looper.getMainLooper()).post { callback(merged) }
        }
    }

    /** Warms both indexes off the caller's thread without waiting for a query — mirrors StrokePhraseRepository.preloadAsync(); call once, e.g. when the Pinyin panel first becomes reachable. */
    fun preloadAsync() {
        executor.execute { ensureLoaded() }
    }

    fun shutdown() = executor.shutdownNow()

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            loadChars()
            loadPhrases()
            loaded = true
        }
    }

    private fun loadChars() {
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
        grouped.forEach { (pinyin, entries) -> charIndex[pinyin] = entries.sortedByDescending { it.weight } }
    }

    private fun loadPhrases() {
        val grouped = HashMap<String, MutableList<Entry>>()
        runCatching {
            appContext.assets.open("pinyin_phrases.tsv").bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val parts = line.split('\t')
                    if (parts.size < 4) return@forEach
                    val phrase = parts[0]
                    val abbreviation = parts[2]
                    val weight = parts[3].toIntOrNull() ?: 0
                    if (phrase.isEmpty() || abbreviation.isEmpty()) return@forEach
                    grouped.getOrPut(abbreviation) { mutableListOf() }.add(Entry(phrase, weight))
                }
            }
        }
        grouped.forEach { (abbreviation, entries) -> abbreviationIndex[abbreviation] = entries.sortedByDescending { it.weight } }
    }
}
