const LABELS = { PAPERBACK: 'Paperback', HARDCOVER: 'Hardcover', EBOOK: 'eBook' };

/** Customer-facing name of a contract `BookFormat` value; an unknown value is shown as given. */
export function formatLabel(format) {
  return LABELS[format] ?? String(format ?? '');
}

/** Catalogue link that filters by one author (FR-CAT-03). */
export function authorLink(author) {
  return `/?${new URLSearchParams({ author })}`;
}

/** Catalogue link that filters by one category or genre tag (FR-CAT-03). */
export function categoryLink(categoryId) {
  return `/?${new URLSearchParams({ category: String(categoryId) })}`;
}
