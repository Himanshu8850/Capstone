import { expect, test } from '@playwright/test';
import { PASSWORD } from '../credentials.js';

/**
 * E2E-05 (quickstart): each of Credit Card, Debit Card, UPI and Cash on Delivery completes a purchase:
 * checkout (address, summary) -> payment -> payment confirmation (not for COD) -> purchase confirmation -> empty basket.
 * My Orders is a later phase, so the order is verified through the confirmation page and the order API.
 * Each test registers its own customer. Runs against E2E_BASE_URL.
 *
 * Seed facts used: book 2 (paperback, 349.00, physical), book 13 "The Two-Minute Reset" (eBook, 99.00).
 * eBook only: 99.00 + 4.95 tax + 0.00 delivery = 103.95. Physical book 2: 349.00 + 17.45 + 40.00 = 406.45.
 */

const badge = (page) => page.getByRole('link', { name: /^Basket, \d+ items?$/ });

async function register(request) {
  const stamp = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const email = `checkout.${stamp}@example.com`;
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

/** `ebookOnly`: nothing is delivered, so checkout has no address fields (AMD-004). */
async function toPayment(page, { ebookOnly = false } = {}) {
  await page.goto('/checkout');
  if (ebookOnly) {
    await expect(page.getByLabel('Full name')).toHaveCount(0);
    await expect(page.getByText('no delivery address is needed')).toBeVisible();
  } else {
    await fillAddress(page);
  }
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expect(page).toHaveURL(/\/payment$/);
  await expect(page.getByRole('heading', { name: 'Complete Payment' })).toBeVisible();
}

async function fillCard(page, number = '4111-1111-1111-1111') {
  await page.getByLabel('Card number').fill(number);
  await page.getByLabel('Name on card').fill('Asha Verma');
  await page.getByLabel('CVV').fill('123');
  await page.getByLabel('Expiry (MM/YYYY)').fill('12/2099');
}

/** The purchase confirmation lists the order and the basket is empty afterwards. */
async function expectPurchaseConfirmed(page, { bookTitle, payable, paymentStatus, ebookOnly = false }) {
  await expect(page).toHaveURL(/\/purchase\/confirmed\/\d+$/);
  await expect(page.getByRole('heading', { name: 'Your purchase was successful' })).toBeVisible();
  await expect(page.getByText(/BG-\d{6}/).first()).toBeVisible();
  await expect(page.getByRole('heading', { name: bookTitle })).toBeVisible();
  if (ebookOnly) {
    await expect(page.getByRole('region', { name: 'Delivery address' })).toHaveCount(0);
  } else {
    await expect(page.getByText('Bengaluru, Karnataka 560001')).toBeVisible();
  }
  await expect(page.getByLabel('Order summary').getByText(payable)).toBeVisible();
  await expect(page.getByLabel('Payment', { exact: true }).getByText(paymentStatus)).toBeVisible();

  const orderId = page.url().split('/').pop();
  const order = await page.request.get(`/api/v1/orders/${orderId}`);
  expect(order.status()).toBe(200);
  const body = await order.json();
  expect(body.reference).toMatch(/^BG-\d{6}$/);
  expect(body.items).toHaveLength(1);

  await expect(badge(page)).toHaveAccessibleName('Basket, 0 items');
  await page.goto('/basket');
  await expect(page.getByRole('heading', { name: 'Your basket is empty' })).toBeVisible();
  return body;
}

async function stockOf(page, bookId) {
  const response = await page.request.get(`/api/v1/books/${bookId}`);
  expect(response.status()).toBe(200);
  return (await response.json()).stockQuantity;
}

test('E2E-05 a credit card payment shows the payment confirmation, then the purchase confirmation', async ({ page, request }) => {
  const account = await register(request);
  await logIn(page, account);
  const stockBefore = await stockOf(page, 2);
  await addToBasket(page, 2);

  await page.goto('/checkout');
  await expect(page.getByRole('heading', { name: 'Checkout' })).toBeVisible();
  const summary = page.getByRole('region', { name: 'Order summary' });
  await expect(summary.getByText('Payable total').locator('xpath=following-sibling::dd')).toHaveText('₹406.45');

  await fillAddress(page);
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expect(page.getByText('Amount payable:')).toContainText('₹406.45');

  await page.getByRole('radio', { name: 'Credit Card' }).check();
  await fillCard(page);
  await page.getByRole('button', { name: 'Pay Now' }).click();

  await expect(page).toHaveURL(/\/payment\/confirmed\/\d+$/);
  await expect(page.getByRole('heading', { name: 'Payment successful' })).toBeVisible();
  await expect(page.getByText('Paid', { exact: true })).toBeVisible();
  await page.getByRole('link', { name: 'Continue', exact: true }).click();

  const order = await expectPurchaseConfirmed(page, {
    bookTitle: 'Calm Mind, Clear Day',
    payable: '₹406.45',
    paymentStatus: 'Paid',
  });
  expect(order.paymentMethod).toBe('CREDIT_CARD');
  expect(order.paymentStatus).toBe('PAID');
  expect(order.payableTotal).toBe('406.45');
  expect(await stockOf(page, 2)).toBe(stockBefore - 1);

  // Going back or reloading the confirmation only reads the order: nothing is created twice.
  await page.goto(`/purchase/confirmed/${order.id}`);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Your purchase was successful' })).toBeVisible();
  expect(await stockOf(page, 2)).toBe(stockBefore - 1);
});

test('E2E-05 a debit card payment completes', async ({ page, request }) => {
  const account = await register(request);
  await logIn(page, account);
  await addToBasket(page, 13);
  await toPayment(page, { ebookOnly: true });
  await expect(page.getByText('Amount payable:')).toContainText('₹103.95');

  await page.getByRole('radio', { name: 'Debit Card' }).check();
  await fillCard(page);
  await page.getByRole('button', { name: 'Pay Now' }).click();

  await expect(page.getByRole('heading', { name: 'Payment successful' })).toBeVisible();
  await page.getByRole('link', { name: 'Continue', exact: true }).click();
  const order = await expectPurchaseConfirmed(page, {
    bookTitle: 'The Two-Minute Reset',
    payable: '₹103.95',
    paymentStatus: 'Paid',
    ebookOnly: true,
  });
  expect(order.paymentMethod).toBe('DEBIT_CARD');
  expect(order.deliveryCharge).toBe('0.00');
  expect(order.address ?? null).toBeNull();
});

test('E2E-05 a UPI payment completes', async ({ page, request }) => {
  const account = await register(request);
  await logIn(page, account);
  await addToBasket(page, 13);
  await toPayment(page, { ebookOnly: true });

  await page.getByRole('radio', { name: 'UPI' }).check();
  await page.getByLabel('UPI ID').fill('asha@okbank');
  await page.getByRole('button', { name: 'Pay Now' }).click();

  await expect(page.getByRole('heading', { name: 'Payment successful' })).toBeVisible();
  await page.getByRole('link', { name: 'Continue', exact: true }).click();
  const order = await expectPurchaseConfirmed(page, {
    bookTitle: 'The Two-Minute Reset',
    payable: '₹103.95',
    paymentStatus: 'Paid',
    ebookOnly: true,
  });
  expect(order.paymentMethod).toBe('UPI');
});

test('E2E-05 Cash on Delivery skips the payment confirmation and leaves the payment pending', async ({ page, request }) => {
  const account = await register(request);
  await logIn(page, account);
  await addToBasket(page, 13);
  await toPayment(page, { ebookOnly: true });

  await page.getByRole('radio', { name: 'Cash on Delivery' }).check();
  await expect(page.getByText('No payment details are needed now.')).toBeVisible();
  await page.getByRole('button', { name: 'Pay Now' }).click();

  await expect(page.getByRole('heading', { name: 'Payment successful' })).toHaveCount(0);
  const order = await expectPurchaseConfirmed(page, {
    bookTitle: 'The Two-Minute Reset',
    payable: '₹103.95',
    paymentStatus: 'Pending (pay on delivery)',
    ebookOnly: true,
  });
  expect(order.paymentMethod).toBe('COD');
  expect(order.paymentStatus).toBe('PENDING_ON_DELIVERY');
});

test('E2E-05 checkout validates the address and the payment page needs a checkout first', async ({ page, request }) => {
  const account = await register(request);
  await logIn(page, account);
  await addToBasket(page, 2); // a physical book, so the address is required

  // The payment page cannot be opened directly.
  await page.goto('/payment');
  await expect(page).toHaveURL(/\/checkout$/);

  // An empty address is rejected inline and does not move on.
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expect(page).toHaveURL(/\/checkout$/);
  await expect(page.getByText(/required|enter/i).first()).toBeVisible();

  // Invalid card details are rejected inline and nothing is sent.
  await fillAddress(page);
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expect(page).toHaveURL(/\/payment$/);
  await page.getByLabel('Card number').fill('1234');
  await page.getByRole('button', { name: 'Pay Now' }).click();
  await expect(page).toHaveURL(/\/payment$/);
  await expect(badge(page)).toHaveAccessibleName('Basket, 1 item');
});
