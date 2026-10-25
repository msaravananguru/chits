import { useCallback, useEffect, useMemo, useState } from 'react';
import Modal from './Modal.jsx';
import { post } from '../api.js';
import { formatDate, formatINR, receiptDateLimits } from '../format.js';

// All receipts of one chit in one month: list, add, edit, delete. A locked month is read-only.
export default function PaymentsModal({ sheet, row, onClose, onChanged }) {
  const readOnly = sheet.status !== 'DRAFT';
  const limits = useMemo(() => receiptDateLimits(sheet.year, sheet.month), [sheet.year, sheet.month]);

  const [payments, setPayments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editId, setEditId] = useState(null);
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(limits.initial);
  const [remark, setRemark] = useState('');
  const [confirmId, setConfirmId] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');

  const load = useCallback(async () => {
    try {
      const res = await post('/payments/list', { year: sheet.year, month: sheet.month, chitNo: row.chitNo });
      setPayments(res.payments);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [sheet.year, sheet.month, row.chitNo]);

  useEffect(() => { load(); }, [load]);

  const received = payments.reduce((a, p) => a + Number(p.amount), 0);
  const left = Math.max(Number(row.dueAmount) - received, 0);

  function resetForm() {
    setEditId(null);
    setAmount('');
    setDate(limits.initial);
    setRemark('');
  }

  function startEdit(p) {
    setError('');
    setInfo('');
    setConfirmId(null);
    setEditId(p.id);
    setAmount(String(Number(p.amount)));
    setDate(p.paidDate);
    setRemark(p.remark || '');
  }

  async function submit(e) {
    e.preventDefault();
    setError('');
    setInfo('');
    const value = Number(amount);
    if (!(value > 0)) return setError('Enter an amount greater than 0');
    if (!date) return setError('Select the date received');
    setBusy(true);
    try {
      if (editId) {
        await post('/payments/update', { id: editId, paidDate: date, amount: value, remark });
        setInfo('Payment changed');
      } else {
        await post('/payments/add', { chitNo: row.chitNo, paidDate: date, amount: value, remark });
        setInfo(`${formatINR(value)} added`);
      }
      resetForm();
      await load();
      onChanged();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  async function remove(id) {
    if (confirmId !== id) { setConfirmId(id); return; }
    setError('');
    setInfo('');
    setBusy(true);
    try {
      await post('/payments/delete', { id });
      setConfirmId(null);
      if (editId === id) resetForm();
      setInfo('Payment deleted');
      await load();
      onChanged();
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title={`Chit ${row.chitNo} · payments`} onClose={onClose}>
      <div className="pay-who">
        <div className="name">{row.memberName}</div>
        {row.memberNameTamil && <div className="name-ta" lang="ta">{row.memberNameTamil}</div>}
        {row.paidMonth ? <div className="muted small">Month {row.paidMonth} of the chit</div> : null}
      </div>

      <div className="mini-stats">
        <div><span>Due</span><b>{formatINR(row.dueAmount)}</b></div>
        <div><span>Received</span><b>{formatINR(received)}</b></div>
        <div><span>Left this month</span><b>{formatINR(left)}</b></div>
      </div>

      {loading ? (
        <p className="muted">Loading…</p>
      ) : payments.length === 0 ? (
        <p className="muted small">No payment in this month yet.</p>
      ) : (
        <ul className="receipts">
          {payments.map((p) => (
            <li key={p.id} className={editId === p.id ? 'editing' : ''}>
              <div className="receipt-main">
                <b>{formatINR(p.amount)}</b>
                <span>{formatDate(p.paidDate)}</span>
                {p.remark && <em>{p.remark}</em>}
              </div>
              {!readOnly && (
                <div className="receipt-actions">
                  <button type="button" className="btn btn-small" onClick={() => startEdit(p)} disabled={busy}>Edit</button>
                  <button type="button" className={'btn btn-small' + (confirmId === p.id ? ' btn-danger' : '')}
                    onClick={() => remove(p.id)} disabled={busy}>
                    {confirmId === p.id ? 'Sure? Delete' : 'Delete'}
                  </button>
                </div>
              )}
            </li>
          ))}
        </ul>
      )}

      {readOnly ? (
        <div className="alert alert-warn">This month is locked. Payments cannot be changed.</div>
      ) : (
        <form onSubmit={submit} noValidate className="pay-form">
          <h3>{editId ? 'Change payment' : 'Add payment'}</h3>
          <div className="form-grid">
            <label className="field">
              <span>Amount (₹)</span>
              <input type="number" inputMode="decimal" min="0" step="0.01" value={amount}
                onChange={(e) => setAmount(e.target.value)} placeholder="0" autoFocus />
            </label>
            <label className="field">
              <span>Date received</span>
              <input type="date" value={date} min={limits.min} max={limits.max} onChange={(e) => setDate(e.target.value)} />
            </label>
          </div>
          {!editId && left > 0 && (
            <div className="quick">
              <button type="button" className="btn btn-small" onClick={() => setAmount(String(left))}>
                Balance due {formatINR(left)}
              </button>
            </div>
          )}
          <label className="field">
            <span>Remark <em>(optional)</em></span>
            <input type="text" maxLength={500} value={remark} onChange={(e) => setRemark(e.target.value)}
              placeholder="e.g. cash, last month's balance" />
          </label>

          {error && <div className="alert alert-error" role="alert">{error}</div>}
          {info && !error && <div className="alert alert-ok" role="status">{info}</div>}

          <div className="modal-actions">
            {editId && <button type="button" className="btn btn-ghost" onClick={resetForm}>Cancel change</button>}
            <button type="button" className="btn btn-ghost" onClick={onClose}>Close</button>
            <button type="submit" className="btn btn-primary" disabled={busy}>
              {busy ? 'Saving…' : editId ? 'Save change' : 'Add payment'}
            </button>
          </div>
        </form>
      )}
      {readOnly && error && <div className="alert alert-error" role="alert">{error}</div>}
      {readOnly && (
        <div className="modal-actions"><button type="button" className="btn" onClick={onClose}>Close</button></div>
      )}
    </Modal>
  );
}
