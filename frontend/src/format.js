export function formatPlan(amount) {
  const n = Number(amount);
  if (n >= 100000) return `${trim(n / 100000)} Lakh`;
  return `${trim(n / 1000)}K`;
}

function trim(n) {
  return String(Math.round(n * 100) / 100);
}

export function formatINR(amount) {
  return '₹' + Number(amount).toLocaleString('en-IN', { maximumFractionDigits: 2 });
}

// "2026-10-01" -> "01 Oct 2026" (parsed by hand so the time zone can never shift the day)
export function formatDate(iso) {
  if (!iso) return '';
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d).toLocaleDateString('en-IN', {
    day: '2-digit', month: 'short', year: 'numeric',
  });
}

export function today() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

// (2026, 10) -> "Oct 2026"
export function monthLabel(year, month) {
  return `${MONTHS[month - 1]} ${year}`;
}

// number of months pending, e.g. 19.75 (always 2 decimals)
export function formatMonths(n) {
  return Number(n).toFixed(2);
}
