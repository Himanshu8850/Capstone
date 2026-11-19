import { useCallback } from 'react';
import { getBookFilterOptions, listBrands, listCategories } from '../api/catalogueApi.js';
import BookCard from '../components/book/BookCard.jsx';
import RecommendedForYou from '../components/book/RecommendedForYou.jsx';
import BrandNav from '../components/catalogue/BrandNav.jsx';
import CategoryNav from '../components/catalogue/CategoryNav.jsx';
import FilterBar from '../components/catalogue/FilterBar.jsx';
import Button from '../components/common/Button.jsx';
import EmptyState from '../components/common/EmptyState.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import Pagination from '../components/common/Pagination.jsx';
import Spinner from '../components/common/Spinner.jsx';
import PageShell from '../components/layout/PageShell.jsx';
import useAsync from '../hooks/useAsync.js';
import useCatalogueQuery from '../hooks/useCatalogueQuery.js';
import './CataloguePage.css';

/**
 * The landing page and the catalogue (`/`, FR-LAND-01..04, FR-CAT-01..10). Guests can use all of it. The category tree
 * and the Brands list are in the sidebar; search is in the header and filters and sort are above the results; 12 books
 * per page, three cards to a row. Every choice is kept in the URL. A signed-in customer with order history also sees
 * "Recommended for You" (FR-LAND-03, FR-CAT-09) above the results, which is hidden when there is nothing to show.
 */
export default function CataloguePage() {
  const { params, books, setFilters, setPage, clearFilters, hasFilters } = useCatalogueQuery();
  const categories = useAsync((signal) => listCategories(signal), []);
  const brands = useAsync((signal) => listBrands(signal), []);
  const options = useAsync((signal) => getBookFilterOptions(signal), []);

  const selectCategory = useCallback((id) => setFilters({ category: id }), [setFilters]);
  const selectBrand = useCallback((name) => setFilters({ brand: name }), [setFilters]);

  const categoryPanel = categories.error ? (
    <ErrorMessage message="The categories could not be loaded." onRetry={categories.retry} />
  ) : categories.data ? (
    <CategoryNav categories={categories.data} selectedId={params.category} onSelect={selectCategory} />
  ) : (
    <Spinner label="Loading categories" />
  );

  const brandPanel = brands.error ? (
    <ErrorMessage message="The brands could not be loaded." onRetry={brands.retry} />
  ) : brands.data ? (
    <BrandNav brands={brands.data} selectedName={params.brand} onSelect={selectBrand} />
  ) : (
    <Spinner label="Loading brands" />
  );

  return (
    <PageShell categories={categoryPanel} brands={brandPanel}>
      <h1 className="catalogue__heading">Catalogue</h1>

      <FilterBar
        params={params}
        options={options.data}
        onChange={setFilters}
        onClear={clearFilters}
        hasFilters={hasFilters}
      />

      {options.error ? (
        <ErrorMessage message="The filter options could not be loaded." onRetry={options.retry} />
      ) : null}

      <RecommendedForYou />

      <section aria-label="Books" aria-busy={books.loading}>
        {books.error ? (
          <ErrorMessage message="The books could not be loaded." onRetry={books.retry} />
        ) : !books.data ? (
          <Spinner label="Loading books" />
        ) : books.data.items.length === 0 ? (
          <EmptyState
            title="No books found"
            message={
              hasFilters
                ? 'No book matches your search and filters. Try changing or clearing them.'
                : 'There are no books in the catalogue yet.'
            }
          >
            {hasFilters ? <Button onClick={clearFilters}>Clear filters</Button> : null}
          </EmptyState>
        ) : (
          <>
            <p className="catalogue__count" role="status">
              {books.data.totalItems} {books.data.totalItems === 1 ? 'book' : 'books'}
            </p>
            <ul className="catalogue__list">
              {books.data.items.map((book) => (
                <li key={book.id}>
                  <BookCard book={book} />
                </li>
              ))}
            </ul>
            <Pagination page={books.data.page} totalPages={books.data.totalPages} onPageChange={setPage} />
          </>
        )}
      </section>
    </PageShell>
  );
}
