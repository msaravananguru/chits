import { monthLabel } from '../format.js';

// Month picker used on the Collection page: "Oct 2026 · open" / "Sep 2026 · final"
export default function MonthSelect({ months, year, month, onChange }) {
  if (!months || months.length === 0) return null;
  const value = `${year}-${month}`;
  return (
    <select className="month-select" value={value} aria-label="Month"
      onChange={(e) => {
        const [y, m] = e.target.value.split('-').map(Number);
        onChange({ year: y, month: m });
      }}>
      {months.map((m) => (
        <option key={`${m.year}-${m.month}`} value={`${m.year}-${m.month}`}>
          {monthLabel(m.year, m.month)} · {m.status === 'DRAFT' ? 'open' : 'final'}
        </option>
      ))}
    </select>
  );
}
