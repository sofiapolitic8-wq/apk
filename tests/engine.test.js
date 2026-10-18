// Run: node tests/engine.test.js   (plain Node, no dependencies)
const fs = require('fs'), path = require('path'), assert = require('assert');

function makeEnv() {
  const events = [];
  class MouseEvent { constructor(t, o) { this.type = t; Object.assign(this, o); } }
  class KeyboardEvent { constructor(t, o) { this.type = t; Object.assign(this, o); } }
  class WheelEvent { constructor(t, o) { this.type = t; Object.assign(this, o); } }
  const canvasEl = {
    width: 1000, height: 500, clientWidth: 500, clientHeight: 250, isConnected: true,
    getBoundingClientRect() { return { left: 0, top: 0, width: 500, height: 250 }; },
    dispatchEvent(e) { events.push(['canvas', e]); }
  };
  const ovCtx = { calls: 0, setTransform() {}, clearRect() {}, save() {}, restore() {}, translate() {}, rotate() {},
    beginPath() { this.calls++; }, arc() {}, moveTo() {}, lineTo() {}, closePath() {}, stroke() {}, fill() {} };
  const body = { appended: [], appendChild(n) { this.appended.push(n); n.isConnected = true; } };
  const doc = {
    hidden: false, body,
    getElementsByTagName(t) { return t === 'canvas' ? [canvasEl, ...body.appended.filter(x => x.tag === 'canvas')] : []; },
    getElementById() { return null; },
    createElement(t) { return { tag: t, style: {}, getContext() { return ovCtx; }, isConnected: false, width: 0, height: 0, clientWidth: 0, clientHeight: 0 }; },
    dispatchEvent(e) { events.push(['document', e]); },
    addEventListener() {}
  };
  const bridgeCalls = [];
  const win = {
    innerWidth: 500, innerHeight: 250, MouseEvent, KeyboardEvent, WheelEvent,
    DAVIDDBridge: { playing(p) { bridgeCalls.push(['playing', p]); }, score(s) { bridgeCalls.push(['score', s]); }, status(j) { bridgeCalls.push(['status', j]); } },
    setInterval() { return 1; }, addEventListener() {}, requestAnimationFrame() { return 1; }, cancelAnimationFrame() {},
    getComputedStyle() { return { display: 'block' }; }
  };
  return { win, doc, events, bridgeCalls, ovCtx };
}

function load(env) {
  const src = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/engine.js'), 'utf8');
  const fn = new Function('window', 'document', 'MouseEvent', 'KeyboardEvent', 'WheelEvent', src);
  fn(env.win, env.doc, env.win.MouseEvent, env.win.KeyboardEvent, env.win.WheelEvent);
  return env.win.__davidd;
}

let passed = 0;
function t(name, f) { try { f(); passed++; console.log('ok   ' + name); } catch (e) { console.log('FAIL ' + name + '\n     ' + e.message); process.exitCode = 1; } }

