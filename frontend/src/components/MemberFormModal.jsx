import { useState } from 'react';
import Modal from './Modal.jsx';
import { post } from '../api.js';
import { formatINR, formatPlan, today } from '../format.js';

const planKey = (p) => String(Number(p));

export default function MemberFormModal({ mode, chit, plans, plan: chitPlan, nextChitNo, onBack, onMarkTaken, onClose, onSaved }) {
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
          payMode: chit.payMode || 'SINGLE',
          dailyAmount: chit.dailyAmount ? String(Number(chit.dailyAmount)) : '',
          status: chit.status,
        }
      : {
          chitNo: nextChitNo ? String(nextChitNo) : '',
          memberName: '',
          memberNameTamil: '',
          planAmount: plans[0] ? planKey(plans[0].planAmount) : '',
          joinedDate: today(),
          takenMonth: '',
          openingPaid: '',
          payMode: 'SINGLE',
          dailyAmount: '',
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
    if (isEdit && form.openingPaid !== '' && !(Number(form.openingPaid) >= 0)) return setError('Opening paid cannot be negative');
    if (form.payMode === 'DAILY' && form.dailyAmount !== '' && !(Number(form.dailyAmount) > 0)) return setError('Usual daily amount must be greater than 0');
    const payFields = {
      payMode: form.payMode,
      dailyAmount: form.payMode === 'DAILY' && form.dailyAmount !== '' ? Number(form.dailyAmount) : null,
    };

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
          ...payFields,
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
          openingPaid: 0,
          ...payFields,
        });
        onSaved(`Chit ${form.chitNo} added`);
      }
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  return (
    <Modal title={isEdit ? `Chit ${chit.chitNo} · ${chit.memberName}` : 'Add member'} onClose={onClose}>
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

          {!isEdit && nextChitNo > 1 && (
            <div className="hint span-2">
              Suggested: the last chit is {nextChitNo - 1}, so the next is {nextChitNo}. You can change it.
            </div>
          )}

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

          <div className="pay-style span-2">
            <div className="pay-style-title">How does this member pay?</div>
            <div className="segmented seg-2" role="group" aria-label="Pay style">
              <button type="button" className={form.payMode === 'SINGLE' ? 'on' : ''}
                onClick={() => setForm((f) => ({ ...f, payMode: 'SINGLE' }))}>Single payment</button>
              <button type="button" className={form.payMode === 'DAILY' ? 'on' : ''}
                onClick={() => setForm((f) => ({ ...f, payMode: 'DAILY' }))}>Daily / part payment</button>
            </div>
            {form.payMode === 'DAILY' && (
              <label className="field">
                <span>Usual daily amount <em>(optional, ₹)</em></span>
                <input type="number" inputMode="decimal" min="0" step="0.01" value={form.dailyAmount}
                  onChange={set('dailyAmount')} placeholder="e.g. 200" />
              </label>
            )}
            <div className="small muted">
              {form.payMode === 'DAILY'
                ? 'Collectors will see "Daily" on this member, and the usual amount is pre-filled in Collection. It can be changed any time.'
                : 'Pays the full amount at once.'}
            </div>
          </div>

          {/* only when editing: opening paid is for old balances, not for new members */}
          {isEdit && (
            <label className="field span-2">
              <span>Already paid before this system <em>(optional, ₹)</em></span>
              <input type="number" inputMode="decimal" min="0" step="0.01" value={form.openingPaid}
                onChange={set('openingPaid')} placeholder="0" />
            </label>
          )}
        </div>

        {isEdit && (
          <div className="taken-box">
            <div>
              <div className="name">Chit taken</div>
              <div className="small muted">
                {chit.takenMonth != null
                  ? `Taken in month ${chit.takenMonth}${chitPlan ? ` · ${formatINR(chitPlan.monthlyAfterTaken)} a month from month ${chit.takenMonth + 1}` : ''}`
                  : chit.status === 'ACTIVE' ? 'Not taken yet' : 'Not taken'}
              </div>
            </div>
            {chit.takenMonth == null && chit.status === 'ACTIVE' && onMarkTaken && (
              <button type="button" className="btn btn-small" onClick={onMarkTaken}>Mark taken…</button>
            )}
          </div>
        )}

        {error && <div className="alert alert-error" role="alert">{error}</div>}

        <div className="modal-actions">
          {isEdit && onBack
            ? <button type="button" className="btn btn-ghost" onClick={onBack}>← Back</button>
            : <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>}
          <button type="submit" className="btn btn-primary" disabled={busy}>
            {busy ? 'Saving…' : isEdit ? 'Save changes' : 'Add member'}
          </button>
        </div>
      </form>
    </Modal>
  );
}
