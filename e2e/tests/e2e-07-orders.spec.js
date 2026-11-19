import { expect, test } from '@playwright/test';
import { PASSWORD } from '../credentials.js';

/**
 * E2E-07 (quickstart): My Orders, order details, Buy Again, cancellation within 48 hours and "Recommended for You".
 * Orders are placed through the UI; every test registers its own customer. Runs against E2E_BASE_URL.
 *
 * The 48-hour boundary itself (exactly 48:00:00 is rejected) cannot be reached from a browser, because an order cannot
 * be back-dated; it is covered by CancellationRulesTest and CancelOrderIT. Here the happy path, the refund/cancel of
 * the payment, the stock restore and the "already cancelled" rejection are verified end to end.
 *
 * Seed facts used (books not used by E2E-04..06): book 15 "Monsoon Letters" (paperback, 299.00, stock 35, category
 * Fiction/3), book 10 "Mann Ki Shanti" (paperback, 199.00, stock 44), book 17 "Dil Ka Safar" (paperback, 199.00,
 * stock 50, category 3), book 16 "A Winter in Lisbon" (category 3). One physical book at 299.00 pays 299.00 + 14.95 +
 * 40.00 = 353.95; at 199.00 it pays 199.00 + 9.95 + 40.00 = 248.95.
 */

const badge = (page) => page.getByRole('link', { name: /^Basket, \d+ items?$/ });

async function register(request) {
  const stamp = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const email = `orders.${stamp}@example.com`;
  const password = PASSWORD;
  const response = await request.post('/api/v1/auth/register', {
    data: { fullName: 'Asha Verma', email, password, confirmPassword: password },
  });
  expect(response.status()).toBe(201);
  return { email, password };
}

async function logIn(page, { email, password }) {
  await page.goto('/login');
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('group', { name: 'Account' })).toContainText('Asha Verma');
}

async function addToBasket(page, bookId) {
  await page.goto(`/books/${bookId}`);
  await page.getByRole('article').first().getByRole('button', { name: 'Add to Cart' }).click();
  await expect(page.getByRole('article').first().getByRole('link', { name: 'Go to Cart' })).toBeVisible();
}

async function fillAddress(page) {
  await page.getByLabel('Full name').fill('Asha Verma');
  await page.getByLabel('Phone').fill('9876543210');
  await page.getByLabel('Address line 1').fill('12 MG Road');
  await page.getByLabel('City').fill('Bengaluru');
  await page.getByLabel('State').selectOption('Karnataka');
  await page.getByLabel('PIN code').fill('560001');
}

async function toPayment(page) {
  await page.goto('/checkout');
  await fillAddress(page);
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expect(page).toHaveURL(/\/payment$/);
  await expect(page.getByRole('heading', { name: 'Complete Payment' })).toBeVisible();
}

async function stockOf(page, bookId) {
  const response = await page.request.get(`/api/v1/books/${bookId}`);
  expect(response.status()).toBe(200);
  return (await response.json()).stockQuantity;
}

async function orderOf(page, orderId) {
  const response = await page.request.get(`/api/v1/orders/${orderId}`);
  expect(response.status()).toBe(200);
  return response.json();
}

/** Buys one copy of a physical book with Cash on Delivery through the UI and returns the placed order. */
async function placeCodOrder(page, bookId) {
  await addToBasket(page, bookId);
  await toPayment(page);
  await page.getByRole('radio', { name: 'Cash on Delivery' }).check();
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expect(page).toHaveURL(/\/purchase\/confirmed\/\d+$/);
  return orderOf(page, page.url().split('/').pop());
}

/** Buys one copy of a physical book by credit card through the UI and returns the placed order. */
async function placeCardOrder(page, bookId) {
  await addToBasket(page, bookId);
  await toPayment(page);
  await page.getByRole('radio', { name: 'Credit Card' }).check();
  await page.getByLabel('Card number').fill('4111-1111-1111-1111');
  await page.getByLabel('Name on card').fill('Asha Verma');
  await page.getByLabel('CVV').fill('123');
  await page.getByLabel('Expiry (MM/YYYY)').fill('12/2099');
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expect(page.getByRole('heading', { name: 'Payment successful' })).toBeVisible();
  await page.getByRole('link', { name: 'Continue', exact: true }).click();
  await expect(page).toHaveURL(/\/purchase\/confirmed\/\d+$/);
  return orderOf(page, page.url().split('/').pop());
}

