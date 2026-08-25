/*
 * Content integrity check for the pose and condition data.
 *
 * The data files are large and hand-written, and every kind of error they can
 * contain is silent at runtime: a session referring to a pose id that does not
 * exist just renders a gap, a contraindication naming a flag that is not in
 * YG.FLAGS never fires, and a pose pointing at a missing figure quietly draws
 * the wrong body. None of that throws. This does.
 *
 *   node tools/validate-content.js
 *
 * Run in CI on every push - see .github/workflows/build.yml.
 */
'use strict';

const fs = require('fs');
const path = require('path');
const vm = require('vm');

const ASSETS = path.join(__dirname, '..', 'app', 'src', 'main', 'assets', 'js');
const FILES = ['figures.js', 'poses.js', 'conditions.js'];

const sandbox = { window: {} };
vm.createContext(sandbox);
for (const f of FILES) {
  vm.runInContext(fs.readFileSync(path.join(ASSETS, f), 'utf8'), sandbox, { filename: f });
}

const YG = sandbox.window.YG;
const errors = [];
const warnings = [];

const err = (m) => errors.push(m);
const warn = (m) => warnings.push(m);

const flagIds = new Set(YG.FLAGS.map((f) => f.id));
const figureIds = new Set(Object.keys(YG.FIGURES));
const conditionIds = new Set(YG.CONDITIONS.map((c) => c.id));
const poseIds = new Set();

/* ---- poses ---- */

const REQUIRED_POSE_FIELDS = ['id', 'name', 'type', 'fig', 'level', 'hold', 'steps',
                              'benefits', 'helps', 'avoid', 'mistakes'];
const TYPES = new Set(['asana', 'pranayama', 'relaxation', 'mobility', 'kriya']);

for (const p of YG.POSES) {
  const at = `pose "${p.id || '(no id)'}"`;

  if (poseIds.has(p.id)) err(`${at}: duplicate id`);
  poseIds.add(p.id);

  for (const field of REQUIRED_POSE_FIELDS) {
    if (p[field] === undefined || p[field] === null) err(`${at}: missing "${field}"`);
  }
  if (!TYPES.has(p.type)) err(`${at}: unknown type "${p.type}"`);
  if (!figureIds.has(p.fig)) err(`${at}: figure "${p.fig}" is not in FIGURES`);
  if (p.fig2 && !figureIds.has(p.fig2)) err(`${at}: figure2 "${p.fig2}" is not in FIGURES`);
  if (!(p.level >= 1 && p.level <= 3)) err(`${at}: level must be 1-3, got ${p.level}`);
  if (!(p.hold > 0)) err(`${at}: hold must be positive, got ${p.hold}`);

  for (const flag of p.avoid || []) {
    if (!flagIds.has(flag)) err(`${at}: avoid "${flag}" is not a health flag`);
  }
  for (const c of p.helps || []) {
    if (!conditionIds.has(c)) err(`${at}: helps "${c}" is not a condition`);
  }
  if (!p.steps.length) err(`${at}: no steps`);
  if (!p.benefits.length) err(`${at}: no benefits`);

  // A pose the filter can remove needs somewhere to send the user. Without an
  // alt the sequence just gets shorter, which is acceptable for a warm-up but
  // not for the pose a whole session was built around.
  if ((p.avoid || []).length && !p.alt && p.type === 'asana') {
    warn(`${at}: has contraindications but no alt substitute`);
  }
  // Every flag that can silently drop a pose should have prose explaining why.
  if ((p.avoid || []).length && !(p.cautions || []).length) {
    warn(`${at}: has avoid flags but no cautions text`);
  }
}

/* ---- alt substitutions ---- */

for (const p of YG.POSES) {
  if (!p.alt) continue;
  const at = `pose "${p.id}"`;
  if (!poseIds.has(p.alt)) {
    err(`${at}: alt "${p.alt}" does not exist`);
    continue;
  }
  if (p.alt === p.id) err(`${at}: alt points at itself`);

  const alt = YG.POSE_BY_ID[p.alt];
  // Partial overlap is fine - the filter walks the alt chain, so a substitute
  // that is safe for some of the original's restrictions still earns its place.
  // Sharing *every* restriction does not: that substitute can never be reached,
  // because whatever removed the original removes the replacement too.
  const avoid = p.avoid || [];
  const shared = avoid.filter((f) => (alt.avoid || []).includes(f));
  if (avoid.length && shared.length === avoid.length) {
    err(`${at}: alt "${p.alt}" shares every contraindication (${shared.join(', ')}), ` +
        `so it can never be substituted in`);
  }

  // Walk the alt chain to make sure it terminates.
  const seen = new Set([p.id]);
  let cur = alt;
  while (cur && cur.alt) {
    if (seen.has(cur.id)) { err(`${at}: alt chain loops at "${cur.id}"`); break; }
    seen.add(cur.id);
    cur = YG.POSE_BY_ID[cur.alt];
  }
}

/* ---- conditions and sessions ---- */

const sessionIds = new Set();

