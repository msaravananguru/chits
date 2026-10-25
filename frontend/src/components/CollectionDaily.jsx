import { useEffect, useMemo, useRef, useState } from 'react';
import { post } from '../api.js';
import { formatDate, formatINR } from '../format.js';
import Modal from './Modal.jsx';
import ConfirmModal from './ConfirmModal.jsx';
import PaymentsModal from './PaymentsModal.jsx';
import { PrevBalance, PrevText } from './Balance.jsx';

const pad = (n) => String(n).padStart(2, '0');
const toIso = (y, m, d) => `${y}-${pad(m)}-${pad(d)}`;
const num = (v) => { const n = Number(v); return Number.isFinite(n) ? n : 0; };
const fmt = (n) => Number(n).toLocaleString('en-IN', { maximumFractionDigits: 2 });
const validAmount = (v) => String(v).trim() === '' || /^\d+(\.\d{1,2})?$/.test(String(v).trim());
const shortDate = (iso) => formatDate(iso).slice(0, 6); // "25 Oct"

// Enter moves to the next amount box (night entry: type, Enter, type, Enter ...)
function enterNext(e) {
  if (e.key !== 'Enter') return;
  e.preventDefault();
  const all = [...document.querySelectorAll('.day-input')].filter((i) => i.offsetParent !== null && !i.disabled);
  const i = all.indexOf(e.target);
  if (i >= 0 && all[i + 1]) { all[i + 1].focus(); } else { e.target.blur(); }
}

