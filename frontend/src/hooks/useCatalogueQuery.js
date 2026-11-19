import { useCallback, useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { listBooks } from '../api/catalogueApi.js';
import useAsync from './useAsync.js';

/** The URL query parameters the catalogue understands, in the order they are written. */
const QUERY_KEYS = ['category', 'brand', 'q', 'author', 'language', 'format', 'minPrice', 'maxPrice', 'sort'];

/**
 * Catalogue state lives in the URL (`?category=&brand=&q=&author=&language=&format=&minPrice=&maxPrice=&sort=&page=`),
 * so a filtered view can be bookmarked, shared and reached with the browser's Back button.
 * Changing any filter or the sort returns to the first page (FR-CAT-10); only `setPage` keeps the filters.
 */
export default function useCatalogueQuery() {
  const [searchParams, setSearchParams] = useSearchParams();

  const params = useMemo(() => {
    const values = {};
    for (const key of QUERY_KEYS) {
      const value = searchParams.get(key);
      if (value) values[key] = value;
    }
    const page = Number.parseInt(searchParams.get('page') ?? '0', 10);
    return { ...values, page: Number.isInteger(page) && page > 0 ? page : 0 };
  }, [searchParams]);

  /** Sets several filters at once (an empty value removes the filter) and goes back to page 0. */
  const setFilters = useCallback(
    (changes) => {
      const next = new URLSearchParams(searchParams);
      for (const [key, value] of Object.entries(changes)) {
        if (value === '' || value == null) next.delete(key);
        else next.set(key, String(value));
      }
      next.delete('page');
      setSearchParams(next);
    },
    [searchParams, setSearchParams],
  );

  const setPage = useCallback(
    (page) => {
      const next = new URLSearchParams(searchParams);
      if (page > 0) next.set('page', String(page));
      else next.delete('page');
      setSearchParams(next);
    },
    [searchParams, setSearchParams],
  );

  const clearFilters = useCallback(() => setSearchParams(new URLSearchParams()), [setSearchParams]);

  const apiParams = useMemo(() => ({ ...params, page: params.page || undefined }), [params]);
  const books = useAsync((signal) => listBooks(apiParams, signal), [searchParams.toString()]);

  const hasFilters = QUERY_KEYS.some((key) => key !== 'sort' && params[key]);

  return { params, books, setFilters, setPage, clearFilters, hasFilters };
}
