// Client-side mirrors of the contract rules in api/openapi.yaml (FR-AUTH-02, FR-CHK-02/04, PL-06).
// Each validator returns an error message, or '' when the value is valid. The server re-validates everything.

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PHONE = /^[0-9]{10}$/;
const PIN_CODE = /^[0-9]{6}$/;
const CARD_NUMBER = /^[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{4}$/;
const CVV = /^[0-9]{3}$/;
const EXPIRY = /^(0[1-9]|1[0-2])\/([0-9]{4})$/;
const UPI_ID = /^[A-Za-z0-9._-]{2,64}@[A-Za-z][A-Za-z0-9]{1,31}$/;

const isBlank = (value) => value === undefined || value === null || String(value).trim() === '';

export function validateFullName(value) {
  if (isBlank(value)) return 'Enter your full name.';
  if (String(value).length > 100) return 'Full name can have at most 100 characters.';
  return '';
}

export function validateEmail(value) {
  if (isBlank(value)) return 'Enter your e-mail address.';
  const email = String(value).trim();
  if (email.length > 254 || !EMAIL.test(email)) return 'Enter a valid e-mail address.';
  return '';
}

export function validatePassword(value) {
  const password = String(value ?? '');
  if (password.length < 8 || password.length > 64) return 'Password must be 8 to 64 characters.';
  if (!/[A-Za-z]/.test(password)) return 'Password must contain at least one letter.';
  if (!/[0-9]/.test(password)) return 'Password must contain at least one digit.';
  return '';
}

export function validateConfirmPassword(password, confirmation) {
  if (isBlank(confirmation)) return 'Confirm your password.';
  if (password !== confirmation) return 'The passwords do not match.';
  return '';
}

export function validatePhone(value) {
  if (isBlank(value)) return 'Enter your phone number.';
  return PHONE.test(String(value)) ? '' : 'Phone must be exactly 10 digits.';
}

export function validatePinCode(value) {
  if (isBlank(value)) return 'Enter your PIN code.';
  return PIN_CODE.test(String(value)) ? '' : 'PIN code must be exactly 6 digits.';
}

export function validateRequired(value, label) {
  return isBlank(value) ? `Enter ${label}.` : '';
}

export function validateCardNumber(value) {
  if (isBlank(value)) return 'Enter the card number.';
  return CARD_NUMBER.test(String(value)) ? '' : 'Card number must look like 1234-5678-9012-3456.';
}

export function validateNameOnCard(value) {
  const name = String(value ?? '').trim();
  if (name.length < 2 || name.length > 60) return 'Name on card must be 2 to 60 characters.';
  return '';
}

export function validateCvv(value) {
  if (isBlank(value)) return 'Enter the CVV.';
  return CVV.test(String(value)) ? '' : 'CVV must be exactly 3 digits.';
}

/** `now` is injectable so tests do not depend on the current date. */
export function validateExpiry(value, now = new Date()) {
  if (isBlank(value)) return 'Enter the expiry date.';
  const match = EXPIRY.exec(String(value));
  if (!match) return 'Expiry must be in the form MM/YYYY.';
  const month = Number(match[1]);
  const year = Number(match[2]);
  const currentYear = now.getFullYear();
  const currentMonth = now.getMonth() + 1;
  if (year < currentYear || (year === currentYear && month < currentMonth)) return 'This card has expired.';
  return '';
}

export function validateUpiId(value) {
  if (isBlank(value)) return 'Enter your UPI ID.';
  return UPI_ID.test(String(value)) ? '' : 'Enter a UPI ID such as name@bank.';
}
