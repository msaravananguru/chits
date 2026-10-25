import { useState } from 'react';
import Modal from './Modal.jsx';
import { post } from '../api.js';
import { formatINR, formatPlan, today } from '../format.js';

const planKey = (p) => String(Number(p));

export default function MemberFormModal({ mode, chit, plans, onClose, onSaved }) {
  const isEdit = mode === 'edit';

  const [form, setForm] = useState(() =>
    isEdit
      ? {
          chitNo: chit.chitNo,
          memberName: chit.memberName,
          memberNameTamil: chit.memberNameTamil || '',
          planAmount: planKey(chit.planAmount),
          joinedDate: chit.joinedDate,
          takenMonth: chit.takenMonth ?? '',
          openingPaid: chit.openingPaid ? String(Number(chit.openingPaid)) : '',
          status: chit.status,
        }
      : {
          chitNo: '',
          memberName: '',
          memberNameTamil: '',
          planAmount: plans[0] ? planKey(plans[0].planAmount) : '',
          joinedDate: today(),
          takenMonth: '',
          openingPaid: '',
          status: 'ACTIVE',
        }
  );
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));
  const plan = plans.find((p) => planKey(p.planAmount) === form.planAmount);

  async function submit(e) {
    e.preventDefault();
    setError('');
    if (!isEdit && !(Number(form.chitNo) > 0)) return setError('Enter the chit number');
    if (!form.memberName.trim()) return setError('Enter the member name');
    if (!form.joinedDate) return setError('Select the joined date');
    if (!isEdit && !form.planAmount) return setError('Select a plan');
    if (form.openingPaid !== '' && !(Number(form.openingPaid) >= 0)) return setError('Opening paid cannot be negative');

    setBusy(true);
    try {
      if (isEdit) {
        await post('/chits/update', {
          chitNo: chit.chitNo,
          memberName: form.memberName,
          memberNameTamil: form.memberNameTamil,
          joinedDate: form.joinedDate,
          openingPaid: form.openingPaid === '' ? 0 : Number(form.openingPaid),
          status: form.status,
        });
        onSaved(`Chit ${chit.chitNo} updated`);
      } else {
        await post('/chits/create', {
          chitNo: Number(form.chitNo),
          memberName: form.memberName,
          memberNameTamil: form.memberNameTamil,
          planAmount: Number(form.planAmount),
          joinedDate: form.joinedDate,
          takenMonth: form.takenMonth ? Number(form.takenMonth) : null,
          openingPaid: form.openingPaid === '' ? 0 : Number(form.openingPaid),
        });
        onSaved(`Chit ${form.chitNo} added`);
      }
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  return (
    <Modal title={isEdit ? `Edit chit ${chit.chitNo}` : 'Add member'} onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <div className="form-grid">
          <label className="field">
            <span>Chit no</span>
            <input type="number" inputMode="numeric" min="1" value={form.chitNo}
              onChange={set('chitNo')} disabled={isEdit} autoFocus={!isEdit} />
          </label>

          <label className="field">
            <span>Plan</span>
            <select value={form.planAmount} onChange={set('planAmount')} disabled={isEdit}>
              {plans.map((p) => (
                <option key={planKey(p.planAmount)} value={planKey(p.planAmount)}>
                  {formatPlan(p.planAmount)} · {formatINR(p.monthlyBeforeTaken)} → {formatINR(p.monthlyAfterTaken)}
                </option>
              ))}
            </select>
          </label>

          <label className="field span-2">
            <span>Member name</span>
            <input type="text" value={form.memberName} onChange={set('memberName')} autoFocus={isEdit} />
          </label>

          <label className="field span-2">
            <span>Name in Tamil <em>(optional)</em></span>
            <input type="text" lang="ta" value={form.memberNameTamil} onChange={set('memberNameTamil')} />
          </label>

          <label className="field">
            <span>Joined date</span>
            <input type="date" value={form.joinedDate} onChange={set('joinedDate')} />
          </label>

          {isEdit ? (
            <label className="field">
              <span>Status</span>
              <select value={form.status} onChange={set('status')}>
                <option value="ACTIVE">Active</option>
                <option value="CLOSED">Closed</option>
              </select>
            </label>
          ) : (
            <label className="field">
              <span>Taken in month <em>(if already taken)</em></span>
              <select value={form.takenMonth} onChange={set('takenMonth')}>
                <option value="">Not taken</option>
                {plan && Array.from({ length: plan.totalMonths }, (_, i) => i + 1).map((m) => (
                  <option key={m} value={m}>Month {m}</option>
                ))}
              </select>
            </label>
          )}

          <label className="field span-2">
            <span>Already paid before this system <em>(optional, ₹)</em></span>
            <input type="number" inputMode="decimal" min="0" step="0.01" value={form.openingPaid}
              onChange={set('openingPaid')} placeholder="0" />
          </label>
        </div>

        {error && <div className="alert alert-error" role="alert">{error}</div>}

        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : isEdit ? 'Save changes' : 'Add member'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
