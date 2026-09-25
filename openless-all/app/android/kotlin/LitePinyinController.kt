package com.openless.app

import android.content.Context

/**
 * Owns the Pinyin-mode encoding buffer AND its candidate query pipeline on
 * the English keyboard panel (see OpenLessImeService.LatinInputMode) — phase
 * 3 of the lite-pinyin plan (docs/pinyin-lite/phase-0-audit.md): single-
 * character full-pinyin lookup only, no phrase/simple-pinyin matching yet
 * (that's phase 4).
 *
 * Deliberately does not touch currentInputConnection or any View — callers
 * get results through the [onCandidates] callback and decide what to do
 * with them (render a row, commit a character, etc.) — see the lite-pinyin
 * plan's 5.2 ("不让 View 直接查询词库或持有 Repository").
 */
internal class LitePinyinController(context: Context) {
    private val repository = LitePinyinRepository(context)
    private val encoding = StringBuilder()

    // Independent from englishCandidateQueryEpoch/strokeQueryEpoch/
    // phraseQueryEpoch by design (plan 8.3: "不得复用笔画 epoch 变量，避免
    // 不同输入模式互相影响") — bumped on every state change so a slow
    // background query for an encoding the user has since edited or
    // cleared can never overwrite what's on screen now.
    private var queryEpoch = 0L

    fun currentEncoding(): String = encoding.toString()

    fun isEmpty(): Boolean = encoding.isEmpty()

    fun preloadAsync() = repository.preloadAsync()

    /** Letters only — OpenLessImeService is responsible for routing non-letter keys elsewhere. */
    fun appendLetter(char: Char, onCandidates: (List<String>) -> Unit) {
        if (!char.isLetter()) return
        encoding.append(char.lowercaseChar())
        query(onCandidates)
    }

    /** @return true if a character was actually removed (false when the encoding was already empty, meaning the caller should fall back to normal backspace). */
    fun backspace(onCandidates: (List<String>) -> Unit): Boolean {
        if (encoding.isEmpty()) return false
        encoding.deleteCharAt(encoding.length - 1)
        query(onCandidates)
        return true
    }

    /** Mode switch, panel switch, new input session, or a space press — never carries a half-typed encoding across any of these (plan 3.2 point 8/9). */
    fun clear() {
        encoding.clear()
        queryEpoch++
    }

    private fun query(onCandidates: (List<String>) -> Unit) {
        if (encoding.isEmpty()) {
            queryEpoch++
            onCandidates(emptyList())
            return
        }
        val epoch = ++queryEpoch
        repository.queryExact(encoding.toString()) { results ->
            if (epoch == queryEpoch) onCandidates(results)
        }
    }
}
