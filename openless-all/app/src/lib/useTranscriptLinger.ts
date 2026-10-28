import { useEffect, useState } from 'react';

interface LingerTrack {
  shown: string;
  closing: boolean;
}

/**
 * Siri enhanced close: when the live transcript disappears (Done, Cancel, Error), keep the last
 * line for `ms` so the band can collapse into its centre instead of vanishing. Derived during
 * render, so the line never drops out for a frame before the closing pass starts.
 */
export function useTranscriptLinger(
  text: string,
  active: boolean,
  ms: number,
): { text: string; closing: boolean } {
  const [track, setTrack] = useState<LingerTrack>({ shown: text, closing: false });
  let next = track;
  if (text) {
    if (track.shown !== text || track.closing) next = { shown: text, closing: false };
  } else if (track.shown && !track.closing) {
    next = active ? { shown: track.shown, closing: true } : { shown: '', closing: false };
  }
  if (next !== track) setTrack(next);

  useEffect(() => {
    if (!track.closing) return undefined;
    const timer = window.setTimeout(
      () => setTrack((current) => (current.closing ? { shown: '', closing: false } : current)),
      ms,
    );
    return () => window.clearTimeout(timer);
  }, [track.closing, ms]);

  return next.closing ? { text: next.shown, closing: true } : { text, closing: false };
}
