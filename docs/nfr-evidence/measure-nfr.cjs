/*
 * Evidence collector for docs/nfr-verification.md (T139). Not part of the product or of the test suites.
 *
 * Needs the stack running on loopback: backend on 8080, the Vite production preview (npm run preview) on 4173 and the
 * Vite dev server on 5173, all talking to the same seeded database. Uses the Playwright installed in ../../e2e.
 *
 *   node docs/nfr-evidence/measure-nfr.cjs <output.json>
 *
 * It registers throw-away customers (dummy data only), measures, and writes JSON plus screenshots to
 * docs/screenshots/. It changes nothing in the application.
 */
const fs = require('fs');
const path = require('path');
const { chromium } = require(path.join(__dirname, '..', '..', 'e2e', 'node_modules', '@playwright', 'test'));

const API = 'http://127.0.0.1:8080/api/v1';
const PREVIEW = process.env.NFR_PREVIEW || 'http://127.0.0.1:4173';
const DEV = process.env.NFR_DEV || 'http://127.0.0.1:5173';
const SHOTS = path.join(__dirname, '..', 'screenshots');
const OUT = process.argv[2] || path.join(__dirname, 'nfr-results.json');
const WIDTHS = [1280, 1024, 1023, 640, 639, 375];
const SHOT_WIDTHS = [1280, 1024, 640, 375];

const results = { api: [], load: [], responsive: [], a11y: {}, keyboard: [], notes: [] };

const median = (a) => {
  const s = [...a].sort((x, y) => x - y);
  return s.length % 2 ? s[(s.length - 1) / 2] : (s[s.length / 2 - 1] + s[s.length / 2]) / 2;
};
const round = (n) => Math.round(n * 10) / 10;
const stamp = () => `${Date.now()}${Math.floor(Math.random() * 1000)}`;

// ---------------------------------------------------------------------------------------------------------------
// 1. API response times (NFR-PERF-01)
// ---------------------------------------------------------------------------------------------------------------
async function call(method, p, opts = {}) {
  const headers = {};
  const cookies = [];
  if (opts.auth) cookies.push('BG_AUTH=' + opts.auth);
  if (opts.csrf) {
    cookies.push('XSRF-TOKEN=tok');
    headers['X-XSRF-TOKEN'] = 'tok';
  }
  if (cookies.length) headers.Cookie = cookies.join('; ');
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
  if (opts.key) headers['Idempotency-Key'] = opts.key;
  const t0 = performance.now();
  const res = await fetch(API + p, {
    method,
    headers,
    body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
  });
  const text = await res.text();
  return { status: res.status, ms: performance.now() - t0, text, setCookie: res.headers.getSetCookie() };
}

async function timeRepeated(operation, method, p, opts, repeats) {
  const first = await call(method, p, opts);
  const rest = [];
  for (let i = 0; i < repeats; i++) rest.push((await call(method, p, opts)).ms);
  results.api.push({
    operation, method, path: p, status: first.status, firstMs: round(first.ms),
    medianMs: round(median(rest)), maxMs: round(Math.max(...rest)), samples: repeats,
  });
  return first;
}

async function single(operation, method, p, opts) {
  const r = await call(method, p, opts);
  results.api.push({ operation, method, path: p, status: r.status, firstMs: round(r.ms), medianMs: null, maxMs: null, samples: 1 });
  return r;
}

