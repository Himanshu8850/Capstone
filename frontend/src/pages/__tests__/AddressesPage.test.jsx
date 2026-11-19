import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('../../api/addressApi.js', () => ({
  listAddresses: vi.fn(),
  createAddress: vi.fn(),
  updateAddress: vi.fn(),
  deleteAddress: vi.fn(),
  listStates: vi.fn(),
}));

import * as addressApi from '../../api/addressApi.js';
import { AuthContext } from '../../context/AuthContext.jsx';
import { BasketContext, EMPTY_BASKET } from '../../context/BasketContext.jsx';
import { address, apiErrors } from '../../test/fixtures/index.js';
import AddressesPage from '../AddressesPage.jsx';

const second = { ...address, id: 2, fullName: 'Ravi Nair', city: 'Kochi', state: 'Kerala', pinCode: '682001', addressLine2: undefined };

function renderPage() {
  render(
    <MemoryRouter>
      <AuthContext.Provider value={{ customer: { id: 1 }, loading: false, login: vi.fn(), logout: vi.fn() }}>
        <BasketContext.Provider value={{ basket: EMPTY_BASKET, itemCount: 0, loading: false, error: null, refresh: vi.fn() }}>
          <AddressesPage />
        </BasketContext.Provider>
      </AuthContext.Provider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  addressApi.listStates.mockResolvedValue(['Karnataka', 'Kerala', 'Maharashtra']);
  addressApi.listAddresses.mockResolvedValue([address, second]);
});

