import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../context/AuthContext.jsx';
import { BasketContext, EMPTY_BASKET } from '../../context/BasketContext.jsx';
import LoginPage from '../LoginPage.jsx';
import { PASSWORD, WRONG_PASSWORD } from '../../test/credentials';

function Where() {
  const location = useLocation();
  return <p data-testid="where">{`${location.pathname}${location.search}`}</p>;
}

function renderLogin({ url = '/login', login = vi.fn(() => Promise.resolve({ id: 1 })) } = {}) {
  const auth = { customer: null, loading: false, login, logout: vi.fn() };
  render(
    <MemoryRouter initialEntries={[url]}>
      <AuthContext.Provider value={auth}>
        <BasketContext.Provider value={{ basket: EMPTY_BASKET, itemCount: 0 }}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="*" element={<p>landed</p>} />
          </Routes>
          <Where />
        </BasketContext.Provider>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
  return { login };
}

const fill = async (email, password) => {
  if (email) await userEvent.type(screen.getByLabelText('E-mail *'), email);
  if (password) await userEvent.type(screen.getByLabelText('Password *'), password);
};

beforeEach(() => vi.clearAllMocks());

describe('LoginPage', () => {
  it('shows field-level errors and does not call the server for empty or malformed input', async () => {
    const { login } = renderLogin();
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));
    expect(screen.getByText('Enter your e-mail address.')).toBeInTheDocument();
    expect(screen.getByText('Enter your password.')).toBeInTheDocument();
    expect(screen.getByLabelText('E-mail *')).toHaveAttribute('aria-invalid', 'true');

    await fill('not-an-email', 'x');
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));
    expect(screen.getByText('Enter a valid e-mail address.')).toBeInTheDocument();
    expect(login).not.toHaveBeenCalled();
  });

  it('logs in with the trimmed e-mail and goes to the home page by default', async () => {
    const { login } = renderLogin();
    await fill('  asha@example.com ', PASSWORD);
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));
    await waitFor(() => expect(screen.getByText('landed')).toBeInTheDocument());
    expect(login).toHaveBeenCalledWith('asha@example.com', PASSWORD);
    expect(screen.getByTestId('where')).toHaveTextContent('/');
  });

  it('returns to the page in returnTo after login (A-12)', async () => {
    renderLogin({ url: '/login?returnTo=%2Forders' });
    await fill('asha@example.com', PASSWORD);
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/orders'));
  });

  it.each(['https://evil.example/x', '//evil.example', '/\\evil', '/login', '/register', 'orders'])(
    'ignores an unsafe or looping returnTo (%s)',
    async (bad) => {
      renderLogin({ url: `/login?returnTo=${encodeURIComponent(bad)}` });
      await fill('asha@example.com', PASSWORD);
      await userEvent.click(screen.getByRole('button', { name: 'Log in' }));
      await waitFor(() => expect(screen.getByText('landed')).toBeInTheDocument());
      expect(screen.getByTestId('where')).toHaveTextContent(/^\/$/);
    },
  );

  it('shows the server ApiError message and keeps the customer on the page', async () => {
    const login = vi.fn(() =>
      Promise.reject({ status: 401, code: 'INVALID_CREDENTIALS', message: 'The e-mail or password is incorrect.', fieldErrors: [] }),
    );
    renderLogin({ login });
    await fill('asha@example.com', WRONG_PASSWORD);
    await userEvent.click(screen.getByRole('button', { name: 'Log in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('The e-mail or password is incorrect.');
    expect(screen.getByRole('heading', { name: 'Log in' })).toBeInTheDocument();
  });

  it('prevents a double submit while the request is running', async () => {
    let resolve;
    const login = vi.fn(() => new Promise((r) => (resolve = r)));
    renderLogin({ login });
    await fill('asha@example.com', PASSWORD);
    const button = screen.getByRole('button', { name: 'Log in' });
    await userEvent.click(button);
    expect(screen.getByRole('button', { name: 'Logging in…' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Logging in…' }));
    expect(login).toHaveBeenCalledTimes(1);
    resolve({ id: 1 });
    await waitFor(() => expect(screen.getByText('landed')).toBeInTheDocument());
  });

  it('shows the account-created notice and links to register keeping returnTo', () => {
    renderLogin({ url: '/login?registered=1&returnTo=%2Forders' });
    expect(screen.getByRole('status')).toHaveTextContent('Your account is ready');
    expect(screen.getByRole('link', { name: 'Create an account' })).toHaveAttribute('href', '/register?returnTo=%2Forders');
  });
});