test('E2E-07 My Orders is empty for a customer without orders and needs a login', async ({ page, request }) => {
  await page.goto('/orders');
  await expect(page).toHaveURL(/\/login\?returnTo=/);

  const account = await register(request);
  await logIn(page, account);
  await page.goto('/orders');
  await expect(page.getByRole('heading', { name: 'My Orders' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'You have no orders yet' })).toBeVisible();
  await expect(page.getByRole('article')).toHaveCount(0);
});

test('E2E-07 a placed order is listed, opens its details and can be bought again', async ({ page, request }) => {
  const account = await register(request);
  await logIn(page, account);
  const order = await placeCodOrder(page, 15);
  expect(order.status).toBe('PLACED');
  expect(order.payableTotal).toBe('353.95');
  await expect(badge(page)).toHaveAccessibleName('Basket, 0 items');

  await page.goto('/orders');
  const card = page.getByRole('article', { name: `Order ${order.reference}` });
  await expect(card).toBeVisible();
  await expect(card.getByText('Placed', { exact: true })).toBeVisible();
  await expect(card.getByRole('heading', { name: 'Monsoon Letters' })).toBeVisible();
  await expect(card.getByText('₹353.95')).toBeVisible();
  await expect(card.getByRole('button', { name: 'Cancel order' })).toBeVisible();

  // Buy Again puts the book back into the basket at the current price.
  await card.getByRole('button', { name: 'Buy Again' }).click();
  const result = page.getByRole('status', { name: 'Buy Again result' });
  await expect(result).toContainText(`The books from order ${order.reference} were added to your basket.`);
  await expect(badge(page)).toHaveAccessibleName('Basket, 1 item');
  await result.getByRole('link', { name: 'View basket' }).click();
  await expect(page).toHaveURL(/\/basket$/);
  await expect(page.getByRole('heading', { name: 'Monsoon Letters' })).toBeVisible();

  // The order page shows the order as it was placed.
  await page.goto('/orders');
  await page.getByRole('article', { name: `Order ${order.reference}` }).getByRole('link', { name: order.reference }).click();
  await expect(page).toHaveURL(new RegExp(`/orders/${order.id}$`));
  const details = page.getByRole('region', { name: 'Order details' });
  await expect(details).toContainText(order.reference);
  await expect(details.getByRole('region', { name: 'Delivery address' })).toContainText('Bengaluru, Karnataka 560001');
  await expect(details.getByRole('region', { name: 'Payment' })).toContainText('Cash on Delivery');
  await expect(details.getByRole('button', { name: 'Cancel order' })).toBeVisible();
});

test('E2E-07 an order can be cancelled from My Orders: stock returns and the payment is cancelled', async ({ page, request }) => {
  const account = await register(request);
  await logIn(page, account);
  const stockBefore = await stockOf(page, 10);
  const order = await placeCodOrder(page, 10);
  expect(await stockOf(page, 10)).toBe(stockBefore - 1);
  expect(order.paymentStatus).toBe('PENDING_ON_DELIVERY');

  await page.goto('/orders');
  const card = page.getByRole('article', { name: `Order ${order.reference}` });

  // Keeping the order changes nothing.
  await card.getByRole('button', { name: 'Cancel order' }).click();
  const dialog = page.getByRole('dialog', { name: 'Cancel order' });
  await expect(dialog).toContainText(order.reference);
  await dialog.getByRole('button', { name: 'Keep order' }).click();
  await expect(dialog).toHaveCount(0);
  await expect(card.getByText('Placed', { exact: true })).toBeVisible();
  expect(await stockOf(page, 10)).toBe(stockBefore - 1);

  // Confirming cancels it.
  await card.getByRole('button', { name: 'Cancel order' }).click();
  await page.getByRole('dialog', { name: 'Cancel order' }).getByRole('button', { name: 'Yes, cancel order' }).click();
  await expect(page.getByText(`Order ${order.reference} was cancelled.`)).toBeVisible();
  await expect(card.getByText('Cancelled', { exact: true })).toBeVisible();
  await expect(card.getByRole('button', { name: 'Cancel order' })).toHaveCount(0);

  expect(await stockOf(page, 10)).toBe(stockBefore);
  const cancelled = await orderOf(page, order.id);
  expect(cancelled.status).toBe('CANCELLED');
  expect(cancelled.paymentStatus).toBe('CANCELLED');
  expect(cancelled.cancellable).toBe(false);

  // A cancelled order stays in the history and its page says it cannot be changed.
  await page.goto(`/orders/${order.id}`);
  await expect(page.getByText('This order was cancelled and cannot be changed.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cancel order' })).toHaveCount(0);
});

