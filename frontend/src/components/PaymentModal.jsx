import { useState } from 'react';
import Modal from './Modal.jsx';
import { post } from '../api.js';
import { formatINR, today } from '../format.js';

// Enter வரவு (amount received), the date and a remark for one chit in a DRAFT month.
export default function PaymentModal({ sheet, row, onClose, onSaved }) {
  const [amount, setAmount] = useState(Number(row.paidAmount) > 0 ? String(Number(row.paidAmount)) : '');
  const [date, setDate] = useState(row.paidDate || today());
  const [remark, setRemark] = useState(row.remark || '');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const value = amount === '' ? 0 : Number(amount);

  async function submit(e) {
    e.preventDefault();
    setError('');
    if (!(value >= 0)) return setError('Amount cannot be negative');
    if (value > 0 && !date) return setError('Select the date received');
    setBusy(true);
    try {
      await post('/sheets/payment', {
        year: sheet.year,
        month: sheet.month,
        chitNo: row.chitNo,
        paidAmount: value,
        paidDate: value > 0 ? date : null,
        remark,
      });
      onSaved(`Chit ${row.chitNo}: ${value > 0 ? formatINR(value) + ' saved' : 'cleared'}`);
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  return (
    <Modal title={`Chit ${row.chitNo} · payment`} onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <div className="pay-who">
          <div className="name">{row.memberName}</div>
          {row.memberNameTamil && <div className="name-ta" lang="ta">{row.memberNameTamil}</div>}
          <div className="muted small">Month {row.paidMonth} · Due {formatINR(row.dueAmount)}</div>
        </div>

        <label className="field">
          <span>Amount received (வரவு)</span>
          <input type="number" inputMode="decimal" min="0" step="0.01" value={amount}
            onChange={(e) => setAmount(e.target.value)} placeholder="0" autoFocus />
        </label>
        <div className="quick">
          <button type="button" className="btn btn-small" onClick={() => setAmount(String(Number(row.dueAmount)))}>
            Full due {formatINR(row.dueAmount)}
          </button>
          <button type="button" className="btn btn-small" onClick={() => setAmount('')}>Nothing received</button>
        </div>

        <label className="field">
          <span>Date received</span>
          <input type="date" value={date} onChange={(e) => setDate(e.target.value)} disabled={value === 0} />
        </label>

        <label className="field">
          <span>Remark <em>(optional)</em></span>
          <input type="text" maxLength={500} value={remark} onChange={(e) => setRemark(e.target.value)}
            placeholder="e.g. paid in cash, last month's balance 500 added" />
        </label>

        {error && <div className="alert alert-error" role="alert">{error}</div>}

        <div className="modal-actions">
          <button type="button" className="btn btn-ghost" onClick={onClose}>Cancel</button>
          <button type="submit" className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
        </div>
      </form>
    </Modal>
  );
}
