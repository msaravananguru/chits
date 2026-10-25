import { useMemo, useState } from 'react';
import { post } from '../api.js';
import { formatDate, formatINR } from '../format.js';
import Modal from './Modal.jsx';
import PaymentsModal from './PaymentsModal.jsx';
import { PrevBalance, PrevText, fromAdvance } from './Balance.jsx';

const pad = (n) => String(n).padStart(2, '0');
const toIso = (y, m, d) => `${y}-${pad(m)}-${pad(d)}`;
const shortDate = (iso) => (iso ? formatDate(iso).slice(0, 6) : '–');
const FILTERS = [['ALL', 'All'], ['PAID', 'Paid'], ['PART', 'Part'], ['NOT_PAID', 'Not paid']];
const RANK = { PART: 0, NOT_PAID: 1, PAID: 2, NONE: 3 };

function StatusChip({ status, row }) {
  if (row && fromAdvance(row)) return <span className="ms ms-paid">✓ From advance</span>;
  if (status === 'PAID') return <span className="ms ms-paid">✓ Paid</span>;
  if (status === 'PART') return <span className="ms ms-part">Part</span>;
  if (status === 'NOT_PAID') return <span className="ms ms-none">Not paid</span>;
  return <span className="muted">–</span>;
}

// the phone "+ Add" sheet: a big amount box, quick Balance / Full chips, one Add button
function AddSheet({ row, defaultDate, minDate, maxDate, onClose, onAdd, onPayments }) {
  const [amount, setAmount] = useState(Number(row.toPay) > 0 ? String(Number(row.toPay)) : '');
  const [date, setDate] = useState(defaultDate);
  const [showDate, setShowDate] = useState(false);
  const [busy, setBusy] = useState(false);
  const value = Number(amount);

  async function go() {
    setBusy(true);
    const ok = await onAdd(row, value, date);
    setBusy(false);
    if (ok) onClose();
  }

  return (
    <Modal title={`Chit ${row.chitNo}`} onClose={onClose}>
      <div className="pay-who">
        <div className="name">{row.memberName}</div>
        {row.memberNameTamil && <div className="name-ta" lang="ta">{row.memberNameTamil}</div>}
        <div className="muted small">
          Due {formatINR(row.dueAmount)} · Got {formatINR(row.paidAmount)} · To pay {formatINR(row.toPay)}
        </div>
      </div>
      <div className="muted small sheet-label">Amount received on {formatDate(date)}</div>
      <input className="big-amount" type="number" inputMode="decimal" min="0" step="0.01" value={amount} autoFocus
        onChange={(e) => setAmount(e.target.value)} onFocus={(e) => e.target.select()}
        onKeyDown={(e) => { if (e.key === 'Enter' && value > 0 && !busy) go(); }} aria-label="Amount received" />
      <div className="quick-chips">
        {Number(row.toPay) > 0 && <button type="button" onClick={() => setAmount(String(Number(row.toPay)))}>To pay {formatINR(row.toPay)}</button>}
        {Number(row.dueAmount) > 0 && <button type="button" onClick={() => setAmount(String(Number(row.dueAmount)))}>Full {formatINR(row.dueAmount)}</button>}
      </div>
      {showDate && (
        <label className="field">
          <span>Date received</span>
          <input type="date" value={date} min={minDate} max={maxDate} onChange={(e) => e.target.value && setDate(e.target.value)} />
        </label>
      )}
      <button type="button" className="btn btn-primary btn-block" disabled={!(value > 0) || busy} onClick={go}>
        {busy ? 'Saving…' : value > 0 ? `Add ${formatINR(value)}` : 'Add'}
      </button>
      <div className="sheet-links">
        <button type="button" className="link" onClick={() => { onClose(); onPayments(row); }}>
          Payments{row.paymentCount ? ` (${row.paymentCount})` : ''}
        </button>
        <button type="button" className="link" onClick={() => setShowDate((v) => !v)}>Change date</button>
      </div>
    </Modal>
  );
}