// Daily tab: members who pay part by part. A DLC-style grid (days 1..31) and one-day entry.
export default function CollectionDaily({ data, reload, notify }) {
  const { year, month, daysInMonth, rows, today, editable } = data;
  const monthKey = `${year}-${pad(month)}`;
  const firstIso = toIso(year, month, 1);
  const lastIso = toIso(year, month, daysInMonth);
  const maxIso = today < lastIso ? today : lastIso;           // last day that can be entered

  const [entryDate, setEntryDate] = useState(() => {
    if (today.startsWith(monthKey)) return today;
    return maxIso >= firstIso ? maxIso : firstIso;
  });
  const [values, setValues] = useState({});                   // chitNo -> text typed for the entry day
  const [view, setView] = useState('enter');                  // phone: 'enter' | 'grid'
  const [confirm, setConfirm] = useState(false);
  const [pendingDate, setPendingDate] = useState(null);
  const [remark, setRemark] = useState('');
  const [busy, setBusy] = useState(false);
  const [saveError, setSaveError] = useState('');
  const [payRow, setPayRow] = useState(null);
  const boxes = useRef({});

  const entryDay = Number(entryDate.slice(8, 10));
  const idx = entryDay - 1;
  const savedDay = data.savedDays.includes(entryDay);

  // what each box shows before the user types: the saved amount, or the usual daily amount on a new day
  const initial = useMemo(() => {
    const o = {};
    for (const r of rows) {
      const saved = num(r.days[idx]);
      if (r.counts[idx] > 0 || savedDay) o[r.chitNo] = saved > 0 ? String(saved) : '0';
      else o[r.chitNo] = num(r.dailyAmount) > 0 ? String(num(r.dailyAmount)) : '';
    }
    return o;
  }, [rows, idx, savedDay]);

  const lines = rows.map((r) => {
    const multi = r.counts[idx] > 1;                         // several receipts: edit them in Payments
    const was = num(r.days[idx]);
    const raw = values[r.chitNo] ?? initial[r.chitNo];
    const now = multi ? was : num(raw);
    return { r, multi, was, raw, now, changed: !multi && now !== was };
  });
  const editLines = lines.filter((l) => !l.multi);
  const invalid = editLines.some((l) => !validAmount(l.raw));
  const paidLines = editLines.filter((l) => l.now > 0).length;
  const zeroLines = editLines.length - paidLines;
  const changedCount = lines.filter((l) => l.changed).length;
  const dayNow = lines.reduce((a, l) => a + l.now, 0);
  const dayWas = num(data.dayTotals[idx]);
  const dirty = Object.keys(values).some((k) => values[k] !== initial[k]);
  const canSave = editable && rows.length > 0 && !invalid && entryDate >= firstIso && entryDate <= maxIso
    && (savedDay ? changedCount > 0 : true);
  const monthNow = num(data.monthTotal) - dayWas + dayNow;

  // keep the entry column in view
  useEffect(() => {
    for (const box of Object.values(boxes.current)) {
      const th = box && box.querySelector('th.entry-col');
      if (th && box.offsetParent !== null) {
        box.scrollLeft = Math.max(0, th.offsetLeft - box.clientWidth / 2 + th.offsetWidth / 2);
      }
    }
  }, [entryDay, view, editable]);

  function goTo(d) {
    setEntryDate(d);
    setValues({});
    setView('enter');
  }
  function requestDate(d) {
    if (!editable || d === entryDate) return;
    if (!d.startsWith(monthKey)) { notify(`Pick a day of this month. Use the month list at the top for other months.`); return; }
    if (d > today) return;
    if (dirty) setPendingDate(d); else goTo(d);
  }
  function step(delta) {
    const nd = entryDay + delta;
    if (nd < 1 || nd > daysInMonth) return;
    const d = toIso(year, month, nd);
    if (d > today) return;
    requestDate(d);
  }
  function setValue(chitNo, v) {
    setValues((o) => ({ ...o, [chitNo]: v }));
  }

  async function save() {
    setBusy(true);
    setSaveError('');
    try {
      const entries = editLines.map((l) => ({ chitNo: l.r.chitNo, amount: l.now }));
      const res = await post('/collection/save-day', { date: entryDate, remark: remark.trim() || null, entries });
      setConfirm(false);
      setValues({});
      setRemark('');
      notify(`Saved ${shortDate(entryDate)}: ${formatINR(res.dayTotal)} from ${entries.length} members`);
      await reload();
    } catch (e) {
      setSaveError(e.message);
    } finally {
      setBusy(false);
    }
  }

  function openConfirm() {
    if (invalid) { notify('Check the amounts: use numbers only, up to 2 decimals'); return; }
    setSaveError('');
    setConfirm(true);
  }

  const reviewText = savedDay
    ? <>{changedCount} changed &nbsp;|&nbsp; Day total <b>{formatINR(dayNow)}</b>{dayNow !== dayWas && <s className="was"> {formatINR(dayWas)}</s>}</>
    : <>Review: <b>{paidLines}</b> paid · <b>{formatINR(dayNow)}</b> &nbsp;|&nbsp; <b>{zeroLines}</b> with 0</>;
  const saveLabel = savedDay ? `Save changes for ${shortDate(entryDate)}` : `Save all for ${shortDate(entryDate)}`;

  // ---------------------------------------------------------------- the grid
  function renderGrid({ compact, inputs, name }) {
    const days = Array.from({ length: daysInMonth }, (_, i) => i + 1);
    return (
      <div className="gscroll" ref={(el) => { boxes.current[name] = el; }}>
        <table className={'dgrid' + (compact ? ' compact' : '')}>
          <thead>
            <tr>
              <th className="s1">Chit</th>
              <th className="s2">Daily members</th>
              {days.map((d) => {
                const ds = toIso(year, month, d);
                const future = ds > today;
                const act = editable && d === entryDay;
                const clickable = editable && !future;
                return (
                  <th key={d} className={(act ? 'entry-col ' : '') + (future ? 'future ' : '') + (clickable ? 'cl' : '')}
                    onClick={clickable ? () => requestDate(ds) : undefined}>{d}</th>
                );
              })}
              <th>Total</th>
              {!compact && <><th>Month due</th><th>Prev. balance</th><th>{editable ? 'To pay now' : 'Left unpaid'}</th></>}
            </tr>
          </thead>
          <tbody>
            {lines.map((l) => {
              const r = l.r;
              const total = num(r.paidAmount) - l.was + l.now;
              const toPay = Math.max(num(r.dueAmount) - num(r.previousBalance) - total, 0);
              return (
                <tr key={r.chitNo}>
                  <td className="s1"><span className={'chit-badge sm' + (r.takenMonth ? ' is-taken' : '')}>{r.chitNo}</span></td>
                  <td className="s2">
                    <div className="name">{r.memberName}</div>
                    {r.memberNameTamil && <div className="name-ta" lang="ta">{r.memberNameTamil}</div>}
                  </td>
                  {days.map((d) => {
                    const ds = toIso(year, month, d);
                    if (ds > today) return <td key={d} className="future" />;
                    const amt = num(r.days[d - 1]);
                    const cnt = r.counts[d - 1];
                    const isEntry = editable && d === entryDay;
                    if (isEntry && cnt > 1) {
                      return (
                        <td key={d} className="entry multi">
                          <button type="button" className="multi-btn" onClick={() => setPayRow(r)}
                            title="Several receipts: change them in Payments">{fmt(amt)} ×{cnt}</button>
                        </td>
                      );
                    }
                    if (isEntry && inputs) {
                      return (
                        <td key={d} className={'entry' + (savedDay && l.changed ? ' chg' : '')}>
                          <input className={'day-input' + (validAmount(l.raw) ? '' : ' bad')} type="number" inputMode="decimal"
                            min="0" step="0.01" value={l.raw} onFocus={(e) => e.target.select()}
                            onChange={(e) => setValue(r.chitNo, e.target.value)} onKeyDown={enterNext}
                            aria-label={`Chit ${r.chitNo}, ${shortDate(entryDate)}`} />
                        </td>
                      );
                    }
                    const text = amt > 0 ? fmt(amt) : (data.savedDays.includes(d) ? '0' : '');
                    return (
                      <td key={d} className={(isEntry ? 'entry ' : '') + (text === '0' ? 'zero ' : '') + (editable ? 'cl' : '')}
                        onClick={editable ? () => requestDate(ds) : undefined}>
                        {text}{cnt > 1 && <sup>×{cnt}</sup>}
                      </td>
                    );
                  })}
                  <td className="sum">{fmt(total)}</td>
                  {!compact && (
                    <>
                      <td className="sum">{fmt(num(r.dueAmount))}</td>
                      <td className="sum"><PrevBalance value={r.previousBalance} /></td>
                      <td className={'sum ' + (toPay > 0 ? 'bal-due' : 'bal-ok')}>{fmt(toPay)}</td>
                    </>
                  )}
                </tr>
              );
            })}
          </tbody>
          <tfoot>
            <tr>
              <td className="s1" />
              <td className="s2"><b>Total day Collection</b><div className="note">Daily members</div></td>
              {days.map((d) => {
                const ds = toIso(year, month, d);
                if (ds > today) return <td key={d} className="future" />;
                const isEntry = editable && d === entryDay;
                const t = isEntry ? dayNow : num(data.dayTotals[d - 1]);
                const show = t > 0 || data.savedDays.includes(d) || isEntry;
                return <td key={d} className={isEntry ? 'entry' : ''}>{show ? fmt(t) : ''}</td>;
              })}
              <td className="sum grand" colSpan={compact ? 1 : 4}>{fmt(monthNow)}</td>
            </tr>
          </tfoot>
        </table>
      </div>
    );
  }

  if (rows.length === 0) {
    return (
      <div className="card empty">
        <p>No member is marked <b>Daily</b> yet. Mark a member as Daily on the Members page (Edit member).</p>
      </div>
    );
  }

  return (
    <>
      {/* ---------------- desktop ---------------- */}
      <div className="cl-desk">
        {editable ? (
          <div className="entrybar">
            <b>Entering for</b>
            <input type="date" className="date-pick" value={entryDate} min={firstIso} max={maxIso}
              onChange={(e) => e.target.value && requestDate(e.target.value)} aria-label="Entering for" />
            {savedDay && <span className="pill pill-warn">Day saved · editing</span>}
            {!savedDay && entryDate < today && <span className="pill pill-info">Past day, not saved yet</span>}
            <span className="grow" />
            <span className="review">{reviewText}</span>
            {dirty && <button type="button" className="btn btn-ghost" onClick={() => setValues({})}>Cancel changes</button>}
            <button type="button" className="btn btn-primary big" disabled={!canSave} onClick={openConfirm}>{saveLabel}</button>
          </div>
        ) : (
          <div className="alert alert-info">Final month: read only. A correction goes into the open month with a remark.</div>
        )}
        {renderGrid({ compact: false, inputs: true, name: 'desk' })}
        {editable && (
          <p className="muted small gridhint">Click a day number or any earlier cell to enter or correct that day. A cell with ×2 has two receipts: click it to change them in Payments.</p>
        )}
      </div>

      {/* ---------------- phone ---------------- */}
      <div className="cl-phone">
        {editable && (
          <div className="segmented" role="group" aria-label="View">
            <button type="button" className={view === 'enter' ? 'on' : ''} onClick={() => setView('enter')}>Enter day</button>
            <button type="button" className={view === 'grid' ? 'on' : ''} onClick={() => setView('grid')}>Month grid</button>
          </div>
        )}

        {editable && view === 'enter' ? (
          <>
            <div className="date-nav">
              <button type="button" className="btn btn-small" onClick={() => step(-1)} disabled={entryDay <= 1} aria-label="Previous day">‹</button>
              <input type="date" className="date-pick" value={entryDate} min={firstIso} max={maxIso}
                onChange={(e) => e.target.value && requestDate(e.target.value)} aria-label="Entering for" />
              <button type="button" className="btn btn-small" onClick={() => step(1)} disabled={entryDate >= maxIso} aria-label="Next day">›</button>
            </div>
            {savedDay && <div className="pill pill-warn status-pill">Day saved · editing</div>}
            {!savedDay && entryDate < today && <div className="pill pill-info status-pill">Past day, not saved yet</div>}

            <div className="day-lines card">
              {lines.map((l) => (
                <div className={'day-line' + (savedDay && l.changed ? ' chg' : '')} key={l.r.chitNo}>
                  <span className={'chit-badge sm' + (l.r.takenMonth ? ' is-taken' : '')}>{l.r.chitNo}</span>
                  <div className="nm">
                    <div className="name">{l.r.memberName}</div>
                    {l.r.memberNameTamil && <div className="name-ta" lang="ta">{l.r.memberNameTamil}</div>}
                    <div className="line-sub">
                      <span className="nw">Due <b>{fmt(num(l.r.dueAmount))}</b></span>
                      {num(l.r.previousBalance) !== 0 && <> · <PrevText value={l.r.previousBalance} /></>}
                      {' · '}<span className="nw">To pay <b>{fmt(Math.max(num(l.r.dueAmount) - num(l.r.previousBalance) - (num(l.r.paidAmount) - l.was + l.now), 0))}</b></span>
                    </div>
                  </div>
                  {l.multi ? (
                    <button type="button" className="multi-btn" onClick={() => setPayRow(l.r)}>{fmt(l.was)} ×{l.r.counts[idx]} ›</button>
                  ) : (
                    <input className={'day-input' + (validAmount(l.raw) ? '' : ' bad')} type="number" inputMode="decimal"
                      min="0" step="0.01" value={l.raw} placeholder="0" onFocus={(e) => e.target.select()}
                      onChange={(e) => setValue(l.r.chitNo, e.target.value)} onKeyDown={enterNext}
                      aria-label={`Chit ${l.r.chitNo}`} />
                  )}
                </div>
              ))}
              <div className="day-line total">
                <div className="nm"><b>Total day Collection</b></div>
                <b>{formatINR(dayNow)}</b>
              </div>
            </div>

            <div className="save-bar">
              <div className="save-info">
                {savedDay ? <b>{changedCount} changed</b> : <b>{paidLines} paid · {formatINR(dayNow)}</b>}
                <div className="muted small">{savedDay ? `Day total ${formatINR(dayNow)}` : `${zeroLines} with 0`}</div>
              </div>
              <button type="button" className="btn btn-primary" disabled={!canSave} onClick={openConfirm}>{saveLabel}</button>
            </div>
          </>
        ) : (
          <>
            {!editable && <div className="alert alert-info">Final month: read only.</div>}
            {renderGrid({ compact: true, inputs: false, name: 'phone' })}
            {editable && <p className="muted small gridhint">Tap a day number or a cell to enter or correct that day.</p>}
          </>
        )}
      </div>

      {/* ---------------- pop-ups ---------------- */}
      {confirm && (
        <Modal title={savedDay ? `Save changes for ${formatDate(entryDate)}?` : `Save collection for ${formatDate(entryDate)}?`}
          onClose={() => { if (!busy) setConfirm(false); }}>
          <p className="muted small">
            {savedDay
              ? `Only the ${changedCount} changed lines are saved. Old amounts stay in the audit log.`
              : 'All lines below are saved at once. A member with 0 gets no receipt.'}
          </p>
          <ul className="confirm-lines">
            {(savedDay ? lines.filter((l) => l.changed) : editLines).map((l) => (
              <li key={l.r.chitNo}>
                <span className="chit-badge sm">{l.r.chitNo}</span>
                <span className="nm">{l.r.memberName}</span>
                {savedDay ? (
                  <span className="chg-vals">
                    <s className="was">{formatINR(l.was)}</s> → <b className={l.now === 0 ? 'zero-red' : ''}>{formatINR(l.now)}</b>
                    {l.now === 0 && <small> receipt removed</small>}
                  </span>
                ) : (
                  <b className={l.now === 0 ? 'zero' : ''}>{formatINR(l.now)}</b>
                )}
              </li>
            ))}
          </ul>
          <div className="confirm-total">
            {savedDay
              ? <><b>Day total</b><span><s className="was">{formatINR(dayWas)}</s> → <b>{formatINR(dayNow)}</b></span></>
              : <><b>{paidLines} paid · {formatINR(dayNow)}</b><span>{zeroLines} with 0 (no receipt saved)</span></>}
          </div>
          <label className="field">
            <span>Remark <em>(optional)</em></span>
            <input type="text" maxLength={500} value={remark} onChange={(e) => setRemark(e.target.value)}
              placeholder="e.g. corrected after checking the book" />
          </label>
          {saveError && <div className="alert alert-error" role="alert">{saveError}</div>}
          <div className="modal-actions">
            <button type="button" className="btn btn-ghost" onClick={() => setConfirm(false)} disabled={busy}>Back to edit</button>
            <button type="button" className="btn btn-primary" onClick={save} disabled={busy}>
              {busy ? 'Saving…' : savedDay ? 'Save changes' : 'Save all'}
            </button>
          </div>
        </Modal>
      )}

      {pendingDate && (
        <ConfirmModal title="Discard changes?" confirmLabel="Discard" danger
          onConfirm={async () => { const d = pendingDate; setPendingDate(null); goTo(d); }}
          onClose={() => setPendingDate(null)}>
          <p>You typed amounts for {formatDate(entryDate)} that are not saved. Go to {formatDate(pendingDate)} and discard them?</p>
        </ConfirmModal>
      )}

      {payRow && (
        <PaymentsModal sheet={{ year, month, status: data.sheetStatus }}
          row={{ chitNo: payRow.chitNo, memberName: payRow.memberName, memberNameTamil: payRow.memberNameTamil, dueAmount: payRow.dueAmount }}
          onClose={() => setPayRow(null)} onChanged={reload} />
      )}
    </>
  );
}
