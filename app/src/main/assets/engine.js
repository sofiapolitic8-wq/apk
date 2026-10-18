/* DAVIDD page engine. Injected into the game page by the app.
 * No work unless a feature is on; one reused overlay canvas; no allocations in the draw loop;
 * every optional feature fails alone (it switches itself off after a few errors and reports it).
 * Talks to the app through window.DAVIDDBridge (playing, score, status); the app calls
 * __davidd.aim / boost / zoom / setConfig / probe.
 */
(function () {
  'use strict';
  var W = window, D = document;
  if (W.__davidd && W.__davidd.v === 1) return;

  var TAU = Math.PI * 2;
  var MAX_FAILS = 6;

  var cfg = {
    overlayHz: 15, dpr: 1,
    guide: false, ring: false, ringRange: 8,
    targets: false, targetCount: 3,
    largest: false,
    radar: false, radarRange: 700,
    bright: 100, sat: 100,
    report: true
  };

  var fails = { overlay: 0, input: 0, score: 0 };
  var dead = { overlay: false };
  var canvas = null;
  var oc = null, ox = null, ocW = 0, ocH = 0, ocDpr = 0;
  var rafId = 0, lastFrame = 0;
  var playing = false, sentPlaying = null;
  var score = -1, sentScore = -2, sentScoreAt = 0;
  var scoreEl = null, scoreSearchAt = 0;
  var ticks = 0, tickTimer = 0;
  var boosting = false;
  var lastStatus = '';

  function bridge() { var b = W.DAVIDDBridge; return (b && typeof b === 'object') ? b : null; }

  function fail(kind) {
    fails[kind] = (fails[kind] || 0) + 1;
    if (kind === 'overlay' && fails.overlay > MAX_FAILS) { dead.overlay = true; clearOverlay(); }
  }

  function num(v, lo, hi, dflt) {
    v = +v;
    if (v !== v || v === Infinity || v === -Infinity) return dflt;
    return v < lo ? lo : (v > hi ? hi : v);
  }

  var filterKey = '', filterEl = null;
  /* Brightness / saturation as a CSS filter on the game canvas. Only touched when the value changes. */
  function applyLook() {
    var c = findCanvas();
    var key = cfg.bright + '/' + cfg.sat;
    if (c === filterEl && key === filterKey) return;
    if (filterEl && filterEl !== c && filterEl.style) filterEl.style.filter = '';
    filterEl = c; filterKey = key;
    if (!c || !c.style) return;
    c.style.filter = (cfg.bright === 100 && cfg.sat === 100) ? '' : 'brightness(' + cfg.bright + '%) saturate(' + cfg.sat + '%)';
  }

  function anyOverlay() { return cfg.guide || cfg.ring || cfg.targets || cfg.largest || cfg.radar; }

  /* ----------------------------------------------------------- canvas / input */

  function findCanvas() {
    if (canvas && canvas.isConnected && canvas !== oc) return canvas;
    var list = D.getElementsByTagName('canvas'), best = null, area = 0;
    for (var i = 0; i < list.length; i++) {
      var c = list[i];
      if (c === oc) continue;
      var a = c.clientWidth * c.clientHeight;
      if (a > area) { area = a; best = c; }
    }
    canvas = best;
    return canvas;
  }

  /* vx, vy is a direction (length <= 1). The game steers by the angle between the canvas centre and the
     pointer, so a pointer a fixed distance away in that direction is enough. */
  function aim(vx, vy) {
    try {
      var c = findCanvas() || D.body;
      if (!c) return;
      var r = c.getBoundingClientRect();
      var cx = r.left + r.width / 2, cy = r.top + r.height / 2;
      var d = Math.min(r.width, r.height) * 0.36;
      var x = cx + vx * d, y = cy + vy * d;
      c.dispatchEvent(new MouseEvent('mousemove', {
        clientX: x, clientY: y, screenX: x, screenY: y, bubbles: true, cancelable: true, view: W
      }));
    } catch (e) { fail('input'); }
  }

  function keyEvent(type, key, code, kc) {
    return new KeyboardEvent(type, { key: key, code: code, keyCode: kc, which: kc, charCode: kc, bubbles: true, cancelable: true });
  }

  function boost(on) {
    try {
      on = !!on;
      if (on === boosting) return;
      boosting = on;
      D.dispatchEvent(keyEvent(on ? 'keydown' : 'keyup', ' ', 'Space', 32));
      if (typeof W.setAcceleration === 'function') W.setAcceleration(on ? 1 : 0);
    } catch (e) { fail('input'); }
  }

  /* dir > 0 zooms in, dir < 0 zooms out (mouse wheel, which is what the game listens to). */
  function zoom(dir) {
    try {
      var c = findCanvas() || D.body;
      if (c) c.dispatchEvent(new WheelEvent('wheel', { deltaY: dir > 0 ? -120 : 120, bubbles: true, cancelable: true }));
    } catch (e) { fail('input'); }
  }

  /* ------------------------------------------------------------ game state */

  function isPlaying() {
    if (typeof W.playing === 'boolean') return W.playing;
    var lg = D.getElementById('login');
    if (lg) return W.getComputedStyle(lg).display === 'none';
    return false;
  }

  function scoreFromClassic(sct, fam, a, b) {
    if (typeof a !== 'number' || typeof b !== 'number' || !b) return -1;
    return Math.floor(15 * (a + fam / b - 1) - 5);
  }

  function findScoreEl() {
    var els = D.getElementsByTagName('div'), re = /your\s+length/i, best = null;
    for (var i = 0; i < els.length; i++) {
      var e = els[i];
      var t = e.textContent;
      if (t && t.length < 48 && re.test(t)) { best = e; }
    }
    return best;
  }

  function readScore() {
    var s = W.snake;
    if (s && typeof s === 'object' && typeof s.sct === 'number' && W.fpsls && W.fmlts) {
      var v = scoreFromClassic(s.sct, s.fam || 0, W.fpsls[s.sct], W.fmlts[s.sct]);
      if (v >= 0) return v;
    }
    if (!scoreEl || !scoreEl.isConnected) {
      scoreEl = null;
      var now = Date.now();
      if (now - scoreSearchAt > 3000) { scoreSearchAt = now; scoreEl = findScoreEl(); }
    }
    if (scoreEl) {
      var m = /length\D*([\d.,\s]+)/i.exec(scoreEl.textContent || '');
      if (m) {
        var n = parseInt(m[1].replace(/[^\d]/g, ''), 10);
        if (n === n) return n;
      }
    }
    return -1;
  }

  var wr = { me: null, snakes: null, foods: null, nf: 0, vx: 0, vy: 0, gsc: 1, cw: 0, ch: 0, k: 1, left: 0, top: 0 };

  function world() {
    var me = W.snake, list = W.snakes || W.slithers, c = findCanvas();
    if (!me || typeof me.xx !== 'number' || !list || !c || !c.width) return null;
    if (typeof W.view_xx !== 'number' || typeof W.view_yy !== 'number' || typeof W.gsc !== 'number') return null;
    var r = c.getBoundingClientRect();
    wr.me = me; wr.snakes = list; wr.foods = W.foods || null;
    wr.nf = (typeof W.foods_c === 'number') ? W.foods_c : (W.foods ? W.foods.length : 0);
    wr.vx = W.view_xx; wr.vy = W.view_yy; wr.gsc = W.gsc;
    wr.cw = c.width; wr.ch = c.height;
    wr.k = r.width / c.width;
    wr.left = r.left; wr.top = r.top;
    return wr;
  }

  function sx(w, x) { return w.left + (w.cw / 2 + (x - w.vx) * w.gsc) * w.k; }
  function sy(w, y) { return w.top + (w.ch / 2 + (y - w.vy) * w.gsc) * w.k; }

  function scaleOf(s) { return s.sc || s.N || 1; }
  function headRadius(s) { return 14.5 * scaleOf(s); }

  /* ------------------------------------------------------------- overlays */

  function ensureOverlay() {
    if (oc && oc.isConnected) return true;
    if (!D.body) return false;
    oc = D.createElement('canvas');
    oc.id = '__davidd_ov';
    oc.style.cssText = 'position:fixed;left:0;top:0;pointer-events:none;z-index:2147483000;';
    D.body.appendChild(oc);
    ox = oc.getContext('2d');
    ocW = 0; ocH = 0; ocDpr = 0;
    return !!ox;
  }

  function sizeOverlay() {
    if (!oc) return;
    var w = W.innerWidth, h = W.innerHeight, dpr = cfg.dpr;
    if (w === ocW && h === ocH && dpr === ocDpr) return;
    ocW = w; ocH = h; ocDpr = dpr;
    oc.width = Math.round(w * dpr);
    oc.height = Math.round(h * dpr);
    oc.style.width = w + 'px';
    oc.style.height = h + 'px';
    ox.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  function clearOverlay() {
    if (ox) { ox.setTransform(1, 0, 0, 1, 0, 0); ox.clearRect(0, 0, oc.width, oc.height); ox.setTransform(ocDpr || 1, 0, 0, ocDpr || 1, 0, 0); }
  }

  var edge = [0, 0, 0];

  function edgePoint(cx, cy, tx, ty, w, h, m, out) {
    var dx = tx - cx, dy = ty - cy;
    var a = Math.atan2(dy, dx);
    var hx = w / 2 - m, hy = h / 2 - m;
    var mx = w / 2, my = h / 2;
    var ax = Math.abs(dx), ay = Math.abs(dy);
    var s;
    if (ax < 1e-6 && ay < 1e-6) s = 0;
    else if (ax * hy > ay * hx) s = hx / ax;
    else s = hy / ay;
    out[0] = mx + dx * s;
    out[1] = my + dy * s;
    out[2] = a;
    return out;
  }

  var tIdx = new Array(8), tDist = new Float64Array(8), tCount = 0;

  function pickTargets(w, K) {
    var me = w.me, list = w.snakes, n = list.length;
    if (K > 8) K = 8;
    tCount = 0;
    for (var i = 0; i < n; i++) {
      var s = list[i];
      if (!s || s === me || s.dead || typeof s.xx !== 'number') continue;
      var dx = s.xx - me.xx, dy = s.yy - me.yy, d = dx * dx + dy * dy;
      if (tCount === K && d >= tDist[K - 1]) continue;
      var j = tCount < K ? tCount : K - 1;
      while (j > 0 && tDist[j - 1] > d) { tDist[j] = tDist[j - 1]; tIdx[j] = tIdx[j - 1]; j--; }
      tDist[j] = d; tIdx[j] = s;
      if (tCount < K) tCount++;
    }
    return tCount;
  }

  function lengthOf(s) { return (s.sct || 0) + (s.fam || 0) * 0.0001; }

  function largestOf(w) {
    var me = w.me, list = w.snakes, best = null, bl = -1;
    for (var i = 0; i < list.length; i++) {
      var s = list[i];
      if (!s || s === me || s.dead || typeof s.xx !== 'number') continue;
      var l = lengthOf(s);
      if (l > bl) { bl = l; best = s; }
    }
    return best;
  }

  var RB = 16;
  var bins = new Float64Array(RB);

  function radarBins(w, range) {
    for (var i = 0; i < RB; i++) bins[i] = 0;
    var f = w.foods, me = w.me, r2 = range * range, mx = 0;
    if (!f) return 0;
    for (var j = 0; j < w.nf; j++) {
      var p = f[j];
      if (!p || typeof p.xx !== 'number') continue;
      var dx = p.xx - me.xx, dy = p.yy - me.yy, d = dx * dx + dy * dy;
      if (d > r2 || d < 1) continue;
      var a = Math.atan2(dy, dx); if (a < 0) a += TAU;
      var b = ((a / TAU) * RB) | 0;
      bins[b] += (p.sz || 1);
      if (bins[b] > mx) mx = bins[b];
    }
    return mx;
  }

  function arrow(x, y, a, size, fill) {
    ox.save();
    ox.translate(x, y);
    ox.rotate(a);
    ox.fillStyle = fill;
    ox.beginPath();
    ox.moveTo(size, 0);
    ox.lineTo(-size * 0.7, size * 0.75);
    ox.lineTo(-size * 0.35, 0);
    ox.lineTo(-size * 0.7, -size * 0.75);
    ox.closePath();
    ox.fill();
    ox.restore();
  }

  function relColor(me, s) {
    var a = lengthOf(s), b = lengthOf(me);
    if (a > b * 1.15) return 'rgba(255,107,107,0.95)';
    if (a < b * 0.85) return 'rgba(140,245,165,0.95)';
    return 'rgba(255,200,87,0.95)';
  }

  function draw() {
    var w = world();
    if (!ox || !w) { clearOverlay(); return; }
    sizeOverlay();
    var vw = ocW, vh = ocH;
    ox.clearRect(0, 0, vw, vh);
    var me = w.me;
    var hx = sx(w, me.xx), hy = sy(w, me.yy);
    var wk = w.gsc * w.k;

    if (cfg.radar) {
      var mxb = radarBins(w, cfg.radarRange);
      if (mxb > 0) {
        var R = 64;
        ox.lineWidth = 5;
        ox.lineCap = 'butt';
        for (var i = 0; i < RB; i++) {
          var q = bins[i] / mxb;
          if (q < 0.18) continue;
          var a0 = (i / RB) * TAU, a1 = ((i + 1) / RB) * TAU - 0.05;
          ox.strokeStyle = 'rgba(140,245,165,' + (0.25 + 0.65 * q).toFixed(2) + ')';
          ox.beginPath();
          ox.arc(hx, hy, R + q * 10, a0, a1);
          ox.stroke();
        }
      }
    }

    if (cfg.ring) {
      var rr = cfg.ringRange * 29 * scaleOf(me) * wk;
      ox.lineWidth = 2;
      ox.strokeStyle = 'rgba(255,200,87,0.7)';
      ox.beginPath();
      ox.arc(hx, hy, rr, 0, TAU);
      ox.stroke();
      var rw = cfg.ringRange * 29 * scaleOf(me), rw2 = rw * rw;
      var list = w.snakes;
      ox.strokeStyle = 'rgba(255,107,107,0.95)';
      for (var k = 0; k < list.length; k++) {
        var s = list[k];
        if (!s || s === me || s.dead || typeof s.xx !== 'number') continue;
        var dx = s.xx - me.xx, dy = s.yy - me.yy;
        if (dx * dx + dy * dy > rw2) continue;
        ox.beginPath();
        ox.arc(sx(w, s.xx), sy(w, s.yy), headRadius(s) * wk + 5, 0, TAU);
        ox.stroke();
      }
    }

    if (cfg.guide && typeof me.ang === 'number') {
      var len = 16 * 29 * scaleOf(me) * wk;
      ox.lineWidth = 2;
      ox.strokeStyle = 'rgba(140,245,165,0.55)';
      ox.beginPath();
      ox.moveTo(hx, hy);
      ox.lineTo(hx + Math.cos(me.ang) * len, hy + Math.sin(me.ang) * len);
      ox.stroke();
    }

    if (cfg.targets) {
      var n = pickTargets(w, cfg.targetCount);
      for (var t = 0; t < n; t++) {
        var e = tIdx[t];
        var ex = sx(w, e.xx), ey = sy(w, e.yy), col = relColor(me, e);
        if (ex > 24 && ex < vw - 24 && ey > 24 && ey < vh - 24) {
          ox.lineWidth = 2;
          ox.strokeStyle = col;
          ox.beginPath();
          ox.arc(ex, ey, headRadius(e) * wk + 9, 0, TAU);
          ox.stroke();
        } else {
          edgePoint(vw / 2, vh / 2, ex, ey, vw, vh, 22, edge);
          arrow(edge[0], edge[1], edge[2], 11, col);
        }
      }
    }

    if (cfg.largest) {
      var big = largestOf(w);
      if (big) {
        var bx = sx(w, big.xx), by = sy(w, big.yy);
        if (bx > 24 && bx < vw - 24 && by > 24 && by < vh - 24) {
          ox.lineWidth = 3;
          ox.strokeStyle = 'rgba(255,214,102,0.95)';
          ox.beginPath();
          ox.moveTo(bx, by - 22); ox.lineTo(bx + 16, by); ox.lineTo(bx, by + 22); ox.lineTo(bx - 16, by);
          ox.closePath();
          ox.stroke();
        } else {
          edgePoint(vw / 2, vh / 2, bx, by, vw, vh, 30, edge);
          arrow(edge[0], edge[1], edge[2], 16, 'rgba(255,214,102,0.95)');
        }
      }
    }
  }

  function loop(ts) {
    rafId = 0;
    if (dead.overlay || !anyOverlay() || !playing || D.hidden) { clearOverlay(); return; }
    if (ts - lastFrame >= 1000 / cfg.overlayHz - 1) {
      lastFrame = ts;
      try { draw(); fails.overlay = 0; } catch (e) { fail('overlay'); }
    }
    rafId = W.requestAnimationFrame(loop);
  }

  function ensureLoop() {
    if (rafId || dead.overlay || !anyOverlay() || !playing || D.hidden) return;
    if (!ensureOverlay()) return;
    sizeOverlay();
    rafId = W.requestAnimationFrame(loop);
  }

  /* ----------------------------------------------------------- app plumbing */

  function setConfig(json) {
    try {
      var o = typeof json === 'string' ? JSON.parse(json) : json;
      if (!o || typeof o !== 'object') return;
      cfg.overlayHz = num(o.overlayHz, 5, 60, cfg.overlayHz);
      cfg.dpr = num(o.dpr, 1, 3, cfg.dpr);
      cfg.guide = !!o.guide; cfg.ring = !!o.ring; cfg.targets = !!o.targets;
      cfg.largest = !!o.largest; cfg.radar = !!o.radar; cfg.report = o.report !== false;
      cfg.ringRange = num(o.ringRange, 2, 30, cfg.ringRange);
      cfg.targetCount = Math.round(num(o.targetCount, 1, 6, cfg.targetCount));
      cfg.radarRange = num(o.radarRange, 200, 3000, cfg.radarRange);
      cfg.bright = Math.round(num(o.bright, 40, 160, cfg.bright));
      cfg.sat = Math.round(num(o.sat, 40, 160, cfg.sat));
      applyLook();
      if (anyOverlay()) { if (ensureOverlay()) sizeOverlay(); ensureLoop(); }
      else { if (rafId) { W.cancelAnimationFrame(rafId); rafId = 0; } clearOverlay(); }
    } catch (e) { /* bad config: keep the previous one */ }
  }

  function probe() {
    var c = findCanvas();
    var haveWorld = !!(W.snake && typeof W.snake.xx === 'number' && (W.snakes || W.slithers) && typeof W.view_xx === 'number' && typeof W.gsc === 'number');
    return {
      v: 1,
      canvas: !!c,
      menuEl: !!D.getElementById('login'),
      snake: !!(W.snake && typeof W.snake.xx === 'number'),
      snakes: !!(W.snakes || W.slithers),
      foods: !!W.foods,
      view: typeof W.view_xx === 'number' && typeof W.gsc === 'number',
      score: score >= 0,
      overlay: dead.overlay ? 'error' : (haveWorld ? 'ready' : 'no-data'),
      playing: playing
    };
  }

  function tick() {
    ticks++;
    var b = bridge();
    var p = false;
    try { p = isPlaying(); } catch (e) { p = false; }
    try { applyLook(); } catch (e) {}
    if (p !== playing) {
      playing = p;
      if (playing) ensureLoop(); else clearOverlay();
    }
    if (b && sentPlaying !== playing) { sentPlaying = playing; try { b.playing(playing); } catch (e) {} }

    if (playing && cfg.report && b && fails.score <= MAX_FAILS) {
      try {
        var s = readScore();
        var now = Date.now();
        if (s >= 0 && s !== sentScore && now - sentScoreAt >= 250) { sentScore = s; sentScoreAt = now; score = s; b.score(s); }
      } catch (e) { fail('score'); }
    }

    if (b && (ticks % 5 === 1)) {
      var js = JSON.stringify(probe());
      if (js !== lastStatus) { lastStatus = js; try { b.status(js); } catch (e) {} }
    }
  }

  function start() {
    if (!tickTimer) tickTimer = W.setInterval(tick, 1000);
    W.addEventListener('resize', function () { if (oc) sizeOverlay(); });
    D.addEventListener('visibilitychange', function () { if (!D.hidden) ensureLoop(); });
    tick();
  }

  W.__davidd = {
    v: 1, aim: aim, boost: boost, zoom: zoom, setConfig: setConfig, probe: probe,
    _t: { sx: sx, sy: sy, edgePoint: edgePoint, pickTargets: pickTargets, largestOf: largestOf, radarBins: radarBins,
          scoreFromClassic: scoreFromClassic, num: num, tIdx: tIdx, tDist: tDist, bins: bins, cfg: cfg,
          getCount: function () { return tCount; }, tick: tick, draw: draw }
  };
  start();
})();
