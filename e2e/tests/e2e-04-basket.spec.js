import { expect, test } from '@playwright/test';
import { PASSWORD } from '../credentials.js';

/**
 * E2E-04 (quickstart): a guest adds two books -> logs in -> the guest basket is merged into the customer's basket
 * (quantities summed, capped at min(10, stock), unavailable books skipped with a report) -> change the quantity,
 * remove a line, the 11th unit is rejected, and the header badge always shows the number of units.
 * Each run registers its own customer. Runs against E2E_BASE_URL.
 *
 * Seed facts used: book 1 "The Habit Ladder" (paperback, 499.00, 25 in stock), book 13 "The Two-Minute Reset"
 * (eBook, 99.00), book 5 "Morning Pages Reloaded" (physical, stock 0).
 */

const badge = (page) => page.getByRole('link', { name: /^Basket, \d+ items?$/ });
const lineOf = (page, title) => page.getByRole('listitem').filter({ has: page.getByRole('heading', { name: title }) });

async function register(page, request) {
  const stamp = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const email = `basket.${stamp}@example.com`;
  const password = PASSWORD;
  const response = await request.post('/api/v1/auth/register', {
    data: { fullName: 'Asha Verma', email, password, confirmPassword: password },
  });
  expect(response.status()).toBe(201);
  return { email, password };
}

async function logIn(page, { email, password }, returnTo = '/') {
  await page.goto(`/login${returnTo === '/' ? '' : `?returnTo=${encodeURIComponent(returnTo)}`}`);
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
}

