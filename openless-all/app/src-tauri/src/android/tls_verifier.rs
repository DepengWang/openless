//! TLS certificate verification for Core's HTTP clients on Android.
//!
//! openless-core uses reqwest 0.13, which verifies certificates through
//! rustls-platform-verifier. On Android that verifier asks the platform
//! TrustManager through JNI and panics on the first HTTPS request unless it
//! was first given the JVM and the application Context — every LLM request
//! and provider validation then dies inside its task. The Kotlin half of the
//! verifier ships as the `org.rustls:rustls-platform-verifier` AAR, added to
//! the generated Gradle project by scripts/patch-android-rustls-verifier.mjs.
//!
//! The WebSocket ASR clients (tokio-tungstenite, bundled webpki roots) build
//! their rustls config from the process-default CryptoProvider instead. The
//! dependency graph enables both the `ring` and `aws-lc-rs` rustls features,
//! so rustls cannot pick one and panics on the first `wss://` connect unless a
//! default is installed. Desktop gets one as a side effect of the updater
//! plugin's startup check; Android has no such caller, so it is installed here.
//!
//! The verifier is built on jni-rs 0.22 while this crate uses 0.21, so the raw
//! JNIEnv and Context pointers are handed across.

/// Installs the process-default rustls CryptoProvider (ring, matching the
/// desktop updater) and initializes the verifier from a JNI entry point that
/// received the application Context. Idempotent: both keep their first
/// successful initialization, so every Context registration may call this.
///
/// # Safety
/// `raw_env` must be the JNIEnv of the current native call and `raw_context`
/// a valid local or global reference to an `android.content.Context`.
pub(crate) unsafe fn init(raw_env: *mut jni::sys::JNIEnv, raw_context: jni::sys::jobject) {
    // Err only means a provider is already installed.
    let _ = rustls::crypto::ring::default_provider().install_default();
    let mut env = jni022::EnvUnowned::from_raw(raw_env.cast());
    env.with_env(|env| -> Result<(), jni022::errors::Error> {
        let context = jni022::objects::JObject::from_raw(env, raw_context.cast());
        rustls_platform_verifier::android::init_with_env(env, context)
    })
    .resolve_with::<jni022::errors::LogContextErrorAndDefault, _>(|| {
        "[android-tls] rustls-platform-verifier initialization".to_string()
    });
}