async function measureApi() {
  const publics = [
    ['listStates', '/reference/states'], ['listCategories', '/categories'], ['listBrands', '/brands'],
    ['listBooks', '/books'], ['listBooks (search, filters, sort)', '/books?q=the&language=English&sort=PRICE_ASC'],
    ['getBookFilterOptions', '/books/filter-options'], ['getBook', '/books/1'], ['listRelatedBooks', '/books/1/related'],
  ];
  for (const [op, p] of publics) await timeRepeated(op, 'GET', p, {}, 10);
  await single('previewBasket', 'POST', '/basket/preview', { body: { items: [{ bookId: 1, quantity: 2 }] } });

  const email = `nfr.api.${stamp()}@example.com`;
  const password = `Pw${stamp()}x`;
  await single('register', 'POST', '/auth/register', { body: { fullName: 'Asha Verma', email, password, confirmPassword: password } });
  const login = await single('login', 'POST', '/auth/login', { body: { email, password } });
  const auth = (login.setCookie.find((c) => c.startsWith('BG_AUTH=')) || '').split(';')[0].replace('BG_AUTH=', '');
  if (!auth) { results.notes.push('API timing: login returned no BG_AUTH cookie'); return; }

  const read = { auth };
  const write = { auth, csrf: true };
  for (const [op, p] of [['getCurrentCustomer', '/auth/me'], ['listAddresses', '/addresses'], ['getBasket', '/basket'],
    ['getRewardAccount', '/rewards/me'], ['listOrders', '/orders'], ['getBasketRecommendations', '/recommendations']]) {
    await timeRepeated(op, 'GET', p, read, 10);
  }
  const address = { fullName: 'Asha Verma', phone: '9876543210', addressLine1: '12 MG Road', city: 'Bengaluru', state: 'Karnataka', pinCode: '560001' };
  const created = await single('createAddress', 'POST', '/addresses', { ...write, body: address });
  const addressId = JSON.parse(created.text).id;
  await single('updateAddress', 'PUT', '/addresses/' + addressId, { ...write, body: { ...address, city: 'Mysuru' } });
  await single('addBasketItem', 'POST', '/basket/items', { ...write, body: { bookId: 1, quantity: 1 } });
  await single('mergeGuestBasket', 'POST', '/basket/merge', { ...write, body: { items: [{ bookId: 13, quantity: 1 }] } });
  await single('updateBasketItem', 'PUT', '/basket/items/13', { ...write, body: { quantity: 2 } });
  await single('removeBasketItem', 'DELETE', '/basket/items/13', write);
  await single('quoteCheckout', 'POST', '/checkout/quote', { ...write, body: { pointsToRedeem: 0 } });
  const placed = await single('placeOrder (cash on delivery)', 'POST', '/orders', {
    ...write, key: crypto.randomUUID(),
    body: { addressId, paymentMethod: 'COD', pointsToRedeem: 0, expectedPayableTotal: '563.95' },
  });
  const orderId = JSON.parse(placed.text).id;
  await single('getOrder', 'GET', '/orders/' + orderId, read);
  await single('buyAgain', 'POST', `/orders/${orderId}/buy-again`, write);
  await single('cancelOrder', 'POST', `/orders/${orderId}/cancellation`, write);
  await single('deleteAddress', 'DELETE', '/addresses/' + addressId, write);
  await single('logout', 'POST', '/auth/logout', write);
}

// ---------------------------------------------------------------------------------------------------------------
// 2. Initial frontend load (NFR-PERF-02)
// ---------------------------------------------------------------------------------------------------------------
async function measureLoad(browser) {
  for (const [label, base] of [['production build (vite preview)', PREVIEW], ['dev server (vite dev)', DEV]]) {
    for (const route of ['/', '/books/1']) {
      const runs = [];
      for (let i = 0; i < 5; i++) {
        const ctx = await browser.newContext(); // new context: empty cache, empty storage
        const page = await ctx.newPage();
        const t0 = Date.now();
        await page.goto(base + route, { waitUntil: 'load' });
        const loadMs = Date.now() - t0;
        await page.locator('article').first().waitFor({ state: 'visible', timeout: 15000 });
        const contentMs = Date.now() - t0;
        const nav = await page.evaluate(() => {
          const n = performance.getEntriesByType('navigation')[0];
          return { dcl: n.domContentLoadedEventEnd, load: n.loadEventEnd, transfer: performance.getEntriesByType('resource').reduce((s, r) => s + (r.transferSize || 0), 0) };
        });
        runs.push({ loadMs, contentMs, dcl: nav.dcl, loadEvent: nav.load, bytes: nav.transfer });
        await ctx.close();
      }
      results.load.push({
        target: label, route, runs: runs.length,
        loadEventMedianMs: round(median(runs.map((r) => r.loadEvent))), loadEventMaxMs: round(Math.max(...runs.map((r) => r.loadEvent))),
        firstBookCardMedianMs: round(median(runs.map((r) => r.contentMs))), firstBookCardMaxMs: round(Math.max(...runs.map((r) => r.contentMs))),
        transferKB: round(median(runs.map((r) => r.bytes)) / 1024),
      });
    }
  }
}

