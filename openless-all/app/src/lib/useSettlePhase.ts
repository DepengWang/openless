import { useEffect, useState } from 'react';
import { SIRI_ENHANCED_GLYPH_MOTION } from './insertTextAnimation';
import type { CapsuleState } from './types';

export type SettlePhase = 'none' | 'waiting' | 'focusing';

/** How long the last line outlives the session: one left-to-right focus pass plus a beat. */
export const SETTLE_LINGER_MS = SIRI_ENHANCED_GLYPH_MOTION.settleSweepMs + 200;

interface SettleTrack {
  state: CapsuleState;
  last: string;
  linger: string;
}

/**
 * Siri enhanced end-of-speech presentation. While the transcript is finalised and polished the
 * line blurs and breathes ("waiting"); when the session completes, the last line is kept for one
 * left-to-right focus pass ("focusing") before the rail lets go of it. Derived during render so
 * the line never drops out for a frame between the Done payload and the linger.
 */
export function useSettlePhase(
  liveText: string,
  state: CapsuleState,
  active: boolean,
): { text: string; phase: SettlePhase } {
  const [track, setTrack] = useState<SettleTrack>({ state, last: liveText, linger: '' });
  let next = track;
  if (state === 'recording' && track.state !== 'recording') {
    next = { ...next, last: liveText };
  } else if (liveText && liveText !== next.last) {
    next = { ...next, last: liveText };
  }
  if (state !== track.state) {
    const settling = track.state === 'transcribing' || track.state === 'polishing';
    next = { ...next, state, linger: active && state === 'done' && settling ? next.last : '' };
  }
  if (next !== track) setTrack(next);

  useEffect(() => {
    if (!track.linger) return undefined;
    const timer = window.setTimeout(
      () => setTrack((current) => (current.linger ? { ...current, linger: '' } : current)),
      SETTLE_LINGER_MS,
    );
    return () => window.clearTimeout(timer);
  }, [track.linger]);

  if (!active) return { text: liveText, phase: 'none' };
  if (next.linger) return { text: next.linger, phase: 'focusing' };
  const waiting = (state === 'transcribing' || state === 'polishing') && Boolean(liveText);
  return { text: liveText, phase: waiting ? 'waiting' : 'none' };
}
