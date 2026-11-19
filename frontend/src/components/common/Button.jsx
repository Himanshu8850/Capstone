import './Button.css';

/**
 * Shared button (DS-03). Primary = blue filled, secondary = grey.
 * `type` defaults to "button" so a button never submits a form by accident.
 */
export default function Button({
  variant = 'primary',
  type = 'button',
  className = '',
  children,
  ...rest
}) {
  const classes = ['btn', `btn--${variant}`, className].filter(Boolean).join(' ');
  return (
    <button type={type} className={classes} {...rest}>
      {children}
    </button>
  );
}
