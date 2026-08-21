/*
 * End-to-end smoke test.
 *
 * Drives the real UI in Chromium: accepts the disclaimer, completes onboarding
 * as a pregnant user with high blood pressure, and checks that the safety
 * filter actually removed the poses it should have. Fails on any console error
 * or page exception along the way.
 *
 *   node tools/smoke.js            headless, exits non-zero on failure
 *   node tools/smoke.js --shots    also writes screenshots to tools/shots/
 */
'use strict';

const { chromium } = require('./playwright');
const http = require('http');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..', 'app', 'src', 'main', 'assets');
const SHOTS = path.join(__dirname, 'shots');
const wantShots = process.argv.includes('--shots');

const MIME = {
  '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript',
  '.svg': 'image/svg+xml', '.json': 'application/json'
};

function serve() {
  return new Promise((resolve) => {
    const server = http.createServer((req, res) => {
      const rel = decodeURIComponent(req.url.split('?')[0]);
      const file = path.join(ROOT, rel === '/' ? 'index.html' : rel);
      if (!file.startsWith(ROOT)) { res.writeHead(403).end(); return; }
      if (rel === '/favicon.ico') { res.writeHead(204).end(); return; }
      fs.readFile(file, (err, data) => {
        if (err) { res.writeHead(404).end('not found'); return; }
        res.writeHead(200, { 'Content-Type': MIME[path.extname(file)] || 'text/plain' });
        res.end(data);
      });
    });
    server.listen(0, '127.0.0.1', () => resolve(server));
  });
}

const problems = [];
let step = 0;

async function shot(page, name) {
  if (!wantShots) return;
  // Screens fade in over 220ms. Capturing immediately after a click catches the
  // animation mid-flight and produces a washed-out image that looks like a
  // contrast bug rather than a timing one.
  await page.waitForTimeout(320);
  fs.mkdirSync(SHOTS, { recursive: true });
  const prefix = process.env.YG_THEME === 'dark' ? 'dark-' : '';
  await page.screenshot({
    path: path.join(SHOTS, `${prefix}${String(++step).padStart(2, '0')}-${name}.png`)
  });
}

function check(cond, message) {
  if (!cond) problems.push(message);
  console.log(`  ${cond ? 'ok  ' : 'FAIL'} ${message}`);
}

