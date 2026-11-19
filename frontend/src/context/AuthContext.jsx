import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import * as authApi from '../api/authApi.js';

export const AuthContext = createContext(null);

/**
 * Holds the signed-in customer. The JWT lives in an HttpOnly cookie that JavaScript cannot read, so the
 * session is restored by asking the server (`getCurrentCustomer`) and nothing is kept in web storage.
 */
export function AuthProvider({ children }) {
  const [state, setState] = useState({ customer: null, loading: true });

  useEffect(() => {
    let active = true;
    authApi
      .getCurrentCustomer()
      .then((customer) => {
        if (active) setState({ customer, loading: false });
      })
      .catch(() => {
        if (active) setState({ customer: null, loading: false });
      });
    return () => {
      active = false;
    };
  }, []);

  const login = useCallback(async (email, password) => {
    const customer = await authApi.login(email, password);
    setState({ customer, loading: false });
    return customer;
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } finally {
      // The local state is cleared even when the server call fails, so the UI never shows a stale customer.
      setState({ customer: null, loading: false });
    }
  }, []);

  const value = useMemo(
    () => ({ customer: state.customer, loading: state.loading, login, logout }),
    [state.customer, state.loading, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside an AuthProvider.');
  return value;
}
