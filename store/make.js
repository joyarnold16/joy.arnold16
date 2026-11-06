const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const path = require('path');

// Play accepts 16:9 or 9:16 only - not 'portrait-ish'. Every size here is
// exactly 9:16, and the 10-inch one also clears its 1080px minimum side.
const SIZES = { phone:[1080,1920], tablet7:[1080,1920], tablet10:[1440,2560] };

// Ordered: Play shows the first few far more than the rest, so the blast and
// the shove - the thing no other game in this genre does - lead.
const SHEETS = [
  { src:'03-blast', head:'Every blast is<br>a <em>cross</em>',
    sub:'Learn the shape. Use it. Survive it.' },
  { src:'05-shove', head:'Shove a <em>live</em><br>charge',
    sub:'Push it into a drone. The kill scores double.' },
  { src:'01-fresh', head:'Bring the site<br><em>down</em>',
    sub:'Break the grid, scrap the patrol, find the lift.' },
  { src:'04-smoke', head:'Pallets <em>catch</em>',
    sub:'Fire spreads. Smoke chokes. Keep moving.' },
  { src:'02-walk',  head:'One more<br><em>sector</em>',
    sub:'Pick a perk, keep your run, go again.' },
];

(async () => {
  const kind = process.argv[2] || 'phone';
  const [W,H] = SIZES[kind];
  const b = await chromium.launch({ executablePath:'/opt/pw-browsers/chromium', args:['--no-sandbox'] });
  const p = await b.newPage({ viewport:{width:W,height:H}, deviceScaleFactor:1 });
  p.on('pageerror', e=>console.log('PAGEERROR '+e.message));
  await p.goto('file://' + path.resolve('compose.html'));
  let n=0;
  for (const s of SHEETS){
    n++;
    const img = 'file://' + path.resolve('raw/' + kind + '-' + s.src + '.png');
    await p.evaluate(o => window.build(o), { ...s, img, W, H });
    await p.waitForTimeout(260);
    const file = `out/${kind}-${String(n).padStart(2,'0')}.png`;
    await p.screenshot({ path:file });
    console.log('  ' + file);
  }
  await b.close();
})();
