package com.openless.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.LinkedHashMap
import java.util.concurrent.Executors

/** Independent confirmed-text -> phrase predictor; it never reads stroke codes. */
internal class StrokePhraseRepository(context: Context) {
    data class Candidate(val text: String, val baseWeight: Int, val matchedPrefix: String)

    private class Node {
        val children = HashMap<Char, Node>()
        val top = ArrayList<Candidate>(NODE_TOP_N)
    }

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "openless-phrase-query").apply { isDaemon = true }
    }
    private val root = Node()
    private val cache = object : LinkedHashMap<String, List<Candidate>>(CACHE_SIZE, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Candidate>>?) = size > CACHE_SIZE
    }
    private var loaded = false

    /**
     * Warm the trie while the IME is idle, same reasoning as
     * StrokeInputRepository.preloadAsync() — this dictionary is ~220k
     * phrases (vs. stroke.dict.tsv's much smaller character list), and
     * every insert() does a linear dedupe-check + a re-sort of up to
     * NODE_TOP_N candidates at every one of a phrase's up to 8 node
     * levels, so building it cold has a real, measurable cost (see the
     * "phrase index ready" log below). Left uncalled until now, so this
     * cost used to land on whichever word's commit happened to be the
     * first one to ever need an association in this repository's
     * lifetime, instead of on IME startup where the user isn't yet
     * waiting on a result.
     */
    fun preloadAsync() {
        executor.execute { ensureLoaded() }
    }

    fun searchAsync(prefix: String, callback: (List<Candidate>) -> Unit) {
        if (prefix.isEmpty()) return callback(emptyList())
        executor.execute {
            val startedAt = android.os.SystemClock.elapsedRealtime()
            ensureLoaded()
            val result = (synchronized(cache) { cache[prefix] } ?: findLongestSuffix(prefix).also {
                synchronized(cache) { cache[prefix] = it }
            }).sortedWith(compareByDescending<Candidate> {
                it.baseWeight + userFrequency.score(it.matchedPrefix.ifEmpty { prefix }, it.text).toInt()
            })
            // Temporary-diagnostic-grade, not removed after: cheap (one
            // log line per commit) and directly answers "is this slow
            // because of the cold trie build, the query itself, or is it
            // not finding anything at all" without guessing — see
            // openless-all/app/android README for how to read it via
            // logcat while reproducing a report of slow/missing
            // associations.
            android.util.Log.d(
                "OpenLessPhrase",
                "query prefix=\"$prefix\" results=${result.size} elapsed=${android.os.SystemClock.elapsedRealtime() - startedAt}ms",
            )
            Handler(Looper.getMainLooper()).post { callback(result) }
        }
    }

    /** Records that `candidate` was committed after `context`, off the caller's thread. */
    fun recordUsage(context: String, candidate: String) {
        executor.execute { userFrequency.record(context, candidate) }
    }

    fun shutdown() = executor.shutdownNow()

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val startedAt = android.os.SystemClock.elapsedRealtime()
            var entryCount = 0
            runCatching {
                appContext.assets.open("phrases.dict.tsv").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        val parts = line.split('\t', limit = 2)
                        val phrase = parts.getOrNull(0) ?: return@forEach
                        val weight = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        if (phrase.length in 2..8 && weight > 0) {
                            insert(Candidate(phrase, weight, ""))
                            entryCount++
                        }
                    }
                }
            }
            loaded = true
            android.util.Log.i(
                "OpenLessPhrase",
                "phrase index ready entries=$entryCount elapsed=${android.os.SystemClock.elapsedRealtime() - startedAt}ms",
            )
        }
    }

    private fun insert(candidate: Candidate) {
        var node = root
        candidate.text.forEach { character ->
            node = node.children.getOrPut(character) { Node() }
            if (node.top.none { it.text == candidate.text }) {
                node.top += candidate
                node.top.sortByDescending { it.baseWeight }
                if (node.top.size > NODE_TOP_N) node.top.removeAt(node.top.lastIndex)
            }
        }
    }

    private fun find(prefix: String): List<Candidate> {
        var node = root
        prefix.forEach { character -> node = node.children[character] ?: return emptyList() }
        return node.top.map { it.copy(matchedPrefix = prefix) }
    }

    private fun findLongestSuffix(prefix: String): List<Candidate> {
        for (length in prefix.length downTo 1) {
            val result = find(prefix.takeLast(length))
            if (result.isNotEmpty()) return result
        }
        return emptyList()
    }

    private companion object {
        const val NODE_TOP_N = 12
        const val CACHE_SIZE = 64
    }

    private val userFrequency = StrokeUserFrequency(context)
}
