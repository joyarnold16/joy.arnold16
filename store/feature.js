const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const path = require('path');
(async () => {
  const b = await chromium.launch({ executablePath:'/opt/pw-browsers/chromium', args:['--no-sandbox'] });
  // deviceScaleFactor 1: Play wants exactly 1024x500, not a 2x version of it
  const p = await b.newPage({ viewport:{width:1024,height:500}, deviceScaleFactor:1 });
  p.on('pageerror', e=>console.log('PAGEERROR '+e.message));
  await p.goto('file://' + path.resolve('feature.html'));
  await p.waitForTimeout(500);
  await p.locator('#f').screenshot({ path:'out-feature/feature-graphic.png' });
  await b.close();
})();