// ---------------------------------------------------------------------------------------------------------------
// 3. Responsive walk-through, accessibility audit, screenshots
// ---------------------------------------------------------------------------------------------------------------
function auditInPage() {
  const out = {
    title: document.title, lang: document.documentElement.lang, h1: document.querySelectorAll('h1').length,
    main: document.querySelectorAll('main,[role=main]').length, unlabelled: [], unnamed: [],
    images: { visible: 0, missingAlt: [], emptyAlt: 0 }, contrast: { checked: 0, failing: [], unknownBackground: 0, minRatio: null },
  };
  const vis = (e) => {
    const r = e.getBoundingClientRect();
    const s = getComputedStyle(e);
    return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';
  };
  const c2d = document.createElement('canvas').getContext('2d', { willReadFrequently: true });
  const rgba = (c) => {
    c2d.clearRect(0, 0, 1, 1);
    c2d.fillStyle = '#000';
    c2d.fillStyle = c;
    c2d.fillRect(0, 0, 1, 1);
    const d = c2d.getImageData(0, 0, 1, 1).data;
    return [d[0], d[1], d[2], d[3] / 255];
  };
  const lum = (c) => {
    const f = (v) => { v /= 255; return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); };
    return 0.2126 * f(c[0]) + 0.7152 * f(c[1]) + 0.0722 * f(c[2]);
  };
  const over = (top, bot) => {
    const a = top[3];
    return [top[0] * a + bot[0] * (1 - a), top[1] * a + bot[1] * (1 - a), top[2] * a + bot[2] * (1 - a), 1];
  };
  const background = (el) => {
    const chain = [];
    let image = false;
    for (let n = el; n && n.nodeType === 1; n = n.parentElement) {
      const s = getComputedStyle(n);
      if (s.backgroundImage !== 'none') image = true;
      const c = rgba(s.backgroundColor);
      chain.push(c);
      if (c[3] === 1) break;
    }
    let bg = [255, 255, 255, 1];
    for (let i = chain.length - 1; i >= 0; i--) bg = over(chain[i], bg);
    return { bg, image };
  };
  const name = (e) => {
    const labelled = e.getAttribute('aria-labelledby');
    const byId = labelled ? labelled.split(/\s+/).map((id) => (document.getElementById(id) || {}).textContent || '').join(' ').trim() : '';
    const img = e.querySelector('img[alt]');
    return (e.getAttribute('aria-label') || byId || (e.innerText || '').trim() || e.getAttribute('title') || (img && img.getAttribute('alt')) || '').trim();
  };

  document.querySelectorAll('input,select,textarea').forEach((e) => {
    if (e.type === 'hidden' || e.type === 'submit' || e.type === 'button' || !vis(e)) return;
    const ok = (e.labels && e.labels.length > 0) || e.getAttribute('aria-label') || e.getAttribute('aria-labelledby');
    if (!ok) out.unlabelled.push({ tag: e.tagName.toLowerCase(), type: e.type, id: e.id, placeholder: e.placeholder || '' });
  });
  document.querySelectorAll('button,a[href],[role=button],[role=link],[role=radio],[role=tab]').forEach((e) => {
    if (!vis(e)) return;
    if (!name(e)) out.unnamed.push({ tag: e.tagName.toLowerCase(), cls: String(e.className).slice(0, 40) });
  });
  document.querySelectorAll('img').forEach((e) => {
    if (!vis(e)) return;
    out.images.visible++;
    if (!e.hasAttribute('alt')) out.images.missingAlt.push(e.getAttribute('src'));
    else if (e.getAttribute('alt') === '') out.images.emptyAlt++;
  });

  document.body.querySelectorAll('*').forEach((el) => {
    if (['SCRIPT', 'STYLE', 'NOSCRIPT', 'SVG', 'PATH'].includes(el.tagName.toUpperCase()) || !vis(el)) return;
    const hasText = Array.from(el.childNodes).some((n) => n.nodeType === 3 && n.textContent.trim());
    if (!hasText || el.closest('[disabled],[aria-disabled=true]')) return;
    const s = getComputedStyle(el);
    const { bg, image } = background(el);
    if (image) { out.contrast.unknownBackground++; return; }
    const fg = over(rgba(s.color), bg);
    const l1 = lum(fg);
    const l2 = lum(bg);
    const ratio = (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    const size = parseFloat(s.fontSize);
    const bold = parseInt(s.fontWeight, 10) >= 700;
    const large = size >= 24 || (size >= 18.66 && bold);
    out.contrast.checked++;
    if (out.contrast.minRatio === null || ratio < out.contrast.minRatio) out.contrast.minRatio = Math.round(ratio * 100) / 100;
    if (ratio < (large ? 3 : 4.5)) {
      out.contrast.failing.push({
        text: (el.innerText || '').trim().slice(0, 40), ratio: Math.round(ratio * 100) / 100, size, bold,
        color: s.color, background: `rgb(${bg.slice(0, 3).map(Math.round).join(',')})`,
      });
    }
  });
  out.contrast.failing = out.contrast.failing.slice(0, 8);
  return out;
}