test('E2E-04 a guest basket is merged at login, then quantities change, lines are removed and the 11th unit is rejected', async ({
  page,
  request,
}) => {
  const account = await register(page, request);

  // A guest adds two books from their pages; the badge counts units and nothing is sent to the server.
  await page.goto('/books/1');
  await page.getByRole('article').first().getByRole('button', { name: 'Add to Cart' }).click();
  await expect(badge(page)).toHaveAccessibleName('Basket, 1 item');
  await page.goto('/books/13');
  await page.getByRole('article').first().getByRole('button', { name: 'Add to Cart' }).click();
  await expect(badge(page)).toHaveAccessibleName('Basket, 2 items');
  await expect(page.getByRole('article').first().getByRole('link', { name: 'Go to Cart' })).toBeVisible();

  // The guest basket page prices the lines with the API: 499 + 99 = 598, tax 29.90, delivery 40.00.
  await page.getByRole('link', { name: /^Basket, / }).click();
  await expect(page.getByRole('heading', { name: 'Basket' })).toBeVisible();
  const totals = page.getByRole('region', { name: 'Order totals' });
  await expect(totals.getByText('Subtotal').locator('xpath=following-sibling::dd')).toHaveText('₹598.00');
  await expect(totals.getByText('Tax (5%)').locator('xpath=following-sibling::dd')).toHaveText('₹29.90');
  await expect(totals.getByText('Delivery').locator('xpath=following-sibling::dd')).toHaveText('₹40.00');
  await expect(totals.getByText('Total', { exact: true }).locator('xpath=following-sibling::dd')).toHaveText('₹667.90');
  expect(await page.evaluate(() => window.localStorage.getItem('bookygeek.guestBasket'))).toBe(
    JSON.stringify([
      { bookId: 1, quantity: 1 },
      { bookId: 13, quantity: 1 },
    ]),
  );

  // Add an unavailable book and extra units of book 1 straight into the guest storage (as another tab could),
  // then log in: quantities are summed, capped at min(10, stock) and the unavailable book is reported.
  await page.evaluate(() =>
    window.localStorage.setItem(
      'bookygeek.guestBasket',
      JSON.stringify([
        { bookId: 1, quantity: 10 },
        { bookId: 13, quantity: 1 },
        { bookId: 5, quantity: 2 },
      ]),
    ),
  );
  await logIn(page, account, '/basket');
  await expect(page).toHaveURL(/\/basket$/);

  const notice = page.getByRole('status', { name: 'Basket update' });
  await expect(notice).toContainText('Morning Pages Reloaded is out of stock and was not added.');
  await expect(lineOf(page, 'The Habit Ladder').getByLabel('Quantity 10')).toBeVisible();
  await expect(lineOf(page, 'The Two-Minute Reset').getByLabel('Quantity 1')).toBeVisible();
  await expect(lineOf(page, 'Morning Pages Reloaded')).toHaveCount(0);
  await expect(badge(page)).toHaveAccessibleName('Basket, 11 items');
  expect(await page.evaluate(() => window.localStorage.getItem('bookygeek.guestBasket'))).toBeNull();
  await notice.getByRole('button', { name: 'Dismiss' }).click();
  await expect(notice).toHaveCount(0);

  // The merged basket survives a reload: it now lives on the server.
  await page.reload();
  await expect(lineOf(page, 'The Habit Ladder').getByLabel('Quantity 10')).toBeVisible();

  // The 11th unit is not possible: the stepper stops at 10, and the API rejects it with QUANTITY_LIMIT.
  await expect(lineOf(page, 'The Habit Ladder').getByRole('button', { name: /Increase quantity/ })).toBeDisabled();
  await page.goto('/books/1');
  const control = page.getByRole('article').first();
  await expect(control.getByRole('button', { name: 'Increase quantity' })).toBeDisabled();
  await expect(control.getByText('Maximum quantity reached')).toBeVisible();
  await expect(badge(page)).toHaveAccessibleName('Basket, 11 items');

  // Change a quantity and remove a line; the totals and the badge follow.
  await page.goto('/basket');
  const habit = lineOf(page, 'The Habit Ladder');
  await habit.getByRole('button', { name: /Decrease quantity/ }).click();
  await expect(habit.getByLabel('Quantity 9')).toBeVisible();
  await expect(badge(page)).toHaveAccessibleName('Basket, 10 items');

  await lineOf(page, 'The Two-Minute Reset').getByRole('button', { name: /Remove/ }).click();
  await expect(lineOf(page, 'The Two-Minute Reset')).toHaveCount(0);
  await expect(badge(page)).toHaveAccessibleName('Basket, 9 items');
  await expect(page.getByRole('region', { name: 'Order totals' }).getByText('Subtotal').locator('xpath=following-sibling::dd')).toHaveText(
    '₹4,491.00',
  );

  // Removing the last line shows the empty state with a way back.
  await habit.getByRole('button', { name: /Remove/ }).click();
  await expect(page.getByRole('heading', { name: 'Your basket is empty' })).toBeVisible();
  await page.getByRole('link', { name: 'Continue shopping' }).click();
  await expect(page.getByRole('heading', { name: 'Catalogue' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Basket, 0 items' })).toBeVisible();
});

test('E2E-04 a guest basket survives a reload, rejects the 11th unit and cannot add an unavailable book', async ({
  page,
}) => {
  await page.goto('/books/13');
  const control = page.getByRole('article').first();
  await control.getByRole('button', { name: 'Add to Cart' }).click();
  for (let i = 0; i < 9; i += 1) await control.getByRole('button', { name: 'Increase quantity' }).click();
  await expect(badge(page)).toHaveAccessibleName('Basket, 10 items');

  // The 11th unit is not offered: plus is disabled at 10 and says why.
  await expect(control.getByRole('button', { name: 'Increase quantity' })).toBeDisabled();
  await expect(control.getByText('Maximum quantity reached')).toBeVisible();
  await expect(badge(page)).toHaveAccessibleName('Basket, 10 items');

  await page.reload();
  await expect(badge(page)).toHaveAccessibleName('Basket, 10 items');

  // An out-of-stock physical book shows Unavailable and no cart control at all.
  await page.goto('/books/5');
  await expect(page.getByRole('article').first().getByText('Unavailable', { exact: true })).toBeVisible();
  await expect(page.getByRole('article').first().getByRole('button')).toHaveCount(0);
});

test('E2E-04 checkout asks a guest to log in and returns to the checkout', async ({ page, request }) => {
  const account = await register(page, request);
  await page.goto('/books/13');
  await page.getByRole('article').first().getByRole('button', { name: 'Add to Cart' }).click();
  await expect(page.getByRole('article').first().getByRole('link', { name: 'Go to Cart' })).toBeVisible();
  await page.goto('/basket');
  await page.getByRole('link', { name: 'Proceed to Checkout' }).click();

  await expect(page).toHaveURL(/\/login\?returnTo=%2Fcheckout/);
  await page.getByLabel('E-mail').fill(account.email);
  await page.getByLabel('Password').fill(account.password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).toHaveURL(/\/checkout$/);
});
