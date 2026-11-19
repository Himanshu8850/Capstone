/*
 * Manual API test run (T145). Calls each of the 29 operations in api/openapi.yaml at least once (success), plus one
 * documented error path per operation where the contract defines one, and prints a Markdown table.
 * Uses throw-away dummy customers; no secret, token or cookie value is printed.
 *
 *   node docs/api-manual-tests/api-manual-test.cjs [baseUrl]     (default http://127.0.0.1:8080/api/v1)
 */
const BASE = process.argv[2] || 'http://127.0.0.1:8080/api/v1';
const rows = [];
const stamp = () => `${Date.now()}${Math.floor(Math.random() * 1000)}`;

async function call(method, path, { auth, csrf = true, body, key } = {}) {
  const headers = {};
  const cookies = [];
  if (auth) cookies.push('BG_AUTH=' + auth);
  if (csrf && method !== 'GET') { cookies.push('XSRF-TOKEN=tok'); headers['X-XSRF-TOKEN'] = 'tok'; }
  if (cookies.length) headers.Cookie = cookies.join('; ');
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (key) headers['Idempotency-Key'] = key;
  const res = await fetch(BASE + path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { /* not JSON */ }
  return { status: res.status, json, setCookie: res.headers.getSetCookie() };
}

// expect: array of acceptable statuses; code: expected ApiError.code for error cases
async function check(operation, label, method, path, opts, expect, code) {
  const r = await call(method, path, opts);
  let ok = expect.includes(r.status);
  let note = '';
  if (r.status >= 400) {
    const e = r.json || {};
    const shape = ['status', 'code', 'message', 'timestamp'].every((k) => k in e) && e.status === r.status;
    if (!shape) { ok = false; note = 'ApiError shape mismatch'; }
    if (code && e.code !== code) { ok = false; note = `code ${e.code}, expected ${code}`; }
    if (code === 'VALIDATION_FAILED' && !Array.isArray(e.fieldErrors)) { ok = false; note = 'no fieldErrors'; }
    if (!note) note = e.code;
  }
  rows.push({ operation, label, request: `${method} ${path.split('?')[0]}`, expected: expect.join('/') + (code ? ` ${code}` : ''), actual: r.status, note, ok });
  return r;
}

const cookieOf = (r) => (r.setCookie.find((c) => c.startsWith('BG_AUTH=')) || '').split(';')[0].replace('BG_AUTH=', '');
const address = { fullName: 'Asha Verma', phone: '9876543210', addressLine1: '12 MG Road', city: 'Bengaluru', state: 'Karnataka', pinCode: '560001', country: 'India' };
const goodCard = { number: '4111-1111-1111-1111', nameOnCard: 'Asha Verma', cvv: '123', expiry: '12/2099' };
const badCard = { ...goodCard, number: '4000-0000-0000-0002' };

(async () => {
  // Public operations
  await check('listStates', 'success', 'GET', '/reference/states', {}, [200]);
  await check('listCategories', 'success', 'GET', '/categories', {}, [200]);
  await check('listBrands', 'success', 'GET', '/brands', {}, [200]);
  await check('listBooks', 'success', 'GET', '/books?q=the&sort=PRICE_ASC', {}, [200]);
  await check('listBooks', 'invalid sort', 'GET', '/books?sort=NOPE', {}, [400], 'VALIDATION_FAILED');
  await check('getBookFilterOptions', 'success', 'GET', '/books/filter-options', {}, [200]);
  await check('getBook', 'success', 'GET', '/books/1', {}, [200]);
  await check('getBook', 'unknown book', 'GET', '/books/999999', {}, [404], 'BOOK_NOT_FOUND');
  await check('listRelatedBooks', 'success', 'GET', '/books/1/related', {}, [200]);
  await check('listRelatedBooks', 'unknown book', 'GET', '/books/999999/related', {}, [404], 'BOOK_NOT_FOUND');
  await check('previewBasket', 'success', 'POST', '/basket/preview', { body: { items: [{ bookId: 1, quantity: 2 }] } }, [200]);
  await check('previewBasket', 'quantity 0', 'POST', '/basket/preview', { body: { items: [{ bookId: 1, quantity: 0 }] } }, [400], 'VALIDATION_FAILED');

  // Accounts
  const emailA = `manual.a.${stamp()}@example.com`;
  const emailB = `manual.b.${stamp()}@example.com`;
  const pw = `Pw${stamp()}x`;
  await check('register', 'success', 'POST', '/auth/register', { body: { fullName: 'Asha Verma', email: emailA, password: pw, confirmPassword: pw } }, [201]);
  await check('register', 'duplicate e-mail', 'POST', '/auth/register', { body: { fullName: 'Asha Verma', email: emailA, password: pw, confirmPassword: pw } }, [409], 'EMAIL_TAKEN');
  await check('register', 'password mismatch', 'POST', '/auth/register', { body: { fullName: 'Asha Verma', email: `x.${stamp()}@example.com`, password: pw, confirmPassword: 'other1234' } }, [400], 'VALIDATION_FAILED');
  await call('POST', '/auth/register', { body: { fullName: 'Ravi Kumar', email: emailB, password: pw, confirmPassword: pw } });
  await check('login', 'wrong password', 'POST', '/auth/login', { body: { email: emailA, password: 'Wrong1234' } }, [401], 'INVALID_CREDENTIALS');
  const login = await check('login', 'success', 'POST', '/auth/login', { body: { email: emailA, password: pw } }, [200]);
  const A = cookieOf(login);
  const B = cookieOf(await call('POST', '/auth/login', { body: { email: emailB, password: pw } }));
  if (!A || !B) { console.error('login returned no BG_AUTH cookie'); process.exit(2); }
  await check('getCurrentCustomer', 'success', 'GET', '/auth/me', { auth: A }, [200]);
  await check('getCurrentCustomer', 'no cookie', 'GET', '/auth/me', {}, [401], 'UNAUTHENTICATED');

  // Addresses
  await check('listAddresses', 'success', 'GET', '/addresses', { auth: A }, [200]);
  await check('listAddresses', 'no cookie', 'GET', '/addresses', {}, [401], 'UNAUTHENTICATED');
  await check('createAddress', 'invalid phone', 'POST', '/addresses', { auth: A, body: { ...address, phone: '123' } }, [400], 'VALIDATION_FAILED');
  await check('createAddress', 'no CSRF token', 'POST', '/addresses', { auth: A, csrf: false, body: address }, [401, 403]);
  const created = await check('createAddress', 'success', 'POST', '/addresses', { auth: A, body: address }, [201]);
  const addressId = created.json && created.json.id;
  await check('updateAddress', 'success', 'PUT', `/addresses/${addressId}`, { auth: A, body: { ...address, city: 'Mysuru' } }, [200]);
  await check('updateAddress', "another customer's address", 'PUT', `/addresses/${addressId}`, { auth: B, body: address }, [404], 'NOT_FOUND');

  // Basket
  await check('getBasket', 'success', 'GET', '/basket', { auth: A }, [200]);
  await check('getBasket', 'no cookie', 'GET', '/basket', {}, [401], 'UNAUTHENTICATED');
  await check('addBasketItem', 'success', 'POST', '/basket/items', { auth: A, body: { bookId: 1, quantity: 1 } }, [200, 201]);
  await check('addBasketItem', 'unknown book', 'POST', '/basket/items', { auth: A, body: { bookId: 999999, quantity: 1 } }, [404], 'BOOK_NOT_FOUND');
  await check('mergeGuestBasket', 'success', 'POST', '/basket/merge', { auth: A, body: { items: [{ bookId: 13, quantity: 1 }] } }, [200]);
  await check('mergeGuestBasket', 'quantity 0', 'POST', '/basket/merge', { auth: A, body: { items: [{ bookId: 13, quantity: 0 }] } }, [400], 'VALIDATION_FAILED');
  await check('updateBasketItem', 'success', 'PUT', '/basket/items/13', { auth: A, body: { quantity: 2 } }, [200]);
  await check('updateBasketItem', 'item not in basket', 'PUT', '/basket/items/14', { auth: A, body: { quantity: 2 } }, [404], 'NOT_FOUND');
  await check('removeBasketItem', 'success', 'DELETE', '/basket/items/13', { auth: A }, [200]);
  await check('removeBasketItem', 'item not in basket', 'DELETE', '/basket/items/13', { auth: A }, [404], 'NOT_FOUND');
  await check('getBasketRecommendations', 'success', 'GET', '/recommendations', { auth: A }, [200]);
  await check('getBasketRecommendations', 'no cookie', 'GET', '/recommendations', {}, [401], 'UNAUTHENTICATED');
  await check('getRewardAccount', 'success', 'GET', '/rewards/me', { auth: A }, [200]);
  await check('getRewardAccount', 'no cookie', 'GET', '/rewards/me', {}, [401], 'UNAUTHENTICATED');

  // Checkout and orders
  await check('quoteCheckout', 'negative points', 'POST', '/checkout/quote', { auth: A, body: { pointsToRedeem: -1 } }, [400], 'VALIDATION_FAILED');
  const quote = await check('quoteCheckout', 'success', 'POST', '/checkout/quote', { auth: A, body: { pointsToRedeem: 0 } }, [200]);
  const total = quote.json && quote.json.payableTotal;
  const order = (extra) => ({ addressId, paymentMethod: 'COD', pointsToRedeem: 0, expectedPayableTotal: total, ...extra });
  await check('placeOrder', 'declined card (4000-0000-0000-0002)', 'POST', '/orders',
    { auth: A, key: crypto.randomUUID(), body: order({ paymentMethod: 'CREDIT_CARD', payment: { card: badCard } }) }, [409], 'PAYMENT_DECLINED');
  await check('placeOrder', 'points above the cap', 'POST', '/orders',
    { auth: A, key: crypto.randomUUID(), body: order({ pointsToRedeem: quote.json.maxRedeemablePoints + 1, expectedPayableTotal: (Number(total) - quote.json.maxRedeemablePoints - 1).toFixed(2) }) }, [409, 400], 'POINTS_LIMIT_EXCEEDED');
  const key = crypto.randomUUID();
  const placed = await check('placeOrder', 'success (cash on delivery)', 'POST', '/orders', { auth: A, key, body: order() }, [201]);
  const orderId = placed.json && placed.json.id;
  await check('placeOrder', 'replay with the same Idempotency-Key', 'POST', '/orders', { auth: A, key, body: order() }, [200, 201]);
  await check('listOrders', 'success', 'GET', '/orders', { auth: A }, [200]);
  await check('listOrders', 'no cookie', 'GET', '/orders', {}, [401], 'UNAUTHENTICATED');
  await check('getOrder', 'success', 'GET', `/orders/${orderId}`, { auth: A }, [200]);
  await check('getOrder', "another customer's order", 'GET', `/orders/${orderId}`, { auth: B }, [404], 'NOT_FOUND');
  await check('buyAgain', 'success', 'POST', `/orders/${orderId}/buy-again`, { auth: A }, [200]);
  await check('buyAgain', "another customer's order", 'POST', `/orders/${orderId}/buy-again`, { auth: B }, [404], 'NOT_FOUND');
  await check('cancelOrder', "another customer's order", 'POST', `/orders/${orderId}/cancellation`, { auth: B }, [404], 'NOT_FOUND');
  await check('cancelOrder', 'success', 'POST', `/orders/${orderId}/cancellation`, { auth: A }, [200]);
  await check('cancelOrder', 'already cancelled', 'POST', `/orders/${orderId}/cancellation`, { auth: A }, [409], 'ALREADY_CANCELLED');

  // Clean-up of the address and session handling
  await check('deleteAddress', "another customer's address", 'DELETE', `/addresses/${addressId}`, { auth: B }, [404], 'NOT_FOUND');
  await check('deleteAddress', 'success', 'DELETE', `/addresses/${addressId}`, { auth: A }, [204]);
  await check('logout', 'no cookie', 'POST', '/auth/logout', {}, [401], 'UNAUTHENTICATED');
  await check('logout', 'success', 'POST', '/auth/logout', { auth: A }, [204]);
  await check('getCurrentCustomer', 'replayed cookie after logout', 'GET', '/auth/me', { auth: A }, [401], 'UNAUTHENTICATED');

  const ops = new Set(rows.map((r) => r.operation));
  const failed = rows.filter((r) => !r.ok);
  console.log('| # | Operation | Case | Request | Expected | Actual | Note | Result |');
  console.log('|---|---|---|---|---|---|---|---|');
  rows.forEach((r, i) => console.log(`| ${i + 1} | ${r.operation} | ${r.label} | ${r.request} | ${r.expected} | ${r.actual} | ${r.note} | ${r.ok ? 'PASS' : 'FAIL'} |`));
  console.log(`\nOperations covered: ${ops.size} of 29. Cases: ${rows.length}. Passed: ${rows.length - failed.length}. Failed: ${failed.length}.`);
  process.exit(failed.length || ops.size !== 29 ? 1 : 0);
})().catch((e) => { console.error(e.message); process.exit(2); });
