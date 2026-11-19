import apiClient from './axiosClient.js';

/** Calls for the address operations of `api/openapi.yaml`. An address is the contract's `Address` / `AddressInput`. */

/** `listAddresses`: the signed-in customer's saved addresses, oldest first. */
export async function listAddresses() {
  const response = await apiClient.get('/addresses');
  return response.data;
}

/** `createAddress`: saves an address and resolves to it (with its id). */
export async function createAddress(address) {
  const response = await apiClient.post('/addresses', address);
  return response.data;
}

/** `updateAddress`: replaces a saved address. */
export async function updateAddress(id, address) {
  const response = await apiClient.put(`/addresses/${id}`, address);
  return response.data;
}

/** `deleteAddress`: removes a saved address (orders already placed keep their own copy). */
export async function deleteAddress(id) {
  await apiClient.delete(`/addresses/${id}`);
}

/** `listStates` (public): the 28 states and 8 union territories, alphabetical. */
export async function listStates() {
  const response = await apiClient.get('/reference/states');
  return response.data;
}
