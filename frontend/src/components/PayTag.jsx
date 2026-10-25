import { formatINR } from '../format.js';

// "Daily · ₹200" / "Daily" / "Single" tag shown under a member name
export default function PayTag({ payMode, dailyAmount }) {
  if (payMode === 'DAILY') {
    return (
      <span className="dtag">
        Daily{Number(dailyAmount) > 0 ? ` · ${formatINR(dailyAmount)}` : ''}
      </span>
    );
  }
  return <span className="stag">Single</span>;
}
