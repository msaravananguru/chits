import { useMemo, useState } from 'react';
import Modal from './Modal.jsx';
import { formatPlan } from '../format.js';

const MAX_SHOWN = 8;

// step 1 of "Edit member": find the member by chit number or name
export default function MemberPickerModal({ chits, onClose, onPick }) {
  const [q, setQ] = useState('');

  const matches = useMemo(() => {
    const text = q.trim().toLowerCase();
    const list = chits.filter((c) => {
      if (!text) return true;
      return (
        String(c.chitNo) === text ||
        String(c.chitNo).startsWith(text) ||
        c.memberName.toLowerCase().includes(text) ||
        (c.memberNameTamil || '').toLowerCase().includes(text)
      );
    });
    // an exact chit number first, then by chit number
    list.sort((a, b) => {
      const ea = String(a.chitNo) === text ? 0 : 1;
      const eb = String(b.chitNo) === text ? 0 : 1;
      return ea - eb || a.chitNo - b.chitNo;
    });
    return list;
  }, [chits, q]);

  const shown = matches.slice(0, MAX_SHOWN);

  const submit = (e) => {
    e.preventDefault();
    if (shown.length === 1) onPick(shown[0]);
  };

  return (
    <Modal title="Edit member" onClose={onClose}>
      <form onSubmit={submit}>
        <div className="step-no">Step 1 of 2 · find the member</div>
        <label className="field">
          <span>Chit no or name</span>
          <input type="search" value={q} onChange={(e) => setQ(e.target.value)} autoFocus
            placeholder="Type a chit number or part of a name" />
        </label>

        {shown.length === 0 ? (
          <p className="muted">No member matches “{q}”.</p>
        ) : (
          <ul className="pick-list">
            {shown.map((c) => (
              <li key={c.chitNo}>
                <button type="button" className="pick-item" onClick={() => onPick(c)}>
                  <span className={'chit-badge ' + (c.takenMonth != null ? 'is-taken' : 'is-open')}>{c.chitNo}</span>
                  <span className="pick-text">
                    <span className="name">{c.memberName}</span>
                    {c.memberNameTamil && <span className="name-ta" lang="ta">{c.memberNameTamil}</span>}
                    <span className="small muted">
                      {formatPlan(c.planAmount)} ·{' '}
                      {c.takenMonth != null ? `taken, month ${c.takenMonth}` : 'not taken'}
                      {c.status === 'CLOSED' ? ' · closed' : ''}
                    </span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
        {matches.length > shown.length && (
          <p className="muted small">Showing {shown.length} of {matches.length}. Type more to narrow down.</p>
        )}

        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
        </div>
      </form>
    </Modal>
  );
}
