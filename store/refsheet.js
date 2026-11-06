const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const path = require('path');
(async () => {
  const b = await chromium.launch({ executablePath:'/opt/pw-browsers/chromium', args:['--no-sandbox'] });
  const p = await b.newPage({ viewport:{width:1400,height:760}, deviceScaleFactor:2 });
  p.on('pageerror', e=>console.log('PAGEERROR '+e.message));
  await p.goto('file://' + path.resolve('refsheet.html'));
  await p.waitForFunction(() => window.__shotReady === true, null, { timeout: 8000 }).catch(()=>console.log('shot did not load'));
  await p.waitForTimeout(400);
  await p.locator('#sheet').screenshot({ path:'icons/icon-reference.png' });
  await b.close();
})();
