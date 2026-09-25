package com.openless.app

/**
 * Owns the Pinyin-mode encoding buffer on the English keyboard panel (see
 * OpenLessImeService.LatinInputMode) — phase 2 of the lite-pinyin plan
 * (docs/pinyin-lite/phase-0-audit.md), skeleton only: no dictionary lookup,
 * candidates, or epoch handling yet (phase 3 adds LitePinyinRepository and
 * wires this up to actually query it).
 *
 * Deliberately does not touch currentInputConnection or any View — see the
 * lite-pinyin plan's 5.2 ("不让 View 直接查询词库或持有 Repository").
 */
internal class LitePinyinController {
    private val encoding = StringBuilder()

    fun currentEncoding(): String = encoding.toString()

    fun isEmpty(): Boolean = encoding.isEmpty()

    /** Letters only — OpenLessImeService is responsible for routing non-letter keys elsewhere. */
    fun appendLetter(char: Char) {
        if (char.isLetter()) encoding.append(char.lowercaseChar())
    }

    /** @return true if a character was actually removed (false when the encoding was already empty). */
    fun backspace(): Boolean {
        if (encoding.isEmpty()) return false
        encoding.deleteCharAt(encoding.length - 1)
        return true
    }

    /** Mode switch, panel switch, new input session, or a space press — never carries a half-typed encoding across any of these (plan 3.2 point 8/9). */
    fun clear() {
        encoding.clear()
    }
}
