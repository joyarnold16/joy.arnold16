const { chromium } = require('/opt/node22/lib/node_modules/playwright');

// recordVideo captures at the viewport's CSS size and only ever scales DOWN
// to fit the size given - asking for 1080x1920 from a 540x960 viewport just
// pasted the page into the corner of a grey frame. So record at the viewport
// size and let ffmpeg do the upscale afterwards.
(async () => {
  const b = await chromium.launch({ executablePath:'/opt/pw-browsers/chromium', args:['--no-sandbox'] });
  const ctx = await b.newContext({
    viewport:{width:540,height:960}, deviceScaleFactor:1, isMobile:true, hasTouch:true,
    recordVideo:{ dir:'vid', size:{width:540,height:960} }
  });
  const p = await ctx.newPage();
  const errs=[]; p.on('pageerror',e=>errs.push(e.message));
  await p.addInitScript(() => { window.AdBridge = { isReviveAvailable:()=>true,
    requestRevive:()=>setTimeout(()=>window.blastgrid.onReviveResult(true),60), onRunEnd:()=>{} }; });

  await p.goto('http://127.0.0.1:8731/blastgrid.html');
  await p.waitForTimeout(700);
  await p.click('#ov-btn');
  await p.waitForTimeout(1100);
  for(let i=0;i<10 && await p.isVisible('#hint-close');i++){ await p.click('#hint-close'); await p.waitForTimeout(220); }
  await p.waitForTimeout(2300);

  const hold = async (k,ms)=>{ await p.keyboard.down(k); await p.waitForTimeout(ms); await p.keyboard.up(k); };
  const beat = ms => p.waitForTimeout(ms);

  // --- open: move, drop, run, detonate. Three times, so the clip has rhythm.
  for (let r=0; r<3; r++){
    await hold('ArrowDown', 420);
    await hold('ArrowRight', 380);
    await p.keyboard.press('Space');
    await beat(140);
    await hold('ArrowLeft', 460);
    await hold('ArrowUp', 320);
    await beat(160);
    await p.keyboard.press('e');          // detonate on the beat
    await beat(1200);
  }

  // --- the money shot: place, step off, lean back in, send it sliding
  await beat(700);
  await p.keyboard.press('Space');
  await beat(220);
  await hold('ArrowRight', 420);
  await beat(200);
  await p.keyboard.down('ArrowLeft');
  await beat(900);                        // charge slides away down the row
  await p.keyboard.up('ArrowLeft');
  await beat(1600);
  await p.keyboard.press('e');
  await beat(1800);

  // --- a little more play so the clip does not end on a freeze
  await hold('ArrowDown', 500);
  await hold('ArrowRight', 500);
  await p.keyboard.press('Space');
  await beat(160);
  await hold('ArrowUp', 520);
  await p.keyboard.press('e');
  await beat(2000);

  console.log(errs.length ? errs.join(' | ') : 'no page errors');
  await ctx.close();                      // flushes the video file
  await b.close();
})();
