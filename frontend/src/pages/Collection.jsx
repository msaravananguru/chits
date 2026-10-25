import { useCallback, useEffect, useState } from 'react';
import { post } from '../api.js';
import MonthSelect from '../components/MonthSelect.jsx';
import CollectionDaily from '../components/CollectionDaily.jsx';
import CollectionSingle from '../components/CollectionSingle.jsx';
import CollectionAll from '../components/CollectionAll.jsx';

const TAB_KEY = 'chits.collectionTab';

function readTab() {
  try {
    const t = localStorage.getItem(TAB_KEY);
    return t === 'SINGLE' || t === 'ALL' ? t : 'DAILY';
  } catch { return 'DAILY'; }
}

// Collection page: Daily (part payers, DLC style grid) and Single (one payment a month) tabs.
export default function Collection() {
  const [tab, setTab] = useState(readTab);
  const [period, setPeriod] = useState(null);   // null = the open month
  const [data, setData] = useState(null);
  const [counts, setCounts] = useState(null);   // { daily, single } for the tab labels
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');

  const load = useCallback(async () => {
    try {
      const d = await post(`/collection/${tab.toLowerCase()}`, period || {});
      setData(d);
      setCounts({ daily: d.dailyCount, single: d.singleCount });
      setError('');
    } catch (e) {
      if (e.status === 404 && period) { setPeriod(null); return; }
      setError(e.message);
    } finally {
      setLoading(false);
    }
  }, [tab, period]);

  useEffect(() => { setLoading(true); load(); }, [load]);

  useEffect(() => {
    if (!toast) return undefined;
    const t = setTimeout(() => setToast(''), 3500);
    return () => clearTimeout(t);
  }, [toast]);

  function chooseTab(t) {
    if (t === tab) return;
    try { localStorage.setItem(TAB_KEY, t); } catch { /* storage blocked */ }
    setData(null);
    setTab(t);
  }

  const locked = data && data.sheetStatus === 'LOCKED';

  return (
    <>
      <div className="page-head">
        <h1>Collection</h1>
        {data && (
          <div className="head-tools">
            <MonthSelect months={data.months} year={data.year} month={data.month}
              onChange={(p) => { setData(null); setPeriod(p); }} />
            {locked && <span className="pill pill-ok">Final · locked</span>}
          </div>
        )}
      </div>

      <div className="ctabs" role="tablist" aria-label="Collection type">
        <button type="button" role="tab" aria-selected={tab === 'DAILY'}
          className={'ctab' + (tab === 'DAILY' ? ' on' : '')} onClick={() => chooseTab('DAILY')}>
          Daily<small>{counts ? `${counts.daily} members · part by part` : 'part by part'}</small>
        </button>
        <button type="button" role="tab" aria-selected={tab === 'SINGLE'}
          className={'ctab' + (tab === 'SINGLE' ? ' on' : '')} onClick={() => chooseTab('SINGLE')}>
          Single<small>{counts ? `${counts.single} members · single payment` : 'single payment'}</small>
        </button>
        <button type="button" role="tab" aria-selected={tab === 'ALL'}
          className={'ctab' + (tab === 'ALL' ? ' on' : '')} onClick={() => chooseTab('ALL')}>
          All<small>{counts ? `${counts.daily + counts.single} members · everyone` : 'everyone'}</small>
        </button>
      </div>

      {error && <div className="alert alert-error">{error} <button className="link" onClick={load}>Retry</button></div>}

      {loading || !data ? (
        !error && <div className="card empty"><p>Loading…</p></div>
      ) : data.sheetStatus === 'NONE' ? (
        <div className="card empty"><p>No sheet yet. Generate the first month on the Monthly Sheet page.</p></div>
      ) : tab === 'DAILY' ? (
        <CollectionDaily key={`d-${data.year}-${data.month}`} data={data} reload={load} notify={setToast} />
      ) : tab === 'ALL' ? (
        <CollectionAll key={`a-${data.year}-${data.month}`} data={data} reload={load} />
      ) : (
        <CollectionSingle key={`s-${data.year}-${data.month}`} data={data} reload={load} notify={setToast} />
      )}

      {toast && <div className="toast" role="status">{toast}</div>}
    </>
  );
}