async function focusAudit(page, max) {
  await page.evaluate(() => { if (document.activeElement) document.activeElement.blur(); window.scrollTo(0, 0); });
  const rows = [];
  for (let i = 0; i < max; i++) {
    await page.keyboard.press('Tab');
    const r = await page.evaluate(() => {
      const e = document.activeElement;
      if (!e || e === document.body) return null;
      const sig = (x) => { const s = getComputedStyle(x); return [s.outlineStyle, s.outlineWidth, s.outlineColor, s.boxShadow, s.borderTopColor, s.backgroundColor, s.color, s.textDecorationLine].join('|'); };
      const focused = sig(e);
      e.blur();
      const blurred = sig(e);
      e.focus();
      const label = (e.getAttribute('aria-label') || (e.labels && e.labels[0] && e.labels[0].innerText) || e.innerText || e.value || e.placeholder || e.tagName).toString().trim().slice(0, 40);
      return { label, indicator: focused !== blurred };
    });
    if (!r) break;
    rows.push(r);
  }
  return { tabbed: rows.length, noIndicator: rows.filter((r) => !r.indicator).map((r) => r.label) };
}

async function overflow(page) {
  return page.evaluate(() => {
    const w = window.innerWidth;
    const doc = document.documentElement;
    const wide = [];
    if (doc.scrollWidth > w + 1) {
      document.body.querySelectorAll('*').forEach((e) => {
        const r = e.getBoundingClientRect();
        if (r.right > w + 1 && wide.length < 3) wide.push(e.tagName.toLowerCase() + '.' + String(e.className).slice(0, 30));
      });
    }
    return { scrollWidth: doc.scrollWidth, innerWidth: w, overflows: doc.scrollWidth > w + 1, wide };
  });
}

