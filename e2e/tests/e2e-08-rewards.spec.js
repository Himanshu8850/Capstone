import { expect, test } from '@playwright/test';
import { PASSWORD } from '../credentials.js';

/**
 * E2E-08 (quickstart): reward points at checkout. The redemption area shows the balance and the maximum redeemable
 * points, which equals min(balance, floor(50% of subtotal + tax + delivery)); that many points are accepted and one
 * more is rejected with a message; the redeemed points are deducted when the order is placed and restored when it is
 * cancelled; points are never earned. Every test registers its own customer. Runs against E2E_BASE_URL.
 *
 * Seed facts used (book 4 is used by no other spec): "Seekhne Ki Kala" is a paperback at 249.00 with stock 30. One
 * copy pays 249.00 + 12.45 tax + 40.00 delivery = 301.45, so the cap is floor(150.725) = 150 points and the new
 * customer's 1,000 points are not the limit. A payment method is always needed for the rest: 301.45 - 150 = 151.45.
 */

const BOOK = 4;
const TITLE = 'Seekhne Ki Kala';
const TOTAL = '₹301.45';
const CAP = 150;

async function register(request) {
  const stamp = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const email = `rewards.${stamp}@example.com`;
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

async function balanceOf(page) {
  const response = await page.request.get('/api/v1/rewards/me');
  expect(response.status()).toBe(200);
  return (await response.json()).balance;
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

/** The summary of the order and the redemption area on the checkout page. */
function checkoutAreas(page) {
  return {
    summary: page.getByRole('region', { name: 'Order summary' }),
    rewards: page.getByRole('region', { name: 'Reward points' }),
    payNow: page.getByRole('button', { name: 'Pay Now' }),
    points: page.getByLabel('Points to redeem'),
  };
}

async function payableOf(summary) {
  return summary.getByText('Payable total').locator('xpath=following-sibling::dd');
}

test('E2E-08 checkout shows the balance and the maximum, accepts the maximum and rejects one point more', async ({ page, request }) => {
  const account = await register(request);
  await logIn(page, account);
  await addToBasket(page, BOOK);
  await page.goto('/checkout');

  const { summary, rewards, payNow, points } = checkoutAreas(page);
  await expect(summary).toBeVisible();
  await expect(await payableOf(summary)).toHaveText(TOTAL);

  // Balance and maximum are shown below the order summary.
  await expect(rewards.getByText('Available balance').locator('xpath=following-sibling::dd')).toHaveText('1,000 points');
  await expect(rewards.getByText('Maximum you can redeem').locator('xpath=following-sibling::dd')).toHaveText(`${CAP} points`);
  const summaryBox = await summary.boundingBox();
  const rewardsBox = await rewards.boundingBox();
  expect(rewardsBox.y).toBeGreaterThan(summaryBox.y);

  // Fewer points reduce the payable total by one rupee each.
  await points.fill('100');
  await expect(summary.getByText('Reward points').locator('xpath=following-sibling::dd')).toHaveText('−₹100.00');
  await expect(await payableOf(summary)).toHaveText('₹201.45');

  // Exactly the maximum is accepted and an amount is still left to pay.
  await points.fill(String(CAP));
  await expect(page.getByText(/You can redeem at most/)).toHaveCount(0);
  await expect(await payableOf(summary)).toHaveText('₹151.45');
  await expect(payNow).toBeEnabled();

  // One point more is rejected with a message, nothing is applied and Pay Now is held back.
  await points.fill(String(CAP + 1));
  await expect(page.getByText(`You can redeem at most ${CAP} points on this order.`)).toBeVisible();
  await expect(points).toHaveAttribute('aria-invalid', 'true');
  await expect(await payableOf(summary)).toHaveText(TOTAL);
  await expect(payNow).toBeDisabled();

  // Text that is not a whole number is explained too; clearing the box means no points.
  await points.fill('12.5');
  await expect(page.getByText('Enter a whole number of points, for example 100.')).toBeVisible();
  await expect(payNow).toBeDisabled();
  await points.fill('');
  await expect(page.getByText(/whole number|at most/)).toHaveCount(0);
  await expect(payNow).toBeEnabled();
  await expect(await payableOf(summary)).toHaveText(TOTAL);
  expect(await balanceOf(page)).toBe(1000);
});

test('E2E-08 the server also rejects one point more than the maximum and changes nothing', async ({ page, request }) => {
  const account = await register(request);
  await logIn(page, account);
  await addToBasket(page, BOOK);

  const cookies = await page.context().cookies();
  const xsrf = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN')?.value;
  expect(xsrf).toBeTruthy();
  const quote = (pointsToRedeem) =>
    page.request.post('/api/v1/checkout/quote', { data: { pointsToRedeem }, headers: { 'X-XSRF-TOKEN': xsrf } });

  const atCap = await quote(CAP);
  expect(atCap.status()).toBe(200);
  const body = await atCap.json();
  expect(body.maxRedeemablePoints).toBe(CAP);
  expect(body.balance).toBe(1000);
  expect(body.payableTotal).toBe('151.45');

  const over = await quote(CAP + 1);
  expect(over.status()).toBe(409);
  expect((await over.json()).code).toBe('POINTS_LIMIT_EXCEEDED');
  expect(await balanceOf(page)).toBe(1000);
});

test('E2E-08 redeemed points are deducted when the order is placed and restored when it is cancelled', async ({ page, request }) => {
  const account = await register(request);
  await logIn(page, account);
  expect(await balanceOf(page)).toBe(1000);
  const stockBefore = await stockOf(page, BOOK);
  await addToBasket(page, BOOK);

  await page.goto('/checkout');
  const { points } = checkoutAreas(page);
  await points.fill(String(CAP));
  await fillAddress(page);
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expect(page).toHaveURL(/\/payment$/);
  await expect(page.getByText('Amount payable:')).toContainText('₹151.45');
  await page.getByRole('radio', { name: 'Cash on Delivery' }).check();
  await page.getByRole('button', { name: 'Pay Now' }).click();

  // The purchase confirmation shows the reduction; the balance went down by exactly the points used.
  await expect(page).toHaveURL(/\/purchase\/confirmed\/\d+$/);
  await expect(page.getByLabel('Order summary').getByText('₹151.45')).toBeVisible();
  const orderId = page.url().split('/').pop();
  const placed = await orderOf(page, orderId);
  expect(placed.pointsRedeemed).toBe(CAP);
  expect(placed.payableTotal).toBe('151.45');
  expect(placed.subtotal).toBe('249.00');
  expect(await balanceOf(page)).toBe(1000 - CAP);
  expect(await stockOf(page, BOOK)).toBe(stockBefore - 1);

  // My Orders shows the amount paid and the order page shows the points that were used.
  await page.goto(`/orders/${orderId}`);
  const details = page.getByRole('region', { name: 'Order details' });
  await expect(details.getByRole('region', { name: 'Order summary' }).getByText('Reward points').locator('xpath=following-sibling::dd'))
    .toHaveText('−₹150.00');
  await expect(details.getByRole('heading', { name: TITLE }).first()).toBeVisible();

  // Cancelling gives the points back, restores the stock and cancels the payment.
  await details.getByRole('button', { name: 'Cancel order' }).click();
  await page.getByRole('dialog', { name: 'Cancel order' }).getByRole('button', { name: 'Yes, cancel order' }).click();
  await expect(page.getByText(`Order ${placed.reference} was cancelled.`)).toBeVisible();
  expect(await balanceOf(page)).toBe(1000);
  expect(await stockOf(page, BOOK)).toBe(stockBefore);
  const cancelled = await orderOf(page, orderId);
  expect(cancelled.status).toBe('CANCELLED');
  expect(cancelled.paymentStatus).toBe('CANCELLED');

  // A second cancellation is rejected and cannot give the points back twice.
  await page.reload();
  await expect(page.getByRole('button', { name: 'Cancel order' })).toHaveCount(0);
  expect(await balanceOf(page)).toBe(1000);

  // The checkout shows the full balance again.
  await addToBasket(page, BOOK);
  await page.goto('/checkout');
  await expect(checkoutAreas(page).rewards.getByText('Available balance').locator('xpath=following-sibling::dd'))
    .toHaveText('1,000 points');
});

test('E2E-08 a card order with fewer points than the maximum keeps the rest of the balance, and buying never earns points', async ({ page, request }) => {
  const account = await register(request);
  await logIn(page, account);
  await addToBasket(page, BOOK);

  await page.goto('/checkout');
  await checkoutAreas(page).points.fill('40');
  await fillAddress(page);
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expect(page.getByText('Amount payable:')).toContainText('₹261.45');
  await page.getByRole('radio', { name: 'Credit Card' }).check();
  await page.getByLabel('Card number').fill('4111-1111-1111-1111');
  await page.getByLabel('Name on card').fill('Asha Verma');
  await page.getByLabel('CVV').fill('123');
  await page.getByLabel('Expiry (MM/YYYY)').fill('12/2099');
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expect(page.getByRole('heading', { name: 'Payment successful' })).toBeVisible();
  await page.getByRole('link', { name: 'Continue', exact: true }).click();
  await expect(page).toHaveURL(/\/purchase\/confirmed\/\d+$/);

  const order = await orderOf(page, page.url().split('/').pop());
  expect(order.paymentStatus).toBe('PAID');
  expect(order.pointsRedeemed).toBe(40);
  expect(order.payableTotal).toBe('261.45');
  // 1,000 - 40: the purchase itself added nothing.
  expect(await balanceOf(page)).toBe(960);

  // A second order without points leaves the balance exactly where it was.
  await addToBasket(page, BOOK);
  await page.goto('/checkout');
  await expect(checkoutAreas(page).rewards.getByText('Available balance').locator('xpath=following-sibling::dd'))
    .toHaveText('960 points');
  await fillAddress(page);
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expect(page.getByText('Amount payable:')).toContainText(TOTAL);
  await page.getByRole('radio', { name: 'Cash on Delivery' }).check();
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expect(page).toHaveURL(/\/purchase\/confirmed\/\d+$/);
  expect(await balanceOf(page)).toBe(960);
});
