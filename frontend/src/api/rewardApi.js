import apiClient from './axiosClient.js';

/** Calls for the reward-point operation of `api/openapi.yaml`. */

/** `getRewardAccount`: the signed-in customer's reward balance. Resolves to `{ balance }` (points; 1 point = ₹1). */
export async function getRewardAccount(signal) {
  const response = await apiClient.get('/rewards/me', { signal });
  return response.data;
}
