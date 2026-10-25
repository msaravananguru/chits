import { useCallback, useEffect, useMemo, useState } from 'react';
import { post } from '../api.js';
import { formatDate, formatINR, formatMonths, formatPlan, monthLabel } from '../format.js';
import PaymentsModal from '../components/PaymentsModal.jsx';
import ConfirmModal from '../components/ConfirmModal.jsx';

const keyOf = (s) => `${s.year}-${s.month}`;

// the month after the latest sheet (or the current month if nothing exists yet)
function nextPeriod(list) {
  if (list.length === 0) {
    const d = new Date();
    return { year: d.getFullYear(), month: d.getMonth() + 1 };
  }
  const { year, month } = list[0]; // newest first
  return month === 12 ? { year: year + 1, month: 1 } : { year, month: month + 1 };
}

function payState(row) {
  const paid = Number(row.paidAmount);
  const due = Number(row.dueAmount);
  if (paid <= 0) return { label: 'Unpaid', cls: 'pill-muted' };
  if (paid < due) return { label: 'Part', cls: 'pill-warn' };
  return { label: 'Paid', cls: 'pill-ok' };
}

export default function MonthlySheet() {
  const [list, setList] = useState([]);
  const [selected, setSelected] = useState(null); // { year, month }
  const [sheet, setSheet] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('ALL'); // ALL | UNPAID
  const [modal, setModal] = useState(null); // { type: 'pay' | 'generate' | 'lock', row? }  (row = chit number)
  const [toast, setToast] = useState('');

  const loadSheet = useCallback(async (period) => {
    setError('');
    try {
      setSheet(await post('/sheets/get', { year: period.year, month: period.month }));
    } catch (e) {
      setSheet(null);
      setError(e.message);
    }
  }, []);

  // load the month list, then open `prefer` (or the newest month)
  const loadList = useCallback(async (prefer) => {
    setError('');
    try {
      const months = await post('/sheets/list');
      setList(months);
      const pick = prefer || (months[0] ? { year: months[0].year, month: months[0].month } : null);
      setSelected(pick);
      if (pick) await loadSheet(pick);
      else setSheet(null);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [loadSheet]);

  useEffect(() => { loadList(null); }, [loadList]);

  useEffect(() => {
    if (!toast) return undefined;
    const t = setTimeout(() => setToast(''), 3500);
    return () => clearTimeout(t);
  }, [toast]);

  const hasDraft = list.some((s) => s.status === 'DRAFT');
  const isDraft = sheet?.status === 'DRAFT';
  const next = useMemo(() => nextPeriod(list), [list]);

  const unpaidCount = sheet ? sheet.rows.filter((r) => Number(r.paidAmount) <= 0).length : 0;

  const visible = useMemo(() => {
    if (!sheet) return [];
    const q = search.trim().toLowerCase();
    return sheet.rows.filter((r) => {
      if (filter === 'UNPAID' && Number(r.paidAmount) > 0) return false;
      if (!q) return true;
      return (
        String(r.chitNo).includes(q) ||
        r.memberName.toLowerCase().includes(q) ||
        (r.memberNameTamil || '').toLowerCase().includes(q)
      );
    });
  }, [sheet, search, filter]);

  const pickMonth = (e) => {
    const [year, month] = e.target.value.split('-').map(Number);
    setSelected({ year, month });
    setSearch('');
    loadSheet({ year, month });
  };

  // the payments window stays open; refresh the sheet behind it
  const onPaymentsChanged = () => loadSheet(selected);

  async function generate() {
    await post('/sheets/generate', { year: next.year, month: next.month });
    setModal(null);
    setToast(`${monthLabel(next.year, next.month)} generated`);
    await loadList({ year: next.year, month: next.month });
  }

  async function lock() {
    await post('/sheets/lock', { year: selected.year, month: selected.month });
    setModal(null);
    setToast(`${monthLabel(selected.year, selected.month)} locked`);
    await loadList(selected);
  }

  const editBtn = (r) => {
    if (!isDraft && !r.paymentCount) return null;
    return (
      <button className="btn btn-small" onClick={() => setModal({ type: 'pay', chitNo: r.chitNo })}>
        {isDraft ? 'Payments' : 'View'}{r.paymentCount ? ` (${r.paymentCount})` : ''}
      </button>
    );
  };

  const t = sheet?.totals;

  return (
    <>
      <div className="page-head">
        <h1>Monthly Sheet</h1>
        <div className="head-actions only-desktop">
          <button className="btn" disabled={hasDraft} onClick={() => setModal({ type: 'generate' })}
            title={hasDraft ? 'Lock the open month first' : ''}>
            Generate {monthLabel(next.year, next.month)}
          </button>
          {isDraft && <button className="btn btn-primary" onClick={() => setModal({ type: 'lock' })}>Lock month</button>}
        </div>
      </div>

      {error && <div className="alert alert-error">{error} <button className="link" onClick={() => loadList(selected)}>Retry</button></div>}

      {loading ? (
        <div className="card empty"><p>Loading…</p></div>
      ) : list.length === 0 ? (
        <div className="card empty">
          <p>No month has been generated yet.</p>
          <button className="btn btn-primary" onClick={() => setModal({ type: 'generate' })}>
            Generate {monthLabel(next.year, next.month)}
          </button>
        </div>
      ) : (
        <>
          <div className="sheet-bar">
            <select className="month-select" value={selected ? keyOf(selected) : ''} onChange={pickMonth} aria-label="Month">
              {list.map((s) => (
                <option key={keyOf(s)} value={keyOf(s)}>
                  {monthLabel(s.year, s.month)} · {s.status === 'LOCKED' ? 'Locked' : 'Open'}
                </option>
              ))}
            </select>
            {sheet && (
              <span className={'pill ' + (isDraft ? 'pill-warn' : 'pill-ok')}>
                {isDraft ? 'Open (editable)' : `Locked ${sheet.lockedAt ? formatDate(sheet.lockedAt.slice(0, 10)) : ''}`}
              </span>
            )}
          </div>

          {sheet && (
            <>
              {isDraft ? (
                <div className="alert alert-info">
                  This month is open. Enter what each member paid, then lock it. A locked month cannot be changed;
                  correct any mistake next month with a remark.
                </div>
              ) : (
                <div className="alert alert-warn">This month is locked and cannot be edited.</div>
              )}

              <div className="stats stats-4">
                <div className="stat"><b>{formatINR(t.paidAmount)}</b><span>Received</span></div>
                <div className="stat"><b>{formatINR(t.dueAmount)}</b><span>Due this month</span></div>
                <div className="stat"><b>{unpaidCount}</b><span>Not paid yet</span></div>
                <div className="stat"><b>{formatINR(t.remainingAmount)}</b><span>Total balance</span></div>
              </div>

              <div className="toolbar">
                <input className="search" type="search" placeholder="Search name or chit no"
                  value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search" />
                <div className="segmented" role="group" aria-label="Filter">
                  <button className={filter === 'ALL' ? 'on' : ''} onClick={() => setFilter('ALL')}>All ({sheet.rows.length})</button>
                  <button className={filter === 'UNPAID' ? 'on' : ''} onClick={() => setFilter('UNPAID')}>Unpaid ({unpaidCount})</button>
                </div>
              </div>

              {visible.length === 0 ? (
                <div className="card empty"><p>No rows match.</p></div>
              ) : (
                <>
                  {/* desktop: table like the Excel sheet */}
                  <div className="card table-wrap">
                    <table className="sheet-table">
                      <thead>
                        <tr>
                          <th>Chit</th><th>Member</th><th className="right">Month</th>
                          <th className="right">Due</th><th className="right">Received</th><th>Date</th>
                          <th className="right">Total paid</th><th className="right">Total to pay</th>
                          <th className="right">Months pending</th><th className="right">Balance</th>
                          <th></th>
                        </tr>
                      </thead>
                      <tbody>
                        {visible.map((r) => {
                          const st = payState(r);
                          return (
                            <tr key={r.chitNo}>
                              <td className="num">{r.chitNo}</td>
                              <td>
                                <div className="name">{r.memberName}</div>
                                {r.memberNameTamil && <div className="name-ta" lang="ta">{r.memberNameTamil}</div>}
                                {r.remark && <div className="remark">{r.remark}</div>}
                              </td>
                              <td className="right">{r.paidMonth}</td>
                              <td className="right">{formatINR(r.dueAmount)}</td>
                              <td className="right"><span className={'pill ' + st.cls}>{formatINR(r.paidAmount)}</span></td>
                              <td>{formatDate(r.paidDate)}</td>
                              <td className="right">{formatINR(r.totalPaid)}</td>
                              <td className="right">{formatINR(r.totalDueAmount)}</td>
                              <td className="right">{formatMonths(r.monthsPending)}</td>
                              <td className="right strong">{formatINR(r.remainingAmount)}</td>
                              <td className="right">{editBtn(r)}</td>
                            </tr>
                          );
                        })}
                      </tbody>
                      <tfoot>
                        <tr>
                          <td colSpan="3">இந்த மாத வரவு · {t.rows} chits</td>
                          <td className="right">{formatINR(t.dueAmount)}</td>
                          <td className="right">{formatINR(t.paidAmount)}</td>
                          <td></td>
                          <td className="right">{formatINR(t.totalPaid)}</td>
                          <td className="right">{formatINR(t.totalDueAmount)}</td>
                          <td></td>
                          <td className="right">{formatINR(t.remainingAmount)}</td>
                          <td></td>
                        </tr>
                      </tfoot>
                    </table>
                  </div>

                  {/* mobile: one card per chit */}
                  <div className="cards">
                    {visible.map((r) => {
                      const st = payState(r);
                      return (
                        <div className="card sheet-card" key={r.chitNo}>
                          <div className="member-card-top">
                            <span className="chit-badge">{r.chitNo}</span>
                            <div className="member-card-name">
                              <div className="name">{r.memberName}</div>
                              {r.memberNameTamil && <div className="name-ta" lang="ta">{r.memberNameTamil}</div>}
                            </div>
                            <span className={'pill ' + st.cls}>{st.label}</span>
                          </div>
                          <div className="kv">
                            <div><span>Month</span><b>{r.paidMonth} · {formatPlan(r.planAmount)}</b></div>
                            <div><span>Due</span><b>{formatINR(r.dueAmount)}</b></div>
                            <div><span>Received</span><b>{formatINR(r.paidAmount)}</b></div>
                            <div><span>Date</span><b>{r.paidDate ? formatDate(r.paidDate) : '–'}</b></div>
                            <div><span>Total paid</span><b>{formatINR(r.totalPaid)}</b></div>
                            <div><span>Total to pay</span><b>{formatINR(r.totalDueAmount)}</b></div>
                            <div><span>Months pending</span><b>{formatMonths(r.monthsPending)}</b></div>
                            <div><span>Balance</span><b>{formatINR(r.remainingAmount)}</b></div>
                          </div>
                          {r.remark && <div className="remark">{r.remark}</div>}
                          {editBtn(r) && <div className="row-actions">{editBtn(r)}</div>}
                        </div>
                      );
                    })}
                    <div className="card sheet-card sheet-total">
                      <div className="name">இந்த மாத வரவு · {t.rows} chits</div>
                      <div className="kv">
                        <div><span>Due</span><b>{formatINR(t.dueAmount)}</b></div>
                        <div><span>Received</span><b>{formatINR(t.paidAmount)}</b></div>
                        <div><span>Total paid</span><b>{formatINR(t.totalPaid)}</b></div>
                        <div><span>Balance</span><b>{formatINR(t.remainingAmount)}</b></div>
                      </div>
                    </div>
                  </div>
                </>
              )}
            </>
          )}
        </>
      )}

      {/* phone: the two main actions sit above the bottom bar */}
      <div className="action-bar only-mobile">
        <button className="btn" disabled={hasDraft} onClick={() => setModal({ type: 'generate' })}>
          Generate {monthLabel(next.year, next.month)}
        </button>
        {isDraft && <button className="btn btn-primary" onClick={() => setModal({ type: 'lock' })}>Lock month</button>}
      </div>

      {modal?.type === 'pay' && sheet && sheet.rows.some((r) => r.chitNo === modal.chitNo) && (
        <PaymentsModal sheet={sheet} row={sheet.rows.find((r) => r.chitNo === modal.chitNo)}
          onClose={() => setModal(null)} onChanged={onPaymentsChanged} />
      )}
      {modal?.type === 'generate' && (
        <ConfirmModal title={`Generate ${monthLabel(next.year, next.month)}`} confirmLabel="Generate"
          onConfirm={generate} onClose={() => setModal(null)}>
          <p>This creates one row for every active chit running in {monthLabel(next.year, next.month)}, with the
            amount due worked out from each plan.</p>
        </ConfirmModal>
      )}
      {modal?.type === 'lock' && sheet && (
        <ConfirmModal title={`Lock ${monthLabel(sheet.year, sheet.month)}`} confirmLabel="Lock month" danger
          onConfirm={lock} onClose={() => setModal(null)}>
          <p><b>{formatINR(t.paidAmount)}</b> received from {sheet.rows.length - unpaidCount} of {sheet.rows.length} chits
            {unpaidCount > 0 && <>; <b>{unpaidCount}</b> have not paid</>}.</p>
          <p>After locking, nothing in this month can be changed. Any mistake has to be corrected next month with a remark.</p>
        </ConfirmModal>
      )}

      {toast && <div className="toast" role="status">{toast}</div>}
    </>
  );
}
