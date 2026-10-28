import {
  animate,
  motion,
  useMotionValue,
  useReducedMotion,
  useTransform,
  type Easing,
} from 'framer-motion';
import { useEffect, useMemo, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import './CapsuleStyles.css';
import {
  INSERT_TEXT_MOTION,
  SIRI_ENHANCED_GLYPH_MOTION,
  appendedBirthAdvance,
  clampInsertContentWidth,
  diffInsertWindowUnits,
  firstBornIndex,
  planCapsuleInsertMotion,
  INSERT_TEXT_RENDER_WINDOW,
  planCharDelays,
  prefixWidths,
  rightAnchoredPositions,
  selectInsertRenderWindow,
  type InsertUnit,
} from '../lib/insertTextAnimation';
import { Icon } from './Icon';
import {
  applyTranscriptEvent,
  applyTranscriptSessionId,
  applyTranscriptSnapshot,
  beginTranscriptGeneration,
  clearTranscriptText,
  createTranscriptViewState,
  shouldRetryTranscriptSnapshot,
  type BackendEvent,
  type TranscriptViewState,
} from '../lib/backendEvent';
import { capsuleTranscriptFontSize, visibleCapsuleTranscript } from '../lib/capsuleTranscript';
import { getCapsuleTranscriptRailHeight } from '../lib/capsuleLayout';
import { planBirthDelays } from '../lib/typewriterText';
import { useTranscriptLinger } from '../lib/useTranscriptLinger';
import { getSettings } from '../lib/ipc/settings';
import { getCapsuleSnapshot, isTauri } from '../lib/ipc';
import type {
  CapsulePayload,
  CapsuleSnapshot,
  CapsuleState,
  CapsuleStyle,
  UserPreferences,
} from '../lib/types';

export type LiveTranscriptTone = 'frost' | 'dark';

export function CapsuleTranslationBadge({ visible = true }: { visible?: boolean }) {
  const { t } = useTranslation();
  return (
    <div className="ol-classic-translation" data-visible={visible}>
      <span />
      {t('capsule.translating')}
    </div>
  );
}

export interface LiveTranscriptPillProps {
  text: string;
  tone?: LiveTranscriptTone;
  stageWidth: number;
  maxWidth: number;
  minWidth?: number;
  height?: number;
  onCancel?: () => void;
  onConfirm?: () => void;
  cancelEnabled?: boolean;
  confirmEnabled?: boolean;
  cancelLabel?: string;
  confirmLabel?: string;
  controlSize?: number;
  fontSize?: number;
  /**
   * Siri enhanced: a centred band that starts at the capsule's width, widens ahead of the text up
   * to `maxWidth`, then scrolls the text through it like a marquee.
   */
  fixedWidth?: boolean;
  /** Siri enhanced: the transcript has gone; collapse the band into its centre. */
  closing?: boolean;
  /** Siri enhanced: ink-wash glyph focus-in, with each burst's new glyphs entering in order. */
  enhancedMotion?: boolean;
  /**
   * Siri enhanced: speech has ended and the text is being finalised / polished; the line loops
   * blur-together then focus left to right until the rail goes away.
   */
  settled?: boolean;
}

const FONT_SIZE = 16;
/** Matches the horizontal feather of the enhanced pill in CapsuleStyles.css. */
const SIRI_ENHANCED_FEATHER_PX = 40;
/** Siri enhanced band height: the 40px Siri rail plus a third. */
const SIRI_ENHANCED_RAIL_HEIGHT = 54;
const FONT_WEIGHT = 500;
const DEFAULT_PILL_HEIGHT = 40;

let measureCanvas: HTMLCanvasElement | null = null;

function getSansFont(): string {
  if (typeof window === 'undefined') return 'system-ui, sans-serif';
  const token = getComputedStyle(document.documentElement)
    .getPropertyValue('--ol-font-sans')
    .trim();
  return token || 'system-ui, sans-serif';
}

function measureGlyphWidth(glyph: string, fontSize: number): number {
  if (typeof document === 'undefined') {
    return Math.max(fontSize * 0.92, glyph.length * fontSize);
  }
  if (!measureCanvas) measureCanvas = document.createElement('canvas');
  const ctx = measureCanvas.getContext('2d');
  if (!ctx) return fontSize;
  ctx.font = `${FONT_WEIGHT} ${fontSize}px ${getSansFont()}`;
  const raw = ctx.measureText(glyph === ' ' ? '\u00a0' : glyph).width;
  return Math.max(1, raw) + INSERT_TEXT_MOTION.glyphGapPx;
}

export interface CapsuleTranscriptRailProps {
  text: string;
  tone?: LiveTranscriptTone;
  stageWidth: number;
  maxWidth: number;
  height?: number;
  fontSize?: number;
  /** Siri enhanced presentation (fixed-length marquee, ink-wash glyphs). */
  enhanced?: boolean;
  /** Speech has ended (transcribing / polishing). */
  settled?: boolean;
  /** The transcript has gone and the enhanced band is collapsing. */
  closing?: boolean;
}

/** Transcript is deliberately a sibling rail: the capsule body remains responsible for audio feedback and controls. */
export function CapsuleTranscriptRail({
  text,
  tone = 'frost',
  stageWidth,
  maxWidth,
  height = DEFAULT_PILL_HEIGHT,
  fontSize = FONT_SIZE,
  enhanced = false,
  settled = false,
  closing = false,
}: CapsuleTranscriptRailProps) {
  const liveText = text.trim();
  if (!liveText) return null;
  return (
    <div className="ol-capsule-transcript-rail" data-tone={tone} data-transcript-rail="true">
      <LiveTranscriptPill
        text={liveText}
        tone={tone}
        stageWidth={stageWidth}
        maxWidth={maxWidth}
        height={height}
        fontSize={fontSize}
        fixedWidth={enhanced}
        enhancedMotion={enhanced}
        settled={enhanced && settled}
        closing={enhanced && closing}
      />
    </div>
  );
}

/**
 * Windows renders the transcript in a separate, permanently click-through HWND.
 * The capsule HWND can therefore keep a small, reliable native hit region for
 * its controls while this rail never participates in cross-process hit testing.
 */
export function CapsuleTranscriptOverlay() {
  const { t } = useTranslation();
  const [state, setState] = useState<CapsuleState>('idle');
  const [style, setStyle] = useState<CapsuleStyle>('siri');
  const [text, setText] = useState('');
  const [enabled, setEnabled] = useState(true);
  const [fontSize, setFontSize] = useState(14);
  const [siriEnhanced, setSiriEnhanced] = useState(false);
  // Siri enhanced: the host sizes this HWND to 1/2.7 of the screen; follow it.
  const [railWidth, setRailWidth] = useState(() =>
    typeof window === 'undefined' ? 460 : window.innerWidth,
  );
  useEffect(() => {
    const onResize = () => setRailWidth(window.innerWidth);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  const [selectionPolish, setSelectionPolish] = useState(false);
  const [translation, setTranslation] = useState(false);
  const transcriptRef = useRef<TranscriptViewState>(createTranscriptViewState());
  const stateRef = useRef<CapsuleState>('idle');
  const snapshotRetryDelays = [0, 16, 32, 64, 128, 256] as const;

  useEffect(() => {
    if (!isTauri) return;
    let cancelled = false;
    let unlisten: (() => void) | undefined;
    (async () => {
      const { listen } = await import('@tauri-apps/api/event');
      const stateHandle = await listen<CapsulePayload>('capsule:state', (event) => {
        const payload = event.payload;
        const previousState = stateRef.current;
        const previousTranscript = transcriptRef.current;
        const sessionTranscript = applyTranscriptSessionId(previousTranscript, payload.sessionId);
        const sessionChanged = sessionTranscript !== previousTranscript;
        transcriptRef.current = sessionTranscript;
        stateRef.current = payload.state;
        setState(payload.state);
        if (payload.capsuleStyle) setStyle(payload.capsuleStyle);
        setSelectionPolish(payload.selectionPolish === true);
        setTranslation(payload.translation === true);
        if (payload.state === 'recording' && previousState !== 'recording' && !sessionChanged) {
          transcriptRef.current = beginTranscriptGeneration(transcriptRef.current);
          setText(transcriptRef.current.text);
        } else if (sessionChanged) {
          setText(sessionTranscript.text);
        }
        if (['idle', 'done', 'cancelled', 'error'].includes(payload.state)) {
          transcriptRef.current = clearTranscriptText(transcriptRef.current);
          setText('');
        }
      });
      const transcriptHandle = await listen<BackendEvent>('backend:event', (event) => {
        const next = applyTranscriptEvent(transcriptRef.current, event.payload);
        transcriptRef.current = next;
        setText(next.text);
      });
      const preferenceHandle = await listen<UserPreferences>('prefs:changed', (event) => {
        setEnabled(event.payload.capsuleTranscriptEnabled ?? true);
        setFontSize(capsuleTranscriptFontSize(event.payload.capsuleTranscriptFontSize));
        setSiriEnhanced(event.payload.capsuleSiriEnhanced ?? false);
        if (event.payload.capsuleStyle) setStyle(event.payload.capsuleStyle);
      });
      const applySnapshot = (
        snapshot: CapsuleSnapshot,
        fallbackStyle: CapsuleStyle,
      ): 'retry' | 'done' => {
        const previousTranscript = transcriptRef.current;
        if (shouldRetryTranscriptSnapshot(previousTranscript, snapshot)) return 'retry';
        const nextTranscript = applyTranscriptSnapshot(previousTranscript, snapshot);
        if (nextTranscript === previousTranscript) return 'done';
        const sessionChanged =
          snapshot.sessionId !== null && snapshot.sessionId !== previousTranscript.sessionId;
        transcriptRef.current = nextTranscript;
        setText(nextTranscript.text);

        const previousState = stateRef.current;
        stateRef.current = snapshot.state;
        setState(snapshot.state);
        setStyle(snapshot.capsuleStyle ?? fallbackStyle);
        setSelectionPolish(snapshot.selectionPolish === true);
        setTranslation(snapshot.translation === true);
        if (snapshot.state === 'recording' && previousState !== 'recording' && !sessionChanged) {
          transcriptRef.current = beginTranscriptGeneration(transcriptRef.current);
          setText(transcriptRef.current.text);
        }
        if (['idle', 'done', 'cancelled', 'error'].includes(snapshot.state)) {
          transcriptRef.current = clearTranscriptText(transcriptRef.current);
          setText('');
        }
        return 'done';
      };
      const replaySnapshot = async (fallbackStyle: CapsuleStyle) => {
        for (const delayMs of snapshotRetryDelays) {
          if (cancelled) return;
          if (delayMs > 0) {
            await new Promise<void>((resolve) => window.setTimeout(resolve, delayMs));
          }
          if (cancelled) return;
          const snapshot = await getCapsuleSnapshot();
          if (cancelled) return;
          if (!snapshot) continue;
          if (applySnapshot(snapshot, fallbackStyle) === 'done') return;
        }
      };
      if (cancelled) {
        stateHandle();
        transcriptHandle();
        preferenceHandle();
      } else {
        unlisten = () => {
          stateHandle();
          transcriptHandle();
          preferenceHandle();
        };
        const preferences = await getSettings();
        if (!cancelled) {
          setEnabled(preferences.capsuleTranscriptEnabled ?? true);
          setFontSize(capsuleTranscriptFontSize(preferences.capsuleTranscriptFontSize));
          setSiriEnhanced(preferences.capsuleSiriEnhanced ?? false);
          setStyle(preferences.capsuleStyle);
          await replaySnapshot(preferences.capsuleStyle);
        }
      }
    })().catch((error) => console.warn('[capsule-rail] subscription failed', error));
    return () => {
      cancelled = true;
      unlisten?.();
    };
  }, []);

  const enhanced = style === 'siri' && siriEnhanced;
  // Enhanced: the last line outlives the session for the collapse (Capsule keeps the rail shown).
  const linger = useTranscriptLinger(
    visibleCapsuleTranscript(text, enabled, state, selectionPolish),
    enhanced,
    SIRI_ENHANCED_GLYPH_MOTION.closeMs,
  );
  const liveText = linger.text;
  const typeless = style === 'typeless';
  // Enhanced: a third taller band; must match SIRI_ENHANCED_RAIL_HEIGHT in the Tauri host.
  const railHeight = enhanced ? SIRI_ENHANCED_RAIL_HEIGHT : getCapsuleTranscriptRailHeight(style);
  // Enhanced: the overlay spans the whole (wider) HWND so the pill and the text share one frame.
  const stageWidth = enhanced ? Math.max(460, railWidth) : 460;
  return (
    <div
      className={`ol-capsule-transcript-overlay${typeless ? ' ol-typeless-capsule-wrap ol-capsule-transcript-overlay--typeless' : ''}`}
      data-style={style}
      data-enhanced={enhanced ? 'true' : undefined}
      data-transcript-visible={liveText ? 'true' : 'false'}
    >
      <CapsuleTranscriptRail
        text={liveText}
        fontSize={typeless ? fontSize / 0.447 : fontSize}
        tone={typeless ? 'dark' : 'frost'}
        stageWidth={stageWidth}
        maxWidth={stageWidth - 20}
        height={railHeight}
        enhanced={enhanced}
        settled={state === 'transcribing' || state === 'polishing'}
        closing={linger.closing}
      />
      {translation && !selectionPolish && style === 'classic' && <CapsuleTranslationBadge />}
      {translation && !selectionPolish && typeless && (
        <div className="ol-typeless-translation-row">
          <span className="ol-typeless-translation">{t('capsule.translating')}</span>
        </div>
      )}
    </div>
  );
}

export function LiveTranscriptPill({
  text,
  tone = 'frost',
  stageWidth,
  maxWidth,
  minWidth = INSERT_TEXT_MOTION.minWidth,
  height = DEFAULT_PILL_HEIGHT,
  onCancel,
  onConfirm,
  cancelEnabled = true,
  confirmEnabled = true,
  cancelLabel,
  confirmLabel,
  controlSize = 28,
  fontSize = FONT_SIZE,
  fixedWidth = false,
  enhancedMotion = false,
  settled = false,
  closing = false,
}: LiveTranscriptPillProps) {
  const reduceMotion = useReducedMotion();
  const hasControls = Boolean(onCancel || onConfirm);
  // The enhanced pill feathers its outer 40px; keep the glyphs inside the solid part.
  const padX =
    (fixedWidth ? SIRI_ENHANCED_FEATHER_PX : INSERT_TEXT_MOTION.padX) +
    (hasControls ? controlSize + 6 : 0);
  const pillMinWidth = hasControls ? Math.max(minWidth, controlSize * 2 + 72) : minWidth;
  // The wide fixed marquee needs more glyphs than the default window to stay filled.
  const windowOptions = useMemo(
    () =>
      fixedWidth
        ? { ...INSERT_TEXT_RENDER_WINDOW, maxUnits: SIRI_ENHANCED_GLYPH_MOTION.maxUnits }
        : INSERT_TEXT_RENDER_WINDOW,
    [fixedWidth],
  );
  const [units, setUnits] = useState<InsertUnit[]>(() =>
    diffInsertWindowUnits([], text, windowOptions),
  );
  const [seenText, setSeenText] = useState(text);
  if (text !== seenText) {
    setSeenText(text);
    setUnits((prev) => diffInsertWindowUnits(prev, text, windowOptions));
  }

  const renderWindow = useMemo(
    () =>
      selectInsertRenderWindow(
        units,
        (glyph) => measureGlyphWidth(glyph, fontSize),
        maxWidth,
        padX,
        windowOptions,
      ),
    [units, fontSize, maxWidth, padX, windowOptions],
  );
  const renderUnits = renderWindow.units;
  const widths = renderWindow.widths;
  const contentWidth = useMemo(
    () =>
      clampInsertContentWidth(
        widths.reduce((sum, width) => sum + width, 0),
        maxWidth,
        padX,
      ),
    [widths, maxWidth, padX],
  );
  const positions = useMemo(() => rightAnchoredPositions(widths), [widths]);
  // Start appended glyphs beyond the old tail, so they never appear on top of
  // retained text while the displacement spring is still gathering speed.
  const birthAdvance = appendedBirthAdvance(renderUnits, widths);
  const originIndex = firstBornIndex(renderUnits);
  const starts = useMemo(() => prefixWidths(widths), [widths]);
  const originX = (starts[originIndex] ?? 0) + (widths[originIndex] ?? 0) / 2;
  const insertedCount = renderUnits.filter((unit) => unit.born).length;
  const delays = useMemo(
    () => planCharDelays(widths, originIndex, insertedCount),
    [widths, originIndex, insertedCount],
  );
  // Enhanced: layout still moves once per ASR burst; the burst's new glyphs then ink in in order.
  const birthDelays = useMemo(
    () => (enhancedMotion ? planBirthDelays(renderUnits.map((unit) => unit.born)) : null),
    [enhancedMotion, renderUnits],
  );
  // Enhanced, polishing: each cycle the line blurs together, then glyphs focus left to right so
  // the last one is sharp settleSweepMs after the first starts.
  const settleStepMs = useMemo(() => {
    const ink = SIRI_ENHANCED_GLYPH_MOTION;
    const count = renderUnits.length;
    return count > 1 ? (ink.settleSweepMs - ink.settleFocusMs) / (count - 1) : 0;
  }, [renderUnits.length]);
  const plan = useMemo(() => {
    if (fixedWidth) {
      // Centred band: capsule-wide at first, then a few glyphs ahead of the text up to maxWidth;
      // closing collapses it to the centre.
      const ink = SIRI_ENHANCED_GLYPH_MOTION;
      const grown = contentWidth + padX * 2 + fontSize * ink.lookaheadEm;
      const width = closing
        ? 0
        : Math.min(maxWidth, Math.max(Math.min(ink.minWidth, maxWidth), grown));
      const left = (stageWidth - width) / 2;
      return { width, left, rightEdge: left + width, widthDelayMs: 0, recenterDelayMs: 0 };
    }
    return planCapsuleInsertMotion({
      stageWidth,
      contentWidth,
      originX,
      minWidth: pillMinWidth,
      maxWidth,
      padX,
    });
  }, [
    fixedWidth,
    closing,
    fontSize,
    stageWidth,
    contentWidth,
    originX,
    pillMinWidth,
    maxWidth,
    padX,
  ]);

  // The enhanced band opens from its centre: it starts at zero width and the width spring below
  // spreads it out to the capsule's width.
  const initialWidth = fixedWidth ? 0 : pillMinWidth;
  const widthMv = useMotionValue(initialWidth);
  const rightEdgeMv = useMotionValue(stageWidth / 2 + initialWidth / 2);
  // Delayed recentering may otherwise push a large batch beyond the native
  // WebView's left edge. Constrain the rendered frame, not spring momentum.
  const visibleRightMv = useTransform([rightEdgeMv, widthMv], ([right, width]) =>
    Math.max(Number(width), Math.min(stageWidth, Number(right))),
  );
  const leftMv = useTransform(
    [visibleRightMv, widthMv],
    ([right, width]) => Number(right) - Number(width),
  );
  // Siri enhanced: while the line is shorter than the band, shift the right-anchored track left by
  // half the free space so the text sits centred and grows outward both ways; once the band is at
  // maxWidth and full, the offset reaches 0 and the line scrolls as a marquee.
  const centerOffset = fixedWidth ? Math.max(0, (plan.width - padX * 2 - contentWidth) / 2) : 0;
  const centerOffsetMv = useMotionValue(centerOffset);
  useEffect(() => {
    if (reduceMotion) {
      centerOffsetMv.set(centerOffset);
      return undefined;
    }
    const anim = animate(centerOffsetMv, centerOffset, {
      type: 'spring',
      ...INSERT_TEXT_MOTION.shiftSpring,
    });
    return () => anim.stop();
  }, [centerOffset, centerOffsetMv, reduceMotion]);
  const trackRightMv = useTransform(
    [visibleRightMv, centerOffsetMv],
    ([right, offset]) => stageWidth - Number(right) + padX + Number(offset),
  );

  const trackWidthMv = useTransform(widthMv, (width) => Math.max(1, width - padX * 2));

  useEffect(() => {
    if (reduceMotion) {
      widthMv.set(plan.width);
      rightEdgeMv.set(plan.rightEdge);
      return undefined;
    }
    // Closing is a short ease-in toward the centre, not a spring, so it ends cleanly.
    const collapse = {
      duration: SIRI_ENHANCED_GLYPH_MOTION.closeMs / 1000,
      ease: [0.4, 0, 1, 1] as Easing,
    };
    const widthAnim = animate(
      widthMv,
      plan.width,
      closing
        ? collapse
        : { type: 'spring', ...INSERT_TEXT_MOTION.widthSpring, delay: plan.widthDelayMs / 1000 },
    );
    const rightAnim = animate(
      rightEdgeMv,
      plan.rightEdge,
      closing
        ? collapse
        : {
            type: 'spring',
            ...INSERT_TEXT_MOTION.recenterSpring,
            delay: plan.recenterDelayMs / 1000,
          },
    );
    return () => {
      widthAnim.stop();
      rightAnim.stop();
    };
  }, [
    plan.width,
    plan.rightEdge,
    plan.widthDelayMs,
    plan.recenterDelayMs,
    closing,
    reduceMotion,
    rightEdgeMv,
    widthMv,
  ]);

  const overflowing = contentWidth + padX * 2 >= maxWidth - 0.5;

  return (
    <div
      className="ol-live-transcript-stage"
      data-tone={tone}
      data-controls={hasControls ? 'true' : 'false'}
      data-closing={closing ? 'true' : undefined}
      style={{ width: '100%', height, maxWidth: stageWidth }}
    >
      <motion.div
        className="ol-live-transcript-pill"
        data-tone={tone}
        data-enhanced={enhancedMotion ? 'true' : undefined}
        style={{
          left: leftMv,
          width: widthMv,
          height,
          pointerEvents: hasControls ? 'auto' : 'none',
        }}
      >
        {onCancel && (
          <button
            type="button"
            className="ol-live-transcript-control"
            style={{ width: controlSize, height: controlSize }}
            aria-label={cancelLabel}
            disabled={!cancelEnabled}
            onMouseDown={(event) => event.preventDefault()}
            onClick={onCancel}
          >
            <Icon name="close" size={Math.round(controlSize * 0.42)} strokeWidth={2.1} />
          </button>
        )}
        {onConfirm && (
          <button
            type="button"
            className="ol-live-transcript-control ol-live-transcript-confirm"
            style={{ width: controlSize, height: controlSize, marginLeft: 'auto' }}
            aria-label={confirmLabel}
            disabled={!confirmEnabled}
            onMouseDown={(event) => event.preventDefault()}
            onClick={onConfirm}
          >
            <Icon name="check" size={Math.round(controlSize * 0.44)} strokeWidth={2.1} />
          </button>
        )}
      </motion.div>
      <motion.div
        className="ol-live-transcript-track"
        data-overflow={overflowing ? 'true' : 'false'}
        style={{
          right: trackRightMv,
          width: trackWidthMv,
          fontSize,
          height,
        }}
      >
        {renderUnits.map((unit, index) => (
          <motion.span
            key={unit.key}
            className="ol-live-transcript-position"
            aria-hidden="true"
            initial={{ x: positions[index] + (unit.born ? birthAdvance : 0) }}
            animate={{ x: positions[index] }}
            transition={
              reduceMotion
                ? { duration: 0 }
                : {
                    type: 'spring',
                    ...INSERT_TEXT_MOTION.shiftSpring,
                    delay: (delays[index] ?? 0) / 1000,
                  }
            }
            style={{ width: widths[index] }}
          >
            <TranscriptGlyph
              text={unit.text}
              delay={birthDelays && unit.born ? (birthDelays[index] ?? 0) : (delays[index] ?? 0)}
              reduceMotion={Boolean(reduceMotion)}
              enhanced={enhancedMotion}
              settleFocusAt={enhancedMotion && settled ? index * settleStepMs : null}
            />
          </motion.span>
        ))}
      </motion.div>
      <span className="ol-live-transcript-sr" role="status" aria-live="polite">
        {text}
      </span>
    </div>
  );
}

/** Birth timing belongs to this keyed glyph, not to later recognition revisions. */
function TranscriptGlyph({
  text,
  delay,
  reduceMotion,
  enhanced = false,
  settleFocusAt = null,
}: {
  text: string;
  delay: number;
  reduceMotion: boolean;
  enhanced?: boolean;
  /** Siri enhanced, polishing: ms after the line has blurred when this glyph starts to focus. */
  settleFocusAt?: number | null;
}) {
  const [birthDelay] = useState(delay);
  const ink = SIRI_ENHANCED_GLYPH_MOTION;
  if (enhanced && !reduceMotion && settleFocusAt !== null) {
    // One breath: all glyphs slowly blur together, rest, focus in their left-to-right turn, stay
    // sharp until the cycle ends. Every glyph shares the cycle length, so the loop stays in step.
    const blurredAt = ink.settleBlurInMs + ink.settleBlurHoldMs;
    const cycle = blurredAt + ink.settleSweepMs + ink.settleHoldMs;
    const focusStart = blurredAt + settleFocusAt;
    const focusEnd = focusStart + ink.settleFocusMs;
    const sine = [...ink.settleEase] as Easing;
    const settle = {
      duration: cycle / 1000,
      times: [0, ink.settleBlurInMs / cycle, focusStart / cycle, focusEnd / cycle, 1],
      ease: [sine, 'linear', sine, 'linear'] as Easing[],
      repeat: Infinity,
    };
    const blurred = `blur(${ink.settleBlurPx}px)`;
    return (
      <motion.span
        className="ol-live-transcript-char"
        animate={{
          opacity: 1,
          y: 0,
          filter: ['blur(0px)', blurred, blurred, 'blur(0px)', 'blur(0px)'],
          scale: [1, ink.settleScale, ink.settleScale, 1, 1],
        }}
        transition={{
          opacity: { duration: 0.2 },
          y: { duration: 0.2 },
          filter: settle,
          scale: settle,
        }}
      >
        {text === ' ' ? ' ' : text}
      </motion.span>
    );
  }
  return (
    <motion.span
      className="ol-live-transcript-char"
      initial={
        reduceMotion
          ? false
          : {
              opacity: 0,
              y: enhanced ? ink.bornY : INSERT_TEXT_MOTION.bornY,
              filter: `blur(${enhanced ? ink.bornBlurPx : INSERT_TEXT_MOTION.bornBlurPx}px)`,
              ...(enhanced ? { scale: ink.bornScale } : {}),
            }
      }
      animate={{ opacity: 1, y: 0, filter: 'blur(0px)', ...(enhanced ? { scale: 1 } : {}) }}
      transition={
        reduceMotion
          ? { duration: 0 }
          : {
              opacity: {
                duration: (enhanced ? ink.fadeMs : INSERT_TEXT_MOTION.bornFadeMs) / 1000,
                ease: [0.22, 0.61, 0.36, 1],
                delay: birthDelay / 1000,
              },
              y: { type: 'spring', ...INSERT_TEXT_MOTION.charSpring, delay: birthDelay / 1000 },
              filter: {
                duration: (enhanced ? ink.focusMs : INSERT_TEXT_MOTION.bornFadeMs) / 1000,
                ease: enhanced ? [...ink.focusEase] : [0.22, 0.61, 0.36, 1],
                delay: birthDelay / 1000,
              },
              // Ink settles: the oversized blur contracts to the glyph as it comes into focus.
              ...(enhanced
                ? {
                    scale: {
                      duration: ink.focusMs / 1000,
                      ease: [...ink.focusEase],
                      delay: birthDelay / 1000,
                    },
                  }
                : {}),
            }
      }
    >
      {text === ' ' ? '\u00a0' : text}
    </motion.span>
  );
}
