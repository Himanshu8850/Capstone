import apiClient from './axiosClient.js';

/** Calls for the four authentication operations of `api/openapi.yaml`. Errors arrive already normalised. */

export async function register({ fullName, email, password, confirmPassword }) {
  const response = await apiClient.post('/auth/register', { fullName, email, password, confirmPassword });
  return response.data;
}

export async function login(email, password) {
  const response = await apiClient.post('/auth/login', { email, password });
  return response.data;
}

export async function logout() {
  await apiClient.post('/auth/logout');
}

export async function getCurrentCustomer() {
  const response = await apiClient.get('/auth/me');
  return response.data;
}
