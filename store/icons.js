const { chromium } = require('/opt/node22/lib/node_modules/playwright');
const path = require('path');
(async () => {
  const b = await chromium.launch({ executablePath:'/opt/pw-browsers/chromium', args:['--no-sandbox'] });
  const p = await b.newPage({ viewport:{width:2048,height:512}, deviceScaleFactor:1 });
  p.on('pageerror', e=>console.log('PAGEERROR '+e.message));
  await p.goto('file://' + path.resolve('icons.html'));
  await p.waitForTimeout(500);
  for (const k of ['A','B','C','D'])
    await p.locator('#icon'+k).screenshot({ path:`icons/icon-${k}.png` });
  // A contact sheet: each option big, and again at 48px, which is the size
  // that actually decides whether an icon works on a phone.
  await p.evaluate(() => {
    document.body.innerHTML=''; document.body.style.cssText='margin:0;background:#17161a';
    const wrap=document.createElement('div');
    wrap.style.cssText='display:flex;gap:28px;padding:30px;align-items:flex-start';
    for(const k of ['A','B','C','D']){
      const col=document.createElement('div');
      col.style.cssText='display:flex;flex-direction:column;align-items:center;gap:14px';
      const big=document.createElement('canvas'); big.width=300; big.height=300;
      big.style.cssText='border-radius:66px';
      big.getContext('2d').drawImage(window.__src[k],0,0,300,300);
      const lbl=document.createElement('div');
      lbl.textContent=k; lbl.style.cssText='color:#e6e4de;font:700 20px ui-monospace,monospace';
      const small=document.createElement('canvas'); small.width=48; small.height=48;
      small.style.cssText='border-radius:11px';
      small.getContext('2d').drawImage(window.__src[k],0,0,48,48);
      const cap=document.createElement('div');
      cap.textContent='48px'; cap.style.cssText='color:#8d8880;font:400 13px ui-monospace,monospace';
      col.append(lbl,big,small,cap); wrap.appendChild(col);
    }
    document.body.appendChild(wrap);
  });
  await p.waitForTimeout(300);
  await p.screenshot({ path:'icons/compare.png', fullPage:true });
  await b.close();
})();
