import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth.jsx';

export default function SignIn() {
  const { user, signIn } = useAuth();
  const [id, setId] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  if (user) return <Navigate to="/" replace />;

  async function submit(e) {
    e.preventDefault();
    if (!Number(id)) return setError('Enter your user ID');
    setError('');
    setBusy(true);
    try {
      await signIn(Number(id));
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  return (
    <div className="signin-page">
      <form className="signin-card" onSubmit={submit}>
        <div className="brand">Sudar Chits</div>
        <h1>Sign in</h1>
        <label className="field">
          <span>User ID</span>
          <input type="number" inputMode="numeric" min="1" value={id}
            onChange={(e) => setId(e.target.value)} autoFocus />
        </label>
        {error && <div className="alert alert-error" role="alert">{error}</div>}
        <button className="btn btn-primary btn-block" disabled={busy}>
          {busy ? 'Signing in…' : 'Sign in'}
        </button>
        <p className="muted small">Temporary sign-in by user ID. Username and password login will be added later.</p>
      </form>
    </div>
  );
}
