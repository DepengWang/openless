import {
  Suspense,
  useEffect,
  useLayoutEffect,
  useRef,
  useState,
  type CSSProperties,
  type ReactNode,
  type RefObject,
} from 'react';
import { motion } from 'framer-motion';
import { useReducedMotion } from '../../lib/motion';
import { useSmoothWheelScroll } from '../../lib/useSmoothWheelScroll';
import { PageSkeleton } from './PageSkeleton';

const easing = [0.22, 1, 0.36, 1] as const;
const settled = { opacity: 1, y: 0, scale: 1, filter: 'none' };
const waiting = { opacity: 0, y: 12, scale: 0.975, filter: 'blur(8px)' };
const departing = { opacity: 0, y: -12, scale: 0.985, filter: 'blur(6px)' };

type Surface = { key: string; children: ReactNode };
interface Props {
  contentKey: string;
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
  scrollRef?: RefObject<HTMLDivElement>;
  page?: string;
  onEntered?: () => void;
}

/** Retain the live outgoing page until the next module and its first useful frame
 * are ready. Both surfaces share one fixed viewport, so navigation never changes
 * document height. New requests supersede the pending destination immediately. */
export function TransitionViewport({
  contentKey,
  children,
  className,
  style,
  scrollRef,
  page,
  onEntered,
}: Props) {
  const reduced = useReducedMotion();
  const [committed, setCommitted] = useState<Surface>({ key: contentKey, children });
  const retained = useRef(committed);
  const latest = useRef({ key: contentKey, children });
  latest.current = { key: contentKey, children };
  const pendingSince = useRef(performance.now());
  const pendingKey = useRef(contentKey);
  if (pendingKey.current !== contentKey) {
    pendingKey.current = contentKey;
    pendingSince.current = performance.now();
  }
  const [slowKey, setSlowKey] = useState<string | null>(null);
  const entered = useRef(onEntered);
  entered.current = onEntered;
  const [readyKey, setReadyKey] = useState<string | null>(null);
  const switching = committed.key !== contentKey;
  const revealing = switching && readyKey === contentKey;

  useLayoutEffect(() => {
    if (!switching || reduced) retained.current = { key: contentKey, children };
    if (reduced && switching) setCommitted({ key: contentKey, children });
  }, [switching, contentKey, children, reduced]);
  useEffect(() => {
    if (reduced) entered.current?.();
  }, [contentKey, reduced]);
  const commit = (key: string) => {
    if (latest.current.key !== key) return;
    retained.current = latest.current;
    setCommitted(latest.current);
    setReadyKey(null);
    requestAnimationFrame(() => {
      if (latest.current.key === key) entered.current?.();
    });
  };
  const surfaces =
    switching && !reduced
      ? [retained.current, { key: contentKey, children }]
      : [{ key: contentKey, children }];

  return (
    <div className="ol-transition-viewport" aria-busy={(!reduced && switching) || undefined}>
      {surfaces.map((surface) => {
        const incoming = !reduced && switching && surface.key === contentKey;
        const leaving = !reduced && revealing && !incoming;
        const phase = incoming ? (revealing ? 'enter' : 'waiting') : leaving ? 'exit' : 'settled';
        return (
          <TransitionSurface
            key={surface.key}
            surfaceKey={surface.key}
            phase={phase}
            reduced={reduced}
            slow={slowKey === contentKey}
            className={className}
            style={style}
            scrollRef={surface.key === contentKey ? scrollRef : undefined}
            page={surface.key === contentKey ? page : undefined}
            onReady={() => {
              if (latest.current.key === surface.key) {
                setSlowKey(performance.now() - pendingSince.current > 160 ? surface.key : null);
                setReadyKey(surface.key);
              }
            }}
            onEntered={() => commit(surface.key)}
          >
            {surface.children}
          </TransitionSurface>
        );
      })}
    </div>
  );
}

function TransitionSurface({
  surfaceKey,
  phase,
  reduced,
  slow,
  children,
  className,
  style,
  scrollRef,
  page,
  onReady,
  onEntered,
}: {
  surfaceKey: string;
  phase: 'settled' | 'waiting' | 'enter' | 'exit';
  reduced: boolean;
  slow: boolean;
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
  scrollRef?: RefObject<HTMLDivElement>;
  page?: string;
  onReady: () => void;
  onEntered: () => void;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const completed = useRef(onEntered);
  completed.current = onEntered;
  useSmoothWheelScroll(ref, surfaceKey);
  useLayoutEffect(() => {
    const node = ref.current;
    if (node) node.inert = phase !== 'settled';
    if (scrollRef) (scrollRef as { current: HTMLDivElement | null }).current = node;
  }, [phase, scrollRef]);
  useEffect(() => {
    if (reduced && phase === 'enter') completed.current();
  }, [phase, reduced]);
  return (
    <motion.div
      ref={ref}
      className={className}
      data-ol-page={page}
      data-transition-phase={phase}
      aria-hidden={phase !== 'settled' || undefined}
      initial={phase === 'waiting' && !reduced ? waiting : false}
      animate={phase === 'waiting' ? waiting : phase === 'exit' ? departing : settled}
      transition={
        reduced
          ? { duration: 0 }
          : {
              duration: phase === 'exit' ? (slow ? 0.22 : 0.16) : slow ? 0.26 : 0.18,
              delay: phase === 'enter' ? (slow ? 0.12 : 0.07) : 0,
              ease: easing,
            }
      }
      onAnimationComplete={() => {
        if (phase === 'enter') completed.current();
      }}
      style={{
        ...style,
        position: 'absolute',
        inset: 0,
        width: '100%',
        height: '100%',
        boxSizing: 'border-box',
        transformOrigin: '50% 30%',
        pointerEvents: phase !== 'settled' ? 'none' : undefined,
        willChange: phase === 'enter' || phase === 'exit' ? 'transform, opacity, filter' : '',
      }}
    >
      <Suspense fallback={phase === 'settled' ? <PageSkeleton /> : null}>
        {children}
        {phase === 'waiting' && <ReadyFrame container={ref} onReady={onReady} />}
      </Suspense>
    </motion.div>
  );
}

function ReadyFrame({
  container,
  onReady,
}: {
  container: RefObject<HTMLDivElement>;
  onReady: () => void;
}) {
  const callback = useRef(onReady);
  callback.current = onReady;
  useEffect(() => {
    const node = container.current;
    if (!node) return;
    let frame = 0;
    let finished = false;
    const finish = () => {
      if (finished) return;
      finished = true;
      observer.disconnect();
      window.clearTimeout(deadline);
      callback.current();
    };
    const check = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        if (
          node.childElementCount > 0 &&
          !node.querySelector('[aria-busy="true"], .ol-page-skeleton, .ol-loading-spinner')
        )
          finish();
      });
    };
    const observer = new MutationObserver(check);
    observer.observe(node, {
      childList: true,
      subtree: true,
      attributes: true,
      attributeFilter: ['aria-busy', 'class'],
    });
    // Long network operations keep their local loading UI instead of freezing navigation.
    const deadline = window.setTimeout(finish, 1200);
    frame = requestAnimationFrame(check);
    return () => {
      finished = true;
      observer.disconnect();
      clearTimeout(deadline);
      cancelAnimationFrame(frame);
    };
  }, [container]);
  return null;
}