(async () => {
  const server = await serve();
  const base = `http://127.0.0.1:${server.address().port}/index.html`;

  const browser = await chromium.launch(
    process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {});
  // YG_THEME=dark exercises the dark token set. The page follows the system
  // preference rather than carrying its own switch, so this is the only way to
  // see that half of the stylesheet.
  const theme = process.env.YG_THEME === 'dark' ? 'dark' : 'light';
  const page = await browser.newPage({
    viewport: { width: 412, height: 892 },
    deviceScaleFactor: 2,
    colorScheme: theme
  });

  page.on('console', (m) => {
    if (m.type() === 'error') problems.push(`console error: ${m.text()}`);
  });
  page.on('pageerror', (e) => problems.push(`page exception: ${e.message}`));

  await page.goto(base);
  await page.waitForSelector('#screen');

  /* ---------------------------------------------------------- disclaimer */

  console.log('\ndisclaimer');
  check(await page.locator('text=educational, not medical advice').count() > 0,
        'disclaimer states the app is not medical advice');
  await shot(page, 'disclaimer');
  await page.click('[data-do="accept-disclaimer"]');

  /* ---------------------------------------------------------- onboarding */

  console.log('\nonboarding');
  await page.waitForSelector('[data-do="ob-goal"]');
  check(await page.locator('[data-do="ob-goal"]').count() === 17, 'all 17 conditions offered');
  await shot(page, 'onboarding-goals');

  await page.click('[data-do="ob-goal"][data-id="pregnancy"]');
  await page.click('[data-do="ob-goal"][data-id="respiratory"]');
  check(await page.locator('[data-do="ob-goal"][data-id="pregnancy"][aria-pressed="true"]').count() === 1,
        'selecting a goal marks it');
  await page.click('[data-do="ob-next"]');

  await page.waitForSelector('[data-do="ob-level"]');
  await page.click('[data-do="ob-level"][data-id="1"]');
  await page.click('[data-do="ob-next"]');

  await page.waitForSelector('[data-do="ob-flag"]');
  check(await page.locator('[data-do="ob-flag"][data-id="pregnancy"][aria-pressed="true"]').count() === 1,
        'choosing the Pregnancy condition pre-set the pregnancy health flag');
  await page.click('[data-do="ob-flag"][data-id="hypertension"]');
  await shot(page, 'onboarding-health');
  await page.click('[data-do="ob-next"]');

  await page.waitForSelector('[data-do="ob-trimester"]');
  check(true, 'pregnancy flag inserted the trimester step');
  await page.click('[data-do="ob-trimester"][data-id="3"]');
  await page.click('[data-do="ob-next"]');

  await page.waitForSelector('[data-do="ob-length"]');
  await page.click('[data-do="ob-length"][data-id="medium"]');
  await page.click('[data-do="ob-next"]');

  await page.waitForSelector('[data-input="ob-reminder-time"]');
  await page.click('[data-do="ob-next"]');

  /* --------------------------------------------------------------- today */

  console.log('\nhome');
  await page.waitForSelector('#tabs');
  const homeText = await page.locator('#screen').innerText();
  check(/today's practice/i.test(homeText), 'home shows a recommended session');
  check(/third trimester/i.test(homeText),
        'the recommended session respects the declared trimester');
  check(/applied to every session/i.test(homeText) &&
        /prone poses, deep twists, inversions/i.test(homeText),
        'home lists the exclusions that apply to this profile');
  check(/left side rather than flat on your back/i.test(homeText),
        'third-trimester back-lying warning is shown');
  await shot(page, 'today');

  /* --------------------------------------------------- the safety filter */

  console.log('\nsafety filter');
  const filtered = await page.evaluate(() => {
    const p = YG.Store.profile();
    const cond = YG.CONDITION_BY_ID.digestion;
    const session = cond.sessions[0];             // the strong morning routine
    const built = YG.Safety.buildSession(session, p);
    return {
      flags: Object.keys(p.flags),
      trimester: p.trimester,
      poses: built.steps.map((s) => s.pose.id),
      swaps: built.adjustments.filter((a) => a.type === 'swap')
                              .map((a) => `${a.original.id}->${a.replacement.id}`),
      drops: built.adjustments.filter((a) => a.type === 'drop').map((a) => a.original.id)
    };
  });

  check(filtered.flags.includes('pregnancy') && filtered.flags.includes('hypertension'),
        'profile carries both flags');
  check(!filtered.poses.includes('kapalabhati'),
        'Kapalabhati removed (contraindicated in pregnancy and hypertension)');
  check(!filtered.poses.includes('pawanmuktasana'),
        'Wind-Relieving Pose removed in pregnancy');
  check(!filtered.poses.includes('dhanurasana'), 'Bow Pose removed in pregnancy');
  check(!filtered.poses.includes('bhujangasana'), 'Cobra removed in pregnancy');
  check(!filtered.poses.includes('ardha_matsyendrasana'), 'deep closed twist removed in pregnancy');
  check(filtered.poses.length > 0, `something safe survives (${filtered.poses.length} poses)`);
  check(filtered.swaps.length > 0, `substitutions happened: ${filtered.swaps.join(', ') || 'none'}`);

  const unsafe = await page.evaluate(() => {
    const p = YG.Store.profile();
    const bad = [];
    for (const c of YG.CONDITIONS) {
      for (const s of c.sessions) {
        for (const step of YG.Safety.buildSession(s, p).steps) {
          if (YG.Safety.isBlocked(step.pose, p.flags)) bad.push(`${s.id}:${step.pose.id}`);
        }
      }
    }
    return bad;
  });
  check(unsafe.length === 0,
        `no contraindicated pose survives any of the 40 sessions${unsafe.length ? ': ' + unsafe.join(', ') : ''}`);

  const advanced = await page.evaluate(() => {
    const p = YG.Store.profile();
    const bad = [];
    for (const c of YG.CONDITIONS) {
      for (const s of c.sessions) {
        for (const step of YG.Safety.buildSession(s, p).steps) {
          if (step.pose.level > 2) bad.push(`${s.id}:${step.pose.id}`);
        }
      }
    }
    return bad;
  });
  check(advanced.length === 0,
        `no advanced pose reaches a beginner${advanced.length ? ': ' + advanced.join(', ') : ''}`);

  // Substitution funnels many poses onto a few safe ones, so a filtered session
  // can easily end up as "Cat-Cow, Cat-Cow, Cat-Cow". Checked across several
  // profiles because the collisions differ per flag combination.
  const repeats = await page.evaluate(() => {
    const profiles = [
      { flags: { pregnancy: true, hypertension: true }, level: 1, trimester: 3, length: 'medium' },
      { flags: { pregnancy: true }, level: 2, trimester: 2, length: 'long' },
      { flags: { disc: true, knee: true, neck: true }, level: 1, length: 'short' },
      { flags: { hypertension: true, glaucoma: true, heart: true }, level: 2, length: 'medium' },
      { flags: {}, level: 3, length: 'medium' }
    ];
    const bad = [];
    for (const p of profiles) {
      for (const c of YG.CONDITIONS) {
        for (const s of c.sessions) {
          const steps = YG.Safety.buildSession(s, p).steps;
          for (let i = 1; i < steps.length; i++) {
            if (steps[i].pose === steps[i - 1].pose) {
              bad.push(`${s.id}:${steps[i].pose.id}@${i}`);
            }
          }
        }
      }
    }
    return bad;
  });
  check(repeats.length === 0,
        `no session repeats the same pose back to back${repeats.length ? ': ' + repeats.slice(0, 6).join(', ') : ''}`);

  // Merging adds time to an existing step; it must not run away. Relaxation
  // gets a looser bound - a 12-minute Yoga Nidra on the "long" setting is the
  // intended practice, not a runaway merge.
  const overlong = await page.evaluate(() => {
    const p = { flags: { pregnancy: true, hypertension: true }, level: 1, trimester: 3, length: 'long' };
    const bad = [];
    for (const c of YG.CONDITIONS) {
      for (const s of c.sessions) {
        for (const step of YG.Safety.buildSession(s, p).steps) {
          const ceiling = step.pose.type === 'relaxation' ? 700 : 300;
          if (step.sec > ceiling) bad.push(`${s.id}:${step.pose.id}=${step.sec}s`);
        }
      }
    }
    return bad;
  });

  // Merging must only ever lengthen a step. A cap derived from the wrong
  // baseline would silently trim an authored duration.
  const shortened = await page.evaluate(() => {
    const bare = { flags: {}, level: 3, length: 'medium' };
    const restricted = { flags: { pregnancy: true, hypertension: true }, level: 1, trimester: 3, length: 'medium' };
    const bad = [];
    for (const c of YG.CONDITIONS) {
      for (const s of c.sessions) {
        const built = YG.Safety.buildSession(s, restricted);
        for (const step of built.steps) {
          // Find what this pose was authored at, where it appears untouched.
          const authored = s.steps.filter((x) => x.p === step.pose.id).map((x) => x.sec);
          if (!step.from && authored.length && step.sec < Math.min(...authored)) {
            bad.push(`${s.id}:${step.pose.id} ${step.sec}s < authored ${Math.min(...authored)}s`);
          }
        }
      }
    }
    return bad;
  });
  check(shortened.length === 0,
        `merging never shortens a step below its authored length${shortened.length ? ': ' + shortened.slice(0, 4).join(', ') : ''}`);
  check(overlong.length === 0,
        `merging never produces an absurd hold${overlong.length ? ': ' + overlong.join(', ') : ''}`);

  /* --------------------------------------------------------- navigation */

  console.log('\nnavigation');
  await page.click('#tabs button[data-tab="conditions"]');
  await page.waitForSelector('[data-go="condition"]');
  await shot(page, 'conditions');

  await page.click('[data-go="condition"][data-id="pregnancy"]');
  await page.waitForSelector('[data-go="session"]');
  check(await page.locator('#screen').innerText().then((t) => /third trimester/i.test(t)),
        'pregnancy detail lists the trimester sessions');
  await shot(page, 'condition-pregnancy');

  await page.click('[data-go="session"] >> nth=2');
  await page.waitForSelector('[data-do="start"]');
  check(await page.locator('#screen').innerText().then((t) => /\d+ poses/i.test(t)),
        'session preview renders its pose list');
  await shot(page, 'session-preview');

  /* ------------------------------------------------------------- player */

  console.log('\nplayer');
  await page.click('[data-do="start"]');
  await page.waitForSelector('.scale');            // before-rating sheet
  await page.click('[data-r="3"]');
  await page.waitForSelector('.player', { timeout: 5000 });
  check(await page.locator('.player-name').innerText().then((t) => t.length > 0),
        'player opened on a named pose');
  check(await page.locator('.ring').count() === 1, 'countdown ring is showing');
  await shot(page, 'player');

  await page.click('[data-act="next"]');
  await page.waitForTimeout(300);
  check(await page.locator('.player').count() === 1, 'skipping forward keeps the player up');

  // Skip to a breathing step and confirm the pacer replaces the ring.
  for (let i = 0; i < 30 && await page.locator('.orb').count() === 0; i++) {
    await page.click('[data-act="next"]');
    await page.waitForTimeout(60);
  }
  check(await page.locator('.orb').count() === 1, 'breath pacer appears for a pranayama step');
  check(await page.locator('.ring').count() === 0, 'the ring is removed while the pacer is up');
  await shot(page, 'player-breath');

  await page.click('[data-act="close"]');
  await page.waitForSelector('.sheet');
  await page.click('[data-x="ok"]');
  await page.waitForTimeout(400);
  check(await page.locator('.player').count() === 0, 'ending the session closes the player');

  /* ------------------------------------------------------ library, rest */

  console.log('\nlibrary and progress');
  await page.click('[data-go="back"]');
  await page.waitForTimeout(150);
  await page.click('[data-go="back"]');
  await page.waitForSelector('#tabs button[data-tab="poses"]:visible');
  check(true, 'back out of a session preview returns to a tab screen');
  await page.click('#tabs button[data-tab="poses"]');
  await page.waitForSelector('[data-input="lib-search"]');
  check(await page.locator('[data-go="pose"]').count() === 72, 'all 72 poses listed');
  check(await page.locator('text=Not for you').count() > 0,
        'poses ruled out by the profile are marked in the library');
  await shot(page, 'library');

  await page.fill('[data-input="lib-search"]', 'cobra');
  await page.waitForTimeout(120);
  const visible = await page.locator('[data-go="pose"]:visible').count();
  check(visible >= 1 && visible <= 3, `search narrows the list (${visible} matches for "cobra")`);

  await page.click('[data-go="pose"]:visible >> nth=0');
  await page.waitForSelector('.steps');
  check(await page.locator('text=Not recommended for you').count() === 1,
        'a contraindicated pose says so on its own page');
  await shot(page, 'pose-detail');

  await page.click('[data-go="back"]');
  await page.waitForSelector('#tabs button[data-tab="progress"]:visible');
  await page.click('#tabs button[data-tab="progress"]');
  await page.waitForSelector('.stats');
  check(await page.locator('.heat i').count() >= 28, 'the heatmap rendered');
  await shot(page, 'progress');

  await page.click('[data-go="settings"]');
  await page.waitForSelector('[data-do="reset"]');
  await shot(page, 'settings');

  /* -------------------------------------------------- every screen, cold */

  console.log('\nrendering every pose and condition page');
  const renderErrors = await page.evaluate(() => {
    const bad = [];
    for (const p of YG.POSES) {
      try {
        const html = YG.UI._screens.pose({ id: p.id });
        if (!html || html.length < 200) bad.push(`pose ${p.id}: suspiciously short`);
      } catch (e) { bad.push(`pose ${p.id}: ${e.message}`); }
    }
    for (const c of YG.CONDITIONS) {
      try {
        YG.UI._screens.condition({ id: c.id });
        for (const s of c.sessions) YG.UI._screens.session({ id: c.id, sub: s.id });
      } catch (e) { bad.push(`condition ${c.id}: ${e.message}`); }
    }
    return bad;
  });
  check(renderErrors.length === 0,
        `all 72 pose pages and 40 session pages render${renderErrors.length ? ': ' + renderErrors.slice(0, 5).join('; ') : ''}`);

  /* ----------------------------------------------------- streak counting */

  const streak = await page.evaluate(() => {
    YG.Store.clearHistory();
    const day = 86400000;
    const now = Date.now();
    YG.Store.history().push(
      { ts: now - 2 * day, s: 'x', c: 'back', sec: 600, done: true, b: null, a: null },
      { ts: now - 1 * day, s: 'x', c: 'back', sec: 600, done: true, b: null, a: null },
      { ts: now, s: 'x', c: 'back', sec: 600, done: true, b: null, a: null }
    );
    const three = YG.Store.streak();
    YG.Store.clearHistory();
    YG.Store.history().push(
      { ts: now - 5 * day, s: 'x', c: 'back', sec: 600, done: true, b: null, a: null },
      { ts: now, s: 'x', c: 'back', sec: 600, done: true, b: null, a: null }
    );
    const broken = YG.Store.streak();
    YG.Store.clearHistory();
    return { three, broken };
  });
  check(streak.three === 3, `three consecutive days counts as a streak of 3 (got ${streak.three})`);
  check(streak.broken === 1, `a five-day gap resets the streak to 1 (got ${streak.broken})`);

  await browser.close();
  server.close();

  console.log('');
  if (problems.length) {
    console.log(`${problems.length} problem(s):`);
    for (const p of problems) console.log(`  - ${p}`);
    process.exit(1);
  }
  console.log('all checks passed');
})().catch((e) => {
  console.error('smoke test crashed:', e);
  process.exit(1);
});
