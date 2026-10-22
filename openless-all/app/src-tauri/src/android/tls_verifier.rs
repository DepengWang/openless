//! TLS setup for Core's network clients on Android.
//!
//! openless-core uses reqwest 0.13, which verifies certificates through
//! rustls-platform-verifier. On Android that verifier asks the platform
//! TrustManager through JNI and panics on the first HTTPS request unless it
//! was first given the JVM and the application Context — every LLM request
//! and provider validation then dies inside its task. The Kotlin half of the
//! verifier ships as the `org.rustls:rustls-platform-verifier` AAR, added to
//! the generated Gradle project by scripts/patch-android-rustls-verifier.mjs.
//!
//! The WebSocket ASR clients additionally need rustls' process-default
//! CryptoProvider, which `run()` installs first thing (`crate::tls_crypto`).
//! It is also installed here because this runs from
//! `OpenLessApplication.onCreate`, before any Activity enters `run()`.
//!
//! The verifier is built on jni-rs 0.22 while this crate uses 0.21, so the raw
//! JNIEnv and Context pointers are handed across.

/// Installs the process-default CryptoProvider and initializes the verifier
/// from a JNI entry point that received the application Context. Idempotent:
/// both keep their first successful initialization, so every Context
/// registration may call this.
///
/// # Safety
/// `raw_env` must be the JNIEnv of the current native call and `raw_context`
/// a valid local or global reference to an `android.content.Context`.
pub(crate) unsafe fn init(raw_env: *mut jni::sys::JNIEnv, raw_context: jni::sys::jobject) {
    crate::tls_crypto::install_tls_crypto_provider();
    let mut env = jni022::EnvUnowned::from_raw(raw_env.cast());
    env.with_env(|env| -> Result<(), jni022::errors::Error> {
        let context = jni022::objects::JObject::from_raw(env, raw_context.cast());
        rustls_platform_verifier::android::init_with_env(env, context)
    })
    .resolve_with::<jni022::errors::LogContextErrorAndDefault, _>(|| {
        "[android-tls] rustls-platform-verifier initialization".to_string()
    });
}
