#!/usr/bin/env node
// openless-core's reqwest 0.13 verifies TLS through rustls-platform-verifier.
// On Android its Kotlin half (org.rustls:rustls-platform-verifier) must be in
// the APK, and Rust must initialize it with the app Context
// (src-tauri/src/android/tls_verifier.rs) — otherwise every Core HTTPS request
// panics. This wires the Kotlin half into the generated Gradle project.
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import process from 'node:process';

const appDir = fileURLToPath(new URL('../src-tauri/gen/android/app/', import.meta.url));
const gradlePath = `${appDir}build.gradle.kts`;
const manifestPath = `${appDir}src/main/AndroidManifest.xml`;
const proguardPath = `${appDir}proguard-rustls-platform-verifier.pro`;

export const GRADLE_MARKER = '// openless: rustls-platform-verifier';

// The AAR version must equal the rustls-platform-verifier-android crate in
// src-tauri/Cargo.lock, so Gradle reads it from there at configuration time.
// A local Maven copy (OPENLESS_RUSTLS_MAVEN, or <cache root>/rustls-maven)
// is preferred; the project's GitHub Maven archive is the fallback.
export const GRADLE_SNIPPET = `
${GRADLE_MARKER} (Kotlin half of Core's TLS verifier; see scripts/patch-android-rustls-verifier.mjs)
val rustlsVerifierVersion: String = rootProject.file("../../Cargo.lock").readLines().let { lines ->
    val start = lines.indexOfFirst { it.trim() == "name = \\"rustls-platform-verifier-android\\"" }
    require(start >= 0) { "rustls-platform-verifier-android not found in src-tauri/Cargo.lock" }
    lines.drop(start + 1).first { it.trimStart().startsWith("version = ") }
        .substringAfter('"').substringBefore('"')
}
repositories {
    val localMaven = file(
        System.getenv("OPENLESS_RUSTLS_MAVEN")
            ?: ((System.getenv("OPENLESS_CACHE_ROOT") ?: "/data-win/openless-cache") + "/rustls-maven"),
    )
    if (localMaven.isDirectory) maven { url = uri(localMaven) }
    maven { url = uri("https://github.com/rustls/rustls-platform-verifier/raw/maven-archive/android-release-support/maven/") }
}
dependencies {
    implementation("org.rustls:rustls-platform-verifier:$rustlsVerifierVersion")
}
`;

export const PROGUARD_RULES = `# The Rust verifier finds these classes by name through JNI.
-keep class org.rustls.platformverifier.** { *; }
`;

const TOOLS_NS = 'xmlns:tools="http://schemas.android.com/tools"';
export const REMOVE_ATTRIBUTE = 'tools:remove="android:networkSecurityConfig"';

export function patchGradle(content) {
  if (content.includes(GRADLE_MARKER)) return { content, changed: false };
  return { content: `${content.trimEnd()}\n${GRADLE_SNIPPET}`, changed: true };
}

// The AAR's manifest sets a networkSecurityConfig that forbids cleartext
// traffic, which would silently override the app's usesCleartextTraffic.
// Its revocation checks are soft-fail, so dropping it keeps TLS working.
export function patchManifest(content) {
  let updated = content;
  if (!/<manifest\b[^>]*xmlns:tools=/.test(updated)) {
    updated = updated.replace(/<manifest\b/, `<manifest ${TOOLS_NS}`);
  }
  if (!updated.includes(REMOVE_ATTRIBUTE)) {
    const application = /<application\b/.exec(updated);
    if (!application) throw new Error('application element not found in AndroidManifest.xml');
    updated = updated.replace(/<application\b/, `<application ${REMOVE_ATTRIBUTE}`);
  }
  return { content: updated, changed: updated !== content };
}

function main() {
  const dryRun = process.argv.includes('--dry-run');
  for (const path of [gradlePath, manifestPath]) {
    if (!existsSync(path)) {
      throw new Error(`Generated Android file not found: ${path}\nRun "npm run tauri -- android init --ci" first.`);
    }
  }
  const results = [
    [gradlePath, patchGradle(readFileSync(gradlePath, 'utf8'))],
    [manifestPath, patchManifest(readFileSync(manifestPath, 'utf8'))],
    [proguardPath, {
      content: PROGUARD_RULES,
      changed: !existsSync(proguardPath) || readFileSync(proguardPath, 'utf8') !== PROGUARD_RULES,
    }],
  ];
  for (const [path, result] of results) {
    if (!result.changed) {
      console.log(`rustls-platform-verifier already wired in ${path}; skipping.`);
    } else if (dryRun) {
      console.log(`[dry-run] Would patch ${path}`);
    } else {
      writeFileSync(path, result.content, 'utf8');
      console.log(`Patched rustls-platform-verifier into ${path}`);
    }
  }
}

const isDirectRun = Boolean(
  process.argv[1]?.replace(/\\/g, '/').endsWith('patch-android-rustls-verifier.mjs'),
);
if (isDirectRun) {
  try {
    main();
  } catch (error) {
    console.error(error instanceof Error ? error.message : error);
    process.exit(1);
  }
}