test('E2E-07 a paid order is cancelled from its page and refunded; a second cancellation is rejected', async ({ page, context, request }) => {
  const account = await register(request);
  await logIn(page, account);
  const stockBefore = await stockOf(page, 17);
  const order = await placeCardOrder(page, 17);
  expect(order.paymentStatus).toBe('PAID');
  expect(await stockOf(page, 17)).toBe(stockBefore - 1);

  // The same order is open in a second tab.
  const other = await context.newPage();
  await other.goto(`/orders/${order.id}`);
  await expect(other.getByRole('button', { name: 'Cancel order' })).toBeVisible();

  await page.goto(`/orders/${order.id}`);
  await page.getByRole('button', { name: 'Cancel order' }).click();
  await page.getByRole('dialog', { name: 'Cancel order' }).getByRole('button', { name: 'Yes, cancel order' }).click();
  await expect(page.getByText(`Order ${order.reference} was cancelled.`)).toBeVisible();
  const details = page.getByRole('region', { name: 'Order details' });
  await expect(details.getByText('Cancelled', { exact: true }).first()).toBeVisible();
  await expect(details.getByRole('region', { name: 'Payment' })).toContainText('Refunded');
  await expect(page.getByRole('button', { name: 'Cancel order' })).toHaveCount(0);

  expect(await stockOf(page, 17)).toBe(stockBefore);
  const cancelled = await orderOf(page, order.id);
  expect(cancelled.status).toBe('CANCELLED');
  expect(cancelled.paymentStatus).toBe('REFUNDED');

  // The stale tab still offers the button; the server answers that the order is already cancelled and nothing changes.
  await other.getByRole('button', { name: 'Cancel order' }).click();
  await other.getByRole('dialog', { name: 'Cancel order' }).getByRole('button', { name: 'Yes, cancel order' }).click();
  await expect(other.getByRole('alert')).toContainText('This order has already been cancelled.');
  expect(await stockOf(page, 17)).toBe(stockBefore);

  await page.reload();
  await expect(page.getByText('This order was cancelled and cannot be changed.')).toBeVisible();
});

test('E2E-07 Recommended for You appears only for a customer with order history, in the same categories', async ({ page, request }) => {
  // A guest sees no recommendations and no request is made.
  const recommendationRequests = [];
  page.on('request', (req) => {
    if (req.url().includes('/api/v1/recommendations')) recommendationRequests.push(req.url());
  });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Recommended for You' })).toHaveCount(0);
  await page.waitForLoadState('networkidle');
  expect(recommendationRequests).toHaveLength(0);

  // A customer without orders sees none either.
  const account = await register(request);
  await logIn(page, account);
  await page.goto('/');
  await page.waitForLoadState('networkidle');
  await expect(page.getByRole('heading', { name: 'Recommended for You' })).toHaveCount(0);

  // After ordering a book (category 3) the other listed, available books of that category are recommended.
  await placeCodOrder(page, 15);
  await page.goto('/');
  const recommended = page.getByRole('region', { name: 'Recommended for You' });
  await expect(recommended).toBeVisible();
  await expect(recommended.getByRole('heading', { name: 'A Winter in Lisbon' })).toBeVisible();
  await expect(recommended.getByRole('heading', { name: 'Dil Ka Safar' })).toBeVisible();

  // The basket page leaves out the books that are already in the basket.
  await addToBasket(page, 16);
  await page.goto('/basket');
  const onBasket = page.getByRole('region', { name: 'Recommended for You' });
  await expect(onBasket.getByRole('heading', { name: 'Dil Ka Safar' })).toBeVisible();
  await expect(onBasket.getByRole('heading', { name: 'A Winter in Lisbon' })).toHaveCount(0);
});

test("E2E-07 another customer's order is not found and is not in the list", async ({ page, browser, request }) => {
  const owner = await register(request);
  await logIn(page, owner);
  const order = await placeCodOrder(page, 15);

  const baseURL = test.info().project.use.baseURL;
  const strangerContext = await browser.newContext({ baseURL });
  try {
    const stranger = await strangerContext.newPage();
    const account = await register(strangerContext.request);
    await logIn(stranger, account);

    await stranger.goto(`/orders/${order.id}`);
    await expect(stranger.getByRole('heading', { name: 'Order not found' })).toBeVisible();
    await expect(stranger.getByText(order.reference)).toHaveCount(0);

    await stranger.goto('/orders');
    await expect(stranger.getByRole('heading', { name: 'You have no orders yet' })).toBeVisible();
  } finally {
    await strangerContext.close();
  }

  // A malformed id is not found either.
  await page.goto('/orders/abc');
  await expect(page.getByRole('heading', { name: 'Order not found' })).toBeVisible();
});
