import './CatalogueNav.css';

/**
 * Brand list (FR-CAT-02, PL-04): the publishers that have listed books, each with its book count. Placed in the
 * sidebar below the categories, because no wireframe shows a brand control. "All brands" clears the choice.
 */
export default function BrandNav({ brands, selectedName, onSelect }) {
  return (
    <div className="catalogue-nav">
      <h2 className="catalogue-nav__heading">Brands</h2>
      <ul className="catalogue-nav__list">
        <li>
          <button
            type="button"
            className={`catalogue-nav__item${selectedName ? '' : ' catalogue-nav__item--current'}`}
            aria-current={selectedName ? undefined : 'true'}
            onClick={() => onSelect(null)}
          >
            All brands
          </button>
        </li>
        {brands.map((brand) => {
          const current = selectedName === brand.name;
          return (
            <li key={brand.name}>
              <button
                type="button"
                className={`catalogue-nav__item${current ? ' catalogue-nav__item--current' : ''}`}
                aria-current={current ? 'true' : undefined}
                onClick={() => onSelect(brand.name)}
              >
                {brand.name} <span className="catalogue-nav__count">({brand.bookCount})</span>
              </button>
            </li>
          );
        })}
      </ul>
    </div>
  );
}
