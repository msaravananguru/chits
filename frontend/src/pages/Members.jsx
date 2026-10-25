import { useCallback, useEffect, useMemo, useState } from 'react';
import { post } from '../api.js';
import { formatINR, formatPlan, monthLabel } from '../format.js';
import MemberFormModal from '../components/MemberFormModal.jsx';
import MemberPickerModal from '../components/MemberPickerModal.jsx';
import TakenModal from '../components/TakenModal.jsx';
import PayTag from '../components/PayTag.jsx';
import { PrevBalance, PrevText } from '../components/Balance.jsx';

const MONTH_FILTERS = [['ALL', 'All'], ['PAID', 'Paid'], ['PART', 'Part'], ['NOT_PAID', 'Not paid']];

const sourceOf = (o) => (o.source === 'FINAL' ? 'Final' : 'Live');

// the small paid / part / not paid chip for the selected month
function MonthChip({ row, inline }) {
  const Gap = inline ? 'span' : 'div';
  const paid = formatINR(row.paidAmount);
  if (row.monthStatus === 'PAID') {
    if (Number(row.paidAmount) === 0 && Number(row.dueAmount) > 0) {
      return <><span className="ms ms-paid">✓ From advance</span><Gap className="note">advance {formatINR(row.previousBalance)}</Gap></>;
    }
    return <><span className="ms ms-paid">✓ Paid</span><Gap className="note">{paid}</Gap></>;
  }
  if (row.monthStatus === 'PART') {
    return <><span className="ms ms-part">Part</span><Gap className="note">{paid} · to pay {formatINR(row.toPay)}</Gap></>;
  }
  if (row.monthStatus === 'NOT_PAID') {
    return <><span className="ms ms-none">Not paid</span><Gap className="note">to pay {formatINR(row.toPay)}</Gap></>;
  }
  return <span className="muted">–</span>;
}

// "Month 4 of 21" with a thin progress bar
function ChitMonth({ row }) {
  if (row.completed) return <span className="muted">Completed</span>;
  const pct = Math.min(100, Math.round((row.chitMonth / row.totalMonths) * 100));
  return (
    <div className="chit-month">
      <b>Month {row.chitMonth}</b> <span className="note">of {row.totalMonths}</span>
      <div className="bar"><i style={{ width: pct + '%' }} /></div>
    </div>
  );
}

