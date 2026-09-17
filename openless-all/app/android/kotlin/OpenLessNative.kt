package com.openless.app

/** JNI bridge from Kotlin overlay / lifecycle code into Rust Coordinator. */
object OpenLessNative {
    private const val BACKEND_CONTRACT_VERSION = "2.0.0"

    init {
        try {
            System.loadLibrary("openless_lib")
        } catch (error: UnsatisfiedLinkError) {
            android.util.Log.e("OpenLessNative", "failed to load openless_lib", error)
        }
    }

    @JvmStatic external fun nativeStartDictation()

    @JvmStatic external fun nativeStartDictationForIme()

    @JvmStatic external fun nativeStartDictationWithTranslation(translation: Boolean)

    @JvmStatic external fun nativeStopDictation()

    @JvmStatic external fun nativeStopDictationForIme()

    @JvmStatic external fun nativeStopDictationWithTranslation(translation: Boolean)

    @JvmStatic external fun nativeCancelDictation()

    /** Records a hand-corrected span from the IME's "edit result" flow into the shared correction dictionary. */
    @JvmStatic external fun nativeAddCorrectionRule(pattern: String, replacement: String)
    // Adds a word/phrase straight to the global Dictionary (same store
    // add_vocab exposes to the desktop UI) instead of a CorrectionRule —
    // see native_bridge.rs's spawn_add_vocabulary_word() doc comment for
    // why the IME's edit/correction flows moved to this.
    @JvmStatic external fun nativeAddVocabularyWord(phrase: String)

    /** JSON array of every existing correction rule's pattern — lets the clipboard swipe-left gesture show "add" vs. "remove" before the drag finishes. */
    @JvmStatic external fun nativeCorrectionRulePatterns(): String

    /** Removes every correction rule whose pattern exactly matches — the clipboard swipe-left "remove" action. */
    @JvmStatic external fun nativeRemoveCorrectionRule(pattern: String)

    /** JSON array of every existing Dictionary entry's phrase — same purpose as nativeCorrectionRulePatterns(), for the clipboard swipe-left zone now that adding writes to the Dictionary instead. */
    @JvmStatic external fun nativeVocabularyPhrases(): String

    /** Removes every Dictionary entry whose phrase exactly matches — the clipboard swipe-left "remove" action's counterpart to nativeAddVocabularyWord(). */
    @JvmStatic external fun nativeRemoveVocabularyWord(phrase: String)

    /**
     * Registers this Activity as the one with_android_env() (every
     * Rust->Kotlin JNI call, including dictation/waveform capsule updates)
     * routes through — replaces whatever was registered before, since a
     * GlobalRef stays valid for its Activity's whole lifecycle (unlike a
     * once-per-process cached context, or tao's own live-but-resumed-only
     * tracked Activity). Call from onCreate(); pair with
     * nativeUnregisterActivityContext() in onDestroy().
     */
    @JvmStatic external fun nativeRegisterActivityContext(activity: android.app.Activity)

    /** Clears the registration from nativeRegisterActivityContext() — only takes effect if `activity` is still the currently-registered one. */
    @JvmStatic external fun nativeUnregisterActivityContext(activity: android.app.Activity)

    /**
     * True once some MainActivity-family instance has called
     * nativeRegisterActivityContext() and nothing has unregistered it
     * since. The Rust backend can stay perfectly healthy for a long time
     * after its last registered Activity is destroyed (that's the whole
     * point of Phase 1/2's recovery design) — requireBackendContract()
     * alone can't see that gap, since it only checks whether the backend
     * itself is running, not whether there's still an Activity around for
     * it to notify.
     */
    @JvmStatic external fun nativeHasRegisteredActivityContext(): Boolean

    @JvmStatic external fun nativeBackendSnapshot(): String

    @JvmStatic
    fun requireBackendContract() {
        val response = org.json.JSONObject(nativeBackendSnapshot())
        val version = response.optString("contractVersion")
        check(version == BACKEND_CONTRACT_VERSION) {
            "unsupported backend contract version: $version"
        }
        check(response.optBoolean("ok")) {
            response.optString("error", "backend unavailable")
        }
    }

    @JvmStatic external fun nativeSwitchStylePack()

    @JvmStatic external fun nativeOpenQaFromOverlay()

    @JvmStatic external fun nativeFinalizeQaFromOverlay()

    @JvmStatic external fun nativeGetOverlayTriggerMode(): String

    @JvmStatic external fun nativeCanDrawOverlays(context: android.content.Context): Boolean

    @JvmStatic external fun nativeShowOverlay(context: android.content.Context)

    @JvmStatic external fun nativeHideOverlay(context: android.content.Context)

    @JvmStatic external fun nativeIsOverlayVisible(): Boolean

    @JvmStatic external fun nativeNotifyOverlayPermissionChanged(context: android.content.Context)

    @JvmStatic external fun nativeNotifyOverlayDestroyed()
}
