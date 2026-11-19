import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/authApi.js', () => ({
  register: vi.fn(),
  login: vi.fn(),
  logout: vi.fn(),
  getCurrentCustomer: vi.fn(),
}));

import * as authApi from '../../api/authApi.js';
import { AuthContext } from '../../context/AuthContext.jsx';
import { BasketContext, EMPTY_BASKET } from '../../context/BasketContext.jsx';
import RegisterPage from '../RegisterPage.jsx';
import { PASSWORD, WRONG_PASSWORD } from '../../test/credentials';

function Where() {
  const location = useLocation();
  return <p data-testid="where">{`${location.pathname}${location.search}`}</p>;
}

function renderRegister(url = '/register') {
  render(
    <MemoryRouter initialEntries={[url]}>
      <AuthContext.Provider value={{ customer: null, loading: false, login: vi.fn(), logout: vi.fn() }}>
        <BasketContext.Provider value={{ basket: EMPTY_BASKET, itemCount: 0 }}>
          <Routes>
            <Route path="/register" element={<RegisterPage />} />
            <Route path="*" element={<p>elsewhere</p>} />
          </Routes>
          <Where />
        </BasketContext.Provider>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
}

const fillAll = async ({ name = 'Asha Verma', email = 'asha@example.com', password = PASSWORD, confirm = PASSWORD } = {}) => {
  if (name) await userEvent.type(screen.getByLabelText('Full name *'), name);
  if (email) await userEvent.type(screen.getByLabelText('E-mail *'), email);
  if (password) await userEvent.type(screen.getByLabelText('Password *'), password);
  if (confirm) await userEvent.type(screen.getByLabelText('Confirm password *'), confirm);
};

const submit = () => userEvent.click(screen.getByRole('button', { name: 'Create account' }));

beforeEach(() => vi.clearAllMocks());

describe('RegisterPage', () => {
  it('shows an error for every invalid field and does not call the server', async () => {
    renderRegister();
    await submit();
    expect(screen.getByText('Enter your full name.')).toBeInTheDocument();
    expect(screen.getByText('Enter your e-mail address.')).toBeInTheDocument();
    expect(screen.getByText('Password must be 8 to 64 characters.')).toBeInTheDocument();
    expect(screen.getByText('Confirm your password.')).toBeInTheDocument();
    expect(authApi.register).not.toHaveBeenCalled();
  });

  it.each([
    ['short', 'Ab1', 'Password must be 8 to 64 characters.'],
    ['no digit', 'Abcdefgh', 'Password must contain at least one digit.'],
    ['no letter', '12345678', 'Password must contain at least one letter.'],
  ])('rejects a password with %s', async (_label, password, message) => {
    renderRegister();
    await fillAll({ password, confirm: password });
    await submit();
    expect(screen.getByText(message)).toBeInTheDocument();
    expect(authApi.register).not.toHaveBeenCalled();
  });

  it('rejects a confirmation that does not match', async () => {
    renderRegister();
    await fillAll({ confirm: WRONG_PASSWORD });
    await submit();
    expect(screen.getByText('The passwords do not match.')).toBeInTheDocument();
    expect(authApi.register).not.toHaveBeenCalled();
  });

  it('registers with trimmed values, then opens the login page with the account-created notice', async () => {
    authApi.register.mockResolvedValue({ id: 1, fullName: 'Asha Verma', email: 'asha@example.com' });
    renderRegister();
    await fillAll({ name: ' Asha Verma ', email: ' asha@example.com ' });
    await submit();
    await waitFor(() => expect(screen.getByText('elsewhere')).toBeInTheDocument());
    expect(authApi.register).toHaveBeenCalledWith({
      fullName: 'Asha Verma',
      email: 'asha@example.com',
      password: PASSWORD,
      confirmPassword: PASSWORD,
    });
    expect(screen.getByTestId('where')).toHaveTextContent('/login?registered=1');
  });

  it('keeps returnTo on the way to the login page', async () => {
    authApi.register.mockResolvedValue({ id: 1 });
    renderRegister('/register?returnTo=%2Fcheckout');
    await fillAll();
    await submit();
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/login?registered=1&returnTo=%2Fcheckout'));
  });

  it('shows the server field errors next to their fields (EMAIL_TAKEN is a form-level message)', async () => {
    authApi.register.mockRejectedValueOnce({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'The request contains invalid values.',
      fieldErrors: [{ field: 'email', message: 'Enter a valid e-mail address.' }],
    });
    renderRegister();
    await fillAll();
    await submit();
    expect(await screen.findByText('Enter a valid e-mail address.')).toBeInTheDocument();
    expect(screen.getByLabelText('E-mail *')).toHaveAttribute('aria-invalid', 'true');

    authApi.register.mockRejectedValueOnce({
      status: 409,
      code: 'EMAIL_TAKEN',
      message: 'An account with this e-mail address already exists.',
      fieldErrors: [],
    });
    await submit();
    expect(await screen.findByRole('alert')).toHaveTextContent('An account with this e-mail address already exists.');
    expect(screen.getByRole('heading', { name: 'Register' })).toBeInTheDocument();
  });

  it('prevents a double submit while the request is running', async () => {
    let resolve;
    authApi.register.mockImplementation(() => new Promise((r) => (resolve = r)));
    renderRegister();
    await fillAll();
    await submit();
    expect(screen.getByRole('button', { name: 'Creating account…' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Creating account…' }));
    expect(authApi.register).toHaveBeenCalledTimes(1);
    resolve({ id: 1 });
    await waitFor(() => expect(screen.getByText('elsewhere')).toBeInTheDocument());
  });

  it('clears a field error as soon as the customer edits that field', async () => {
    renderRegister();
    await submit();
    expect(screen.getByText('Enter your full name.')).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Full name *'), 'A');
    expect(screen.queryByText('Enter your full name.')).not.toBeInTheDocument();
  });
});
