import './Sidebar.css';

/**
 * Category sidebar. The page supplies the content through the `categories` and `brands` slots (the category
 * tree, then the Brands list per PL-04); the sidebar itself only provides the frame and the open/closed state.
 */
export default function Sidebar({ open, categories, brands }) {
  return (
    <aside id="category-sidebar" className={`sidebar${open ? ' sidebar--open' : ''}`} aria-label="Browse by">
      {categories ? (
        <nav className="sidebar__section" aria-label="Categories">
          {categories}
        </nav>
      ) : null}
      {brands ? (
        <nav className="sidebar__section" aria-label="Brands">
          {brands}
        </nav>
      ) : null}
    </aside>
  );
}