async function walkthrough(browser, width) {
  const rows = [];
  const audit = width === 1280;
  const ctx = await browser.newContext({ viewport: { width, height: 900 } });
  const page = await ctx.newPage();

  const record = async (screen, extra) => {
    await page.waitForLoadState('networkidle').catch(() => {});
    await page.waitForTimeout(250);
    const o = await overflow(page);
    rows.push({ width, screen, url: page.url().replace(PREVIEW, ''), ...o, ...(extra || {}) });
    if (SHOT_WIDTHS.includes(width)) {
      fs.mkdirSync(SHOTS, { recursive: true });
      await page.evaluate(() => window.scrollTo(0, 0));
      await page.screenshot({ path: path.join(SHOTS, `${screen}-${width}.jpg`), type: 'jpeg', quality: 55, fullPage: true });
    }
    if (audit) {
      results.a11y[screen] = { ...(await page.evaluate(auditInPage)), focus: await focusAudit(page, 25) };
    }
  };
  const step = async (screen, fn) => {
    try { await fn(); await record(screen); } catch (e) {
      rows.push({ width, screen, error: String(e.message).split('\n')[0] });
    }
  };
  const visit = (screen, url) => step(screen, async () => { await page.goto(PREVIEW + url); await page.locator('main, h1').first().waitFor({ timeout: 10000 }); });

  await visit('catalogue', '/');
  await visit('book-detail', '/books/1');
  await visit('login', '/login');
  await visit('register', '/register');
  await visit('not-found', '/no-such-page');
  await visit('basket-empty', '/basket');

  const email = `nfr.ui.${width}.${stamp()}@example.com`;
  const password = `Pw${stamp()}x`;
  await ctx.request.post(PREVIEW + '/api/v1/auth/register', { data: { fullName: 'Asha Verma', email, password, confirmPassword: password } });
  await ctx.request.post(PREVIEW + '/api/v1/auth/login', { data: { email, password } });
  const xsrf = (await ctx.cookies()).find((c) => c.name === 'XSRF-TOKEN');
  const add = await ctx.request.post(PREVIEW + '/api/v1/basket/items', { data: { bookId: 2, quantity: 1 }, headers: { 'X-XSRF-TOKEN': xsrf ? xsrf.value : '' } });
  if (add.status() !== 200) rows.push({ width, screen: 'setup', error: 'basket add returned ' + add.status() });

  await visit('basket', '/basket');
  await step('checkout', async () => {
    await page.getByRole('link', { name: 'Proceed to Checkout' }).click();
    await page.getByRole('heading', { name: 'Checkout' }).waitFor({ timeout: 10000 });
    await page.getByLabel('Full name').fill('Asha Verma');
    await page.getByLabel('Phone').fill('9876543210');
    await page.getByLabel('Address line 1').fill('12 MG Road');
    await page.getByLabel('City').fill('Bengaluru');
    await page.getByLabel('State').selectOption('Karnataka');
    await page.getByLabel('PIN code').fill('560001');
  });
  await step('payment', async () => {
    await page.getByRole('button', { name: 'Pay Now' }).click();
    await page.getByRole('heading', { name: 'Complete Payment' }).waitFor({ timeout: 10000 });
    await page.getByRole('radio', { name: 'Credit Card' }).check();
    await page.getByLabel('Card number').fill('4111-1111-1111-1111');
    await page.getByLabel('Name on card').fill('Asha Verma');
    await page.getByLabel('CVV').fill('123');
    await page.getByLabel('Expiry (MM/YYYY)').fill('12/2099');
  });
  await step('payment-confirmed', async () => {
    await page.getByRole('button', { name: 'Pay Now' }).click();
    await page.getByRole('heading', { name: 'Payment successful' }).waitFor({ timeout: 15000 });
  });
  await step('purchase-confirmed', async () => {
    await page.getByRole('link', { name: 'Continue', exact: true }).click();
    await page.getByRole('heading', { name: 'Your purchase was successful' }).waitFor({ timeout: 10000 });
  });
  await visit('orders', '/orders');
  await step('order-detail', async () => {
    await page.getByRole('article').first().getByRole('link', { name: /^BG-\d{6}$/ }).click();
    await page.getByRole('region', { name: 'Order details' }).waitFor({ timeout: 10000 });
  });
  await visit('addresses', '/account/addresses');
  await ctx.close();
  return rows;
}

