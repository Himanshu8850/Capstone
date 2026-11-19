import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../../context/AuthContext.jsx';
import AccountMenu from '../AccountMenu.jsx';

const customer = { id: 1, fullName: 'Asha Verma', email: 'asha.verma@example.com' };

function renderMenu(auth) {
  return render(
    <MemoryRouter>
      <AuthContext.Provider value={{ customer: null, loading: false, login: vi.fn(), logout: vi.fn(), ...auth }}>
        <AccountMenu />
      </AuthContext.Provider>
    </MemoryRouter>,
  );
}

describe('AccountMenu', () => {
  it('offers only a Log in link to a guest', () => {
    renderMenu();
    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login');
    expect(screen.queryByRole('button', { name: 'Log out' })).not.toBeInTheDocument();
  });

  it('shows a profile icon (named after the customer) and a Log out button once signed in (FR-AUTH-05)', () => {
    renderMenu({ customer });
    expect(screen.getByRole('link', { name: 'Asha Verma' })).toHaveAttribute('href', '/profile');
    expect(screen.getByRole('button', { name: 'Log out' })).toBeEnabled();
    expect(screen.queryByRole('link', { name: 'Log in' })).not.toBeInTheDocument();
  });

  it('renders nothing while the session is being restored', () => {
    const { container } = renderMenu({ loading: true });
    expect(container).toBeEmptyDOMElement();
  });

  it('logs out once and disables the button while the call is running', async () => {
    let finish;
    const logout = vi.fn(() => new Promise((resolve) => (finish = resolve)));
    renderMenu({ customer, logout });
    await userEvent.click(screen.getByRole('button', { name: 'Log out' }));
    expect(screen.getByRole('button', { name: 'Log out' })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: 'Log out' }));
    expect(logout).toHaveBeenCalledTimes(1);
    finish();
    await waitFor(() => expect(screen.getByRole('button', { name: 'Log out' })).toBeEnabled());
  });

  it('survives a failing logout call without an unhandled rejection', async () => {
    const logout = vi.fn(() => Promise.reject({ status: 500, code: 'INTERNAL_ERROR' }));
    renderMenu({ customer, logout });
    await userEvent.click(screen.getByRole('button', { name: 'Log out' }));
    await waitFor(() => expect(logout).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(screen.getByRole('button', { name: 'Log out' })).toBeEnabled());
  });
});
