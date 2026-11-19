import Field from '../common/Field.jsx';
import { isIndia } from '../../utils/addressForm.js';

/**
 * The delivery-address fields (FR-CHK-02): full name, phone, address lines 1 and 2, city, state, PIN code and country
 * (default India). For India the state is chosen from the listed states; for another country it is free text.
 * The parent owns `values` and the inline `errors` and decides when to validate.
 */
export default function AddressForm({ values, errors = {}, states = [], onChange }) {
  const india = isIndia(values.country);

  return (
    <>
      <p className="field__required-note">* required</p>
      <Field
        label="Full name"
        name="fullName"
        required
        autoComplete="name"
        value={values.fullName}
        onChange={onChange}
        error={errors.fullName}
      />
      <Field
        label="Phone"
        name="phone"
        required
        type="tel"
        inputMode="numeric"
        autoComplete="tel-national"
        hint="10 digits."
        value={values.phone}
        onChange={onChange}
        error={errors.phone}
      />
      <Field
        label="Address line 1"
        name="addressLine1"
        required
        autoComplete="address-line1"
        value={values.addressLine1}
        onChange={onChange}
        error={errors.addressLine1}
      />
      <Field
        label="Address line 2 (optional)"
        name="addressLine2"
        autoComplete="address-line2"
        value={values.addressLine2}
        onChange={onChange}
        error={errors.addressLine2}
      />
      <Field
        label="City"
        name="city"
        required
        autoComplete="address-level2"
        value={values.city}
        onChange={onChange}
        error={errors.city}
      />
      {india ? (
        <Field label="State" name="state" as="select" required value={values.state} onChange={onChange} error={errors.state}>
          <option value="">Choose a state</option>
          {states.map((state) => (
            <option key={state} value={state}>
              {state}
            </option>
          ))}
        </Field>
      ) : (
        <Field
          label="State"
          name="state"
          required
          autoComplete="address-level1"
          value={values.state}
          onChange={onChange}
          error={errors.state}
        />
      )}
      <Field
        label="PIN code"
        name="pinCode"
        required
        inputMode="numeric"
        autoComplete="postal-code"
        hint="6 digits."
        value={values.pinCode}
        onChange={onChange}
        error={errors.pinCode}
      />
      <Field
        label="Country"
        name="country"
        required
        autoComplete="country-name"
        value={values.country}
        onChange={onChange}
        error={errors.country}
      />
    </>
  );
}
