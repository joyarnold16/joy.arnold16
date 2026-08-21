/*
 * Renders every figure in js/figures.js to one contact sheet PNG.
 *
 * The illustrations are authored as raw joint coordinates, which is efficient
 * to write and impossible to proof-read. This is how you check them: one page,
 * every pose, labelled.
 *
 *   node tools/figure-sheet.js          -> tools/shots/figures.png
 */
'use strict';

const { chromium } = require('./playwright');
const fs = require('fs');
const path = require('path');

const ASSETS = path.join(__dirname, '..', 'app', 'src', 'main', 'assets');
const OUT = path.join(__dirname, 'shots');

(async () => {
  const browser = await chromium.launch(
    process.env.CHROME_PATH ? { executablePath: process.env.CHROME_PATH } : {});
  const page = await browser.newPage({ viewport: { width: 2000, height: 900 }, deviceScaleFactor: 2 });

  const css = fs.readFileSync(path.join(ASSETS, 'css', 'app.css'), 'utf8');
  const figure = fs.readFileSync(path.join(ASSETS, 'js', 'figure.js'), 'utf8');
  const figures = fs.readFileSync(path.join(ASSETS, 'js', 'figures.js'), 'utf8');
  const poses = fs.readFileSync(path.join(ASSETS, 'js', 'poses.js'), 'utf8');

  await page.setContent(
    `<!doctype html><meta charset="utf-8"><style>${css}
     body{overflow:auto;padding:20px}
     /* minmax(0,1fr): a bare 1fr floors at min-content, and an inline SVG's
        min-content is its intrinsic size - which blows some columns out and
        makes the sheet useless for comparing figures against each other. */
     .grid{display:grid;grid-template-columns:repeat(8,minmax(0,1fr));gap:14px}
     .cell{background:var(--surface);border:1px solid var(--line);border-radius:12px;padding:6px}
     .cell .figbox{aspect-ratio:6/5}
     .cap{font-size:10px;text-align:center;color:var(--muted);margin-top:4px;
          overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
     .cap b{color:var(--ink);font-weight:600}
     </style><body><div class="grid" id="g"></div>
     <script>${figure}<\/script><script>${figures}<\/script><script>${poses}<\/script>
     <script>
       var byFig = {};
       YG.POSES.forEach(function(p){
         (byFig[p.fig] = byFig[p.fig] || []).push(p.name);
         if (p.fig2) (byFig[p.fig2] = byFig[p.fig2] || []).push(p.name + ' (2)');
       });
       document.getElementById('g').innerHTML = Object.keys(YG.FIGURES).map(function(k){
         return '<div class="cell"><div class="figbox">' + YG.figureFor(k) +
           '</div><div class="cap"><b>' + k + '</b></div><div class="cap">' +
           (byFig[k] ? byFig[k].join(', ') : '(unused)') + '</div></div>';
       }).join('');
     <\/script>`);

  await page.waitForTimeout(300);
  fs.mkdirSync(OUT, { recursive: true });
  await page.screenshot({ path: path.join(OUT, 'figures.png'), fullPage: true });
  console.log('wrote', path.join(OUT, 'figures.png'));
  await browser.close();
})();
