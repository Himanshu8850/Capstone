import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import Sidebar from '../Sidebar.jsx';
import CategoryNav from '../../catalogue/CategoryNav.jsx';
import BrandNav from '../../catalogue/BrandNav.jsx';
import { brands, categories } from '../../../test/fixtures/index.js';

describe('Sidebar with the category tree and the Brands list', () => {
  it('shows All, the two levels of the tree and highlights the selection', () => {
    render(
      <Sidebar
        open
        categories={<CategoryNav categories={categories} selectedId="13" onSelect={vi.fn()} />}
        brands={<BrandNav brands={brands} selectedName="Northwind Books" onSelect={vi.fn()} />}
      />,
    );
    expect(screen.getByRole('button', { name: 'All' })).not.toHaveAttribute('aria-current');
    expect(screen.getByRole('button', { name: 'Fiction' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Romance' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Self Help' })).toHaveAttribute('aria-current', 'true');
    expect(screen.getByRole('button', { name: /Northwind Books/ })).toHaveAttribute('aria-current', 'true');
    expect(screen.getByRole('button', { name: 'All brands' })).not.toHaveAttribute('aria-current');
  });

  it('marks All as selected when nothing is chosen and reports each choice', async () => {
    const onCategory = vi.fn();
    const onBrand = vi.fn();
    render(
      <Sidebar
        open
        categories={<CategoryNav categories={categories} selectedId={null} onSelect={onCategory} />}
        brands={<BrandNav brands={brands} selectedName={null} onSelect={onBrand} />}
      />,
    );
    expect(screen.getByRole('button', { name: 'All' })).toHaveAttribute('aria-current', 'true');
    expect(screen.getByRole('button', { name: 'All brands' })).toHaveAttribute('aria-current', 'true');

    await userEvent.click(screen.getByRole('button', { name: 'Non-Fiction' }));
    expect(onCategory).toHaveBeenCalledWith(2);
    await userEvent.click(screen.getByRole('button', { name: 'All' }));
    expect(onCategory).toHaveBeenLastCalledWith(null);
    await userEvent.click(screen.getByRole('button', { name: /Maple Leaf Press/ }));
    expect(onBrand).toHaveBeenCalledWith('Maple Leaf Press');
  });

  it('lists the brand counts', () => {
    render(<BrandNav brands={brands} selectedName={null} onSelect={vi.fn()} />);
    expect(screen.getByRole('button', { name: /Maple Leaf Press/ })).toHaveTextContent('(8)');
  });
});
