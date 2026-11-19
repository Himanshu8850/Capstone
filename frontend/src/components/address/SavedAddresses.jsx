import { useState } from 'react';
import * as addressApi from '../../api/addressApi.js';
import useAsync from '../../hooks/useAsync.js';
import { useFormSubmit } from '../../hooks/useFormSubmit.js';
import {
  EMPTY_ADDRESS,
  addressToValues,
  validateAddressValues,
  valuesToAddress,
} from '../../utils/addressForm.js';
import Button from '../common/Button.jsx';
import EmptyState from '../common/EmptyState.jsx';
import ErrorMessage from '../common/ErrorMessage.jsx';
import Modal from '../common/Modal.jsx';
import Spinner from '../common/Spinner.jsx';
import AddressForm from './AddressForm.jsx';
import './SavedAddresses.css';

function AddressSummary({ address }) {
  return (
    <address className="address-summary">
      <strong>{address.fullName}</strong>
      <span>{address.addressLine1}</span>
      {address.addressLine2 ? <span>{address.addressLine2}</span> : null}
      <span>
        {address.city}, {address.state} {address.pinCode}
      </span>
      <span>{address.country}</span>
      <span>Phone: {address.phone}</span>
    </address>
  );
}

function AddressDialog({ address, states, onSaved, onClose }) {
  const { submitting, formError, fieldErrors, setFieldErrors, clearFieldError, submit } = useFormSubmit();
  const [values, setValues] = useState(address ? addressToValues(address) : EMPTY_ADDRESS);

  function change(event) {
    const { name, value } = event.target;
    setValues((current) => ({ ...current, [name]: value }));
    clearFieldError(name);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const found = validateAddressValues(values);
    if (Object.keys(found).length > 0) {
      setFieldErrors(found);
      return;
    }
    const input = valuesToAddress(values);
    let saved;
    const ok = await submit(async () => {
      saved = address ? await addressApi.updateAddress(address.id, input) : await addressApi.createAddress(input);
    });
    if (ok) onSaved(saved);
  }

  return (
    <Modal title={address ? 'Edit address' : 'Add address'} onClose={onClose}>
      <form onSubmit={handleSubmit} noValidate>
        <AddressForm values={values} errors={fieldErrors} states={states} onChange={change} />
        {formError ? <ErrorMessage message={formError} /> : null}
        <div className="addresses__dialog-actions">
          <Button variant="secondary" onClick={onClose} disabled={submitting}>
            Cancel
          </Button>
          <Button type="submit" disabled={submitting}>
            {submitting ? 'Savingâ€¦' : 'Save address'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

function DeleteDialog({ address, onDeleted, onClose }) {
  const { submitting, formError, submit } = useFormSubmit();

  async function confirm() {
    const ok = await submit(() => addressApi.deleteAddress(address.id));
    if (ok) onDeleted(address);
  }

  return (
    <Modal title="Delete address" onClose={onClose}>
      <p>Delete this address? Orders you have already placed keep their delivery address.</p>
      <AddressSummary address={address} />
      {formError ? <ErrorMessage message={formError} /> : null}
      <div className="addresses__dialog-actions">
        <Button variant="secondary" onClick={onClose} disabled={submitting}>
          Cancel
        </Button>
        <Button onClick={confirm} disabled={submitting}>
          {submitting ? 'Deletingâ€¦' : 'Delete address'}
        </Button>
      </div>
    </Modal>
  );
}

/**
 * The saved addresses of the signed-in customer (FR-CHK-03): list, add, edit and delete. The server owns every
 * address, so the list is read again after each change. Deleting never alters an order already placed (the order has
 * its own copy). Shared by the Saved addresses page and the profile page.
 */
export default function SavedAddresses() {
  const addresses = useAsync((signal) => addressApi.listAddresses(signal), []);
  const states = useAsync(() => addressApi.listStates(), []);
  const [dialog, setDialog] = useState(null);

  function close() {
    setDialog(null);
  }

  function changed() {
    close();
    addresses.retry();
  }

  let content;
  if (addresses.error) {
    content = <ErrorMessage message="Your addresses could not be loaded." onRetry={addresses.retry} />;
  } else if (!addresses.data) {
    content = <Spinner label="Loading your addresses" />;
  } else if (addresses.data.length === 0) {
    content = (
      <EmptyState title="No saved addresses" message="Add an address to use it at checkout.">
        <Button onClick={() => setDialog({ type: 'edit', address: null })}>Add address</Button>
      </EmptyState>
    );
  } else {
    content = (
      <>
        <ul className="addresses__list">
          {addresses.data.map((address) => (
            <li key={address.id} className="addresses__item">
              <AddressSummary address={address} />
              <div className="addresses__actions">
                <Button variant="secondary" onClick={() => setDialog({ type: 'edit', address })}>
                  Edit<span className="visually-hidden"> address of {address.fullName}, {address.addressLine1}</span>
                </Button>
                <Button variant="secondary" onClick={() => setDialog({ type: 'delete', address })}>
                  Delete<span className="visually-hidden"> address of {address.fullName}, {address.addressLine1}</span>
                </Button>
              </div>
            </li>
          ))}
        </ul>
        <Button onClick={() => setDialog({ type: 'edit', address: null })}>Add address</Button>
      </>
    );
  }

  return (
    <>
      {content}
      {dialog?.type === 'edit' ? (
        <AddressDialog address={dialog.address} states={states.data ?? []} onSaved={changed} onClose={close} />
      ) : null}
      {dialog?.type === 'delete' ? <DeleteDialog address={dialog.address} onDeleted={changed} onClose={close} /> : null}
    </>
  );
}