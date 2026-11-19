import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import * as authApi from '../api/authApi.js';
import Button from '../components/common/Button.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import Field from '../components/common/Field.jsx';
import AuthCard from '../components/layout/AuthCard.jsx';
import { useFormSubmit } from '../hooks/useFormSubmit.js';
import { returnToQuery, safeReturnTo } from '../utils/returnTo.js';
import {
  validateConfirmPassword,
  validateEmail,
  validateFullName,
  validatePassword,
} from '../utils/validators.js';

/**
 * Register (SCR-03, FR-AUTH-01/02): full name, e-mail, password and confirmation, validated here and again by the
 * server. Registration does not sign the customer in, so the next step is the login page with the same `returnTo`.
 */
export default function RegisterPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { submitting, formError, fieldErrors, setFieldErrors, clearFieldError, submit } = useFormSubmit();
  const [values, setValues] = useState({ fullName: '', email: '', password: '', confirmPassword: '' });

  const returnTo = safeReturnTo(searchParams.get('returnTo'));

  function change(event) {
    const { name, value } = event.target;
    setValues((current) => ({ ...current, [name]: value }));
    clearFieldError(name);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const errors = {
      fullName: validateFullName(values.fullName),
      email: validateEmail(values.email),
      password: validatePassword(values.password),
      confirmPassword: validateConfirmPassword(values.password, values.confirmPassword),
    };
    const found = Object.fromEntries(Object.entries(errors).filter(([, message]) => message));
    if (Object.keys(found).length > 0) {
      setFieldErrors(found);
      return;
    }
    const ok = await submit(() =>
      authApi.register({
        fullName: values.fullName.trim(),
        email: values.email.trim(),
        password: values.password,
        confirmPassword: values.confirmPassword,
      }),
    );
    if (ok) {
      const next = new URLSearchParams({ registered: '1' });
      if (returnTo !== '/') next.set('returnTo', returnTo);
      navigate(`/login?${next.toString()}`, { replace: true });
    }
  }

  return (
    <AuthCard
      title="Register"
      footer={
        <>
          Already have an account?{' '}
          <Link to={`/login${returnToQuery(returnTo)}`}>Log in</Link>
        </>
      }
    >
      <form onSubmit={handleSubmit} noValidate>
        <p className="field__required-note">* required</p>
        <Field
          label="Full name"
          name="fullName"
          autoComplete="name"
          required
          value={values.fullName}
          onChange={change}
          error={fieldErrors.fullName}
        />
        <Field
          label="E-mail"
          name="email"
          type="email"
          autoComplete="email"
          required
          value={values.email}
          onChange={change}
          error={fieldErrors.email}
        />
        <Field
          label="Password"
          name="password"
          type="password"
          autoComplete="new-password"
          required
          hint="8 to 64 characters with at least one letter and one digit."
          value={values.password}
          onChange={change}
          error={fieldErrors.password}
        />
        <Field
          label="Confirm password"
          name="confirmPassword"
          type="password"
          autoComplete="new-password"
          required
          value={values.confirmPassword}
          onChange={change}
          error={fieldErrors.confirmPassword}
        />
        {formError ? <ErrorMessage message={formError} /> : null}
        <div className="auth-card__actions">
          <Button type="submit" disabled={submitting}>
            {submitting ? 'Creating account…' : 'Create account'}
          </Button>
        </div>
      </form>
    </AuthCard>
  );
}
