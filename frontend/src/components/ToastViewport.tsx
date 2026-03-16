import { Link } from 'react-router-dom';
import { useJobs } from '../jobs/JobsContext';
import '../styles/ToastViewport.scss';

function ToastViewport() {
  const { toasts, dismissToast } = useJobs();

  if (toasts.length === 0) {
    return null;
  }

  return (
    <div className="toast-viewport" aria-live="polite" aria-label="Notifications">
      {toasts.map((toast) => (
        <article key={toast.id} className={`toast toast--${toast.kind}`}>
          <div className="toast__body">
            <p className="toast__title">{toast.title}</p>
            <p className="toast__description">{toast.description}</p>
          </div>
          <div className="toast__actions">
            {toast.ctaLabel && toast.ctaTo ? (
              <Link className="toast__link" to={toast.ctaTo} onClick={() => dismissToast(toast.id)}>
                {toast.ctaLabel}
              </Link>
            ) : null}
            <button type="button" className="toast__dismiss" onClick={() => dismissToast(toast.id)}>
              Dismiss
            </button>
          </div>
        </article>
      ))}
    </div>
  );
}

export default ToastViewport;
