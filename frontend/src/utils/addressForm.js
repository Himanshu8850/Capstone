import { validatePhone, validatePinCode, validateRequired } from './validators.js';

const DEFAULT_COUNTRY = 'India';

export const EMPTY_ADDRESS = {
  fullName: '',
  phone: '',
  addressLine1: '',
  addressLine2: '',
  city: '',
  state: '',
  pinCode: '',
  country: DEFAULT_COUNTRY,
};

/** True when the entered country is India (the state must then be one of the listed states). */
export function isIndia(country) {
  return String(country ?? '').trim() === '' || String(country).trim().toLowerCase() === DEFAULT_COUNTRY.toLowerCase();
}

/** Form values of a saved address (null fields become empty strings). */
export function addressToValues(address) {
  return {
    fullName: address.fullName ?? '',
    phone: address.phone ?? '',
    addressLine1: address.addressLine1 ?? '',
    addressLine2: address.addressLine2 ?? '',
    city: address.city ?? '',
    state: address.state ?? '',
    pinCode: address.pinCode ?? '',
    country: address.country ?? DEFAULT_COUNTRY,
  };
}

/** The contract's `AddressInput` for form values: trimmed, and no `addressLine2` when it is blank. */
export function valuesToAddress(values) {
  const address = {
    fullName: values.fullName.trim(),
    phone: values.phone.trim(),
    addressLine1: values.addressLine1.trim(),
    city: values.city.trim(),
    state: values.state.trim(),
    pinCode: values.pinCode.trim(),
    country: values.country.trim() || DEFAULT_COUNTRY,
  };
  if (values.addressLine2.trim()) address.addressLine2 = values.addressLine2.trim();
  return address;
}

/** Inline errors per field (FR-CHK-04), or an empty object when the values are valid. The server validates again. */
export function validateAddressValues(values) {
  const errors = {
    fullName: validateRequired(values.fullName, 'the full name'),
    phone: validatePhone(values.phone),
    addressLine1: validateRequired(values.addressLine1, 'address line 1'),
    city: validateRequired(values.city, 'the city'),
    state: validateRequired(values.state, 'the state'),
    pinCode: validatePinCode(values.pinCode),
  };
  return Object.fromEntries(Object.entries(errors).filter(([, message]) => message));
}