for (const c of YG.CONDITIONS) {
  const at = `condition "${c.id}"`;
  for (const field of ['name', 'hero', 'tagline', 'about', 'why', 'safety', 'sessions']) {
    if (!c[field]) err(`${at}: missing "${field}"`);
  }
  if (!poseIds.has(c.hero)) err(`${at}: hero "${c.hero}" is not a pose`);
  for (const flag of c.autoFlags || []) {
    if (!flagIds.has(flag)) err(`${at}: autoFlag "${flag}" is not a health flag`);
  }
  if (!c.sessions.length) err(`${at}: no sessions`);

  for (const s of c.sessions) {
    const sat = `${at} session "${s.id}"`;
    if (sessionIds.has(s.id)) err(`${sat}: duplicate session id`);
    sessionIds.add(s.id);
    if (!s.name) err(`${sat}: missing name`);
    if (!s.steps || !s.steps.length) { err(`${sat}: no steps`); continue; }

    let total = 0;
    for (const st of s.steps) {
      if (!poseIds.has(st.p)) { err(`${sat}: unknown pose "${st.p}"`); continue; }
      if (!(st.sec > 0)) err(`${sat}: step "${st.p}" has non-positive sec`);
      total += st.sec;
    }
    if (total < 120) warn(`${sat}: only ${total}s long`);
    if (total > 2400) warn(`${sat}: ${Math.round(total / 60)} minutes may be too long`);

    // A condition's own sessions should mostly be poses tagged for it.
    const tagged = s.steps.filter((st) => {
      const p = YG.POSE_BY_ID[st.p];
      return p && (p.helps || []).includes(c.id);
    }).length;
    if (tagged / s.steps.length < 0.25) {
      warn(`${sat}: only ${tagged}/${s.steps.length} poses are tagged helps:${c.id}`);
    }
  }
}

/* ---- reverse coverage: every condition needs safe poses to fall back on ---- */

for (const c of YG.CONDITIONS) {
  const auto = c.autoFlags || [];
  if (!auto.length) continue;
  const safe = YG.POSES.filter((p) =>
    (p.helps || []).includes(c.id) && !(p.avoid || []).some((f) => auto.includes(f)));
  if (safe.length < 5) {
    err(`condition "${c.id}": only ${safe.length} poses are both tagged for it and ` +
        `safe under its own autoFlags ${auto.join(', ')}`);
  }
}

/* ---- figures ---- */

const usedFigures = new Set();
for (const p of YG.POSES) {
  usedFigures.add(p.fig);
  if (p.fig2) usedFigures.add(p.fig2);
}
for (const name of figureIds) {
  if (!usedFigures.has(name)) warn(`figure "${name}" is never used by a pose`);
}
for (const [name, fig] of Object.entries(YG.FIGURES)) {
  const chains = [].concat(fig.b || [], fig.f || []);
  if (!chains.length) err(`figure "${name}": no strokes`);
  for (const ch of chains) {
    const pts = ch.p || ch;
    if (!Array.isArray(pts) || pts.length < 2) {
      err(`figure "${name}": a chain has fewer than 2 points`);
      continue;
    }
    for (const pt of pts) {
      if (!Array.isArray(pt) || pt.length !== 2 || pt.some((n) => typeof n !== 'number')) {
        err(`figure "${name}": malformed point ${JSON.stringify(pt)}`);
      } else if (pt[0] < -5 || pt[0] > 125 || pt[1] < -5 || pt[1] > 105) {
        warn(`figure "${name}": point ${JSON.stringify(pt)} is outside the 120x100 frame`);
      }
    }
  }
}

/* ---- android xml ---- */

/*
 * A double hyphen inside an XML comment is illegal, and aapt rejects the whole
 * resource merge over it. Naming a CSS custom property in a comment - "matches
 * --bg in app.css" - is the natural way to write that sentence and the natural
 * way to break the build. Catching it here costs a millisecond; catching it in
 * Gradle costs a twenty-second build and a CI round trip.
 */
const XML_ROOTS = [
  path.join(__dirname, '..', 'app', 'src', 'main', 'res'),
  path.join(__dirname, '..', 'app', 'src', 'main')
];

function xmlFiles(dir, out = []) {
  let entries;
  try { entries = fs.readdirSync(dir, { withFileTypes: true }); } catch (e) { return out; }
  for (const e of entries) {
    const full = path.join(dir, e.name);
    if (e.isDirectory()) xmlFiles(full, out);
    else if (e.name.endsWith('.xml')) out.push(full);
  }
  return out;
}

const seenXml = new Set();
for (const root of XML_ROOTS) {
  for (const file of xmlFiles(root)) {
    if (seenXml.has(file)) continue;
    seenXml.add(file);
    const text = fs.readFileSync(file, 'utf8');
    const rel = path.relative(path.join(__dirname, '..'), file);
    const re = /<!--([\s\S]*?)-->/g;
    let m;
    while ((m = re.exec(text)) !== null) {
      if (m[1].includes('--')) {
        const line = text.slice(0, m.index).split('\n').length;
        err(`${rel}:${line}: "--" inside an XML comment - aapt rejects this`);
      }
      if (m[1].endsWith('-')) {
        const line = text.slice(0, m.index).split('\n').length;
        err(`${rel}:${line}: XML comment ends with "-", which forms "--->"`);
      }
    }
  }
}

/* ---- report ---- */

console.log(`poses: ${YG.POSES.length}   figures: ${figureIds.size}   ` +
            `conditions: ${YG.CONDITIONS.length}   sessions: ${sessionIds.size}`);

for (const w of warnings) console.log(`  warn  ${w}`);
for (const e of errors) console.log(`  ERROR ${e}`);

if (errors.length) {
  console.log(`\n${errors.length} error(s), ${warnings.length} warning(s)`);
  process.exit(1);
}
console.log(`\nOK - ${warnings.length} warning(s)`);
