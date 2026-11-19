import { useCallback, useRef, useState } from 'react';

/**
 * State shared by the form pages: a submit that cannot run twice at once, the server's field errors keyed by field
 * name, and one form-level message for errors that belong to no field. `submit(action)` resolves true when the
 * action succeeded. Client-side validation happens in the page before it calls `submit`.
 */
export function useFormSubmit() {
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState('');
  const [fieldErrors, setFieldErrors] = useState({});
  const inFlight = useRef(false);

  const clearFieldError = useCallback((name) => {
    setFieldErrors((current) => {
      if (!current[name]) return current;
      const { [name]: _removed, ...rest } = current;
      return rest;
    });
  }, []);

  const submit = useCallback(async (action) => {
    if (inFlight.current) return false;
    inFlight.current = true;
    setSubmitting(true);
    setFormError('');
    setFieldErrors({});
    try {
      await action();
      return true;
    } catch (error) {
      const fields = Object.fromEntries((error?.fieldErrors ?? []).map((item) => [item.field, item.message]));
      setFieldErrors(fields);
      if (Object.keys(fields).length === 0) {
        setFormError(error?.message || 'Something went wrong. Please try again.');
      }
      return false;
    } finally {
      inFlight.current = false;
      setSubmitting(false);
    }
  }, []);

  return { submitting, formError, fieldErrors, setFieldErrors, clearFieldError, submit };
}
