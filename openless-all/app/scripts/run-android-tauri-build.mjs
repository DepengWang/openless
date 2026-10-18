/**
 * Run `tauri android build` with ABI targets from OPENLESS_ANDROID_TARGETS
 * (space/comma-separated CLI names: aarch64 armv7 i686 x86_64).
 *
 * Usage:
 *   node scripts/run-android-tauri-build.mjs --debug
 *   node scripts/run-android-tauri-build.mjs --release
 */
import { spawnSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';
import { parseAndroidAbis, ANDROID_ABI_MATRIX } from './android-abi-matrix.mjs';
import {
  SHERPA_ONNX_ANDROID_ABI,
  SHERPA_ONNX_ANDROID_LIBS,
  ensureSherpaOnnxAndroidLibs,
} from './sherpa-onnx-android.mjs';

const appRoot = fileURLToPath(new URL('..', import.meta.url));

/**
 * Local ASR links sherpa-onnx on arm64 only (see sherpa-onnx-android.mjs):
 * point the crate at the verified libraries and put them in jniLibs so the
 * arm64 APK ships what libopenless_lib.so needs at load time.
 */
async function prepareLocalAsrLibs(targets, env) {
  if (!targets.includes('aarch64')) return;
  const libDir = await ensureSherpaOnnxAndroidLibs();
  env.SHERPA_ONNX_LIB_DIR = libDir;
  const jniLibs = join(appRoot, 'src-tauri', 'gen', 'android', 'app', 'src', 'main', 'jniLibs', SHERPA_ONNX_ANDROID_ABI);
  if (!existsSync(join(appRoot, 'src-tauri', 'gen', 'android'))) {
    throw new Error('Generated Android project not found under src-tauri/gen/android; run "npm run tauri -- android init --ci" first.');
  }
  mkdirSync(jniLibs, { recursive: true });
  for (const lib of SHERPA_ONNX_ANDROID_LIBS) copyFileSync(join(libDir, lib), join(jniLibs, lib));
  console.log(`[android-build] local ASR libraries: ${libDir}`);
}

function resolveTargets() {
  const raw = process.env.OPENLESS_ANDROID_TARGETS ?? '';
  if (!raw.trim()) {
    return ANDROID_ABI_MATRIX.map((e) => e.abi);
  }
  return parseAndroidAbis(raw, {
    defaultAbis: ANDROID_ABI_MATRIX.map((e) => e.abi),
  }).map((e) => e.abi);
}

async function main() {
  const mode = process.argv[2];
  if (mode !== '--debug' && mode !== '--release') {
    console.error('Usage: node scripts/run-android-tauri-build.mjs --debug|--release');
    process.exit(1);
  }
  const targets = resolveTargets();
  const env = { ...process.env };
  await prepareLocalAsrLibs(targets, env);
  const args = ['tauri', 'android', 'build', '--apk', '--split-per-abi', '--target', ...targets];
  if (mode === '--debug') {
    args.push('--debug');
  }

  console.log(`[android-build] targets=${targets.join(',')} mode=${mode.slice(2)}`);
  // #1103: Tauri CLI runs an initial cargo build for the first target to
  // "initialize plugins", then Gradle invokes `android-studio-script` per ABI
  // (including the first). Expect two cargo passes for the first ABI; do not
  // bypass the Gradle/native path.
  console.log(
    '[android-build] note: first ABI may compile twice (Tauri plugin init + android-studio-script)',
  );

  const bin = process.platform === 'win32' ? 'npx.cmd' : 'npx';
  const result = spawnSync(bin, args, {
    stdio: 'inherit',
    env,
    shell: process.platform === 'win32',
  });
  if (result.error) {
    throw result.error;
  }
  process.exit(result.status ?? 1);
}

main().catch((error) => {
  console.error(`[android-build] ${error.message ?? error}`);
  process.exit(1);
});
