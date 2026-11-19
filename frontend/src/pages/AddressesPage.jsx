import SavedAddresses from '../components/address/SavedAddresses.jsx';
import PageShell from '../components/layout/PageShell.jsx';
import './AddressesPage.css';

/** Saved addresses (/account/addresses, FR-CHK-03): the shared list with its add, edit and delete dialogs. */
export default function AddressesPage() {
  return (
    <PageShell>
      <h1 className="addresses__heading">Saved addresses</h1>
      <SavedAddresses />
    </PageShell>
  );
}