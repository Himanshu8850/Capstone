import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import Button from '../components/common/Button.jsx';
import ErrorMessage from '../components/common/ErrorMessage.jsx';
import Field from '../components/common/Field.jsx';
import AuthCard from '../components/layout/AuthCard.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useFormSubmit } from '../hooks/useFormSubmit.js';
import { returnToQuery, safeReturnTo } from '../utils/returnTo.js';
import { validateEmail, validateRequired } from '../utils/validators.js';

/** Log in (SCR-02, FR-AUTH-03/04/06): e-mail and password, a generic failure message, then back to `returnTo`. */
export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { submitting, formError, fieldErrors, setFieldErrors, clearFieldError, submit } = useFormSubmit();
  const [values, setValues] = useState({ email: '', password: '' });

  const returnTo = safeReturnTo(searchParams.get('returnTo'));
  const registered = searchParams.get('registered') === '1';

  function change(event) {
    const { name, value } = event.target;
    setValues((current) => ({ ...current, [name]: value }));
    clearFieldError(name);
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const errors = {
      email: validateEmail(values.email),
      password: validateRequired(values.password, 'your password'),
    };
    const found = Object.fromEntries(Object.entries(errors).filter(([, message]) => message));
    if (Object.keys(found).length > 0) {
      setFieldErrors(found);
      return;
    }
    const ok = await submit(() => login(values.email.trim(), values.password));
    if (ok) navigate(returnTo, { replace: true });
  }

  return (
    <AuthCard
      title="Log in"
      footer={
        <>
          New to BookyGeek? <Link to={`/register${returnToQuery(returnTo)}`}>Create an account</Link>
        </>
      }
    >
      {registered ? (
        <p className="auth-card__notice" role="status">
          Your account is ready. Log in to continue.
        </p>
      ) : null}
      <form onSubmit={handleSubmit} noValidate>
        <p className="field__required-note">* required</p>
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
          autoComplete="current-password"
          required
          value={values.password}
          onChange={change}
          error={fieldErrors.password}
        />
        {formError ? <ErrorMessage message={formError} /> : null}
        <div className="auth-card__actions">
          <Button type="submit" disabled={submitting}>
            {submitting ? 'Logging in…' : 'Log in'}
          </Button>
        </div>
      </form>
    </AuthCard>
  );
}
