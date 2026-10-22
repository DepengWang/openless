import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
  GRADLE_MARKER,
  REMOVE_ATTRIBUTE,
  patchGradle,
  patchManifest,
} from './patch-android-rustls-verifier.mjs';

const gradle = 'android {\n}\ndependencies {\n    implementation("androidx.webkit:webkit:1.14.0")\n}\n';
const patchedGradle = patchGradle(gradle);
assert.equal(patchedGradle.changed, true);
assert.match(patchedGradle.content, /implementation\("org\.rustls:rustls-platform-verifier:\$rustlsVerifierVersion"\)/);
// The AAR version comes from the same Cargo.lock the Rust verifier is resolved from.
assert.match(patchedGradle.content, /rootProject\.file\("\.\.\/\.\.\/Cargo\.lock"\)/);
assert.match(patchedGradle.content, /name = \\"rustls-platform-verifier-android\\"/);
assert.match(patchedGradle.content, /rustls-platform-verifier\/raw\/maven-archive\/android-release-support\/maven\//);
assert.equal(patchGradle(patchedGradle.content).changed, false);
assert.equal(patchedGradle.content.split(GRADLE_MARKER).length, 2);

const manifest =
  '<?xml version="1.0" encoding="utf-8"?>\n<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n' +
  '<application android:name=".OpenLessApplication"\n        android:usesCleartextTraffic="${usesCleartextTraffic}">\n</application>\n</manifest>\n';
const patchedManifest = patchManifest(manifest);
assert.equal(patchedManifest.changed, true);
assert.match(patchedManifest.content, /<manifest xmlns:tools="http:\/\/schemas\.android\.com\/tools" xmlns:android=/);
assert.match(patchedManifest.content, new RegExp(`<application ${REMOVE_ATTRIBUTE} android:name="\\.OpenLessApplication"`));
// The app keeps its own cleartext policy.
assert.match(patchedManifest.content, /android:usesCleartextTraffic="\$\{usesCleartextTraffic\}"/);
assert.equal(patchManifest(patchedManifest.content).changed, false);

// The Rust half initializes the verifier when the application Context registers.
const bridge = readFileSync(
  fileURLToPath(new URL('../src-tauri/src/android/native_bridge.rs', import.meta.url)),
  'utf8',
);
assert.match(bridge, /nativeRegisterActivityContext[\s\S]{0,400}tls_verifier::init\(env, context\.as_raw\(\)\)/);
// tokio-tungstenite's wss:// clients need a process-default CryptoProvider.
// run() installs it, but the Application Context registers before any
// Activity enters run(), so the verifier init installs it too.
const tlsVerifier = readFileSync(
  fileURLToPath(new URL('../src-tauri/src/android/tls_verifier.rs', import.meta.url)),
  'utf8',
);
assert.match(tlsVerifier, /crate::tls_crypto::install_tls_crypto_provider\(\);/);

// Every Android build path wires the Kotlin half in.
for (const path of [
  '../../../.github/workflows/android-apk.yml',
  '../../../.github/workflows/ci.yml',
  './build-android-local.sh',
  './run-android-tauri-dev.mjs',
]) {
  const content = readFileSync(fileURLToPath(new URL(path, import.meta.url)), 'utf8');
  assert.match(content, /patch-android-rustls-verifier\.mjs/, path);
}

console.log('patch-android-rustls-verifier contract checks passed');
