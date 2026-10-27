import { TYPEWRITER, commonPrefixLength, nextTypewriterFrame } from './typewriterText';

function assert(condition: boolean, message: string): void {
  if (!condition) throw new Error(message);
}

function drain(shown: string, target: string): string[] {
  const frames: string[] = [];
  let current = shown;
  for (let guard = 0; guard < 500; guard += 1) {
    const frame = nextTypewriterFrame(current, target);
    frames.push(frame.shown);
    current = frame.shown;
    if (frame.delayMs === null) return frames;
  }
  throw new Error('typewriter never caught up');
}

assert(commonPrefixLength(['a', 'b'], ['a', 'c']) === 1, 'prefix stops at first difference');

const typed = drain('', '你好世界');
assert(
  typed.join('|') === '你|你好|你好世|你好世界',
  `types one character per frame, got ${typed.join('|')}`,
);

const caughtUp = nextTypewriterFrame('你好', '你好');
assert(caughtUp.shown === '你好' && caughtUp.delayMs === null, 'idle once caught up');

const revised = nextTypewriterFrame('今天天汽', '今天天气很好');
assert(revised.shown === '今天天气', `rolls back a revised tail first, got ${revised.shown}`);

const emoji = drain('', 'a😀b');
assert(emoji.join('|') === 'a|a😀|a😀b', 'never splits a surrogate pair');

const long = 'x'.repeat(100);
const first = nextTypewriterFrame('', long);
assert(
  Array.from(first.shown).length === 100 - TYPEWRITER.maxLagChars + 1,
  'jumps ahead so the lag stays bounded',
);

const burst = nextTypewriterFrame('', 'x'.repeat(30));
assert(burst.delayMs === TYPEWRITER.minStepMs, 'large backlog types at the fastest pace');
const trickle = nextTypewriterFrame('', 'xy');
assert(trickle.delayMs === TYPEWRITER.maxStepMs, 'small backlog types at the slowest pace');

const cleared = nextTypewriterFrame('你好', '');
assert(cleared.shown === '' && cleared.delayMs === null, 'clearing the target clears at once');

console.log('typewriterText.test.ts: ok');