describe('AddressesPage', () => {
  it('lists the saved addresses (FR-CHK-03)', async () => {
    renderPage();

    const items = await screen.findAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(within(items[0]).getByText('Asha Verma')).toBeInTheDocument();
    expect(within(items[0]).getByText('Near City Park')).toBeInTheDocument();
    expect(within(items[0]).getByText('Bengaluru, Karnataka 560001')).toBeInTheDocument();
    expect(within(items[0]).getByText('Phone: 9876543210')).toBeInTheDocument();
    expect(within(items[1]).getByText('Kochi, Kerala 682001')).toBeInTheDocument();
  });

  it('shows an empty state with an Add address action when none is saved (FR-CHK-03)', async () => {
    addressApi.listAddresses.mockResolvedValue([]);
    renderPage();

    expect(await screen.findByText('No saved addresses')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Add address' })).toBeInTheDocument();
  });

  it('shows an error with a retry when the list cannot be loaded', async () => {
    addressApi.listAddresses.mockRejectedValueOnce(apiErrors.INTERNAL_ERROR);
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: 'Retry' }));
    expect(await screen.findAllByRole('listitem')).toHaveLength(2);
  });

  it('adds an address with a state chosen from the list, then reloads the list', async () => {
    addressApi.createAddress.mockResolvedValue({ ...address, id: 3 });
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Add address' }));

    const dialog = within(screen.getByRole('dialog', { name: 'Add address' }));
    expect(dialog.getByLabelText('Country *')).toHaveValue('India');
    await user.type(dialog.getByLabelText('Full name *'), 'Asha Verma');
    await user.type(dialog.getByLabelText('Phone *'), '9876543210');
    await user.type(dialog.getByLabelText('Address line 1 *'), '12 Lake View Road');
    await user.type(dialog.getByLabelText('City *'), 'Bengaluru');
    await user.selectOptions(dialog.getByLabelText('State *'), 'Karnataka');
    await user.type(dialog.getByLabelText('PIN code *'), '560001');
    await user.click(dialog.getByRole('button', { name: 'Save address' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(addressApi.createAddress).toHaveBeenCalledWith({
      fullName: 'Asha Verma',
      phone: '9876543210',
      addressLine1: '12 Lake View Road',
      city: 'Bengaluru',
      state: 'Karnataka',
      pinCode: '560001',
      country: 'India',
    });
    expect(addressApi.listAddresses).toHaveBeenCalledTimes(2);
  });

  it('shows an inline error for each invalid field and does not call the API (FR-CHK-04)', async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Add address' }));
    const dialog = within(screen.getByRole('dialog'));

    await user.type(dialog.getByLabelText('Phone *'), '123456789');
    await user.type(dialog.getByLabelText('PIN code *'), '12345');
    await user.click(dialog.getByRole('button', { name: 'Save address' }));

    expect(dialog.getByText('Enter the full name.')).toBeInTheDocument();
    expect(dialog.getByText('Phone must be exactly 10 digits.')).toBeInTheDocument();
    expect(dialog.getByText('PIN code must be exactly 6 digits.')).toBeInTheDocument();
    expect(dialog.getByText('Enter the state.')).toBeInTheDocument();
    expect(addressApi.createAddress).not.toHaveBeenCalled();
  });

  it('shows the server field errors under the field', async () => {
    addressApi.createAddress.mockRejectedValue({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'The request contains invalid values.',
      fieldErrors: [{ field: 'state', message: 'Choose one of the listed states.' }],
    });
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole('button', { name: 'Add address' }));
    const dialog = within(screen.getByRole('dialog'));
    await user.type(dialog.getByLabelText('Full name *'), 'Asha Verma');
    await user.type(dialog.getByLabelText('Phone *'), '9876543210');
    await user.type(dialog.getByLabelText('Address line 1 *'), '12 Lake View Road');
    await user.type(dialog.getByLabelText('City *'), 'Bengaluru');
    await user.selectOptions(dialog.getByLabelText('State *'), 'Kerala');
    await user.type(dialog.getByLabelText('PIN code *'), '560001');
    await user.click(dialog.getByRole('button', { name: 'Save address' }));

    expect(await dialog.findByText('Choose one of the listed states.')).toBeInTheDocument();
  });

  it('edits an address: the form starts with its values and saves the change', async () => {
    addressApi.updateAddress.mockResolvedValue({ ...address, city: 'Mysuru' });
    const user = userEvent.setup();
    renderPage();
    const [first] = await screen.findAllByRole('listitem');
    await user.click(within(first).getByRole('button', { name: /^Edit/ }));

    const dialog = within(screen.getByRole('dialog', { name: 'Edit address' }));
    expect(dialog.getByLabelText('Full name *')).toHaveValue('Asha Verma');
    expect(dialog.getByLabelText('Address line 2 (optional)')).toHaveValue('Near City Park');
    expect(dialog.getByLabelText('State *')).toHaveValue('Karnataka');
    await user.clear(dialog.getByLabelText('City *'));
    await user.type(dialog.getByLabelText('City *'), 'Mysuru');
    await user.click(dialog.getByRole('button', { name: 'Save address' }));

    await waitFor(() => expect(addressApi.updateAddress).toHaveBeenCalledTimes(1));
    expect(addressApi.updateAddress.mock.calls[0][0]).toBe(1);
    expect(addressApi.updateAddress.mock.calls[0][1]).toMatchObject({ city: 'Mysuru', addressLine2: 'Near City Park' });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('asks before deleting, deletes the address and reloads the list', async () => {
    addressApi.deleteAddress.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderPage();
    const [first] = await screen.findAllByRole('listitem');
    await user.click(within(first).getByRole('button', { name: /^Delete/ }));

    const dialog = within(screen.getByRole('dialog', { name: 'Delete address' }));
    expect(dialog.getByText(/Orders you have already placed keep their delivery address/)).toBeInTheDocument();
    expect(addressApi.deleteAddress).not.toHaveBeenCalled();
    await user.click(dialog.getByRole('button', { name: 'Delete address' }));

    await waitFor(() => expect(addressApi.deleteAddress).toHaveBeenCalledWith(1));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(addressApi.listAddresses).toHaveBeenCalledTimes(2);
  });

  it('keeps the address when the delete is cancelled', async () => {
    const user = userEvent.setup();
    renderPage();
    const [first] = await screen.findAllByRole('listitem');
    await user.click(within(first).getByRole('button', { name: /^Delete/ }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(addressApi.deleteAddress).not.toHaveBeenCalled();
  });

  it('shows a failed delete in the dialog', async () => {
    addressApi.deleteAddress.mockRejectedValue(apiErrors.NOT_FOUND);
    const user = userEvent.setup();
    renderPage();
    const [first] = await screen.findAllByRole('listitem');
    await user.click(within(first).getByRole('button', { name: /^Delete/ }));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete address' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The requested item was not found.');
  });
});
