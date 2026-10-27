// Typewriter pacing for the live transcript rail.
//
// Streaming ASR delivers text in bursts (every 200-400 ms). Revealing each burst one character
// at a time, faster when more is pending, makes the rail read as continuous typing instead of
// jumping by phrases. Pure functions here; the React hook lives next to them.

import { useEffect, useRef, useState } from 'react';

export const TYPEWRITER = {
  /**
   * Fastest gap between two revealed characters. Kept well under the glyph focus animation
   * (~300 ms) so consecutive glyphs overlap into a wave instead of flashing one by one.
   */
  minStepMs: 32,
  /** Slowest gap, used when only a character or two is pending. */
  maxStepMs: 70,
  /** Aim to drain the pending text within roughly one ASR update interval. */
  catchUpMs: 320,
  /** Never trail the recognizer by more than this many characters. */
  maxLagChars: 40,
} as const;

export interface TypewriterFrame {
  shown: string;
  /** Delay before the next frame, or null when `shown` has caught up with the target. */
  delayMs: number | null;
}

/** Shared prefix length of two code-point arrays. */
export function commonPrefixLength(a: readonly string[], b: readonly string[]): number {
  const limit = Math.min(a.length, b.length);
  let index = 0;
  while (index < limit && a[index] === b[index]) index += 1;
  return index;
}

/**
 * Advances `shown` one character toward `target`.
 *
 * A revision of already shown text (common in streaming ASR) rolls back to the shared prefix
 * before typing resumes, so stale characters never linger.
 */
export function nextTypewriterFrame(shown: string, target: string): TypewriterFrame {
  const goal = Array.from(target);
  let current = Array.from(shown);
  const shared = commonPrefixLength(current, goal);
  if (shared < current.length) current = goal.slice(0, shared);

  let pending = goal.length - current.length;
  if (pending <= 0) return { shown: target, delayMs: null };
  if (pending > TYPEWRITER.maxLagChars) {
    current = goal.slice(0, goal.length - TYPEWRITER.maxLagChars);
    pending = TYPEWRITER.maxLagChars;
  }

  const next = goal.slice(0, current.length + 1).join('');
  const remaining = pending - 1;
  if (remaining === 0) return { shown: next, delayMs: null };
  const step = Math.round(TYPEWRITER.catchUpMs / pending);
  return {
    shown: next,
    delayMs: Math.min(TYPEWRITER.maxStepMs, Math.max(TYPEWRITER.minStepMs, step)),
  };
}

/** Returns `target` revealed typewriter-style; with `enabled` false it follows `target` directly. */
export function useTypewriterText(target: string, enabled: boolean): string {
  const initial = enabled ? '' : target;
  const shownRef = useRef(initial);
  const [shown, setShown] = useState(initial);

  useEffect(() => {
    if (!enabled) {
      shownRef.current = target;
      setShown(target);
      return undefined;
    }
    let timer: ReturnType<typeof setTimeout> | undefined;
    const step = () => {
      const frame = nextTypewriterFrame(shownRef.current, target);
      shownRef.current = frame.shown;
      setShown(frame.shown);
      if (frame.delayMs !== null) timer = setTimeout(step, frame.delayMs);
    };
    step();
    return () => {
      if (timer !== undefined) clearTimeout(timer);
    };
  }, [target, enabled]);

  return shown;
}
