const { chromium } = require('/opt/node22/lib/node_modules/playwright');

// width/height are CSS pixels; deviceScaleFactor 2 doubles them into the
// real store asset size, so 540x960 -> 1080x1920.
const SIZES = {
  phone:    { w: 540,  h: 960  },   // -> 1080x1920
  tablet7:  { w: 600,  h: 960  },   // -> 1200x1920
  tablet10: { w: 800,  h: 1280 },   // -> 1600x2560
};
const kind = process.argv[2] || 'phone';
const { w, h } = SIZES[kind];

(async () => {
  const b = await chromium.launch({ executablePath:'/opt/pw-browsers/chromium', args:['--no-sandbox'] });
  const p = await b.newPage({ viewport:{width:w,height:h}, deviceScaleFactor:2, isMobile:true, hasTouch:true });
  const errs=[]; p.on('pageerror',e=>errs.push(e.message));
  // a loaded rewarded ad, so the revive screen can be shown if wanted
  await p.addInitScript(() => { window.AdBridge = { isReviveAvailable:()=>true,
    requestRevive:()=>setTimeout(()=>window.blastgrid.onReviveResult(true),60), onRunEnd:()=>{} }; });

  await p.goto('http://127.0.0.1:8731/blastgrid.html');
  await p.waitForTimeout(900);
  const shot = n => p.screenshot({ path:`raw/${kind}-${n}.png` });

  await shot('00-title');

  await p.click('#ov-btn');
  await p.waitForTimeout(1200);
  for(let i=0;i<10 && await p.isVisible('#hint-close');i++){ await p.click('#hint-close'); await p.waitForTimeout(250); }
  await p.waitForTimeout(2400);
  await shot('01-fresh');

  const hold = async (k,ms)=>{ await p.keyboard.down(k); await p.waitForTimeout(ms); await p.keyboard.up(k); };

  // Walk out into the board so the frames are not all spawn corner.
  await hold('ArrowDown', 900);
  await hold('ArrowRight', 700);
  await p.waitForTimeout(300);
  await shot('02-walk');

  // A detonation, caught while the fire is still up.
  await p.keyboard.press('Space');
  await p.waitForTimeout(150);
  await hold('ArrowLeft', 420);
  await p.waitForTimeout(120);
  await p.keyboard.press('e');
  await p.waitForTimeout(140);
  await shot('03-blast');
  await p.waitForTimeout(1400);
  await shot('04-smoke');

  // The shove: drop a charge, step off it, then walk back into it. The push
  // needs KICK_HOLD (0.22s) of contact before the charge starts sliding.
  await p.waitForTimeout(2200);
  await p.keyboard.press('Space');
  await p.waitForTimeout(200);
  await hold('ArrowRight', 420);         // step clear of it
  await p.waitForTimeout(150);
  await p.keyboard.down('ArrowLeft');    // lean back into it -> shove
  await p.waitForTimeout(520);
  await shot('05-shove');
  await p.keyboard.up('ArrowLeft');
  await p.waitForTimeout(260);
  await shot('06-shove2');

  console.log(kind + ': ' + (errs.length ? errs.join(' | ') : 'no page errors'));
  await b.close();
})();
