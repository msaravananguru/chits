import { useState } from 'react';
import Modal from './Modal.jsx';
import { post } from '../api.js';
import { formatINR } from '../format.js';

export default function TakenModal({ chit, plan, onClose, onSaved }) {
  const [month, setMonth] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const total = plan ? plan.totalMonths : 0;
  const m = Number(month);

  async function submit(e) {
    e.preventDefault();
    if (!m) return setError('Select the month');
    setError('');
    setBusy(true);
    try {
      await post('/chits/taken', { chitNo: chit.chitNo, takenMonth: m });
      onSaved(`Chit ${chit.chitNo} marked as taken in month ${m}`);
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  return (
    <Modal title={`Mark chit ${chit.chitNo} as taken`} onClose={onClose}>
      <form onSubmit={submit}>
        <p className="muted">{chit.memberName}</p>
        <label className="field">
          <span>Taken in month</span>
          <select value={month} onChange={(e) => setMonth(e.target.value)} autoFocus>
            <option value="">Select month</option>
            {Array.from({ length: total }, (_, i) => i + 1).map((n) => (
              <option key={n} value={n}>Month {n}</option>
            ))}
          </select>
        </label>

        {m > 0 && plan && (
          <div className="alert alert-info">
            Pays {formatINR(plan.monthlyBeforeTaken)} up to month {m}, then{' '}
            {formatINR(plan.monthlyAfterTaken)} from month {m + 1} onward.
          </div>
        )}
        <div className="alert alert-warn">This cannot be changed afterwards.</div>

        {error && <div className="alert alert-error" role="alert">{error}</div>}

        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : 'Confirm'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
