import Button from '../common/Button.jsx';
import { formatLabel } from '../../utils/bookLinks.js';
import './FilterBar.css';

const SORTS = [
  ['RELEVANCE', 'Relevance'],
  ['PRICE_ASC', 'Price: Low to High'],
  ['PRICE_DESC', 'Price: High to Low'],
  ['TITLE_ASC', 'Title: A to Z'],
];

function bandIndex(bands, params) {
  return bands.findIndex(
    (band) => (band.minPrice ?? '') === (params.minPrice ?? '') && (band.maxPrice ?? '') === (params.maxPrice ?? ''),
  );
}

/**
 * Language / Format / Price filters (all defaulting to "All"), Sort in one row, and the chip for an active author
 * filter (FR-CAT-04..06, CL-16, CL-25). Search lives in the header. The option lists come from the server; a filter
 * that is not yet loaded is shown disabled. Changing anything calls `onChange` and the page returns to the first page.
 */
export default function FilterBar({ params, options, onChange, onClear, hasFilters }) {
  const bands = options?.priceBands ?? [];
  const selectedBand = bandIndex(bands, params);

  return (
    <div className="filter-bar">
      <div className="filter-bar__row">
        <label className="filter-bar__field">
          Language
          <select
            value={params.language ?? ''}
            disabled={!options}
            onChange={(event) => onChange({ language: event.target.value })}
          >
            <option value="">All</option>
            {(options?.languages ?? []).map((language) => (
              <option key={language} value={language}>
                {language}
              </option>
            ))}
          </select>
        </label>

        <label className="filter-bar__field">
          Format
          <select
            value={params.format ?? ''}
            disabled={!options}
            onChange={(event) => onChange({ format: event.target.value })}
          >
            <option value="">All</option>
            {(options?.formats ?? []).map((format) => (
              <option key={format} value={format}>
                {formatLabel(format)}
              </option>
            ))}
          </select>
        </label>

        <label className="filter-bar__field">
          Price
          <select
            value={selectedBand >= 0 ? String(selectedBand) : ''}
            disabled={!options}
            onChange={(event) => {
              const band = bands[Number(event.target.value)];
              onChange({ minPrice: band?.minPrice ?? '', maxPrice: band?.maxPrice ?? '' });
            }}
          >
            <option value="">All</option>
            {bands.map((band, index) => (
              <option key={band.label} value={String(index)}>
                {band.label}
              </option>
            ))}
          </select>
        </label>

        <label className="filter-bar__field">
          Sort by
          <select value={params.sort ?? 'RELEVANCE'} onChange={(event) => onChange({ sort: event.target.value })}>
            {SORTS.map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
      </div>

      {params.author ? (
        <p className="filter-bar__chip">
          Author: <strong>{params.author}</strong>
          <button type="button" className="filter-bar__chip-remove" onClick={() => onChange({ author: '' })}>
            <span aria-hidden="true">×</span>
            <span className="visually-hidden">Remove author filter</span>
          </button>
        </p>
      ) : null}

      {hasFilters ? (
        <Button variant="secondary" onClick={onClear}>
          Clear filters
        </Button>
      ) : null}
    </div>
  );
}
