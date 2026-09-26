#!/usr/bin/env node
/**
 * Decode ANDROID_KEYSTORE_* env vars and patch gen/android signing for
 * release (and optionally debug) APK builds.
 *
 * When OPENLESS_SIGN_DEBUG=true (CI signed_debug dispatch), also wires the
 * debug buildType to openlessRelease so a debug APK can overlay-install over
 * a matching release-signed package while keeping the debug artifact name.
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

const appRoot = fileURLToPath(new URL('..', import.meta.url));
const gradlePath = join(appRoot, 'src-tauri/gen/android/app/build.gradle.kts');
const keystorePath = join(appRoot, 'src-tauri/gen/android/openless-release.keystore');
const SIGNING_ASSIGNMENT = 'signingConfig = signingConfigs.getByName("openlessRelease")';

function requireEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`${name} is required for Android release signing`);
  }
  return value;
}

/** True when this buildType block already assigns openlessRelease. */
function buildTypeHasOpenlessSigning(content, buildType) {
  const openRe = new RegExp(`getByName\\("${buildType}"\\)\\s*\\{`);
  const match = content.match(openRe);
  if (!match || match.index == null) return false;
  const blockStart = match.index + match[0].length;
  const blockEnd = content.indexOf('\n        }', blockStart);
  const blockContent =
    blockEnd === -1
      ? content.slice(blockStart, blockStart + 256)
      : content.slice(blockStart, blockEnd);
  return blockContent.includes(SIGNING_ASSIGNMENT);
}

/**
 * Ensure getByName("<buildType>") { ... signingConfig = openlessRelease }.
 * Creates the buildType block when missing.
 */
function ensureBuildTypeSigning(content, buildType) {
  if (buildTypeHasOpenlessSigning(content, buildType)) {
    return content;
  }

  const openRe = new RegExp(`getByName\\("${buildType}"\\)\\s*\\{`);
  if (openRe.test(content)) {
    return content.replace(openRe, `getByName("${buildType}") {\n            ${SIGNING_ASSIGNMENT}`);
  }

  if (/buildTypes\s*\{/.test(content)) {
    return content.replace(
      /buildTypes\s*\{/,
      `buildTypes {\n        getByName("${buildType}") {\n            ${SIGNING_ASSIGNMENT}\n        }`,
    );
  }

  return content.replace(
    /android\s*\{/,
    `android {\n    buildTypes {\n        getByName("${buildType}") {\n            ${SIGNING_ASSIGNMENT}\n        }\n    }`,
  );
}

function main() {
  const base64 = requireEnv('ANDROID_KEYSTORE_BASE64');
  const storePassword = requireEnv('ANDROID_KEYSTORE_PASSWORD');
  const keyAlias = requireEnv('ANDROID_KEY_ALIAS');
  const keyPassword = requireEnv('ANDROID_KEY_PASSWORD');
  const signDebug = ['1', 'true', 'yes'].includes(
    String(process.env.OPENLESS_SIGN_DEBUG || '').trim().toLowerCase(),
  );

  if (!existsSync(gradlePath)) {
    throw new Error(`Gradle file not found: ${gradlePath} (run tauri android init first)`);
  }

  mkdirSync(dirname(keystorePath), { recursive: true });
  writeFileSync(keystorePath, Buffer.from(base64, 'base64'));

  const escapedStore = storePassword.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
  const escapedKey = keyPassword.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
  const escapedAlias = keyAlias.replace(/\\/g, '\\\\').replace(/"/g, '\\"');
  const escapedKeystore = keystorePath.replace(/\\/g, '/');

  let content = readFileSync(gradlePath, 'utf8');

  if (!content.includes('create("openlessRelease")')) {
    const signingConfigsBlock = `
    signingConfigs {
        create("openlessRelease") {
            storeFile = file("${escapedKeystore}")
            storePassword = "${escapedStore}"
            keyAlias = "${escapedAlias}"
            keyPassword = "${escapedKey}"
        }
    }`;
    if (!/signingConfigs\s*\{/.test(content)) {
      content = content.replace(/android\s*\{/, `android {${signingConfigsBlock}`);
    } else {
      throw new Error(
        'build.gradle.kts already has signingConfigs but no openlessRelease; refuse to patch blindly',
      );
    }
  }

  content = ensureBuildTypeSigning(content, 'release');
  if (signDebug) {
    content = ensureBuildTypeSigning(content, 'debug');
  }

  writeFileSync(gradlePath, content);
  console.log(
    signDebug
      ? `Configured release+debug signing in ${gradlePath}`
      : `Configured release signing in ${gradlePath}`,
  );
}

main();
