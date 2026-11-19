import './Spinner.css';

/** Loading indicator announced to assistive technology through role="status". */
export default function Spinner({ label = 'Loading' }) {
  return (
    <div className="spinner" role="status">
      <span className="spinner__ring" aria-hidden="true" />
      <span className="visually-hidden">{label}</span>
    </div>
  );
}