// ---------------------------------------------------------------------------------------------------------------
// 4. Keyboard-only journey (NFR-ACC-01)
// ---------------------------------------------------------------------------------------------------------------
async function keyboardJourney(browser) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await ctx.newPage();
  const log = (step, ok, note) => results.keyboard.push({ step, ok, note: note || '' });
  const email = `nfr.kb.${stamp()}@example.com`;
  const password = `Pw${stamp()}x`;
  await ctx.request.post(PREVIEW + '/api/v1/auth/register', { data: { fullName: 'Asha Verma', email, password, confirmPassword: password } });

  // Presses Tab until the focused element's name matches; never uses the mouse.
  const tabTo = async (pattern, max = 120) => {
    for (let i = 0; i < max; i++) {
      await page.keyboard.press('Tab');
      const hit = await page.evaluate((src) => {
        const e = document.activeElement;
        if (!e || e === document.body) return false;
        const text = e.getAttribute('aria-label') || (e.labels && e.labels[0] && e.labels[0].innerText) || e.innerText || e.value || '';
        return new RegExp(src, 'i').test(text.trim());
      }, pattern);
      if (hit) return i + 1;
    }
    throw new Error('not reachable by Tab within ' + max + ' presses: ' + pattern);
  };
  const run = async (step, fn) => {
    try { const n = await fn(); log(step, true, n ? n + ' Tab presses' : ''); return true; } catch (e) { log(step, false, String(e.message).split('\n')[0]); return false; }
  };

  await page.goto(PREVIEW + '/login');
  const ok = await run('Log in with E-mail, Password and Enter', async () => {
    const a = await tabTo('^E-mail');
    await page.keyboard.type(email);
    const b = await tabTo('^Password');
    await page.keyboard.type(password);
    await page.keyboard.press('Enter');
    await page.getByRole('group', { name: 'Account' }).getByText('Asha Verma').waitFor({ timeout: 10000 });
    return a + b;
  });
  if (!ok) { await ctx.close(); return; }
  await run('Add a book to the basket (Add to Basket + Enter)', async () => {
    await page.goto(PREVIEW + '/');
    await page.locator('article').first().waitFor();
    const n = await tabTo('^Add to Basket');
    await page.keyboard.press('Enter');
    await page.getByRole('link', { name: 'Basket, 1 item' }).waitFor({ timeout: 10000 });
    return n;
  });
  await run('Open the basket from the header', async () => {
    const n = await tabTo('^Basket, 1 item');
    await page.keyboard.press('Enter');
    await page.waitForURL('**/basket');
    return n;
  });
  await run('Proceed to Checkout', async () => {
    const n = await tabTo('Proceed to Checkout');
    await page.keyboard.press('Enter');
    await page.waitForURL('**/checkout');
    return n;
  });
  await run('Fill the address with the keyboard (text fields and the State list)', async () => {
    let n = 0;
    for (const [label, value] of [['^Full name', 'Asha Verma'], ['^Phone', '9876543210'], ['^Address line 1', '12 MG Road'], ['^City', 'Bengaluru']]) {
      n += await tabTo(label);
      await page.keyboard.type(value);
    }
    n += await tabTo('^State');
    await page.keyboard.type('Karnataka');
    n += await tabTo('^PIN code');
    await page.keyboard.type('560001');
    return n;
  });
  await run('Pay Now on checkout, then choose Cash on Delivery with the keyboard', async () => {
    let n = await tabTo('^Pay Now');
    await page.keyboard.press('Enter');
    await page.getByRole('heading', { name: 'Complete Payment' }).waitFor({ timeout: 10000 });
    // A native radio group is entered with Tab on the checked radio only; the arrow keys move inside the group.
    n += await tabTo('Credit Card');
    for (let i = 0; i < 3; i++) await page.keyboard.press('ArrowDown');
    const chosen = await page.evaluate(() => { const r = document.querySelector('input[type=radio]:checked'); return r && r.labels[0].innerText.trim(); });
    if (chosen !== 'Cash on Delivery') throw new Error('arrow keys selected "' + chosen + '"');
    return n;
  });
  await run('Pay Now on the payment page reaches the purchase confirmation', async () => {
    const n = await tabTo('^Pay Now');
    await page.keyboard.press('Enter');
    await page.getByRole('heading', { name: 'Your purchase was successful' }).waitFor({ timeout: 15000 });
    return n;
  });
  await run('Open My Orders from the header', async () => {
    const n = await tabTo('^My Orders');
    await page.keyboard.press('Enter');
    await page.getByRole('heading', { name: 'My Orders' }).waitFor({ timeout: 10000 });
    return n;
  });
  await run('Open the cancel dialog; focus moves into it; Escape closes it', async () => {
    const n = await tabTo('^Cancel order');
    await page.keyboard.press('Enter');
    const dialog = page.getByRole('dialog', { name: 'Cancel order' });
    await dialog.waitFor({ timeout: 5000 });
    const inside = await page.evaluate(() => !!document.activeElement.closest('[role=dialog]'));
    if (!inside) throw new Error('focus did not move into the dialog');
    await page.keyboard.press('Escape');
    await dialog.waitFor({ state: 'detached', timeout: 3000 });
    return n;
  });
  await run('Cancel the order from the dialog with Enter', async () => {
    await tabTo('^Cancel order');
    await page.keyboard.press('Enter');
    await tabTo('^Yes, cancel order');
    await page.keyboard.press('Enter');
    await page.getByText('Cancelled', { exact: false }).first().waitFor({ timeout: 10000 });
  });
  await ctx.close();
}

(async () => {
  const browser = await chromium.launch();
  if (process.env.NFR_ONLY === 'keyboard') {
    try { await keyboardJourney(browser); } finally { await browser.close(); fs.writeFileSync(OUT, JSON.stringify(results, null, 2)); }
    return;
  }
  try {
    await measureApi();
    await measureLoad(browser);
    for (const w of WIDTHS) results.responsive.push(...(await walkthrough(browser, w)));
    await keyboardJourney(browser);
    results.notes.push('Browser: Playwright Chromium ' + browser.version());
  } finally {
    await browser.close();
    fs.writeFileSync(OUT, JSON.stringify(results, null, 2));
  }
})().catch((e) => { console.error(e); process.exit(1); });
