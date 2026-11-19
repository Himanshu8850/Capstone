import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext.jsx';
import Button from '../common/Button.jsx';
import { ProfileIcon } from '../common/Icons.jsx';
import './AccountMenu.css';

/**
 * The account area of the header (FR-AUTH-05): a "Log in" link for a guest, and for a signed-in customer a profile
 * icon (a link to /profile whose accessible name is the customer's name) with a Log out button. Nothing is shown
 * while the session is being restored. Logout cannot be started twice.
 */
export default function AccountMenu() {
  const { customer, loading, logout } = useAuth();
  const [loggingOut, setLoggingOut] = useState(false);

  if (loading) return null;

  if (!customer) {
    return (
      <Link to="/login" className="header__link">
        Log in
      </Link>
    );
  }

  async function handleLogout() {
    if (loggingOut) return;
    setLoggingOut(true);
    try {
      await logout();
    } catch {
      // The session is cleared locally even when the server call fails; there is nothing more to show.
    } finally {
      setLoggingOut(false);
    }
  }

  return (
    <div className="account-menu" role="group" aria-label="Account">
      <Link to="/profile" className="account-menu__profile">
        <ProfileIcon />
        <span className="visually-hidden">{customer.fullName}</span>
      </Link>
      <Button variant="secondary" onClick={handleLogout} disabled={loggingOut}>
        Log out
      </Button>
    </div>
  );
}
