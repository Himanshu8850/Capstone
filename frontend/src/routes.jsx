import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import Spinner from './components/common/Spinner.jsx';
import { useAuth } from './context/AuthContext.jsx';
import AddressesPage from './pages/AddressesPage.jsx';
import BasketPage from './pages/BasketPage.jsx';
import BookDetailPage from './pages/BookDetailPage.jsx';
import CataloguePage from './pages/CataloguePage.jsx';
import CheckoutPage from './pages/CheckoutPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import NotFoundPage from './pages/NotFoundPage.jsx';
import OrderDetailPage from './pages/OrderDetailPage.jsx';
import OrdersPage from './pages/OrdersPage.jsx';
import PaymentConfirmedPage from './pages/PaymentConfirmedPage.jsx';
import PaymentPage from './pages/PaymentPage.jsx';
import ProfilePage from './pages/ProfilePage.jsx';
import PurchaseConfirmedPage from './pages/PurchaseConfirmedPage.jsx';
import RegisterPage from './pages/RegisterPage.jsx';
import WishlistPage from './pages/WishlistPage.jsx';
import WritersPage from './pages/WritersPage.jsx';

/**
 * Sends a visitor who is not signed in to /login?returnTo=<requested page> (A-12). Customer data is never
 * rendered for a guest. While the session is being restored nothing but a spinner is shown.
 */
function ProtectedRoute({ children }) {
  const { customer, loading } = useAuth();
  const location = useLocation();

  if (loading) return <Spinner label="Checking your session" />;
  if (!customer) {
    const returnTo = encodeURIComponent(`${location.pathname}${location.search}${location.hash}`);
    return <Navigate to={`/login?returnTo=${returnTo}`} replace />;
  }
  return children;
}

const protect = (element) => <ProtectedRoute>{element}</ProtectedRoute>;

/** All routes of research R-22. */
export default function AppRoutes() {
  return (
    <Routes>
      <Route path="/" element={<CataloguePage />} />
      <Route path="/books" element={<CataloguePage />} />
      <Route path="/books/:id" element={<BookDetailPage />} />
      <Route path="/basket" element={<BasketPage />} />
      <Route path="/wishlist" element={<WishlistPage />} />
      <Route path="/writers" element={<WritersPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/checkout" element={protect(<CheckoutPage />)} />
      <Route path="/payment" element={protect(<PaymentPage />)} />
      <Route path="/payment/confirmed/:orderId" element={protect(<PaymentConfirmedPage />)} />
      <Route path="/purchase/confirmed/:orderId" element={protect(<PurchaseConfirmedPage />)} />
      <Route path="/orders" element={protect(<OrdersPage />)} />
      <Route path="/orders/:id" element={protect(<OrderDetailPage />)} />
      <Route path="/account/addresses" element={protect(<AddressesPage />)} />
      <Route path="/profile" element={protect(<ProfilePage />)} />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
