import PageShell from './PageShell.jsx';
import './AuthCard.css';

/** Page frame of the login and registration screens: the shared shell with a centred card and a heading. */
export default function AuthCard({ title, children, footer }) {
  return (
    <PageShell>
      <section className="auth-card" aria-labelledby="auth-card-title">
        <h1 id="auth-card-title" className="auth-card__title">
          {title}
        </h1>
        {children}
        {footer ? <p className="auth-card__footer">{footer}</p> : null}
      </section>
    </PageShell>
  );
}
