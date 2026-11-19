import { expect, test } from '@playwright/test';
import { PASSWORD } from '../credentials.js';

/**
 * E2E-06 (quickstart): a declined card or UPI payment shows a message and changes nothing (basket, stock and points
 * stay as they were, no order exists); the customer retries and the purchase succeeds.
 * Simulator values: card 4000-0000-0000-0002 and UPI failure@upi decline; 4111-1111-1111-1111 and asha@okbank approve.
 * Each test registers its own customer. Runs against E2E_BASE_URL.
 *
 * Seed facts used: book 16 (paperback, 349.00, physical): 349.00 + 17.45 tax + 40.00 delivery = 406.45.
 */

const DECLINED = 'Your payment was declined. Please try again or choose another payment method.';
const badge = (page) => page.getByRole('link', { name: /^Basket, \d+ items?$/ });

async function register(request) {
  const stamp = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const email = `declined.${stamp}@example.com`;
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

async function stockOf(page, bookId) {
  const response = await page.request.get(`/api/v1/books/${bookId}`);
  expect(response.status()).toBe(200);
  return (await response.json()).stockQuantity;
}

async function toPayment(page) {
  await page.goto('/books/16');
  await page.getByRole('article').first().getByRole('button', { name: 'Add to Cart' }).click();
  await expect(page.getByRole('article').first().getByRole('link', { name: 'Go to Cart' })).toBeVisible();
  await page.goto('/checkout');
  await page.getByLabel('Full name').fill('Asha Verma');
  await page.getByLabel('Phone').fill('9876543210');
  await page.getByLabel('Address line 1').fill('12 MG Road');
  await page.getByLabel('City').fill('Bengaluru');
  await page.getByLabel('State').selectOption('Karnataka');
  await page.getByLabel('PIN code').fill('560001');
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expect(page.getByRole('heading', { name: 'Complete Payment' })).toBeVisible();
}

async function fillCard(page, number) {
  await page.getByLabel('Card number').fill(number);
  await page.getByLabel('Name on card').fill('Asha Verma');
  await page.getByLabel('CVV').fill('123');
  await page.getByLabel('Expiry (MM/YYYY)').fill('12/2099');
}

/** A decline leaves the basket, the stock and the page as they were. */
async function expectNothingChanged(page, stockBefore) {
  await expect(page).toHaveURL(/\/payment$/);
  await expect(page.getByRole('alert')).toContainText(DECLINED);
  expect(await stockOf(page, 16)).toBe(stockBefore);
  await expect(badge(page)).toHaveAccessibleName('Basket, 1 item');
}

test('E2E-06 a declined card changes nothing and a retry with an approved card succeeds', async ({ page, request }) => {
  const account = await register(request);
  await logIn(page, account);
  const stockBefore = await stockOf(page, 16);
  await toPayment(page);

  await fillCard(page, '4000-0000-0000-0002');
  await page.getByRole('button', { name: 'Pay Now' }).click();

  await expectNothingChanged(page, stockBefore);
  // Card details are not kept after a failure.
  await expect(page.getByLabel('Card number')).toHaveValue('');
  await expect(page.getByLabel('CVV')).toHaveValue('');
  await expect(page.getByRole('link', { name: 'Review your basket' })).toBeVisible();

  // The basket page still holds the book.
  await page.getByRole('link', { name: 'Review your basket' }).click();
  await expect(page.getByRole('heading', { name: 'Basket' })).toBeVisible();
  await expect(badge(page)).toHaveAccessibleName('Basket, 1 item');
  await page.goBack();
  await expect(page.getByRole('heading', { name: 'Complete Payment' })).toBeVisible();

  // A second decline is the same; the retry with an approved card then succeeds.
  await fillCard(page, '4000-0000-0000-0002');
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expectNothingChanged(page, stockBefore);

  await fillCard(page, '4111-1111-1111-1111');
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expect(page).toHaveURL(/\/payment\/confirmed\/\d+$/);
  await expect(page.getByRole('heading', { name: 'Payment successful' })).toBeVisible();

  const orderId = page.url().split('/').pop();
  const order = await (await page.request.get(`/api/v1/orders/${orderId}`)).json();
  expect(order.paymentStatus).toBe('PAID');
  expect(order.payableTotal).toBe('406.45');
  expect(await stockOf(page, 16)).toBe(stockBefore - 1);
  await expect(badge(page)).toHaveAccessibleName('Basket, 0 items');
});

test('E2E-06 a declined UPI payment changes nothing and a retry with another method succeeds', async ({ page, request }) => {
  const account = await register(request);
  await logIn(page, account);
  const stockBefore = await stockOf(page, 16);
  await toPayment(page);

  await page.getByRole('radio', { name: 'UPI' }).check();
  await page.getByLabel('UPI ID').fill('failure@upi');
  await page.getByRole('button', { name: 'Pay Now' }).click();

  await expectNothingChanged(page, stockBefore);
  await expect(page.getByLabel('UPI ID')).toHaveValue('');

  // Retry with a valid UPI ID.
  await page.getByLabel('UPI ID').fill('asha@okbank');
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expect(page).toHaveURL(/\/payment\/confirmed\/\d+$/);
  await expect(page.getByRole('heading', { name: 'Payment successful' })).toBeVisible();
  expect(await stockOf(page, 16)).toBe(stockBefore - 1);
});

test('E2E-06 after a decline the customer can switch to Cash on Delivery', async ({ page, request }) => {
  const account = await register(request);
  await logIn(page, account);
  const stockBefore = await stockOf(page, 16);
  await toPayment(page);

  await fillCard(page, '4000-0000-0000-0002');
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expectNothingChanged(page, stockBefore);

  await page.getByRole('radio', { name: 'Cash on Delivery' }).check();
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expect(page).toHaveURL(/\/purchase\/confirmed\/\d+$/);
  await expect(page.getByRole('heading', { name: 'Your purchase was successful' })).toBeVisible();
  expect(await stockOf(page, 16)).toBe(stockBefore - 1);
});
