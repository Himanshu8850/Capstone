import { useState } from 'react';
import { Link, NavLink, useLocation, useNavigate } from 'react-router-dom';
import { useBasketContext } from '../../context/BasketContext.jsx';
import { CartIcon, SearchIcon } from '../common/Icons.jsx';
import AccountMenu from './AccountMenu.jsx';
import './Header.css';

function HeaderSearchForm({ initial, current }) {
  const navigate = useNavigate();
  const [text, setText] = useState(initial);

  function submit(event) {
    event.preventDefault();
    const q = text.trim();
    // On the catalogue the other filters stay; the page returns to the first one.
    const next = new URLSearchParams(current);
    next.delete('page');
    if (q) next.set('q', q);
    else next.delete('q');
    const query = next.toString();
    navigate(query ? `/?${query}` : '/');
  }

  return (
    <form className="header__search" role="search" aria-label="Site search" onSubmit={submit}>
      <input
        className="header__search-input"
        type="search"
        maxLength={100}
        aria-label="Search the catalogue"
        placeholder="Search by title or author"
        value={text}
        onChange={(event) => setText(event.target.value)}
      />
      <button type="submit" className="header__search-submit" aria-label="Find books">
        <SearchIcon />
      </button>
    </form>
  );
}

/** Search box of the header: opens the catalogue with the typed text as the search term (`/?q=`). */
function HeaderSearch() {
  const { pathname, search } = useLocation();
  const onCatalogue = pathname === '/' || pathname === '/books';
  const current = onCatalogue ? search : '';
  const urlText = new URLSearchParams(current).get('q') ?? '';
  // Re-created when the term changes from outside (for example "Clear filters").
  return <HeaderSearchForm key={urlText} initial={urlText} current={current} />;
}

/**
 * Common header (DS-07): brand, optional menu icon that toggles the category sidebar (CL-25), My Orders, My Wishlist,
 * My Writers, a search box, the basket with item count, and the account area (profile icon). Every element is
 * functional; nothing is inert.
 */
export default function Header({ sidebarAvailable = false, sidebarOpen = false, onToggleSidebar }) {
  const { itemCount } = useBasketContext();

  return (
    <header className="header">
      <div className="header__inner">
        {sidebarAvailable ? (
          <button
            type="button"
            className="header__menu"
            aria-label="Categories"
            aria-expanded={sidebarOpen}
            aria-controls="category-sidebar"
            onClick={onToggleSidebar}
          >
            <span aria-hidden="true">☰</span>
          </button>
        ) : null}

        <Link to="/" className="header__brand">
          BookyGeek
        </Link>

        <nav className="header__nav" aria-label="Main">
          <NavLink to="/orders" className="header__link">
            My Orders
          </NavLink>
          <NavLink to="/wishlist" className="header__link">
            My Wishlist
          </NavLink>
          <NavLink to="/writers" className="header__link">
            My Writers
          </NavLink>
        </nav>

        <HeaderSearch />

        <div className="header__actions">
          <Link
            to="/basket"
            className="header__basket"
            aria-label={`Basket, ${itemCount} ${itemCount === 1 ? 'item' : 'items'}`}
          >
            <CartIcon />
            {itemCount > 0 ? (
              <span className="header__badge" aria-hidden="true">
                {itemCount}
              </span>
            ) : null}
          </Link>

          <AccountMenu />
        </div>
      </div>
    </header>
  );
}
