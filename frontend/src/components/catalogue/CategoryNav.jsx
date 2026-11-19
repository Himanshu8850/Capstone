import './CatalogueNav.css';

/**
 * Category navigation (FR-CAT-01): an "All" entry, then each top-level category with its sub-categories. The selected
 * entry is marked with `aria-current`. Selecting a parent lists its own books and those of its children.
 */
export default function CategoryNav({ categories, selectedId, onSelect }) {
  const selected = selectedId ? Number(selectedId) : null;

  function item(category, className = '') {
    const current = selected === category.id;
    return (
      <button
        type="button"
        className={`catalogue-nav__item ${className}${current ? ' catalogue-nav__item--current' : ''}`.trim()}
        aria-current={current ? 'true' : undefined}
        onClick={() => onSelect(category.id)}
      >
        {category.name}
      </button>
    );
  }

  return (
    <div className="catalogue-nav">
      <h2 className="catalogue-nav__heading">Categories</h2>
      <ul className="catalogue-nav__list">
        <li>
          <button
            type="button"
            className={`catalogue-nav__item${selected === null ? ' catalogue-nav__item--current' : ''}`}
            aria-current={selected === null ? 'true' : undefined}
            onClick={() => onSelect(null)}
          >
            All
          </button>
        </li>
        {categories.map((category) => (
          <li key={category.id}>
            {item(category, 'catalogue-nav__item--parent')}
            {category.children.length > 0 ? (
              <ul className="catalogue-nav__list catalogue-nav__list--nested">
                {category.children.map((child) => (
                  <li key={child.id}>{item(child)}</li>
                ))}
              </ul>
            ) : null}
          </li>
        ))}
      </ul>
    </div>
  );
}
