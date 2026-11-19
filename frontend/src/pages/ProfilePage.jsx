import { getCurrentCustomer } from '../api/authApi.js';
import { getRewardAccount } from '../api/rewardApi.js';
import SavedAddresses from '../components/address/SavedAddresses.jsx';
import Button from '../components/common/Button.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import Spinner from '../components/common/Spinner.jsx';
import PageShell from '../components/layout/PageShell.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import useAsync from '../hooks/useAsync.js';
import './ProfilePage.css';

function PersonalDetails() {
  const customer = useAsync((signal) => getCurrentCustomer(signal), []);

  return (
    <section className="profile__card" aria-labelledby="profile-details-heading">
      <h2 id="profile-details-heading" className="profile__card-heading">
        Personal Details
      </h2>
      {customer.error ? (
        <ErrorMessage message="Your details could not be loaded." onRetry={customer.retry} />
      ) : !customer.data ? (
        <Spinner label="Loading your details" />
      ) : (
        <dl className="profile__details">
          <div>
            <dt>Full Name</dt>
            <dd>{customer.data.fullName}</dd>
          </div>
          <div>
            <dt>Email Address</dt>
            <dd>{customer.data.email}</dd>
          </div>
        </dl>
      )}
    </section>
  );
}

function GiftPoints() {
  const account = useAsync((signal) => getRewardAccount(signal), []);

  return (
    <section className="profile__card" aria-labelledby="profile-points-heading">
      <h2 id="profile-points-heading" className="profile__card-heading">
        Gift Points
      </h2>
      {account.error ? (
        <ErrorMessage message="Your gift points could not be loaded." onRetry={account.retry} />
      ) : !account.data ? (
        <Spinner label="Loading your gift points" />
      ) : (
        <p className="profile__points">{account.data.balance} pts</p>
      )}
    </section>
  );
}

/**
 * My Profile (/profile, AMD-002): the customer's name and e-mail (read only), the gift points balance, the saved
 * addresses with their add, edit and delete dialogs (the same component as the Saved addresses page) and Log out.
 */
export default function ProfilePage() {
  const { logout } = useAuth();

  return (
    <PageShell>
      <h1 className="profile__heading">My Profile</h1>
      <div className="profile__cards">
        <PersonalDetails />
        <GiftPoints />
      </div>

      <section className="profile__addresses" aria-labelledby="profile-addresses-heading">
        <h2 id="profile-addresses-heading" className="profile__card-heading">
          Saved Addresses
        </h2>
        <SavedAddresses />
      </section>

      <Button variant="secondary" onClick={() => logout().catch(() => {})}>
        Log out
      </Button>
    </PageShell>
  );
}
