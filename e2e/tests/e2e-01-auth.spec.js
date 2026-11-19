import { expect, test } from '@playwright/test';
import { PASSWORD, WRONG_PASSWORD } from '../credentials.js';

/**
 * E2E-01 (quickstart): register -> login -> account menu shows the name -> logout -> /orders redirects to login
 * -> login returns to /orders. Runs against the Compose stack (E2E_BASE_URL, default http://127.0.0.1:3000).
 * Each run registers its own customer, so the test needs no seeded accounts and leaves no shared state behind.
 * The password comes from TEST_USER_PASSWORD (git-ignored .env or the environment); it is never committed.
 */
test('E2E-01 register, log in, log out and return to the requested page', async ({ page }) => {
  const stamp = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const fullName = 'Asha Verma';
  const email = `asha.${stamp}@example.com`;
  const password = PASSWORD;

  // Register.
  await page.goto('/register');
  await expect(page.getByRole('heading', { name: 'Register' })).toBeVisible();
  await page.getByLabel('Full name').fill(fullName);
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Password *', { exact: true }).fill(password);
  await page.getByLabel('Confirm password').fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();

  // Registration does not sign in: the login page opens with the account-created notice.
  await expect(page).toHaveURL(/\/login\?registered=1/);
  await expect(page.getByRole('status')).toContainText('Your account is ready');

  // Log in; the account menu shows the customer's name.
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).toHaveURL('/');
  const account = page.getByRole('group', { name: 'Account' });
  await expect(account).toContainText(fullName);

  // The session is restored from the HttpOnly cookie after a reload, with nothing kept in web storage.
  await page.reload();
  await expect(page.getByRole('group', { name: 'Account' })).toContainText(fullName);
  const storage = await page.evaluate(() => ({ local: window.localStorage.length, session: window.sessionStorage.length }));
  expect(storage.session).toBe(0);
  expect(await page.evaluate(() => document.cookie)).not.toContain('BG_AUTH');

  // Log out; the protected page now redirects to login and keeps the requested page.
  await page.getByRole('button', { name: 'Log out' }).click();
  await expect(page.getByRole('link', { name: 'Log in' })).toBeVisible();
  await page.goto('/orders');
  await expect(page).toHaveURL(/\/login\?returnTo=%2Forders/);
  await expect(page.getByRole('heading', { name: 'Log in' })).toBeVisible();

  // A wrong password shows the generic message and stays on the page.
  await page.getByLabel('E-mail').fill(email);
  await page.getByLabel('Password').fill(WRONG_PASSWORD);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByRole('alert')).toContainText('The e-mail or password is incorrect.');

  // Correct credentials return the customer to /orders.
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).toHaveURL('/orders');
  await expect(page.getByRole('heading', { name: 'My Orders' })).toBeVisible();
  await expect(page.getByRole('group', { name: 'Account' })).toContainText(fullName);
});

test('E2E-01 a duplicate e-mail in a different letter case is rejected with a clear message', async ({ page }) => {
  const stamp = `${Date.now()}${Math.floor(Math.random() * 1000)}`;
  const email = `dup.${stamp}@example.com`;
  const password = PASSWORD;

  async function register(address) {
    await page.goto('/register');
    await page.getByLabel('Full name').fill('Asha Verma');
    await page.getByLabel('E-mail').fill(address);
    await page.getByLabel('Password *', { exact: true }).fill(password);
    await page.getByLabel('Confirm password').fill(password);
    await page.getByRole('button', { name: 'Create account' }).click();
  }

  await register(email);
  await expect(page).toHaveURL(/\/login\?registered=1/);

  await register(email.toUpperCase());
  await expect(page.getByRole('alert')).toContainText('An account with this e-mail address already exists.');
  await expect(page.getByRole('heading', { name: 'Register' })).toBeVisible();
});
