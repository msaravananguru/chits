import { useMemo, useState } from 'react';
import { formatDate, formatINR } from '../format.js';
import PaymentsModal from './PaymentsModal.jsx';
import { PrevBalance, PrevText, fromAdvance } from './Balance.jsx';

const shortDate = (iso) => (iso ? formatDate(iso).slice(0, 6) : '–');
const FILTERS = [['ALL', 'All'], ['PAID', 'Paid'], ['PART', 'Part'], ['NOT_PAID', 'Not paid']];
const STYLES = [['ALL', 'All members'], ['DAILY', 'Daily members'], ['SINGLE', 'Single members']];

function StatusChip({ status, row }) {
  if (row && fromAdvance(row)) return <span className="ms ms-paid">✓ From advance</span>;
  if (status === 'PAID') return <span className="ms ms-paid">✓ Paid</span>;
  if (status === 'PART') return <span className="ms ms-part">Part</span>;
  if (status === 'NOT_PAID') return <span className="ms ms-none">Not paid</span>;
  return <span className="muted">–</span>;
}

function StyleTag({ row }) {
  if (row.payMode === 'DAILY') {
    return <span className="stag stag-daily">Daily{Number(row.dailyAmount) > 0 ? ` · ${formatINR(row.dailyAmount)}` : ''}</span>;
  }
  return <span className="stag">Single</span>;
}

// All tab: every member (Daily and Single) with the basic data of the month. View only.
export default function CollectionAll({ data, reload }) {
  const { year, month, rows, summary, editable } = data;
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('ALL');
  const [style, setStyle] = useState('ALL');
  const [payRow, setPayRow] = useState(null);

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    return rows.filter((r) => {
      if (filter !== 'ALL' && r.status !== filter) return false;
      if (style !== 'ALL' && r.payMode !== style) return false;
      if (!q) return true;
      return String(r.chitNo).includes(q) || r.memberName.toLowerCase().includes(q)
        || (r.memberNameTamil || '').toLowerCase().includes(q);
    });
  }, [rows, search, filter, style]);

  const rowPayRow = (r) => ({ chitNo: r.chitNo, memberName: r.memberName, memberNameTamil: r.memberNameTamil, dueAmount: r.dueAmount });

  if (rows.length === 0) {
    return <div className="card empty"><p>No members in this month.</p></div>;
  }

  return (
    <>
      {!editable && <div className="alert alert-info">Final month: read only. A correction goes into the open month with a remark.</div>}

      <div className="stats cl-desk">
        <div className="stat"><b>{formatINR(summary.collected)}</b><span>Collected in this month (all members)</span></div>
        <div className="stat"><b>{summary.paidCount} / {summary.count}</b><span>Paid in full this month</span></div>
        <div className="stat"><b>{formatINR(summary.toCollect)}</b><span>{editable ? 'To collect now (incl. arrears)' : 'Left unpaid'}</span></div>
      </div>

      <div className="toolbar">
        <input className="search" type="search" placeholder="Search name or chit no" value={search}
          onChange={(e) => setSearch(e.target.value)} aria-label="Search" />
        <div className="segmented" role="group" aria-label="Filter">
          {FILTERS.map(([k, label]) => (
            <button key={k} type="button" className={filter === k ? 'on' : ''} onClick={() => setFilter(k)}>{label}</button>
          ))}
        </div>
        <select className="style-select" value={style} onChange={(e) => setStyle(e.target.value)} aria-label="Pay style">
          {STYLES.map(([k, label]) => <option key={k} value={k}>{label}</option>)}
        </select>
      </div>

      <div className="sline cl-phone">
        <span>{summary.paidCount} of {summary.count} paid</span>
        <span>Collected <b>{formatINR(summary.collected)}</b></span>
      </div>

      {visible.length === 0 ? (
        <div className="card empty"><p>No members match.</p></div>
      ) : (
        <>
          <div className="cl-desk">
            <div className="card table-wrap">
              <table className="sheet-table">
                <thead>
                  <tr>
                    <th>Chit</th><th>Member</th><th>Pay style</th><th className="right">Due</th>
                    <th className="right">Prev. balance</th><th className="right">Received</th>
                    <th className="right">{editable ? 'To pay now' : 'Left unpaid'}</th><th>Status</th><th />
                  </tr>
                </thead>
                <tbody>
                  {visible.map((r) => (
                    <tr key={r.chitNo}>
                      <td><span className={'chit-badge' + (r.takenMonth ? ' is-taken' : '')}>{r.chitNo}</span></td>
                      <td>
                        <div className="name">{r.memberName}</div>
                        {r.memberNameTamil && <div className="name-ta" lang="ta">{r.memberNameTamil}</div>}
                      </td>
                      <td><StyleTag row={r} /></td>
                      <td className="right">{formatINR(r.dueAmount)}</td>
                      <td className="right"><PrevBalance value={r.previousBalance} /></td>
                      <td className="right">{formatINR(r.paidAmount)}</td>
                      <td className="right strong">{formatINR(r.toPay)}</td>
                      <td><StatusChip status={r.status} row={r} /></td>
                      <td className="right">
                        <button type="button" className="btn btn-small" onClick={() => setPayRow(r)}>
                          Payments{r.paymentCount ? ` (${r.paymentCount})` : ''}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="card total-line">
              <span>
                <b>{editable ? 'Total to collect now' : 'Left unpaid'}</b>{' '}
                <span className="muted small">
                  Due {formatINR(summary.dueTotal)} · Arrears {formatINR(summary.arrears)} · Advance {formatINR(summary.advance)} · Received {formatINR(summary.collected)}
                </span>
              </span>
              <b>{formatINR(summary.toCollect)}</b>
            </div>
          </div>

          <div className="cl-phone">
            <div className="card srows">
              {visible.map((r) => (
                <div className="srow" key={r.chitNo}>
                  <span className={'chit-badge sm' + (r.takenMonth ? ' is-taken' : '')}>{r.chitNo}</span>
                  <button type="button" className="srow-name" onClick={() => setPayRow(r)}>
                    <span className="name">{r.memberName} <StyleTag row={r} /></span>
                    {r.memberNameTamil && <span className="name-ta" lang="ta">{r.memberNameTamil}</span>}
                  </button>
                  <div className="srow-right">
                    {Number(r.toPay) > 0 ? (
                      <><b className="topay">{formatINR(r.toPay)}</b><span className="muted small">to pay</span></>
                    ) : <StatusChip status={r.status} row={r} />}
                  </div>
                  <div className="srow-sub">
                    <span className="nw">Due <b>{formatINR(r.dueAmount)}</b></span>
                    {Number(r.previousBalance) !== 0 && <>{' · '}<PrevText value={r.previousBalance} /></>}
                    {Number(r.paidAmount) > 0 && <>{' · '}<span className="nw">Got <b>{formatINR(r.paidAmount)}</b></span></>}
                  </div>
                </div>
              ))}
            </div>
            <div className="card total-line">
              <b>{editable ? 'Total to collect now' : 'Left unpaid'}</b><b>{formatINR(summary.toCollect)}</b>
            </div>
          </div>
        </>
      )}

      {payRow && (
        <PaymentsModal sheet={{ year, month, status: data.sheetStatus }} row={rowPayRow(payRow)}
          onClose={() => setPayRow(null)} onChanged={reload} />
      )}
    </>
  );
}
