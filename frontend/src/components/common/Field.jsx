import { useId } from 'react';
import './Field.css';

/**
 * Labelled form control (DS-04). The label is above the control; the error text and the optional hint are
 * linked with aria-describedby so screen readers announce them. `as` selects input, select or textarea.
 */
export default function Field({
  label,
  error,
  hint,
  as: Control = 'input',
  id,
  className = '',
  required = false,
  children,
  ...rest
}) {
  const generatedId = useId();
  const controlId = id || generatedId;
  const errorId = `${controlId}-error`;
  const hintId = `${controlId}-hint`;
  const describedBy = [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(' ') || undefined;

  return (
    <div className={['field', error ? 'field--invalid' : '', className].filter(Boolean).join(' ')}>
      <label className="field__label" htmlFor={controlId}>
        {label}
        {required ? <span aria-hidden="true"> *</span> : null}
      </label>
      <Control
        id={controlId}
        className="field__control"
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={describedBy}
        required={required || undefined}
        {...rest}
      >
        {children}
      </Control>
      {hint ? (
        <p id={hintId} className="field__hint">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} className="field__error" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}
