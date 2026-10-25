import { useCallback, useEffect, useMemo, useState } from 'react';
import { post } from '../api.js';
import { formatDate, formatINR, formatPlan } from '../format.js';
import MemberFormModal from '../components/MemberFormModal.jsx';
import TakenModal from '../components/TakenModal.jsx';

export default function Members() {
  const [members, setMembers] = useState([]);
  const [plans, setPlans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('ACTIVE');
  const [modal, setModal] = useState(null); // { type: 'add' | 'edit' | 'taken', chit? }
  const [toast, setToast] = useState('');

  const load = useCallback(async () => {
    setError('');
    try {
      const [m, p] = await Promise.all([post('/chits/list', {}), post('/plans/list')]);
      setMembers(m);
      setPlans(p);
    } catch (e) {
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    if (!toast) return undefined;
    const t = setTimeout(() => setToast(''), 3500);
    return () => clearTimeout(t);
  }, [toast]);

  const planMap = useMemo(
    () => Object.fromEntries(plans.map((p) => [Number(p.planAmount), p])),
    [plans]
  );

  const stats = useMemo(() => {
    const active = members.filter((m) => m.status === 'ACTIVE');
    const taken = active.filter((m) => m.takenMonth != null).length;
    return { active: active.length, taken, notTaken: active.length - taken };
  }, [members]);

  const visible = useMemo(() => {
    const q = search.trim().toLowerCase();
    return members.filter((m) => {
      if (statusFilter !== 'ALL' && m.status !== statusFilter) return false;
      if (!q) return true;
      return (
        String(m.chitNo).includes(q) ||
        m.memberName.toLowerCase().includes(q) ||
        (m.memberNameTamil || '').toLowerCase().includes(q)
      );
    });
  }, [members, search, statusFilter]);

  const onSaved = (message) => {
    setModal(null);
    setToast(message);
    load();
  };

  const canMarkTaken = (m) => m.status === 'ACTIVE' && m.takenMonth == null;

  const actions = (m) => (
    <div className="row-actions">
      <button className="btn btn-small" onClick={() => setModal({ type: 'edit', chit: m })}>Edit</button>
      <button className="btn btn-small" disabled={!canMarkTaken(m)}
        onClick={() => setModal({ type: 'taken', chit: m })}>Mark taken</button>
    </div>
  );

  const takenPill = (m) =>
    m.takenMonth != null
      ? <span className="pill pill-warn">Month {m.takenMonth}</span>
      : <span className="pill pill-soft">Not taken</span>;

  const statusPill = (m) =>
    <span className={'pill ' + (m.status === 'ACTIVE' ? 'pill-ok' : 'pill-muted')}>
      {m.status === 'ACTIVE' ? 'Active' : 'Closed'}
    </span>;

  return (
    <>
      <div className="page-head">
        <h1>Members</h1>
        <button className="btn btn-primary only-desktop" onClick={() => setModal({ type: 'add' })}>
          + Add member
        </button>
      </div>

      <div className="stats">
        <div className="stat"><b>{stats.active}</b><span>Active</span></div>
        <div className="stat"><b>{stats.taken}</b><span>Taken</span></div>
        <div className="stat"><b>{stats.notTaken}</b><span>Not taken</span></div>
      </div>

      <div className="toolbar">
        <input className="search" type="search" placeholder="Search name or chit no"
          value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Search members" />
        <div className="segmented" role="group" aria-label="Status filter">
          {[['ACTIVE', 'Active'], ['CLOSED', 'Closed'], ['ALL', 'All']].map(([v, label]) => (
            <button key={v} className={statusFilter === v ? 'on' : ''} onClick={() => setStatusFilter(v)}>
              {label}
            </button>
          ))}
        </div>
      </div>

      {error && (
        <div className="alert alert-error">
          {error} <button className="link" onClick={load}>Retry</button>
        </div>
      )}

      {loading ? (
        <div className="card empty"><p>Loading…</p></div>
      ) : visible.length === 0 ? (
        <div className="card empty">
          <p>{members.length === 0 ? 'No members yet. Add the first one.' : 'No members match this search.'}</p>
        </div>
      ) : (
        <>
          {/* desktop: table */}
          <div className="card table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Chit no</th><th>Member</th><th>Plan</th><th>Joined</th>
                  <th>Taken</th><th className="right">Opening paid</th><th>Status</th><th className="right">Actions</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((m) => (
                  <tr key={m.chitNo}>
                    <td className="num">{m.chitNo}</td>
                    <td>
                      <div className="name">{m.memberName}</div>
                      {m.memberNameTamil && <div className="name-ta" lang="ta">{m.memberNameTamil}</div>}
                    </td>
                    <td>{formatPlan(m.planAmount)}</td>
                    <td>{formatDate(m.joinedDate)}</td>
                    <td>{takenPill(m)}</td>
                    <td className="right">{Number(m.openingPaid) > 0 ? formatINR(m.openingPaid) : '–'}</td>
                    <td>{statusPill(m)}</td>
                    <td className="right">{actions(m)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* mobile: cards */}
          <div className="cards">
            {visible.map((m) => (
              <div className="card member-card" key={m.chitNo}>
                <div className="member-card-top">
                  <span className="chit-badge">{m.chitNo}</span>
                  <div className="member-card-name">
                    <div className="name">{m.memberName}</div>
                    {m.memberNameTamil && <div className="name-ta" lang="ta">{m.memberNameTamil}</div>}
                  </div>
                </div>
                <div className="member-card-meta">
                  <span>{formatPlan(m.planAmount)}</span>
                  <span>Joined {formatDate(m.joinedDate)}</span>
                </div>
                {Number(m.openingPaid) > 0 && (
                  <div className="member-card-meta"><span>Opening paid</span><span>{formatINR(m.openingPaid)}</span></div>
                )}
                <div className="member-card-pills">{takenPill(m)}{statusPill(m)}</div>
                {actions(m)}
              </div>
            ))}
          </div>
        </>
      )}

      <button className="fab only-mobile" onClick={() => setModal({ type: 'add' })} aria-label="Add member">+</button>

      {modal && (modal.type === 'add' || modal.type === 'edit') && (
        <MemberFormModal mode={modal.type} chit={modal.chit} plans={plans}
          onClose={() => setModal(null)} onSaved={onSaved} />
      )}
      {modal && modal.type === 'taken' && (
        <TakenModal chit={modal.chit} plan={planMap[Number(modal.chit.planAmount)]}
          onClose={() => setModal(null)} onSaved={onSaved} />
      )}

      {toast && <div className="toast" role="status">{toast}</div>}
    </>
  );
}
