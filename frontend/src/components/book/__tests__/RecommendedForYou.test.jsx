import { act, render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../../api/basketApi.js', () => ({ getRecommendations: vi.fn() }));

import { getRecommendations } from '../../../api/basketApi.js';
import { AuthContext } from '../../../context/AuthContext.jsx';
import { BasketContext, EMPTY_BASKET } from '../../../context/BasketContext.jsx';
import { ebook, paperback } from '../../../test/fixtures/index.js';
import RecommendedForYou from '../RecommendedForYou.jsx';

function renderSection(customer, props = {}) {
  return render(
    <MemoryRouter>
      <AuthContext.Provider value={{ customer, loading: false, login: vi.fn(), logout: vi.fn() }}>
        <BasketContext.Provider value={{ basket: EMPTY_BASKET, itemCount: 0, addItem: vi.fn() }}>
          <RecommendedForYou {...props} />
        </BasketContext.Provider>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
}

/** Lets the request finish and React apply its result, so an assertion that nothing is shown is not made too early. */
async function settle() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('RecommendedForYou', () => {
  it('lists the recommended books for a signed-in customer (FR-LAND-03, FR-CAT-09)', async () => {
    getRecommendations.mockResolvedValue([paperback, ebook]);
    renderSection({ id: 1 });

    const section = within(await screen.findByRole('region', { name: 'Recommended for You' }));
    expect(section.getByRole('heading', { name: 'Recommended for You' })).toBeInTheDocument();
    expect(section.getAllByRole('listitem')).toHaveLength(2);
    expect(section.getByRole('link', { name: 'The Habit Loop' })).toHaveAttribute('href', '/books/7');
    expect(section.getByRole('link', { name: 'Quiet Mornings' })).toHaveAttribute('href', '/books/12');
    expect(section.getByText('₹500.00')).toBeInTheDocument();
  });

  it('renders nothing when the customer has no recommendations (no order history)', async () => {
    getRecommendations.mockResolvedValue([]);
    const { container } = renderSection({ id: 1 });

    await settle();
    expect(getRecommendations).toHaveBeenCalledTimes(1);
    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByText('Recommended for You')).not.toBeInTheDocument();
  });

  it('renders nothing for a guest and never calls the API', async () => {
    const { container } = renderSection(null);

    await settle();
    expect(container).toBeEmptyDOMElement();
    expect(getRecommendations).not.toHaveBeenCalled();
  });

  it('renders nothing while loading and when the request fails (no error banner on a landing section)', async () => {
    getRecommendations.mockRejectedValue({ status: 500, code: 'INTERNAL_ERROR', message: 'Something went wrong.' });
    const { container } = renderSection({ id: 1 });

    expect(container).toBeEmptyDOMElement();
    await settle();
    expect(getRecommendations).toHaveBeenCalledTimes(1);
    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('renders nothing when the response is not a list', async () => {
    getRecommendations.mockResolvedValue({ items: [] });
    const { container } = renderSection({ id: 1 });

    await settle();
    expect(getRecommendations).toHaveBeenCalledTimes(1);
    expect(container).toBeEmptyDOMElement();
  });

  it('asks again when the refresh key changes, for example after the basket changes', async () => {
    getRecommendations.mockResolvedValue([paperback]);
    const { rerender } = renderSection({ id: 1 }, { refreshKey: '7' });
    await screen.findByRole('region', { name: 'Recommended for You' });

    getRecommendations.mockResolvedValue([ebook]);
    rerender(
      <MemoryRouter>
        <AuthContext.Provider value={{ customer: { id: 1 }, loading: false, login: vi.fn(), logout: vi.fn() }}>
          <BasketContext.Provider value={{ basket: EMPTY_BASKET, itemCount: 0, addItem: vi.fn() }}>
            <RecommendedForYou refreshKey="7,12" />
          </BasketContext.Provider>
        </AuthContext.Provider>
      </MemoryRouter>,
    );

    expect(await screen.findByRole('link', { name: 'Quiet Mornings' })).toBeInTheDocument();
    expect(getRecommendations).toHaveBeenCalledTimes(2);
  });
});