t('loads once and exposes the API', () => {
  const env = makeEnv(); const c = load(env);
  assert.strictEqual(c.v, 1);
  ['aim', 'boost', 'zoom', 'setConfig', 'probe'].forEach(k => assert.strictEqual(typeof c[k], 'function'));
  assert.strictEqual(load(env), c);
});
t('aim sends a mousemove a fixed distance from the canvas centre', () => {
  const env = makeEnv(); const c = load(env);
  c.aim(1, 0);
  const e = env.events.find(x => x[1].type === 'mousemove')[1];
  assert.ok(Math.abs(e.clientX - (250 + 250 * 0.36)) < 1e-6);
  assert.ok(Math.abs(e.clientY - 125) < 1e-6);
});
t('boost sends one keydown / one keyup and ignores repeats', () => {
  const env = makeEnv(); const c = load(env);
  c.boost(true); c.boost(true); c.boost(false); c.boost(false);
  assert.deepStrictEqual(env.events.filter(x => x[0] === 'document').map(x => x[1].type), ['keydown', 'keyup']);
});
t('zoom uses wheel events with the right sign', () => {
  const env = makeEnv(); const c = load(env);
  c.zoom(1); c.zoom(-1);
  assert.deepStrictEqual(env.events.filter(x => x[1].type === 'wheel').map(x => x[1].deltaY), [-120, 120]);
});
t('setConfig clamps values and ignores garbage', () => {
  const env = makeEnv(); const c = load(env);
  c.setConfig(JSON.stringify({ overlayHz: 9999, dpr: 0, ringRange: -5, targetCount: 99, radarRange: 'x', guide: 1 }));
  const g = c._t.cfg;
  assert.strictEqual(g.overlayHz, 60); assert.strictEqual(g.dpr, 1); assert.strictEqual(g.ringRange, 2);
  assert.strictEqual(g.targetCount, 6); assert.strictEqual(g.radarRange, 700); assert.strictEqual(g.guide, true);
  c.setConfig('{not json');
  assert.strictEqual(c._t.cfg.targetCount, 6);
});
t('score formula matches the classic length formula', () => {
  const c = load(makeEnv());
  assert.strictEqual(c._t.scoreFromClassic(10, 0.5, 100, 200), Math.floor(15 * (100 + 0.5 / 200 - 1) - 5));
  assert.strictEqual(c._t.scoreFromClassic(10, 0, undefined, 5), -1);
});
t('world to screen mapping', () => {
  const c = load(makeEnv());
  const w = { vx: 1000, vy: 2000, gsc: 0.5, cw: 1000, ch: 500, k: 0.5, left: 10, top: 20 };
  assert.strictEqual(c._t.sx(w, 1000), 10 + 500 * 0.5);
  assert.strictEqual(c._t.sy(w, 2000), 20 + 250 * 0.5);
  assert.strictEqual(c._t.sx(w, 1100), 10 + (500 + 100 * 0.5) * 0.5);
});
t('pickTargets returns K nearest living enemies in order', () => {
  const c = load(makeEnv());
  const me = { xx: 0, yy: 0 };
  const list = [me, { xx: 500, yy: 0 }, { xx: 100, yy: 0 }, { xx: 300, yy: 0 }, { xx: 50, yy: 0, dead: true }, null, { xx: 200, yy: 0 }];
  assert.strictEqual(c._t.pickTargets({ me, snakes: list }, 3), 3);
  assert.deepStrictEqual([0, 1, 2].map(i => c._t.tIdx[i].xx), [100, 200, 300]);
  assert.strictEqual(c._t.pickTargets({ me, snakes: [me] }, 3), 0);
});
t('largest picks the longest enemy and never the player', () => {
  const c = load(makeEnv());
  const me = { xx: 0, yy: 0, sct: 999 }, a = { xx: 1, yy: 1, sct: 40 }, b = { xx: 2, yy: 2, sct: 80 };
  assert.strictEqual(c._t.largestOf({ me, snakes: [me, a, b] }), b);
  assert.strictEqual(c._t.largestOf({ me, snakes: [me] }), null);
});
t('radar bins weight food by size and respect the range', () => {
  const c = load(makeEnv());
  const me = { xx: 0, yy: 0 };
  const foods = [{ xx: 100, yy: 1, sz: 2 }, { xx: 100, yy: 2, sz: 3 }, { xx: 0, yy: -100, sz: 1 }, { xx: 5000, yy: 0, sz: 9 }];
  assert.strictEqual(c._t.radarBins({ me, foods, nf: 4 }, 700), 5);
  assert.strictEqual(c._t.bins[0], 5); assert.strictEqual(c._t.bins[12], 1);
  assert.strictEqual(c._t.bins.reduce((s, v) => s + v, 0), 6);
});
t('edgePoint lands on the inset rectangle', () => {
  const c = load(makeEnv()); const out = [0, 0, 0];
  c._t.edgePoint(250, 125, 1000, 125, 500, 250, 20, out);
  assert.ok(Math.abs(out[0] - 480) < 1e-6 && Math.abs(out[1] - 125) < 1e-6);
  c._t.edgePoint(250, 125, 250, -500, 500, 250, 20, out);
  assert.ok(Math.abs(out[0] - 250) < 1e-6 && Math.abs(out[1] - 20) < 1e-6);
  c._t.edgePoint(250, 125, 250, 125, 500, 250, 20, out);
  assert.ok(Number.isFinite(out[0]) && Number.isFinite(out[1]));
});
t('probe reports missing data instead of throwing', () => {
  const p = load(makeEnv()).probe();
  assert.strictEqual(p.snake, false); assert.strictEqual(p.overlay, 'no-data'); assert.strictEqual(p.canvas, true);
});
t('full overlay draw runs on a classic-style world', () => {
  const env = makeEnv(); const c = load(env);
  const me = { xx: 0, yy: 0, ang: 0.5, sc: 1, sct: 10, fam: 0 };
  env.win.snake = me;
  env.win.snakes = [me, { xx: 200, yy: 50, sc: 1, sct: 30, fam: 0 }, { xx: 5000, yy: 0, sc: 1, sct: 90, fam: 0 }];
  env.win.view_xx = 0; env.win.view_yy = 0; env.win.gsc = 1;
  env.win.foods = [{ xx: 80, yy: 10, sz: 2 }, { xx: -60, yy: 40, sz: 3 }]; env.win.foods_c = 2;
  env.win.playing = true;
  assert.strictEqual(c.probe().overlay, 'ready');
  c.setConfig({ ring: true, guide: true, targets: true, largest: true, radar: true, overlayHz: 30, dpr: 1 });
  c._t.tick();
  const before = env.ovCtx.calls;
  c._t.draw();
  assert.ok(env.ovCtx.calls > before);
  assert.deepStrictEqual(env.bridgeCalls.filter(x => x[0] === 'playing').map(x => x[1]).slice(-1), [true]);
});
t('draw with a half-broken world throws nothing', () => {
  const env = makeEnv(); const c = load(env);
  const me = { xx: 0, yy: 0 };
  env.win.snake = me; env.win.snakes = [me, null, {}, { xx: 'bad' }];
  env.win.view_xx = 0; env.win.view_yy = 0; env.win.gsc = 1; env.win.foods = [null, {}, { xx: 1 }]; env.win.foods_c = 3;
  c.setConfig({ ring: true, guide: true, targets: true, largest: true, radar: true, dpr: 1 });
  c._t.draw();
});
console.log('\n' + passed + ' tests passed' + (process.exitCode ? ', SOME FAILED' : ''));