// Single tab: members who pay the full amount once a month.
export default function CollectionSingle({ data, reload, notify }) {
  const { year, month, rows, editable, today, summary } = data;
  const monthKey = `${year}-${pad(month)}`;
  const firstIso = toIso(year, month, 1);
  const lastIso = toIso(year, month, new Date(year, month, 0).getDate());
  const maxIso = today < lastIso ? today : lastIso;

  const [payDate, setPayDate] = useState(() => (today.startsWith(monthKey) ? today : maxIso >= firstIso ? maxIso : firstIso));
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('ALL');
  const [amounts, setAmounts] = useState({});
  const [busyChit, setBusyChit] = useState(null);
  const [addRow, setAddRow] = useState(null);
  const [payRow, setPayRow] = useState(null);

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    return rows.filter((r) => {
      if (filter !== 'ALL' && r.status !== filter) return false;
      if (!q) return true;
      return String(r.chitNo).includes(q) || r.memberName.toLowerCase().includes(q)
        || (r.memberNameTamil || '').toLowerCase().includes(q);
    });
  }, [rows, search, filter]);
  const phoneRows = useMemo(
    () => [...visible].sort((a, b) => (RANK[a.status] - RANK[b.status]) || (a.chitNo - b.chitNo)),
    [visible],
  );

  // returns true when the receipt was added
  async function addPayment(r, value, date) {
    if (!(value > 0)) { notify('Enter an amount greater than 0'); return false; }
    setBusyChit(r.chitNo);
    try {
      await post('/payments/add', { chitNo: r.chitNo, paidDate: date || payDate, amount: value });
      setAmounts((a) => { const n = { ...a }; delete n[r.chitNo]; return n; });
      notify(`Chit ${r.chitNo}: ${formatINR(value)} added`);
      await reload();
      return true;
    } catch (e) {
      notify(e.message);
      return false;
    } finally {
      setBusyChit(null);
    }
  }

  const rowPayRow = (r) => ({ chitNo: r.chitNo, memberName: r.memberName, memberNameTamil: r.memberNameTamil, dueAmount: r.dueAmount });

  if (rows.length === 0) {
    return <div className="card empty"><p>No member pays as Single in this month.</p></div>;
  }

  return (
    <>
      {editable ? (
        <div className="entrybar">
          <b>Payment date</b>
          <input type="date" className="date-pick" value={payDate} min={firstIso} max={maxIso}
            onChange={(e) => e.target.value && setPayDate(e.target.value)} aria-label="Payment date" />
          <span className="muted small cl-desk">date used for every Add below</span>
        </div>
      ) : (
        <div className="alert alert-info">Final month: read only. A correction goes into the open month with a remark.</div>
      )}

      <div className="stats cl-desk">
        <div className="stat"><b>{formatINR(summary.collected)}</b><span>Collected in this month (Single members)</span></div>
        <div className="stat"><b>{summary.paidCount} / {summary.count}</b><span>Paid in full</span></div>
        <div className="stat"><b>{formatINR(summary.toCollect)}</b><span>{editable ? 'Still to collect this month' : 'Not collected'}</span></div>
      </div>

      <div className="toolbar">
        <input className="search" type="search" placeholder="Search name or chit no" value={search}
          onChange={(e) => setSearch(e.target.value)} aria-label="Search" />
        <div className="segmented" role="group" aria-label="Filter">
          {FILTERS.map(([k, label]) => (
            <button key={k} type="button" className={filter === k ? 'on' : ''} onClick={() => setFilter(k)}>{label}</button>
          ))}
        </div>
      </div>

      <div className="sline cl-phone">
        <span>{summary.paidCount} of {summary.count} paid</span>
        <span>Collected <b>{formatINR(summary.collected)}</b></span>
      </div>

      {visible.length === 0 ? (
        <div className="card empty"><p>No members match.</p></div>
      ) : (
        <>
          {/* ---------------- desktop table ---------------- */}
          <div className="cl-desk">
            <div className="card table-wrap">
              <table className="sheet-table">
                <thead>
                  <tr>
                    <th>Chit</th><th>Member</th><th className="right">Due</th><th className="right">Prev. balance</th>
                    <th className="right">Received</th><th className="right">{editable ? 'To pay now' : 'Left unpaid'}</th>
                    <th>Last paid</th><th>Status</th><th>{editable ? 'Add payment' : ''}</th><th />
                  </tr>
                </thead>
                <tbody>
                  {visible.map((r) => {
                    const typed = amounts[r.chitNo] ?? '';
                    return (
                      <tr key={r.chitNo}>
                        <td><span className={'chit-badge' + (r.takenMonth ? ' is-taken' : '')}>{r.chitNo}</span></td>
                        <td>
                          <div className="name">{r.memberName}</div>
                          {r.memberNameTamil && <div className="name-ta" lang="ta">{r.memberNameTamil}</div>}
                        </td>
                        <td className="right">{formatINR(r.dueAmount)}</td>
                        <td className="right"><PrevBalance value={r.previousBalance} /></td>
                        <td className="right">{formatINR(r.paidAmount)}</td>
                        <td className="right strong">{formatINR(r.toPay)}</td>
                        <td className="muted">{shortDate(r.lastPaidDate)}</td>
                        <td><StatusChip status={r.status} row={r} /></td>
                        <td>
                          {editable && (
                            <div className="amt-box">
                              <input type="number" inputMode="decimal" min="0" step="0.01" placeholder="₹" value={typed}
                                disabled={busyChit === r.chitNo} aria-label={`Amount for chit ${r.chitNo}`}
                                onChange={(e) => setAmounts((a) => ({ ...a, [r.chitNo]: e.target.value }))}
                                onKeyDown={(e) => { if (e.key === 'Enter') addPayment(r, Number(typed)); }} />
                              {Number(r.toPay) > 0 && (
                                <button type="button" className="btn btn-small btn-chip"
                                  onClick={() => setAmounts((a) => ({ ...a, [r.chitNo]: String(Number(r.toPay)) }))}
                                  title="Fill what is to be paid now">To pay {formatINR(r.toPay)}</button>
                              )}
                              <button type="button" className="btn btn-small btn-primary" disabled={busyChit === r.chitNo}
                                onClick={() => addPayment(r, Number(typed))}>Add</button>
                            </div>
                          )}
                        </td>
                        <td className="right">
                          <button type="button" className="btn btn-small" onClick={() => setPayRow(r)}>
                            Payments{r.paymentCount ? ` (${r.paymentCount})` : ''}
                          </button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className="card total-line">
              <b>Total collected from Single members</b><b>{formatINR(summary.collected)}</b>
            </div>
          </div>

          {/* ---------------- phone list ---------------- */}
          <div className="cl-phone">
            <div className="card srows">
              {phoneRows.map((r) => (
                <div className="srow" key={r.chitNo}>
                  <span className={'chit-badge sm' + (r.takenMonth ? ' is-taken' : '')}>{r.chitNo}</span>
                  <button type="button" className="srow-name" onClick={() => setPayRow(r)}>
                    <span className="name">{r.memberName}</span>
                    {r.memberNameTamil && <span className="name-ta" lang="ta">{r.memberNameTamil}</span>}
                  </button>
                  <div className="srow-right">
                    <StatusChip status={r.status} row={r} />
                    {editable && (r.status === 'PART' || r.status === 'NOT_PAID') && (
                      <button type="button" className="add-btn" onClick={() => setAddRow(r)}>+ Add</button>
                    )}
                  </div>
                  <div className="srow-sub">
                    <span className="nw">Due <b>{formatINR(r.dueAmount)}</b></span>
                    {' · '}<span className="nw">Got <b>{formatINR(r.paidAmount)}</b></span>
                    {Number(r.previousBalance) !== 0 && <>{' · '}<PrevText value={r.previousBalance} /></>}
                    {Number(r.toPay) > 0 && <>{' · '}<span className="nw">To pay <b>{formatINR(r.toPay)}</b></span></>}
                    {r.lastPaidDate && r.status !== 'PAID' && <>{' · '}<span className="nw">{shortDate(r.lastPaidDate)}</span></>}
                  </div>
                </div>
              ))}
            </div>
            <div className="card total-line">
              <b>Total collected</b><b>{formatINR(summary.collected)}</b>
            </div>
          </div>
        </>
      )}

      {addRow && (
        <AddSheet row={addRow} defaultDate={payDate} minDate={firstIso} maxDate={maxIso}
          onClose={() => setAddRow(null)} onAdd={addPayment} onPayments={(r) => setPayRow(r)} />
      )}
      {payRow && (
        <PaymentsModal sheet={{ year, month, status: data.sheetStatus }} row={rowPayRow(payRow)}
          onClose={() => setPayRow(null)} onChanged={reload} />
      )}
    </>
  );
}
