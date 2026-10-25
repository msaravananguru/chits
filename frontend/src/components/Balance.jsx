import { formatINR } from '../format.js';

// previous balance: green "+₹800 adv" (paid ahead), red "−₹2,000 due" (left unpaid earlier), "–" (settled)
export function PrevBalance({ value }) {
  const v = Number(value) || 0;
  if (v > 0) return <span className="pb-adv">+{formatINR(v)} adv</span>;
  if (v < 0) return <span className="pb-arr">−{formatINR(-v)} due</span>;
  return <span className="muted">–</span>;
}

// small line for phone lists: "Adv ₹800" / "Arrears ₹2,000" / nothing
export function PrevText({ value }) {
  const v = Number(value) || 0;
  if (v > 0) return <span className="pb-adv nw">Adv {formatINR(v)}</span>;
  if (v < 0) return <span className="pb-arr nw">Arrears {formatINR(-v)}</span>;
  return null;
}

// nothing was received this month but the advance covers the whole due
export const fromAdvance = (r) => r.status === 'PAID' && Number(r.paidAmount) === 0 && Number(r.dueAmount) > 0;