export default function Members() {
  const [overview, setOverview] = useState(null);
  const [chits, setChits] = useState([]);       // full member records (for the edit popup and the next chit number)
  const [plans, setPlans] = useState([]);
  const [period, setPeriod] = useState(null);   // null = default month (newest sheet)
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [monthFilter, setMonthFilter] = useState('ALL');
  const [payFilter, setPayFilter] = useState('ALL'); // ALL | DAILY | SINGLE
  const [showClosed, setShowClosed] = useState(false);
  const [modal, setModal] = useState(null); // { type: 'add' | 'pick' | 'edit' | 'taken', chit? }
  const [toast, setToast] = useState('');

  const load = useCallback(async (p) => {
    setError('');
    try {
      const [o, c, pl] = await Promise.all([
        post('/chits/overview', p || {}),
        post('/chits/list', {}),
        post('/plans/list'),
      ]);
      setOverview(o);
      setChits(c);
      setPlans(pl);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(period); }, [load, period]);

  useEffect(() => {
    if (!toast) return undefined;
    const t = setTimeout(() => setToast(''), 3500);
    return () => clearTimeout(t);
  }, [toast]);

  const planMap = useMemo(
    () => Object.fromEntries(plans.map((p) => [Number(p.planAmount), p])),
    [plans]
  );

  // last chit number + 1 (closed chits count too), suggested when adding a member
  const nextChitNo = useMemo(
    () => chits.reduce((max, c) => Math.max(max, c.chitNo), 0) + 1,
    [chits]
  );

  const rows = overview ? overview.rows : [];
  const closedCount = rows.filter((r) => r.status === 'CLOSED').length;

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    return rows.filter((r) => {
      if (r.status === 'CLOSED' && !showClosed) return false;
      if (monthFilter !== 'ALL' && r.monthStatus !== monthFilter) return false;
      if (payFilter !== 'ALL' && r.payMode !== payFilter) return false;
      if (!q) return true;
      return (
        String(r.chitNo).includes(q) ||
        r.memberName.toLowerCase().includes(q) ||
        (r.memberNameTamil || '').toLowerCase().includes(q)
      );
    });
  }, [rows, search, monthFilter, payFilter, showClosed]);

  const onSaved = (message) => {
    setModal(null);
    setToast(message);
    load(period);
  };

  const pickMonth = (e) => {
    const [year, month] = e.target.value.split('-').map(Number);
    setPeriod({ year, month });
    setSearch('');
    setMonthFilter('ALL');
    setPayFilter('ALL');
  };

  const label = overview ? monthLabel(overview.year, overview.month) : '';
  const months = overview && overview.months.length > 0
    ? overview.months
    : overview ? [{ year: overview.year, month: overview.month, status: 'DRAFT' }] : [];

  const rowClass = (r) =>
    (r.takenMonth != null ? 'row-taken' : '') + (r.status === 'CLOSED' ? ' row-closed' : '');

  const nameCell = (r) => (
    <>
      <div className="name">{r.memberName}</div>
      {r.memberNameTamil && <div className="name-ta" lang="ta">{r.memberNameTamil}</div>}
      {r.takenMonth != null && <div className="taken-line">Taken · month {r.takenMonth}</div>}
      <div><PayTag payMode={r.payMode} dailyAmount={r.dailyAmount} /></div>
      {r.status === 'CLOSED' && <span className="pill pill-muted">Closed</span>}
    </>
  );

  const badge = (r) => (
    <span className={'chit-badge ' + (r.takenMonth != null ? 'is-taken' : 'is-open')}>{r.chitNo}</span>
  );

  return (
    <>
      <div className="page-head">
        <h1>Members</h1>
        <div className="head-tools">
          <select className="month-select" value={overview ? `${overview.year}-${overview.month}` : ''}
            onChange={pickMonth} aria-label="Month" disabled={!overview}>
            {months.map((s) => (
              <option key={`${s.year}-${s.month}`} value={`${s.year}-${s.month}`}>
                {monthLabel(s.year, s.month)} · {s.status === 'LOCKED' ? 'final' : 'live'}
              </option>
            ))}
          </select>
          <button className="btn" onClick={() => setModal({ type: 'pick' })} disabled={chits.length === 0}>
            ✎ Edit member
          </button>
          <button className="btn btn-primary only-desktop" onClick={() => setModal({ type: 'add' })}>
            + Add member
          </button>
        </div>
      </div>

      {overview && (
        <p className="source-line">
          <span className={'pill ' + (overview.source === 'FINAL' ? 'pill-ok' : 'pill-info')}>
            {overview.source === 'FINAL' ? 'Final' : 'Live'}
          </span>{' '}
          {overview.source === 'FINAL'
            ? `Saved figures of ${label}, read from the locked master table.`
            : overview.sheetStatus === 'NONE'
              ? `Calculated now. No sheet is generated for ${label} yet.`
              : `Calculated now from the receipts so far. ${label} is not locked yet.`}
        </p>
      )}

      {overview && (
        <div className="stats">
          <div className="stat"><b>{overview.summary.activeCount}</b><span>Active chits</span></div>
          <div className="stat"><b>{formatINR(overview.summary.totalPaid)}</b><span>Total paid</span></div>
          <div className="stat">
            <b>{overview.summary.paidCount} / {overview.summary.activeCount}</b>
            <span>Paid in {label}</span>
          </div>
        </div>
      )}

      <div className="toolbar">
        <input className="search" type="search" placeholder="Search name or chit no"
          value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search members" />
        <div className="segmented" role="group" aria-label="Month payment filter">
          {MONTH_FILTERS.map(([v, text]) => (
            <button key={v} className={monthFilter === v ? 'on' : ''} onClick={() => setMonthFilter(v)}>{text}</button>
          ))}
        </div>
        <select className="pay-filter" value={payFilter} onChange={(e) => setPayFilter(e.target.value)}
          aria-label="Pay style filter">
          <option value="ALL">All members</option>
          <option value="DAILY">Daily only</option>
          <option value="SINGLE">Single only</option>
        </select>
        {closedCount > 0 && (
          <label className="check">
            <input type="checkbox" checked={showClosed} onChange={(e) => setShowClosed(e.target.checked)} />
            Show closed ({closedCount})
          </label>
        )}
      </div>

      {error && (
        <div className="alert alert-error">
          {error} <button className="link" onClick={() => load(period)}>Retry</button>
        </div>
      )}

      {loading ? (
        <div className="card empty"><p>Loading…</p></div>
      ) : visible.length === 0 ? (
        <div className="card empty">
          <p>{rows.length === 0 ? 'No members yet. Add the first one.' : 'No members match this search.'}</p>
        </div>
      ) : (
        <>
          {/* desktop: table */}
          <div className="card table-wrap">
            <table className="sheet-table members-table">
              <thead>
                <tr>
                  <th>Chit</th><th>Member</th><th>Plan</th><th>Chit month</th><th>{label}</th>
                  <th className="right">Prev. balance</th><th className="right">To pay now</th>
                  <th className="right">Total paid</th><th className="right">Total to pay</th>
                  <th className="right">Remaining</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((r) => (
                  <tr key={r.chitNo} className={rowClass(r)}>
                    <td>{badge(r)}</td>
                    <td>{nameCell(r)}</td>
                    <td>{formatPlan(r.planAmount)}</td>
                    <td><ChitMonth row={r} /></td>
                    <td><MonthChip row={r} /></td>
                    <td className="right"><PrevBalance value={r.previousBalance} /></td>
                    <td className="right strong">{formatINR(r.toPay)}</td>
                    <td className="right">{formatINR(r.totalPaid)}</td>
                    <td className="right">{formatINR(r.totalToPay)}</td>
                    <td className="right strong">{formatINR(r.remaining)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* mobile: cards */}
          <div className="cards">
            {visible.map((r) => (
              <div className={'card member-card ' + rowClass(r)} key={r.chitNo}>
                <div className="member-card-top">
                  {badge(r)}
                  <div className="member-card-name">{nameCell(r)}</div>
                </div>
                <div className="card-month">
                  <span className="muted">{label}</span>
                  <MonthChip row={r} inline />
                </div>
                {(Number(r.previousBalance) !== 0 || Number(r.toPay) > 0) && (
                  <div className="card-month">
                    <span className="muted">To pay now</span>
                    <span><b>{formatINR(r.toPay)}</b> <PrevText value={r.previousBalance} /></span>
                  </div>
                )}
                <div className="member-card-meta">
                  <span>{formatPlan(r.planAmount)}</span>
                  <span>{r.completed ? 'Completed' : `Month ${r.chitMonth} of ${r.totalMonths}`}</span>
                </div>
                {!r.completed && (
                  <div className="bar"><i style={{ width: Math.min(100, Math.round((r.chitMonth / r.totalMonths) * 100)) + '%' }} /></div>
                )}
                <div className="nums">
                  <div><span>Total paid</span><b>{formatINR(r.totalPaid)}</b></div>
                  <div><span>Total to pay</span><b>{formatINR(r.totalToPay)}</b></div>
                  <div><span>Remaining</span><b>{formatINR(r.remaining)}</b></div>
                </div>
              </div>
            ))}
          </div>
        </>
      )}

      <button className="fab only-mobile" onClick={() => setModal({ type: 'add' })} aria-label="Add member">+</button>

      {modal && modal.type === 'add' && (
        <MemberFormModal mode="add" plans={plans} nextChitNo={nextChitNo}
          onClose={() => setModal(null)} onSaved={onSaved} />
      )}
      {modal && modal.type === 'pick' && (
        <MemberPickerModal chits={chits} onClose={() => setModal(null)}
          onPick={(chit) => setModal({ type: 'edit', chit })} />
      )}
      {modal && modal.type === 'edit' && (
        <MemberFormModal mode="edit" chit={modal.chit} plans={plans}
          plan={planMap[Number(modal.chit.planAmount)]}
          onBack={() => setModal({ type: 'pick' })}
          onMarkTaken={() => setModal({ type: 'taken', chit: modal.chit })}
          onClose={() => setModal(null)} onSaved={onSaved} />
      )}
      {modal && modal.type === 'taken' && (
        <TakenModal chit={modal.chit} plan={planMap[Number(modal.chit.planAmount)]}
          onClose={() => setModal({ type: 'edit', chit: modal.chit })} onSaved={onSaved} />
      )}

      {toast && <div className="toast" role="status">{toast}</div>}
    </>
  );
}
