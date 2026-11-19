import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { AuthContext } from '../../../context/AuthContext.jsx';
import { BasketContext, EMPTY_BASKET } from '../../../context/BasketContext.jsx';
import Header from '../Header.jsx';
import PageShell from '../PageShell.jsx';
import Sidebar from '../Sidebar.jsx';

const customer = { id: 1, fullName: 'Asha Verma', email: 'asha.verma@example.com' };

function renderWith(ui, { auth = {}, itemCount = 0 } = {}) {
  const authValue = { customer: null, loading: false, login: vi.fn(), logout: vi.fn(() => Promise.resolve()), ...auth };
  const basketValue = { basket: EMPTY_BASKET, itemCount };
  return render(
    <MemoryRouter>
      <AuthContext.Provider value={authValue}>
        <BasketContext.Provider value={basketValue}>{ui}</BasketContext.Provider>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
}

describe('Header', () => {
  it('shows the brand, My Orders, My Wishlist, My Writers, search, the basket and Log in for a guest', () => {
    renderWith(<Header />);
    expect(screen.getByRole('link', { name: 'BookyGeek' })).toHaveAttribute('href', '/');
    expect(screen.getByRole('link', { name: 'My Orders' })).toHaveAttribute('href', '/orders');
    expect(screen.getByRole('link', { name: 'My Wishlist' })).toHaveAttribute('href', '/wishlist');
    expect(screen.getByRole('link', { name: 'My Writers' })).toHaveAttribute('href', '/writers');
    expect(screen.getByRole('searchbox', { name: 'Search the catalogue' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /^Basket/ })).toHaveAttribute('href', '/basket');
    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login');
    expect(screen.queryByRole('button', { name: 'Log out' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Categories' })).not.toBeInTheDocument();
  });

  it('shows the item count badge only when the basket is not empty', () => {
    const { unmount } = renderWith(<Header />, { itemCount: 0 });
    expect(screen.getByRole('link', { name: 'Basket, 0 items' })).toBeInTheDocument();
    expect(screen.queryByText('3')).not.toBeInTheDocument();
    unmount();

    renderWith(<Header />, { itemCount: 3 });
    expect(screen.getByRole('link', { name: 'Basket, 3 items' })).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
  });

  it('uses the singular for one item', () => {
    renderWith(<Header />, { itemCount: 1 });
    expect(screen.getByRole('link', { name: 'Basket, 1 item' })).toBeInTheDocument();
  });

  it('shows a profile icon linking to /profile and a working Log out for a signed-in customer', async () => {
    const logout = vi.fn(() => Promise.resolve());
    renderWith(<Header />, { auth: { customer, logout } });
    expect(screen.getByRole('link', { name: 'Asha Verma' })).toHaveAttribute('href', '/profile');
    expect(screen.getByRole('link', { name: 'Asha Verma' }).querySelector('svg')).not.toBeNull();
    expect(screen.queryByRole('link', { name: 'Log in' })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Log out' }));
    expect(logout).toHaveBeenCalledTimes(1);
  });

  it('shows neither Log in nor Log out while the session is being restored', () => {
    renderWith(<Header />, { auth: { loading: true } });
    expect(screen.queryByRole('link', { name: 'Log in' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Log out' })).not.toBeInTheDocument();
  });

  it('shows the menu icon only when a sidebar exists and reports its state', async () => {
    const onToggle = vi.fn();
    renderWith(<Header sidebarAvailable sidebarOpen onToggleSidebar={onToggle} />);
    const menu = screen.getByRole('button', { name: 'Categories' });
    expect(menu).toHaveAttribute('aria-expanded', 'true');
    await userEvent.click(menu);
    expect(onToggle).toHaveBeenCalledTimes(1);
  });
});

describe('Sidebar', () => {
  it('renders the category and brand slots in labelled navigation regions', () => {
    render(<Sidebar open categories={<p>Tree</p>} brands={<p>Publishers</p>} />);
    expect(screen.getByRole('navigation', { name: 'Categories' })).toHaveTextContent('Tree');
    expect(screen.getByRole('navigation', { name: 'Brands' })).toHaveTextContent('Publishers');
    expect(screen.getByRole('complementary', { name: 'Browse by' })).toHaveClass('sidebar--open');
  });

  it('omits a slot that is not supplied and is closed when open is false', () => {
    render(<Sidebar open={false} categories={<p>Tree</p>} />);
    expect(screen.queryByRole('navigation', { name: 'Brands' })).not.toBeInTheDocument();
    expect(screen.getByRole('complementary', { name: 'Browse by' })).not.toHaveClass('sidebar--open');
  });
});

describe('PageShell', () => {
  it('renders the header and the page content without a sidebar by default', () => {
    renderWith(
      <PageShell>
        <h1>Basket</h1>
      </PageShell>,
    );
    expect(screen.getByRole('banner')).toBeInTheDocument();
    expect(screen.getByRole('main')).toContainElement(screen.getByRole('heading', { name: 'Basket' }));
    expect(screen.queryByRole('complementary')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Categories' })).not.toBeInTheDocument();
  });

  it('toggles the sidebar from the header menu icon', async () => {
    renderWith(
      <PageShell categories={<p>Tree</p>} brands={<p>Publishers</p>}>
        <h1>Catalogue</h1>
      </PageShell>,
    );
    const menu = screen.getByRole('button', { name: 'Categories' });
    const sidebar = screen.getByRole('complementary', { name: 'Browse by' });
    expect(sidebar).toHaveClass('sidebar--open'); // jsdom has no matchMedia, so desktop default
    await userEvent.click(menu);
    expect(sidebar).not.toHaveClass('sidebar--open');
    expect(menu).toHaveAttribute('aria-expanded', 'false');
    await userEvent.click(menu);
    expect(sidebar).toHaveClass('sidebar--open');
  });
});
