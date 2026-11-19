import { AuthProvider } from './context/AuthContext.jsx';
import { BasketProvider } from './context/BasketContext.jsx';
import AppRoutes from './routes.jsx';

/** Application root: the two global contexts (the only global state) around the routes. */
export default function App() {
  return (
    <AuthProvider>
      <BasketProvider>
        <AppRoutes />
      </BasketProvider>
    </AuthProvider>
  );
}
